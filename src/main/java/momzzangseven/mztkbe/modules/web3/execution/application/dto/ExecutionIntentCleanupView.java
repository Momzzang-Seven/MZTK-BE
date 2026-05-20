package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;

public record ExecutionIntentCleanupView(
    Long id,
    String publicId,
    ExecutionResourceType resourceType,
    String resourceId,
    ExecutionActionType actionType,
    Long requesterUserId,
    String payloadSnapshotJson) {}
