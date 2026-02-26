package dev.zeann3th.stresspilot.core.services.executors.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import javax.annotation.Nullable;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j(topic = "GrpcEndpointExecutor")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S112")
public class GrpcEndpointExecutor implements EndpointExecutorService {

    private final ObjectMapper objectMapper;

    private final Map<String, Descriptors.MethodDescriptor> descriptorCache = new ConcurrentHashMap<>();

    private final Map<String, ManagedChannel> channelCache = new ConcurrentHashMap<>();

    private final ConfigService configService;

    private ProxyDetector proxyDetector;

    @PostConstruct
    public void init() {
        proxyDetector = new ProxyDetector() {
            @Nullable
            @Override
            public io.grpc.ProxiedSocketAddress proxyFor(SocketAddress targetAddress) {
                String proxyHost = configService.getValue(ConfigKey.HTTP_PROXY_HOST.name()).orElse(null);
                Integer proxyPort = configService.getValue(ConfigKey.HTTP_PROXY_PORT.name()).map(Integer::parseInt).orElse(null);
                String proxyUser = configService.getValue(ConfigKey.HTTP_PROXY_USERNAME.name()).orElse(null);
                String proxyPass = configService.getValue(ConfigKey.HTTP_PROXY_PASSWORD.name()).orElse(null);

                if (proxyHost == null || proxyPort == null) {
                    return null;
                }

                log.debug("Routing gRPC traffic to {} through proxy {}:{}", targetAddress, proxyHost, proxyPort);
                var builder = HttpConnectProxiedSocketAddress.newBuilder()
                        .setProxyAddress(new InetSocketAddress(proxyHost, proxyPort))
                        .setTargetAddress((InetSocketAddress) targetAddress);

                if (proxyUser != null && proxyPass != null) {
                    builder.setUsername(proxyUser).setPassword(proxyPass);
                }

                return builder.build();
            }
        };
    }

    @Override
    public String getType() {
        return EndpointType.GRPC.name();
    }

    @Override
    public ExecuteEndpointResponse execute(EndpointEntity endpoint, Map<String, Object> environment, ExecutionContext context) {
        try {
            String target = parseTarget(endpoint.getUrl(), environment);
            String bodyJson = parseBody(endpoint.getBody(), environment);

            Descriptors.MethodDescriptor methodProto = getDescriptor(endpoint);

            DynamicMessage requestMessage = buildRequestMessage(methodProto, bodyJson);

            ManagedChannel channel = getChannel(target);

            long startTime = System.currentTimeMillis();

            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = buildGrpcMethod(methodProto, endpoint);

            String responseString = executeCall(channel, grpcMethod, requestMessage);

            long responseTimeMs = System.currentTimeMillis() - startTime;

            Object dataObject = objectMapper.readValue(responseString, Object.class);

            return ExecuteEndpointResponse.builder()
                    .success(true)
                    .statusCode(HttpStatus.OK.value())
                    .message("OK")
                    .responseTimeMs(responseTimeMs)
                    .data(dataObject)
                    .rawResponse(responseString)
                    .build();

        } catch (Exception e) {
            log.error("gRPC Error", e);
            return ExecuteEndpointResponse.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .success(false)
                    .message(e.getMessage())
                    .data(e.toString())
                    .build();
        }
    }

    @PreDestroy
    public void cleanup() {
        channelCache.values().forEach(ManagedChannel::shutdownNow);
    }

    private String parseTarget(String rawUrl, Map<String, Object> environment) {
        String target = rawUrl;
        if (target.contains("{{")) {
            target = DataUtils.replaceVariables(target, environment);
        }
        if (target.contains("@{")) {
            target = MockDataUtils.interpolate(target);
        }
        return target;
    }

    private String parseBody(String rawBody, Map<String, Object> environment) {
        String body = rawBody;
        if (body.contains("{{")) {
            body = DataUtils.replaceVariables(body, environment);
        }
        if (body.contains("@{")) {
            body = MockDataUtils.interpolate(body);
        }
        return body;
    }

