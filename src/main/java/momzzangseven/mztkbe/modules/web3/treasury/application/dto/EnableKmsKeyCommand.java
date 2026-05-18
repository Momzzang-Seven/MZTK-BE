package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

public record EnableKmsKeyCommand(
    String walletAlias, String kmsKeyId, String walletAddress, Long operatorUserId) {}
