package momzzangseven.mztkbe.modules.web3.transfer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.CreateTransferCommand;

public record CreateTransferRequestDTO(
    @NotNull @Positive Long toUserId,
    @NotBlank @Size(max = 100) String clientRequestId,
    @NotBlank @Pattern(regexp = "^[0-9]+$") String amountWei) {

  public CreateTransferCommand toCommand(Long userId) {
    return new CreateTransferCommand(userId, toUserId, clientRequestId, new BigInteger(amountWei));
  }
}
