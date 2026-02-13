package momzzangseven.mztkbe.modules.web3.token.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProvisionTreasuryKeyRequestDTO(@NotBlank String treasuryPrivateKey) {}
