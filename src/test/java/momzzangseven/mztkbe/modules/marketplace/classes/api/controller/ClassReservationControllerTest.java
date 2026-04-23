package momzzangseven.mztkbe.modules.marketplace.classes.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassReservationInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
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
 * Controller contract tests for {@link
 * momzzangseven.mztkbe.modules.marketplace.classes.api.controller.ClassReservationController}.
 *
 * <p>Covers all 6 endpoints: reservation-info, my-list, detail, create, cancel, complete. Verifies
 * authentication enforcement, bean-validation constraints, and response structure.
 */
@DisplayName("ClassReservationController 컨트롤러 계약 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class ClassReservationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  // ── mandatory web3 worker mocks ─────────────────────────────────────────
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
  @MockitoBean private GetClassReservationInfoUseCase getClassReservationInfoUseCase;
  @MockitoBean private GetUserReservationsUseCase getUserReservationsUseCase;
  @MockitoBean private GetReservationDetailUseCase getReservationDetailUseCase;
  @MockitoBean private CreateReservationUseCase createReservationUseCase;
  @MockitoBean private CancelPendingReservationUseCase cancelPendingReservationUseCase;
  @MockitoBean private CompleteReservationUseCase completeReservationUseCase;

  // ── fixtures ────────────────────────────────────────────────────────────

  private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

  private ReservationSummaryResult summaryResult() {
    return new ReservationSummaryResult(
        1L, 10L, 100L, 50L,
        FUTURE_DATE, LocalTime.of(10, 0),
        60, ReservationStatus.PENDING, null);
  }

  private GetReservationResult detailResult(Long viewerId) {
    return new GetReservationResult(
        1L, viewerId, 100L, 10L,
        FUTURE_DATE, LocalTime.of(10, 0),
        60, ReservationStatus.PENDING,
        null, "order-abc", null,
        LocalDateTime.now(), LocalDateTime.now());
  }

  /** Minimal valid create-reservation request body. */
  private Map<String, Object> validCreateBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("slotId", 10);
    body.put("reservationDate", FUTURE_DATE.toString());
    body.put("reservationTime", "10:00:00");
    body.put("signedAmount", 1000);
    body.put("delegationSignature", "0xDELEGATION");
    body.put("executionSignature", "0xEXECUTION");
    return body;
  }

  // ── GET /marketplace/classes/{classId}/reservation-info ────────────────

  @Nested
  @DisplayName("GET /marketplace/classes/{classId}/reservation-info")
  class GetReservationInfo {

    @Test
    @DisplayName("[CR-01] 공개 엔드포인트 — 비인증 사용자도 200을 반환한다")
    void reservationInfo_public_noAuth_returns200() throws Exception {
      given(getClassReservationInfoUseCase.execute(any()))
          .willReturn(
              new momzzangseven.mztkbe.modules.marketplace.classes.application.dto
                  .GetClassReservationInfoResult(1L, "PT 클래스", 100L, 50000, 60, List.of()));

      mockMvc
          .perform(get("/marketplace/classes/1/reservation-info"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("[CR-02] 음수 classId로 요청하면 400을 반환한다 (@Positive)")
    void reservationInfo_negativeClassId_returns400() throws Exception {
      mockMvc
          .perform(get("/marketplace/classes/-1/reservation-info").with(userPrincipal(50L)))
          .andExpect(status().isBadRequest());
    }
  }

  // ── GET /marketplace/me/reservations ───────────────────────────────────

  @Nested
  @DisplayName("GET /marketplace/me/reservations")
  class GetMyReservations {

    @Test
    @DisplayName("[CR-03] 인증된 사용자는 예약 목록을 조회할 수 있다 — 200 + 목록 반환")
    void getMyReservations_authenticated_returns200() throws Exception {
      given(getUserReservationsUseCase.execute(any())).willReturn(List.of(summaryResult()));

      mockMvc
          .perform(get("/marketplace/me/reservations").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data[0].reservationId").value(1))
          .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("[CR-04] 인증 없이 요청하면 401을 반환한다")
    void getMyReservations_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/me/reservations"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR-05] principal이 null이면 401을 반환한다")
    void getMyReservations_nullPrincipal_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/me/reservations").with(nullUserPrincipal()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR-06] status 필터 파라미터를 전달하면 필터링 결과를 반환한다 — 200")
    void getMyReservations_withStatusFilter_returns200() throws Exception {
      given(getUserReservationsUseCase.execute(any())).willReturn(List.of());

      mockMvc
          .perform(get("/marketplace/me/reservations?status=APPROVED").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data").isArray());
    }
  }

  // ── GET /marketplace/reservations/{id} ─────────────────────────────────

  @Nested
  @DisplayName("GET /marketplace/reservations/{id}")
  class GetReservationDetail {

    @Test
    @DisplayName("[CR-07] 존재하는 예약 상세를 조회하면 200을 반환한다")
    void getDetail_authenticated_returns200() throws Exception {
      given(getReservationDetailUseCase.execute(any())).willReturn(detailResult(50L));

      mockMvc
          .perform(get("/marketplace/reservations/1").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.orderId").value("order-abc"));
    }

    @Test
    @DisplayName("[CR-08] 인증 없이 조회하면 401을 반환한다")
    void getDetail_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(get("/marketplace/reservations/1"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR-09] 음수 ID로 요청하면 400을 반환한다 (@Positive)")
    void getDetail_negativeId_returns400() throws Exception {
      mockMvc
          .perform(get("/marketplace/reservations/-1").with(userPrincipal(50L)))
          .andExpect(status().isBadRequest());
    }
  }

  // ── POST /marketplace/classes/{classId}/reservations ───────────────────

  @Nested
  @DisplayName("POST /marketplace/classes/{classId}/reservations")
  class CreateReservation {

    @Test
    @DisplayName("[CR-10] 정상 요청 시 200 + PENDING 상태를 반환한다")
    void create_validRequest_returns200() throws Exception {
      given(createReservationUseCase.execute(any()))
          .willReturn(new CreateReservationResult(1L, ReservationStatus.PENDING));

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(validCreateBody())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("[CR-11] slotId가 없으면 400을 반환한다 (@NotNull)")
    void create_missingSlotId_returns400() throws Exception {
      Map<String, Object> body = validCreateBody();
      body.remove("slotId");

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[CR-12] reservationDate가 과거이면 400을 반환한다 (@FutureOrPresent)")
    void create_pastDate_returns400() throws Exception {
      Map<String, Object> body = new LinkedHashMap<>(validCreateBody());
      body.put("reservationDate", LocalDate.now().minusDays(1).toString());

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[CR-13] delegationSignature가 공백이면 400을 반환한다 (@NotBlank)")
    void create_blankSignature_returns400() throws Exception {
      Map<String, Object> body = new LinkedHashMap<>(validCreateBody());
      body.put("delegationSignature", "   ");

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[CR-14] userRequest가 500자를 초과하면 400을 반환한다 (@Size)")
    void create_tooLongUserRequest_returns400() throws Exception {
      Map<String, Object> body = new LinkedHashMap<>(validCreateBody());
      body.put("userRequest", "a".repeat(501));

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[CR-15] 인증 없이 예약 생성하면 401을 반환한다")
    void create_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .contentType(APPLICATION_JSON)
                  .content(json(validCreateBody())))
          .andExpect(status().isUnauthorized());
    }
  }

  // ── PATCH /marketplace/me/reservations/{id}/cancel ─────────────────────

  @Nested
  @DisplayName("PATCH /marketplace/me/reservations/{id}/cancel")
  class CancelReservation {

    @Test
    @DisplayName("[CR-16] 정상 취소 요청 시 200 + CANCELLED 상태를 반환한다")
    void cancel_authenticated_returns200() throws Exception {
      given(cancelPendingReservationUseCase.execute(any()))
          .willReturn(new CancelPendingReservationResult(1L, ReservationStatus.USER_CANCELLED));

      mockMvc
          .perform(patch("/marketplace/me/reservations/1/cancel").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("USER_CANCELLED"));
    }

    @Test
    @DisplayName("[CR-17] 인증 없이 취소하면 401을 반환한다")
    void cancel_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(patch("/marketplace/me/reservations/1/cancel"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR-18] 음수 ID로 취소 요청하면 400을 반환한다")
    void cancel_negativeId_returns400() throws Exception {
      mockMvc
          .perform(patch("/marketplace/me/reservations/-1/cancel").with(userPrincipal(50L)))
          .andExpect(status().isBadRequest());
    }
  }

  // ── PATCH /marketplace/me/reservations/{id}/complete ───────────────────

  @Nested
  @DisplayName("PATCH /marketplace/me/reservations/{id}/complete")
  class CompleteReservation {

    @Test
    @DisplayName("[CR-19] 정상 완료 요청 시 200 + SETTLED 상태를 반환한다")
    void complete_authenticated_returns200() throws Exception {
      given(completeReservationUseCase.execute(any()))
          .willReturn(new CompleteReservationResult(1L, ReservationStatus.SETTLED));

      mockMvc
          .perform(patch("/marketplace/me/reservations/1/complete").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("SETTLED"));
    }

    @Test
    @DisplayName("[CR-20] 인증 없이 완료 요청하면 401을 반환한다")
    void complete_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(patch("/marketplace/me/reservations/1/complete"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[CR-21] 음수 ID로 완료 요청하면 400을 반환한다")
    void complete_negativeId_returns400() throws Exception {
      mockMvc
          .perform(patch("/marketplace/me/reservations/-1/complete").with(userPrincipal(50L)))
          .andExpect(status().isBadRequest());
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private RequestPostProcessor userPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    return SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private RequestPostProcessor nullUserPrincipal() {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            null, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    return SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
