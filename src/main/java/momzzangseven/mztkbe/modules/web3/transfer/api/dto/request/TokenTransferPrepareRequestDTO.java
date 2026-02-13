package momzzangseven.mztkbe.modules.web3.transfer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.TokenTransferReferenceType;

public record TokenTransferPrepareRequestDTO(
    @NotNull TokenTransferReferenceType referenceType,
    @NotBlank @Size(max = 100) String referenceId,
    @Positive Long toUserId,
    @NotBlank @Pattern(regexp = "^[0-9]+$") String amountWei) {}
