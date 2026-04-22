package momzzangseven.mztkbe.modules.marketplace.reservation.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ApproveReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RejectReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RejectReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationDetailResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationSummaryResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

  @GetMapping
  public ResponseEntity<ApiResponse<List<ReservationSummaryResponseDTO>>> getTrainerReservations(
      @AuthenticationPrincipal Long trainerId,
      @RequestParam(required = false) ReservationStatus status) {
    requireTrainerId(trainerId);
    List<ReservationSummaryResponseDTO> response =
        getTrainerReservationsUseCase
            .execute(new GetTrainerReservationsQuery(trainerId, status))
            .stream()
            .map(ReservationSummaryResponseDTO::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ReservationDetailResponseDTO>> getReservationDetail(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long trainerId) {
    requireTrainerId(trainerId);
    return ResponseEntity.ok(
        ApiResponse.success(
            ReservationDetailResponseDTO.from(
                getReservationDetailUseCase.execute(new GetReservationQuery(id, trainerId)))));
  }

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
