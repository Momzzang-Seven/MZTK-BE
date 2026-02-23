package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProvisionTreasuryKeyRequestDTO(
    @NotBlank String treasuryPrivateKey, String walletAlias) {}
