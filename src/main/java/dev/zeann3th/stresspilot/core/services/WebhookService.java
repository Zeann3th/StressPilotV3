package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.webhook.WebhookCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.FlowStore;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import dev.zeann3th.stresspilot.core.services.parsers.WebhookParser;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "[WebhookService]")
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookParser webhookParser;
    private final ProjectService projectService;
    private final FlowService flowService;
    private final FlowStore flowStore;

    public void execute(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0014,
                    Map.of(Constants.REASON, "File is empty or null"));
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception _) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0014,
                    Map.of(Constants.REASON, "Failed to read file content"));
        }

        WebhookCommand command = webhookParser.unmarshalWebhook(content);
        validate(command);

        ProjectEntity project = projectService.importProject(command);

        List<FlowEntity> flows = flowStore.findAllByProjectId(project.getId());
        if (flows.isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Webhook import produced no flow"));
        }
        FlowEntity flow = flows.getFirst();

        WebhookCommand.RunConfig run = command.getRun();
        RunFlowCommand runCmd = RunFlowCommand.builder()
                .threads(run != null ? run.getThreads() : 1)
                .totalDuration(run != null ? run.getDuration() : 60)
                .rampUpDuration(run != null ? run.getRampUpDuration() : 0)
                .build();

        log.info("Webhook executing flow '{}' on project '{}' (threads={}, duration={}s)",
                flow.getName(), project.getName(), runCmd.getThreads(), runCmd.getTotalDuration());

        flowService.runFlow(flow.getId(), runCmd);
    }

    private void validate(WebhookCommand command) {
        if (command.getFlows() == null || command.getFlows().isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Webhook must define exactly one flow"));
        }
        if (command.getFlows().size() > 1) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Webhook must define exactly one flow, found " + command.getFlows().size()));
        }
    }
}
