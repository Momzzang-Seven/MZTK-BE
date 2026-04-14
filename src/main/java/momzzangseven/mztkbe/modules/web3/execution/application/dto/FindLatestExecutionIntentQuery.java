package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;

public record FindLatestExecutionIntentQuery(
    Long requesterUserId, ExecutionResourceTypeCode resourceType, String resourceId) {}
