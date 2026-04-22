package momzzangseven.mztkbe.modules.marketplace.api.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CreateReservationCommand;

public record CreateReservationRequestDTO(
    @NotNull @Positive Long slotId,
    @NotNull @FutureOrPresent LocalDate reservationDate,
    @NotNull LocalTime reservationTime,
    @Size(max = 500) String userRequest,
    @NotNull BigInteger signedAmount,
    @NotBlank String delegationSignature,
    @NotBlank String executionSignature) {

  public CreateReservationCommand toCommand(Long userId, Long classId) {
    return new CreateReservationCommand(
        userId,
        classId,
        slotId,
        reservationDate,
        reservationTime,
        userRequest,
        signedAmount,
        delegationSignature,
        executionSignature);
  }
}
