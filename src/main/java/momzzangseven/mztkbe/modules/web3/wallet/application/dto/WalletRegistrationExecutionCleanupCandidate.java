package momzzangseven.mztkbe.modules.web3.wallet.application.dto;

public record WalletRegistrationExecutionCleanupCandidate(
    Long id, String executionIntentId, String resourceId, String resourceType, String actionType) {}
