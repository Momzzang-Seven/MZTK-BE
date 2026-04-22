package momzzangseven.mztkbe.modules.marketplace.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.api.dto.ApproveReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.api.dto.RejectReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.api.dto.RejectReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.application.port.in.RejectReservationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trainer-facing reservation endpoints.
 *
 * <ul>
 *   <li>PATCH /marketplace/trainer/reservations/{id}/approve — approve a pending reservation
 *   <li>PATCH /marketplace/trainer/reservations/{id}/reject — reject a pending reservation
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/marketplace/trainer/reservations")
@RequiredArgsConstructor
@Validated
public class ReservationTrainerController {

  private final ApproveReservationUseCase approveReservationUseCase;
  private final RejectReservationUseCase rejectReservationUseCase;

  @PatchMapping("/{id}/approve")
  public ResponseEntity<ApiResponse<ApproveReservationResponseDTO>> approveReservation(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long trainerId) {
    requireTrainerId(trainerId);
    return ResponseEntity.ok(
        ApiResponse.success(
            ApproveReservationResponseDTO.from(
                approveReservationUseCase.execute(new ApproveReservationCommand(id, trainerId)))));
  }

  @PatchMapping("/{id}/reject")
  public ResponseEntity<ApiResponse<RejectReservationResponseDTO>> rejectReservation(
      @PathVariable @Positive Long id,
      @AuthenticationPrincipal Long trainerId,
      @Valid @RequestBody RejectReservationRequestDTO request) {
    requireTrainerId(trainerId);
    return ResponseEntity.ok(
        ApiResponse.success(
            RejectReservationResponseDTO.from(
                rejectReservationUseCase.execute(request.toCommand(id, trainerId)))));
  }

  private void requireTrainerId(Long trainerId) {
    if (trainerId == null) throw new MarketplaceUnauthorizedAccessException();
  }
}
