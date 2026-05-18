package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

public record ReplaceKmsKeyCommand(
    String walletAlias,
    String oldKmsKeyId,
    String newKmsKeyId,
    String walletAddress,
    Long operatorUserId,
    boolean disposeOldKey) {}
