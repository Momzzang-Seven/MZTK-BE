package momzzangseven.mztkbe.modules.web3.token.api.dto;

import lombok.Builder;

@Builder
public record ProvisionTreasuryKeyResponseDTO(
    String treasuryAddress,
    String treasuryPrivateKeyEncrypted,
    String treasuryKeyEncryptionKeyB64) {}
