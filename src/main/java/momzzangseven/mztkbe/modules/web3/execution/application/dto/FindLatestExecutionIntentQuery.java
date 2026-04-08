package momzzangseven.mztkbe.modules.web3.execution.application.dto;

public record FindLatestExecutionIntentQuery(
    Long requesterUserId, String resourceType, String resourceId) {}
