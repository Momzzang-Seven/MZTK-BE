package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.DomainReferenceType;

public record TokenTransferPrepareRequestDTO(
    @NotNull DomainReferenceType domainType,
    @NotBlank @Size(max = 100) String referenceId,
    @NotNull @Positive Long toUserId,
    @NotBlank @Pattern(regexp = "^[0-9]+$") String amountWei) {}
