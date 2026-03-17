package dev.zeann3th.stresspilot.core.services.parsers.endpoints.strategies;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.constants.FileFormat;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.ParserType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.parsers.endpoints.ParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j(topic = "GRPC_PARSER")
@Component
@RequiredArgsConstructor
@SuppressWarnings("java:S112")
public class ProtoParser implements ParserService {

    private static final String PROTOC = "protoc";

    @Override
    public String getType() {
        return ParserType.PROTO.name();
    }

    @Override
    public List<String> getSupportedFormats() {
        return List.of(FileFormat.PROTO);
    }

    @Override
    public boolean supports(String filename, String contentType, String content) {
        if (filename == null) {
            return false;
        }

        return filename.endsWith(".proto") || filename.endsWith(".pb");
    }

    @Override
    public List<EndpointEntity> unmarshal(String spec) {
        try {
            String version = UUID.randomUUID().toString();
            Path versionFolder = getAppBaseDir().resolve(Paths.get("core", "grpc", "schemas", version));
            Files.createDirectories(versionFolder);

            Path protoFile = versionFolder.resolve("service.proto");
            Files.writeString(protoFile, spec);

            Path descriptorFile = versionFolder.resolve("service.pb");
            generateDescriptor(protoFile, descriptorFile);

            return extractEndpointsFromDescriptor(descriptorFile, versionFolder);

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006, Map.of(Constants.REASON, "Interrupted"));
        } catch (Exception e) {
            log.error("Failed to parse gRPC proto", e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006, Map.of(Constants.REASON, e.getMessage()));
        }
    }

    private void generateDescriptor(Path protoFile, Path descriptorFile) throws IOException, InterruptedException {
        Path protoDir = protoFile.getParent();

        List<String> command = List.of(
                PROTOC,
                "--proto_path=" + protoDir.toAbsolutePath(),
                "--descriptor_set_out=" + descriptorFile.toAbsolutePath(),
                "--include_imports",
                "--include_source_info",
                protoFile.getFileName().toString()
        );

        runCommand(command, protoDir);
    }

    private List<EndpointEntity> extractEndpointsFromDescriptor(Path descriptorFile, Path storageFolder) throws Exception {
        byte[] descriptorBytes = Files.readAllBytes(descriptorFile);
        DescriptorProtos.FileDescriptorSet descriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes);

        List<EndpointEntity> endpoints = new ArrayList<>();

        Map<String, Descriptors.FileDescriptor> parsedDescriptors = new HashMap<>();

        for (DescriptorProtos.FileDescriptorProto fileProto : descriptorSet.getFileList()) {

            Descriptors.FileDescriptor[] dependencies = new Descriptors.FileDescriptor[fileProto.getDependencyCount()];
            for (int i = 0; i < fileProto.getDependencyCount(); i++) {
                String dependencyName = fileProto.getDependency(i);
                dependencies[i] = parsedDescriptors.get(dependencyName);
            }

            Descriptors.FileDescriptor fileDescriptor =
                    Descriptors.FileDescriptor.buildFrom(fileProto, dependencies);

            parsedDescriptors.put(fileProto.getName(), fileDescriptor);

            for (Descriptors.ServiceDescriptor service : fileDescriptor.getServices()) {
                for (Descriptors.MethodDescriptor method : service.getMethods()) {

                    EndpointEntity entity = EndpointEntity.builder()
                            .name(method.getName())
                            .type(EndpointType.GRPC.name())
                            .url("{{url}}")
                            .grpcServiceName(service.getFullName())
                            .grpcMethodName(method.getName())
                            .grpcStubPath(storageFolder.toAbsolutePath().toString())
                            .build();

                    endpoints.add(entity);
                }
            }
        }
        return endpoints;
    }

    private void runCommand(List<String> command, Path workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(output::append);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Protoc failed: " + output);
        }
    }

    private Path getAppBaseDir() {
        String appDir = System.getProperty(Constants.PILOT_HOME);
        if (appDir != null && !appDir.isBlank()) return Paths.get(appDir);
        return Paths.get(System.getProperty(Constants.USER_HOME), Constants.APP_DIR);
    }
}