    private Descriptors.MethodDescriptor getDescriptor(EndpointEntity entity) {
        String key = entity.getGrpcStubPath() + "|" + entity.getGrpcServiceName() + "|" + entity.getGrpcMethodName();

        return descriptorCache.computeIfAbsent(key, _ -> {
            try {
                Path descriptorPath = Paths.get(entity.getGrpcStubPath(), "service.pb");
                return resolveMethodDescriptor(descriptorPath, entity.getGrpcServiceName(), entity.getGrpcMethodName());
            } catch (Exception e) {
                throw new RuntimeException("Failed to load gRPC descriptor: " + e.getMessage(), e);
            }
        });
    }

    private ManagedChannel getChannel(String target) {
        return channelCache.computeIfAbsent(target, t ->
                ManagedChannelBuilder.forTarget(t)
                        .usePlaintext()
                        .proxyDetector(proxyDetector)
                        .build()
        );
    }

    private DynamicMessage buildRequestMessage(Descriptors.MethodDescriptor methodDesc, String jsonBody) throws IOException {
        DynamicMessage.Builder requestBuilder = DynamicMessage.newBuilder(methodDesc.getInputType());
        JsonFormat.parser().ignoringUnknownFields().merge(jsonBody, requestBuilder);
        return requestBuilder.build();
    }

    private MethodDescriptor<DynamicMessage, DynamicMessage> buildGrpcMethod(Descriptors.MethodDescriptor methodProto, EndpointEntity entity) {
        MethodDescriptor.MethodType type = getMethodType(methodProto);

        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(type)
                .setFullMethodName(MethodDescriptor.generateFullMethodName(
                        entity.getGrpcServiceName(),
                        entity.getGrpcMethodName()))
                .setRequestMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodProto.getInputType())))
                .setResponseMarshaller(ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(methodProto.getOutputType())))
                .build();
    }

    private String executeCall(ManagedChannel channel,
                               MethodDescriptor<DynamicMessage, DynamicMessage> methodDesc,
                               DynamicMessage request) throws IOException {

        MethodDescriptor.MethodType type = methodDesc.getType();

        if (type == MethodDescriptor.MethodType.UNARY) {
            DynamicMessage response = ClientCalls.blockingUnaryCall(channel, methodDesc, CallOptions.DEFAULT, request);
            return JsonFormat.printer().print(response);

        } else if (type == MethodDescriptor.MethodType.SERVER_STREAMING) {
            Iterator<DynamicMessage> responses = ClientCalls.blockingServerStreamingCall(channel, methodDesc, CallOptions.DEFAULT, request);
            StringBuilder sb = new StringBuilder("[");
            while (responses.hasNext()) {
                sb.append(JsonFormat.printer().print(responses.next()));
                if (responses.hasNext()) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }

        throw new UnsupportedOperationException("Client Streaming/Bidi Streaming not supported yet.");
    }

    private Descriptors.MethodDescriptor resolveMethodDescriptor(Path pbPath, String serviceName, String methodName) throws Exception {
        log.debug("Loading .pb file from disk: {}", pbPath);
        try (FileInputStream fis = new FileInputStream(pbPath.toFile())) {
            DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet.parseFrom(fis);

            for (DescriptorProtos.FileDescriptorProto fileProto : set.getFileList()) {
                Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[]{});

                for (Descriptors.ServiceDescriptor svc : fd.getServices()) {
                    if (namesMatch(svc.getFullName(), serviceName)) {
                        Descriptors.MethodDescriptor method = svc.findMethodByName(methodName);
                        if (method != null) {
                            return method;
                        }
                    }
                }
            }
        }
        throw CommandExceptionBuilder.exception(ErrorCode.SP0008);
    }

    private boolean namesMatch(String protoName, String dbName) {
        String p = protoName.startsWith(".") ? protoName.substring(1) : protoName;
        String d = dbName.startsWith(".") ? dbName.substring(1) : dbName;
        return p.equals(d);
    }

    private MethodDescriptor.MethodType getMethodType(Descriptors.MethodDescriptor method) {
        boolean clientStreaming = method.isClientStreaming();
        boolean serverStreaming = method.isServerStreaming();

        if (!clientStreaming && !serverStreaming) return MethodDescriptor.MethodType.UNARY;
        if (!clientStreaming) return MethodDescriptor.MethodType.SERVER_STREAMING;
        if (!serverStreaming) return MethodDescriptor.MethodType.CLIENT_STREAMING;
        return MethodDescriptor.MethodType.BIDI_STREAMING;
    }
}
