package dev.zeann3th.stresspilot.core.services.executors.strategies;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorService;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.MockDataUtils;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "[GrpcExecutor]")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S112")
public class GrpcEndpointExecutor implements EndpointExecutorService {

    private final JsonMapper jsonMapper;
    private final ConfigService configService;

    private final Map<String, Descriptors.MethodDescriptor> descriptorCache = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    private ProxyDetector proxyDetector;

    @PostConstruct
    public void init() {
        proxyDetector = targetAddress -> {
            String proxyHost = configService.getValue(ConfigKey.HTTP_PROXY_HOST.name()).orElse(null);
            Integer proxyPort = configService.getValue(ConfigKey.HTTP_PROXY_PORT.name())
                    .map(Integer::parseInt).orElse(null);
            String proxyUser = configService.getValue(ConfigKey.HTTP_PROXY_USERNAME.name()).orElse(null);
            String proxyPass = configService.getValue(ConfigKey.HTTP_PROXY_PASSWORD.name()).orElse(null);

            if (proxyHost == null || proxyPort == null)
                return null;

            log.debug("Routing gRPC through proxy {}:{}", proxyHost, proxyPort);
            var builder = HttpConnectProxiedSocketAddress.newBuilder()
                    .setProxyAddress(new InetSocketAddress(proxyHost, proxyPort))
                    .setTargetAddress((InetSocketAddress) targetAddress);
            if (proxyUser != null && proxyPass != null)
                builder.setUsername(proxyUser).setPassword(proxyPass);
            return builder.build();
        };
    }

    @PreDestroy
    public void cleanup() {
        channelCache.values().forEach(ManagedChannel::shutdownNow);
    }

    @Override
    public String getType() {
        return EndpointType.GRPC.name();
    }

    @Override
    public ExecuteEndpointResponse execute(EndpointEntity endpoint,
            Map<String, Object> environment,
            ExecutionContext context) {
        try {
            String target = interpolate(endpoint.getUrl(), environment);
            String bodyJson = interpolate(endpoint.getBody(), environment);

            Descriptors.MethodDescriptor methodProto = getDescriptor(endpoint);
            DynamicMessage requestMsg = buildRequest(methodProto, bodyJson);
            ManagedChannel channel = getChannel(target);

            long start = System.currentTimeMillis();
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = buildGrpcMethod(methodProto, endpoint);
            String responseJson = executeCall(channel, grpcMethod, requestMsg);
            long elapsed = System.currentTimeMillis() - start;

            Object data = jsonMapper.readValue(responseJson, Object.class);
            return ExecuteEndpointResponse.builder()
                    .success(true)
                    .statusCode(HttpStatus.OK.value())
                    .message("OK")
                    .responseTimeMs(elapsed)
                    .data(data)
                    .rawResponse(responseJson)
                    .build();

        } catch (Exception e) {
            log.error("gRPC execution error", e);
            return ExecuteEndpointResponse.builder()
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message(e.getMessage())
                    .responseTimeMs(0)
                    .data(Map.of("error", String.valueOf(e.getMessage())))
                    .rawResponse(e.toString())
                    .build();
        }
    }

    private String interpolate(String raw, Map<String, Object> env) {
        if (raw == null)
            return "";
        String out = raw;
        if (out.contains("{{"))
            out = DataUtils.replaceVariables(out, env);
        if (out.contains("@{"))
            out = MockDataUtils.interpolate(out);
        return out;
    }

    private Descriptors.MethodDescriptor getDescriptor(EndpointEntity e) {
        String key = e.getGrpcStubPath() + "|" + e.getGrpcServiceName() + "|" + e.getGrpcMethodName();
        return descriptorCache.computeIfAbsent(key, _ -> {
            try {
                Path pbPath = Paths.get(e.getGrpcStubPath(), "service.pb");
                return resolveMethodDescriptor(pbPath, e.getGrpcServiceName(), e.getGrpcMethodName());
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load gRPC descriptor: " + ex.getMessage(), ex);
            }
        });
    }

    private ManagedChannel getChannel(String target) {
        return channelCache.computeIfAbsent(target, t -> ManagedChannelBuilder.forTarget(t)
                .usePlaintext()
                .proxyDetector(proxyDetector)
                .build());
    }

    private DynamicMessage buildRequest(Descriptors.MethodDescriptor method, String json) throws IOException {
        DynamicMessage.Builder b = DynamicMessage.newBuilder(method.getInputType());
        JsonFormat.parser().ignoringUnknownFields().merge(json, b);
        return b.build();
    }

    private MethodDescriptor<DynamicMessage, DynamicMessage> buildGrpcMethod(
            Descriptors.MethodDescriptor proto, EndpointEntity e) {
        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(methodType(proto))
                .setFullMethodName(MethodDescriptor.generateFullMethodName(
                        e.getGrpcServiceName(), e.getGrpcMethodName()))
                .setRequestMarshaller(ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(proto.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(proto.getOutputType())))
                .build();
    }

    private String executeCall(ManagedChannel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> method,
            DynamicMessage request) throws IOException {
        if (method.getType() == MethodDescriptor.MethodType.UNARY) {
            DynamicMessage resp = ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT, request);
            return JsonFormat.printer().print(resp);
        }
        if (method.getType() == MethodDescriptor.MethodType.SERVER_STREAMING) {
            Iterator<DynamicMessage> it = ClientCalls.blockingServerStreamingCall(
                    channel, method, CallOptions.DEFAULT, request);
            var sb = new StringBuilder("[");
            while (it.hasNext()) {
                sb.append(JsonFormat.printer().print(it.next()));
                if (it.hasNext())
                    sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
        throw new UnsupportedOperationException("Client/Bidi streaming not yet supported.");
    }

    private Descriptors.MethodDescriptor resolveMethodDescriptor(
            Path pbPath, String svcName, String methodName) throws Exception {
        log.debug("Loading .pb descriptor: {}", pbPath);
        try (FileInputStream fis = new FileInputStream(pbPath.toFile())) {
            DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(fis);
            for (DescriptorProtos.FileDescriptorProto fp : fds.getFileList()) {
                Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(
                        fp, new Descriptors.FileDescriptor[] {});
                for (Descriptors.ServiceDescriptor svc : fd.getServices()) {
                    if (namesMatch(svc.getFullName(), svcName)) {
                        Descriptors.MethodDescriptor m = svc.findMethodByName(methodName);
                        if (m != null)
                            return m;
                    }
                }
            }
        }
        throw CommandExceptionBuilder.exception(ErrorCode.ER0008);
    }

    private boolean namesMatch(String proto, String db) {
        String p = proto.startsWith(".") ? proto.substring(1) : proto;
        String d = db.startsWith(".") ? db.substring(1) : db;
        return p.equals(d);
    }

    private MethodDescriptor.MethodType methodType(Descriptors.MethodDescriptor m) {
        if (!m.isClientStreaming() && !m.isServerStreaming())
            return MethodDescriptor.MethodType.UNARY;
        if (!m.isClientStreaming())
            return MethodDescriptor.MethodType.SERVER_STREAMING;
        if (!m.isServerStreaming())
            return MethodDescriptor.MethodType.CLIENT_STREAMING;
        return MethodDescriptor.MethodType.BIDI_STREAMING;
    }
}
