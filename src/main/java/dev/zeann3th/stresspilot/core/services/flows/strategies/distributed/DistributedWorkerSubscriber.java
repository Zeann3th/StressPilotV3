package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.strategies.DefaultFlowExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j(topic = "[DistributedWorkerSubscriber]")
@Component
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedWorkerSubscriber implements MessageListener {
    private final ActiveRunRegistry activeRunRegistry;
    private final String localNodeId;
    private final String keyPrefix;
    private final JsonMapper jsonMapper;
    private final WorkerExecutionRunner workerExecutionRunner;
    private final StringRedisTemplate redisTemplate;
    private RedisMessageListenerContainer listenerContainer;

    @Autowired
    public DistributedWorkerSubscriber(
            ActiveRunRegistry activeRunRegistry,
            DefaultFlowExecutor defaultFlowExecutor,
            StringRedisTemplate redisTemplate,
            @Value("${application.distributed.node-id:local}") String localNodeId,
            @Value("${application.distributed.key-prefix:stresspilot}") String keyPrefix
    ) {
        this(
                activeRunRegistry,
                localNodeId,
                keyPrefix,
                new JsonMapper(),
                defaultFlowExecutor::executeAssignedWorkers,
                redisTemplate);
    }

    DistributedWorkerSubscriber(
            ActiveRunRegistry activeRunRegistry,
            String localNodeId,
            String keyPrefix,
            JsonMapper jsonMapper,
            WorkerExecutionRunner workerExecutionRunner
    ) {
        this(activeRunRegistry, localNodeId, keyPrefix, jsonMapper, workerExecutionRunner, null);
    }

    private DistributedWorkerSubscriber(
            ActiveRunRegistry activeRunRegistry,
            String localNodeId,
            String keyPrefix,
            JsonMapper jsonMapper,
            WorkerExecutionRunner workerExecutionRunner,
            StringRedisTemplate redisTemplate
    ) {
        this.activeRunRegistry = activeRunRegistry;
        this.localNodeId = localNodeId;
        this.keyPrefix = keyPrefix;
        this.jsonMapper = jsonMapper;
        this.workerExecutionRunner = workerExecutionRunner;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    void subscribe() {
        if (redisTemplate == null) {
            return;
        }

        DistributedChannels channels = new DistributedChannels(keyPrefix);
        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisTemplate.getConnectionFactory());
        listenerContainer.addMessageListener(this, new ChannelTopic(channels.workChannel()));
        listenerContainer.addMessageListener(this, new ChannelTopic(channels.stopChannel()));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
    }

    @PreDestroy
    void unsubscribe() throws Exception {
        if (listenerContainer != null) {
            listenerContainer.destroy();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        DistributedChannels channels = new DistributedChannels(keyPrefix);
        if (channels.workChannel().equals(channel)) {
            handleWorkMessage(body);
        } else if (channels.stopChannel().equals(channel)) {
            handleStopMessage(body);
        }
    }

    void handleWorkMessage(String message) {
        try {
            DistributedEventPublisher.WorkloadPayload payload = jsonMapper.readValue(
                    message,
                    DistributedEventPublisher.WorkloadPayload.class);
            if (!localNodeId.equals(payload.targetNodeId()) || payload.assignedThreads() <= 0) {
                return;
            }

            AtomicBoolean stopSignal = activeRunRegistry.registerRun(payload.runId());
            try {
                FlowExecutionContext context = toContext(payload, stopSignal);
                workerExecutionRunner.execute(context);
            } finally {
                activeRunRegistry.deregisterRun(payload.runId());
            }
        } catch (Exception e) {
            log.warn("Failed to handle distributed workload: {}", e.getMessage());
        }
    }

    void handleStopMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        activeRunRegistry.interruptRun(message);
    }

    private FlowExecutionContext toContext(
            DistributedEventPublisher.WorkloadPayload payload,
            AtomicBoolean stopSignal
    ) {
        FlowEntity flow = FlowEntity.builder()
                .id(payload.flowId())
                .type(payload.flowType())
                .name("Distributed worker flow " + payload.flowId())
                .build();
        RunEntity run = RunEntity.builder()
                .id(payload.runId())
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(payload.assignedThreads())
                .duration(payload.totalDuration())
                .loopCount(payload.loopCount())
                .rampUpDuration(payload.rampUpDuration())
                .startedAt(payload.runStartedAt())
                .build();
        RunFlowCommand command = RunFlowCommand.builder()
                .threads(payload.assignedThreads())
                .totalDuration(payload.totalDuration())
                .loopCount(payload.loopCount())
                .rampUpDuration(payload.rampUpDuration())
                .variables(payload.variables())
                .credentials(payload.credentials())
                .build();

        return FlowExecutionContext.builder()
                .runId(payload.runId())
                .run(run)
                .flowType(payload.flowType())
                .steps(toSteps(flow, payload.steps()))
                .baseEnvironment(payload.baseEnvironment())
                .variables(new ConcurrentHashMap<>(payload.variables() != null ? payload.variables() : Map.of()))
                .command(command)
                .stopSignal(stopSignal)
                .deadline(payload.deadline())
                .distributedWorker(true)
                .build();
    }

    private List<FlowStepEntity> toSteps(
            FlowEntity flow,
            List<DistributedEventPublisher.FlowStepPayload> payloads
    ) {
        if (payloads == null) {
            return List.of();
        }

        return payloads.stream()
                .map(payload -> (FlowStepEntity) FlowStepEntity.builder()
                        .id(payload.id())
                        .flow(flow)
                        .type(payload.type())
                        .endpoint(payload.endpoint() != null ? toEndpoint(payload.endpoint()) : null)
                        .preProcessor(payload.preProcessor())
                        .postProcessor(payload.postProcessor())
                        .nextIfTrue(payload.nextIfTrue())
                        .nextIfFalse(payload.nextIfFalse())
                        .condition(payload.condition())
                        .build())
                .toList();
    }

    private EndpointEntity toEndpoint(DistributedEventPublisher.EndpointPayload payload) {
        return EndpointEntity.builder()
                .id(payload.id())
                .name(payload.name())
                .description(payload.description())
                .type(payload.type())
                .url(payload.url())
                .body(payload.body())
                .successCondition(payload.successCondition())
                .httpMethod(payload.httpMethod())
                .httpHeaders(payload.httpHeaders())
                .httpParameters(payload.httpParameters())
                .grpcServiceName(payload.grpcServiceName())
                .grpcMethodName(payload.grpcMethodName())
                .grpcStubPath(payload.grpcStubPath())
                .build();
    }

    @FunctionalInterface
    interface WorkerExecutionRunner {
        void execute(FlowExecutionContext context);
    }
}
