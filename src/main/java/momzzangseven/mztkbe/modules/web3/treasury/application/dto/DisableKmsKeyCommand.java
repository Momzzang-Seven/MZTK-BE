package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

public record DisableKmsKeyCommand(
    String walletAlias, String kmsKeyId, String walletAddress, Long operatorUserId) {}
