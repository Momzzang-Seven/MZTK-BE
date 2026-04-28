package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryRole;

public record ProvisionTreasuryKeyRequestDTO(
    @NotBlank String rawPrivateKey, @NotNull TreasuryRole role, @NotBlank String expectedAddress) {}
