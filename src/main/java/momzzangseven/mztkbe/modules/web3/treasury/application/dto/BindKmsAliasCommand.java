package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

public record BindKmsAliasCommand(
    String walletAlias, String kmsKeyId, String walletAddress, Long operatorUserId) {}
