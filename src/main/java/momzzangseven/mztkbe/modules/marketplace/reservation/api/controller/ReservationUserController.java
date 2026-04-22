package momzzangseven.mztkbe.modules.marketplace.reservation.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.marketplace.classes.api.dto.GetClassReservationInfoResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassReservationInfoQuery;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassReservationInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CancelPendingReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CompleteReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CreateReservationRequestDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.api.dto.CreateReservationResponseDTO;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user-facing reservation endpoints.
 *
 * <ul>
 *   <li>GET /marketplace/classes/{classId}/reservation-info — 4-week availability (public)
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
public class ReservationUserController {

  private final GetClassReservationInfoUseCase getClassReservationInfoUseCase;
  private final CreateReservationUseCase createReservationUseCase;
  private final CancelPendingReservationUseCase cancelPendingReservationUseCase;
  private final CompleteReservationUseCase completeReservationUseCase;

  @GetMapping("/classes/{classId}/reservation-info")
  public ResponseEntity<ApiResponse<GetClassReservationInfoResponseDTO>> getReservationInfo(
      @PathVariable @Positive Long classId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            GetClassReservationInfoResponseDTO.from(
                getClassReservationInfoUseCase.execute(
                    new GetClassReservationInfoQuery(classId)))));
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

  private void requireUserId(Long userId) {
    if (userId == null) throw new MarketplaceUnauthorizedAccessException();
  }
}
