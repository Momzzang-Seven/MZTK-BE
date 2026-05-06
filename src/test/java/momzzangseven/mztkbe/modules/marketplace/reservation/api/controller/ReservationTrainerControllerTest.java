package momzzangseven.mztkbe.modules.marketplace.reservation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Controller contract tests for {@link ReservationTrainerController}.
 *
 * <p>Uses a full {@code @SpringBootTest} context with MockMvc so that the security filter chain and
 * content-negotiation are exercised. All use-case beans are replaced with mocks.
 */
@DisplayName("ReservationTrainerController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class ReservationTrainerControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  // ── mandatory web3 worker mocks (required by the full app context) ──────
  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  // ── reservation use-case mocks ──────────────────────────────────────────
  @MockitoBean private GetTrainerReservationsUseCase getTrainerReservationsUseCase;
  @MockitoBean private GetReservationDetailUseCase getReservationDetailUseCase;
  @MockitoBean private ApproveReservationUseCase approveReservationUseCase;
  @MockitoBean private RejectReservationUseCase rejectReservationUseCase;

  // ── fixtures ────────────────────────────────────────────────────────────

  private ReservationSummaryResult summaryResult() {
    return ReservationSummaryResult.from(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .slotId(10L)
            .trainerId(100L)
            .userId(50L)
            .reservationDate(LocalDate.of(2025, 6, 10))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.PENDING)
            .userRequest("부탁드립니다")
            .build(),
        null,
        null);
  }

  private GetReservationResult detailResult() {
    return GetReservationResult.from(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .userId(50L)
            .trainerId(100L)
            .slotId(10L)
            .reservationDate(LocalDate.of(2025, 6, 10))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.PENDING)
            .userRequest("부탁드립니다")
            .orderId("order-abc")
            .txHash(null)
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build(),
        null,
        null,
        null);
  }

  // ── GET /marketplace/trainer/reservations ──────────────────────────────

  @Nested
  @DisplayName("GET /marketplace/trainer/reservations")
  class GetTrainerReservations {

    @Test
    @DisplayName("[TC-01] 인증된 트레이너는 예약 목록을 조회할 수 있다 — 200 + 목록 반환")
    void getTrainerReservations_authenticated_returns200() throws Exception {
      given(getTrainerReservationsUseCase.execute(any())).willReturn(List.of(summaryResult()));

      mockMvc
          .perform(get("/marketplace/trainer/reservations").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data[0].reservationId").value(1))
          .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("[TC-02] 인증 없이 요청하면 401을 반환한다")
    void getTrainerReservations_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/trainer/reservations"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[TC-03] principal이 null이면 401을 반환한다")
    void getTrainerReservations_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/trainer/reservations").with(nullTrainerPrincipal()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[TC-04] status 필터 파라미터를 전달하면 필터링 결과를 반환한다 — 200")
    void getTrainerReservations_withStatusFilter_returns200() throws Exception {
      given(getTrainerReservationsUseCase.execute(any())).willReturn(List.of());

      mockMvc
          .perform(
              get("/marketplace/trainer/reservations?status=APPROVED").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data").isArray());
    }
  }

  // ── GET /marketplace/trainer/reservations/{id} ─────────────────────────

  @Nested
  @DisplayName("GET /marketplace/trainer/reservations/{id}")
  class GetReservationDetail {

    @Test
    @DisplayName("[TC-05] 존재하는 예약 상세를 조회하면 200을 반환한다")
    void getDetail_authenticated_returns200() throws Exception {
      given(getReservationDetailUseCase.execute(any())).willReturn(detailResult());

      mockMvc
          .perform(get("/marketplace/trainer/reservations/1").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.trainerId").value(100));
    }

    @Test
    @DisplayName("[TC-06] 인증 없이 상세 조회하면 401을 반환한다")
    void getDetail_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/trainer/reservations/1"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[TC-07] 음수 ID로 요청하면 400을 반환한다 (@Positive 검증)")
    void getDetail_negativeId_returns400() throws Exception {
      mockMvc
          .perform(get("/marketplace/trainer/reservations/-1").with(trainerPrincipal(100L)))
          .andExpect(status().isBadRequest());
    }
  }

  // ── PATCH /marketplace/trainer/reservations/{id}/approve ───────────────

  @Nested
  @DisplayName("PATCH /marketplace/trainer/reservations/{id}/approve")
  class ApproveReservation {

    @Test
    @DisplayName("[TC-08] 정상 승인 요청 시 200 + APPROVED 상태를 반환한다")
    void approve_authenticated_returns200() throws Exception {
      given(approveReservationUseCase.execute(any()))
          .willReturn(new ApproveReservationResult(1L, ReservationStatus.APPROVED));

      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/approve").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("[TC-09] 인증 없이 승인하면 401을 반환한다")
    void approve_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(patch("/marketplace/trainer/reservations/1/approve"))
          .andExpect(status().isUnauthorized());
    }
  }

  // ── PATCH /marketplace/trainer/reservations/{id}/reject ────────────────

  @Nested
  @DisplayName("PATCH /marketplace/trainer/reservations/{id}/reject")
  class RejectReservation {

    @Test
    @DisplayName("[TC-10] 정상 반려 요청 시 200 + REJECTED 상태를 반환한다")
    void reject_withReason_returns200() throws Exception {
      given(rejectReservationUseCase.execute(any()))
          .willReturn(new RejectReservationResult(1L, ReservationStatus.REJECTED));

      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .with(trainerPrincipal(100L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("rejectionReason", "일정 불가"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("[TC-11] rejectionReason이 공백이면 400을 반환한다 (@NotBlank 검증)")
    void reject_blankReason_returns400() throws Exception {
      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .with(trainerPrincipal(100L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("rejectionReason", "   "))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[TC-12] rejectionReason이 200자를 초과하면 400을 반환한다 (@Size 검증)")
    void reject_tooLongReason_returns400() throws Exception {
      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .with(trainerPrincipal(100L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("rejectionReason", "a".repeat(201)))))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[TC-13] rejectionReason이 없으면 400을 반환한다")
    void reject_missingReason_returns400() throws Exception {
      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .with(trainerPrincipal(100L))
                  .contentType(APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[TC-14] 인증 없이 반려하면 401을 반환한다")
    void reject_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("rejectionReason", "이유"))))
          .andExpect(status().isUnauthorized());
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private RequestPostProcessor trainerPrincipal(Long trainerId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            trainerId, null, List.of(new SimpleGrantedAuthority("ROLE_TRAINER")));
    return SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private RequestPostProcessor nullTrainerPrincipal() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            null, null, List.of(new SimpleGrantedAuthority("ROLE_TRAINER"))));
    return SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
