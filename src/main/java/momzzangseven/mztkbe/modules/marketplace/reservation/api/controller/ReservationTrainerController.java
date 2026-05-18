package momzzangseven.mztkbe.modules.marketplace.reservation.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ApproveReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ApproveReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.GetReservationDetailRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.GetTrainerReservationsRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RecoverReservationEscrowRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RecoverReservationEscrowResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RejectReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RejectReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationCursorResponse;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationDetailResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationListStatusFilter;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trainer-facing reservation endpoints.
 *
 * <ul>
 *   <li>GET /marketplace/trainer/reservations — trainer's incoming reservation list
 *   <li>GET /marketplace/trainer/reservations/{id} — reservation detail (trainer view)
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

  private final GetTrainerReservationsUseCase getTrainerReservationsUseCase;
  private final GetReservationDetailUseCase getReservationDetailUseCase;
  private final ApproveReservationUseCase approveReservationUseCase;
  private final RejectReservationUseCase rejectReservationUseCase;
  private final RecoverReservationEscrowUseCase recoverReservationEscrowUseCase;

  @GetMapping
  public ResponseEntity<ApiResponse<ReservationCursorResponse>> getTrainerReservations(
      @AuthenticationPrincipal Long trainerId,
      @RequestParam(required = false) ReservationListStatusFilter status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    requireTrainerId(trainerId);
    GetTrainerReservationsRequestDTO request =
        new GetTrainerReservationsRequestDTO(status, cursor, size);
    return ResponseEntity.ok(
        ApiResponse.success(
            ReservationCursorResponse.from(
                getTrainerReservationsUseCase.execute(request.toQuery(trainerId)))));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ReservationDetailResponseDTO>> getReservationDetail(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long trainerId) {
    requireTrainerId(trainerId);
    GetReservationDetailRequestDTO request = new GetReservationDetailRequestDTO(id);
    return ResponseEntity.ok(
        ApiResponse.success(
            ReservationDetailResponseDTO.from(
                getReservationDetailUseCase.execute(request.toQuery(trainerId)))));
  }

  @PatchMapping("/{id}/approve")
  public ResponseEntity<ApiResponse<ApproveReservationResponseDTO>> approveReservation(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long trainerId) {
    requireTrainerId(trainerId);
    ApproveReservationRequestDTO request = new ApproveReservationRequestDTO(id);
    return ResponseEntity.ok(
        ApiResponse.success(
            ApproveReservationResponseDTO.from(
                approveReservationUseCase.execute(request.toCommand(trainerId)))));
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

  @PostMapping("/{id}/web3/recover")
  public ResponseEntity<ApiResponse<RecoverReservationEscrowResponseDTO>> recoverReservationEscrow(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long trainerId) {
    requireTrainerId(trainerId);
    RecoverReservationEscrowRequestDTO request = new RecoverReservationEscrowRequestDTO(id);
    return ResponseEntity.ok(
        ApiResponse.success(
            RecoverReservationEscrowResponseDTO.from(
                recoverReservationEscrowUseCase.execute(request.toCommand(trainerId)))));
  }

  private void requireTrainerId(Long trainerId) {
    if (trainerId == null) throw new UserNotAuthenticatedException();
  }
}
