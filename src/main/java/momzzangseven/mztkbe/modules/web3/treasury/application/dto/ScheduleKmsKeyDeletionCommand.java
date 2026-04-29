package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

public record ScheduleKmsKeyDeletionCommand(
    String walletAlias,
    String kmsKeyId,
    String walletAddress,
    Long operatorUserId,
    int pendingWindowDays) {}
