package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TokenTransferSubmitRequestDTO(
    @NotBlank @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String prepareId,
    @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{130}$") String authorizationSignature,
    @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{130}$") String executionSignature) {}
