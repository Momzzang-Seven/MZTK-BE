package momzzangseven.mztkbe.modules.marketplace.classes.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CancelPendingReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ClaimExpiredRefundReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CompleteReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CreateReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CreateReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.RecoverReservationEscrowResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationCursorResponse;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.ReservationDetailResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
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
 * REST controller for user-facing reservation endpoints. Moved to classes sub-module per user
 * request.
 *
 * <ul>
 *   <li>GET /marketplace/classes/{classId}/reservation-info — 4-week availability (public)
 *   <li>GET /marketplace/me/reservations — user's own reservation list
 *   <li>GET /marketplace/reservations/{id} — reservation detail (user or trainer)
 *   <li>POST /marketplace/classes/{classId}/reservations — create reservation (user)
 *   <li>PATCH /marketplace/me/reservations/{id}/cancel — cancel pending (user)
 *   <li>PATCH /marketplace/me/reservations/{id}/complete — complete & settle (user)
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
@Validated
public class ClassReservationController {

  private final GetUserReservationsUseCase getUserReservationsUseCase;
  private final GetReservationDetailUseCase getReservationDetailUseCase;
  private final CreateReservationUseCase createReservationUseCase;
  private final CancelPendingReservationUseCase cancelPendingReservationUseCase;
  private final CompleteReservationUseCase completeReservationUseCase;
  private final ClaimExpiredRefundReservationUseCase claimExpiredRefundReservationUseCase;
  private final RecoverReservationEscrowUseCase recoverReservationEscrowUseCase;

  @GetMapping("/me/reservations")
  public ResponseEntity<ApiResponse<ReservationCursorResponse>> getMyReservations(
      @AuthenticationPrincipal Long userId,
      @RequestParam(required = false) ReservationStatus status,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    requireUserId(userId);
    CursorPageRequest pageRequest =
        CursorPageRequest.of(cursor, size, 20, 100, GetUserReservationsQuery.cursorScope(status));
    return ResponseEntity.ok(
        ApiResponse.success(
            ReservationCursorResponse.from(
                getUserReservationsUseCase.execute(
                    new GetUserReservationsQuery(userId, status, pageRequest)))));
  }

  @GetMapping("/reservations/{id}")
  public ResponseEntity<ApiResponse<ReservationDetailResponseDTO>> getReservationDetail(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long userId) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            ReservationDetailResponseDTO.from(
                getReservationDetailUseCase.execute(new GetReservationQuery(id, userId)))));
  }

  @PostMapping("/classes/{classId}/reservations")
  public ResponseEntity<ApiResponse<CreateReservationResponseDTO>> createReservation(
      @PathVariable @Positive Long classId,
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody CreateReservationRequestDTO request) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            CreateReservationResponseDTO.from(
                createReservationUseCase.execute(request.toCommand(userId, classId)))));
  }

  @PatchMapping("/me/reservations/{id}/cancel")
  public ResponseEntity<ApiResponse<CancelPendingReservationResponseDTO>> cancelReservation(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long userId) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            CancelPendingReservationResponseDTO.from(
                cancelPendingReservationUseCase.execute(
                    new CancelPendingReservationCommand(id, userId)))));
  }

  @PatchMapping("/me/reservations/{id}/complete")
  public ResponseEntity<ApiResponse<CompleteReservationResponseDTO>> completeReservation(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long userId) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            CompleteReservationResponseDTO.from(
                completeReservationUseCase.execute(new CompleteReservationCommand(id, userId)))));
  }

  @PatchMapping("/me/reservations/{id}/deadline-refund")
  public ResponseEntity<ApiResponse<ClaimExpiredRefundReservationResponseDTO>> claimExpiredRefund(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long userId) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            ClaimExpiredRefundReservationResponseDTO.from(
                claimExpiredRefundReservationUseCase.execute(
                    new ClaimExpiredRefundReservationCommand(id, userId)))));
  }

  @PostMapping("/me/reservations/{id}/web3/recover")
  public ResponseEntity<ApiResponse<RecoverReservationEscrowResponseDTO>> recoverReservationEscrow(
      @PathVariable @Positive Long id, @AuthenticationPrincipal Long userId) {
    requireUserId(userId);
    return ResponseEntity.ok(
        ApiResponse.success(
            RecoverReservationEscrowResponseDTO.from(
                recoverReservationEscrowUseCase.execute(
                    new RecoverReservationEscrowCommand(id, userId)))));
  }

  private void requireUserId(Long userId) {
    if (userId == null) throw new UserNotAuthenticatedException();
  }
}
