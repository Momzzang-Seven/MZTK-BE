package momzzangseven.mztkbe.modules.marketplace.classes.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassReservationInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CancelPendingReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CompleteReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetUserReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.service.ReservationDisplayStatusMapper;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
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
 * momzzangseven.mztkbe.modules.marketplace.reservation.api.controller.ClassReservationController}.
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
  @MockitoBean private ClaimExpiredRefundReservationUseCase claimExpiredRefundReservationUseCase;
  @MockitoBean private RecoverReservationEscrowUseCase recoverReservationEscrowUseCase;

  // ── fixtures ────────────────────────────────────────────────────────────

  private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
  private static final Clock TEST_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T00:00:00Z"), ZoneId.of("Asia/Seoul"));

  /** Non-null class/user summary fixtures to validate the enrichment response contract. */
  private static final momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
          .LoadClassSummaryPort.ClassSummary
      SAMPLE_CLASS_SUMMARY =
          new momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
              .LoadClassSummaryPort.ClassSummary("요가 기초", 50000, "thumb/yoga.jpg");

  private static final momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
          .LoadUserSummaryPort.UserSummary
      SAMPLE_TRAINER_SUMMARY =
          new momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
              .LoadUserSummaryPort.UserSummary(100L, "trainer-nick");

  private static final momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
          .LoadUserSummaryPort.UserSummary
      SAMPLE_USER_SUMMARY =
          new momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out
              .LoadUserSummaryPort.UserSummary(50L, "user-nick");

  private ReservationSummaryResult summaryResult() {
    return ReservationDisplayStatusMapper.summaryResult(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .slotId(10L)
            .trainerId(100L)
            .userId(50L)
            .reservationDate(FUTURE_DATE)
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.PENDING)
            .escrowStatus(ReservationEscrowStatus.LOCKED)
            .userRequest(null)
            .orderKey("0x" + "1".repeat(64))
            .contractDeadlineAt(LocalDateTime.of(2025, 6, 10, 10, 0))
            .contractDeadlineEpochSeconds(1_749_528_000L)
            .build(),
        SAMPLE_CLASS_SUMMARY.title(),
        SAMPLE_CLASS_SUMMARY.priceAmount(),
        SAMPLE_CLASS_SUMMARY.thumbnailFinalObjectKey(),
        SAMPLE_TRAINER_SUMMARY.nickname(),
        null, // userNickname not exposed on user-list path
        50L,
        web3ExecutionView("MARKETPLACE_CLASS_PURCHASE", "PURCHASE", true, true),
        TEST_CLOCK);
  }

  private ReservationSummaryResult repairedSummaryResult() {
    return ReservationDisplayStatusMapper.summaryResult(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .slotId(10L)
            .trainerId(100L)
            .userId(50L)
            .reservationDate(FUTURE_DATE)
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.DEADLINE_REFUNDED)
            .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUNDED)
            .userRequest(null)
            .orderKey("0x" + "2".repeat(64))
            .contractDeadlineAt(LocalDateTime.of(2025, 6, 10, 10, 0))
            .contractDeadlineEpochSeconds(1_749_528_000L)
            .build(),
        SAMPLE_CLASS_SUMMARY.title(),
        SAMPLE_CLASS_SUMMARY.priceAmount(),
        SAMPLE_CLASS_SUMMARY.thumbnailFinalObjectKey(),
        SAMPLE_TRAINER_SUMMARY.nickname(),
        null,
        50L,
        web3ExecutionView(
            "DEADLINE_REFUNDED",
            "MARKETPLACE_CLASS_EXPIRED_REFUND",
            "DEADLINE_REFUND",
            false,
            false),
        TEST_CLOCK);
  }

  private GetReservationResult detailResult(Long viewerId) {
    return ReservationDisplayStatusMapper.detailResult(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .userId(viewerId)
            .trainerId(100L)
            .slotId(10L)
            .reservationDate(FUTURE_DATE)
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.PENDING)
            .escrowStatus(ReservationEscrowStatus.LOCKED)
            .userRequest(null)
            .orderId("order-abc")
            .orderKey("0x" + "1".repeat(64))
            .txHash(null)
            .contractDeadlineAt(LocalDateTime.of(2025, 6, 10, 10, 0))
            .contractDeadlineEpochSeconds(1_749_528_000L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build(),
        SAMPLE_CLASS_SUMMARY.title(),
        SAMPLE_CLASS_SUMMARY.priceAmount(),
        SAMPLE_CLASS_SUMMARY.thumbnailFinalObjectKey(),
        SAMPLE_TRAINER_SUMMARY.nickname(),
        SAMPLE_USER_SUMMARY.nickname(),
        viewerId,
        web3ExecutionView("MARKETPLACE_CLASS_PURCHASE", "PURCHASE", true, true),
        TEST_CLOCK);
  }

  private GetReservationResult repairedDetailResult(Long viewerId) {
    return ReservationDisplayStatusMapper.detailResult(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .userId(viewerId)
            .trainerId(100L)
            .slotId(10L)
            .reservationDate(FUTURE_DATE)
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.DEADLINE_REFUNDED)
            .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUNDED)
            .userRequest(null)
            .orderId("order-abc")
            .orderKey("0x" + "2".repeat(64))
            .txHash("0xrefund")
            .contractDeadlineAt(LocalDateTime.of(2025, 6, 10, 10, 0))
            .contractDeadlineEpochSeconds(1_749_528_000L)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build(),
        SAMPLE_CLASS_SUMMARY.title(),
        SAMPLE_CLASS_SUMMARY.priceAmount(),
        SAMPLE_CLASS_SUMMARY.thumbnailFinalObjectKey(),
        SAMPLE_TRAINER_SUMMARY.nickname(),
        SAMPLE_USER_SUMMARY.nickname(),
        viewerId,
        web3ExecutionView(
            "DEADLINE_REFUNDED",
            "MARKETPLACE_CLASS_EXPIRED_REFUND",
            "DEADLINE_REFUND",
            false,
            false),
        TEST_CLOCK);
  }

  private ReservationExecutionResumeView web3ExecutionView(
      String actionType, String viewerAction, boolean viewerCanExecute, boolean viewerCanRecover) {
    return web3ExecutionView(
        "PENDING", actionType, viewerAction, viewerCanExecute, viewerCanRecover);
  }

  private ReservationExecutionResumeView web3ExecutionView(
      String resourceStatus,
      String actionType,
      String viewerAction,
      boolean viewerCanExecute,
      boolean viewerCanRecover) {
    return new ReservationExecutionResumeView(
        new ReservationExecutionResumeView.Resource("ORDER", "0xorderkey", resourceStatus),
        actionType,
        new ReservationExecutionResumeView.ExecutionIntent(
            "intent-public-1",
            "AWAITING_SIGNATURE",
            LocalDateTime.of(2025, 6, 10, 9, 55),
            1_749_527_700L),
        new ReservationExecutionResumeView.Execution("EIP7702", 1),
        new ReservationExecutionResumeView.Transaction(77L, "SIGNED", "0xabc"),
        viewerAction,
        viewerCanExecute,
        viewerCanRecover);
  }

  private ReservationExecutionWriteView web3WriteView(String actionType) {
    return web3WriteView(actionType, true);
  }

  private ReservationExecutionWriteView web3WriteView(
      String actionType, boolean includeSignatureMeta) {
    String fromRole = "MARKETPLACE_CLASS_PURCHASE".equals(actionType) ? "BUYER" : "ESCROW";
    String toRole =
        switch (actionType) {
          case "MARKETPLACE_CLASS_PURCHASE" -> "ESCROW";
          case "MARKETPLACE_CLASS_CONFIRM" -> "TRAINER";
          default -> "BUYER";
        };
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        actionType,
        "0xorderkey",
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-write-1",
            "AWAITING_SIGNATURE",
            LocalDateTime.of(2025, 6, 10, 9, 55),
            1_749_527_700L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        new ReservationExecutionWriteView.SignRequest(
            new ReservationExecutionWriteView.Authorization(
                10L, "0xdelegate", 7L, "0xauthorizationHash"),
            new ReservationExecutionWriteView.Submit("0xexecutionDigest", 1_749_527_760L),
            null),
        null,
        false,
        includeSignatureMeta
            ? new ReservationExecutionWriteView.SignatureMeta(1_749_527_600L, 1_749_527_760L)
            : null,
        new ReservationExecutionWriteView.TokenMovement(
            "0xtoken", "50000000000000000000", fromRole, "0xfrom", toRole, "0xto"));
  }

  /** Minimal valid create-reservation request body. */
  private Map<String, Object> validCreateBody() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("slotId", 10);
    body.put("reservationDate", FUTURE_DATE.toString());
    body.put("reservationTime", "10:00:00");
    body.put("signedAmount", 1000);
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
      given(getUserReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(summaryResult()), false, null));

      mockMvc
          .perform(get("/marketplace/me/reservations").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations[0].reservationId").value(1))
          .andExpect(jsonPath("$.data.reservations[0].status").value("PENDING"))
          .andExpect(jsonPath("$.data.reservations[0].escrowStatus").value("LOCKED"))
          .andExpect(jsonPath("$.data.reservations[0].orderKey").value("0x" + "1".repeat(64)))
          .andExpect(
              jsonPath("$.data.reservations[0].contractDeadlineAt").value("2025-06-10T10:00:00"))
          .andExpect(
              jsonPath("$.data.reservations[0].contractDeadlineEpochSeconds").value(1_749_528_000L))
          // enrichment contract — new fields must be present in the response
          .andExpect(jsonPath("$.data.reservations[0].classTitle").value("요가 기초"))
          .andExpect(jsonPath("$.data.reservations[0].priceAmount").value(50000))
          .andExpect(jsonPath("$.data.reservations[0].trainerNickname").value("trainer-nick"))
          .andExpect(
              jsonPath("$.data.reservations[0].thumbnailFinalObjectKey").value("thumb/yoga.jpg"))
          // web3 read hydration contract
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.actionType")
                  .value("MARKETPLACE_CLASS_PURCHASE"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.executionIntent.id")
                  .value("intent-public-1"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.executionIntent.expiresAtEpochSeconds")
                  .value(1_749_527_700L))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.transaction.id").value(77))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.transaction.status").value("SIGNED"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.transaction.txHash").value("0xabc"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.viewerAction").value("PURCHASE"))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanExecute").value(true))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanRecover").value(true))
          // cursor contract
          .andExpect(jsonPath("$.data.hasNext").value(false))
          .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("[CR-03b] chain read repair가 반영된 목록 결과를 같은 API 응답에 노출한다")
    void getMyReservations_repairedReadResult_returnsRepairedStatus() throws Exception {
      given(getUserReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(repairedSummaryResult()), false, null));

      mockMvc
          .perform(get("/marketplace/me/reservations").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations[0].reservationId").value(1))
          .andExpect(jsonPath("$.data.reservations[0].status").value("DEADLINE_REFUNDED"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.resource.status")
                  .value("DEADLINE_REFUNDED"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.actionType")
                  .value("MARKETPLACE_CLASS_EXPIRED_REFUND"))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanExecute").value(false))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.viewerCanRecover").value(false));
    }

    @Test
    @DisplayName("[CR-04] 인증 없이 요청하면 401을 반환한다")
    void getMyReservations_unauthenticated_returns401() throws Exception {
      mockMvc.perform(get("/marketplace/me/reservations")).andExpect(status().isUnauthorized());
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
      given(getUserReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(), false, null));

      mockMvc
          .perform(get("/marketplace/me/reservations?status=APPROVED").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.reservations").isArray())
          .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("[CR-06b] user in-flight status 필터도 API 파라미터로 허용한다")
    void getMyReservations_withInFlightStatusFilter_returns200() throws Exception {
      given(getUserReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(), false, null));

      mockMvc
          .perform(
              get("/marketplace/me/reservations?status=PURCHASE_PENDING").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.reservations").isArray())
          .andExpect(jsonPath("$.data.hasNext").value(false));
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
          .andExpect(jsonPath("$.data.orderId").value("order-abc"))
          .andExpect(jsonPath("$.data.escrowStatus").value("LOCKED"))
          .andExpect(jsonPath("$.data.orderKey").value("0x" + "1".repeat(64)))
          .andExpect(jsonPath("$.data.contractDeadlineAt").value("2025-06-10T10:00:00"))
          .andExpect(jsonPath("$.data.contractDeadlineEpochSeconds").value(1_749_528_000L))
          // enrichment contract
          .andExpect(jsonPath("$.data.classTitle").value("요가 기초"))
          .andExpect(jsonPath("$.data.priceAmount").value(50000))
          .andExpect(jsonPath("$.data.thumbnailFinalObjectKey").value("thumb/yoga.jpg"))
          .andExpect(jsonPath("$.data.trainerNickname").value("trainer-nick"))
          .andExpect(jsonPath("$.data.userNickname").value("user-nick"))
          // web3 read hydration contract
          .andExpect(
              jsonPath("$.data.web3Execution.actionType").value("MARKETPLACE_CLASS_PURCHASE"))
          .andExpect(jsonPath("$.data.web3Execution.executionIntent.id").value("intent-public-1"))
          .andExpect(
              jsonPath("$.data.web3Execution.executionIntent.expiresAtEpochSeconds")
                  .value(1_749_527_700L))
          .andExpect(jsonPath("$.data.web3Execution.transaction.id").value(77))
          .andExpect(jsonPath("$.data.web3Execution.transaction.status").value("SIGNED"))
          .andExpect(jsonPath("$.data.web3Execution.transaction.txHash").value("0xabc"))
          .andExpect(jsonPath("$.data.web3Execution.viewerAction").value("PURCHASE"))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanExecute").value(true))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanRecover").value(true));
    }

    @Test
    @DisplayName("[CR-07b] chain read repair가 반영된 상세 결과를 같은 API 응답에 노출한다")
    void getDetail_repairedReadResult_returnsRepairedStatus() throws Exception {
      given(getReservationDetailUseCase.execute(any())).willReturn(repairedDetailResult(50L));

      mockMvc
          .perform(get("/marketplace/reservations/1").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("DEADLINE_REFUNDED"))
          .andExpect(jsonPath("$.data.txHash").value("0xrefund"))
          .andExpect(jsonPath("$.data.web3Execution.resource.status").value("DEADLINE_REFUNDED"))
          .andExpect(
              jsonPath("$.data.web3Execution.actionType").value("MARKETPLACE_CLASS_EXPIRED_REFUND"))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanExecute").value(false))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanRecover").value(false));
    }

    @Test
    @DisplayName("[CR-08] 인증 없이 조회하면 401을 반환한다")
    void getDetail_unauthenticated_returns401() throws Exception {
      mockMvc.perform(get("/marketplace/reservations/1")).andExpect(status().isUnauthorized());
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
    @DisplayName("[CR-10] 정상 요청 시 200 + purchase execution write 응답을 반환한다")
    void create_validRequest_returns200() throws Exception {
      given(createReservationUseCase.execute(any()))
          .willReturn(
              new CreateReservationResult(
                  1L,
                  ReservationStatus.PURCHASE_PENDING,
                  "PURCHASE_PENDING",
                  "0xorderkey",
                  web3WriteView("MARKETPLACE_CLASS_PURCHASE")));

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(validCreateBody())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("PURCHASE_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("PURCHASE_PENDING"))
          .andExpect(jsonPath("$.data.orderKey").value("0xorderkey"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_PURCHASE"))
          .andExpect(jsonPath("$.data.web3.orderKey").value("0xorderkey"))
          .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-write-1"))
          .andExpect(
              jsonPath("$.data.web3.executionIntent.expiresAtEpochSeconds").value(1_749_527_700L))
          .andExpect(jsonPath("$.data.web3.execution.mode").value("EIP7702"))
          .andExpect(jsonPath("$.data.web3.signRequest.authorization.chainId").value(10))
          .andExpect(
              jsonPath("$.data.web3.signRequest.submit.deadlineEpochSeconds").value(1_749_527_760L))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signedAt").value(1_749_527_600L))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signatureExpiresAt").value(1_749_527_760L))
          .andExpect(
              jsonPath("$.data.web3.tokenMovement.amountBaseUnits").value("50000000000000000000"));
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
    @DisplayName("[CR-13] legacy signature 필드는 request contract에서 제거되어 역직렬화 시 무시된다")
    void create_legacySignatureFields_areIgnored() throws Exception {
      given(createReservationUseCase.execute(any()))
          .willReturn(
              new CreateReservationResult(
                  1L,
                  ReservationStatus.PURCHASE_PENDING,
                  "PURCHASE_PENDING",
                  "0xorderkey",
                  web3WriteView("MARKETPLACE_CLASS_PURCHASE")));
      Map<String, Object> body = new LinkedHashMap<>(validCreateBody());
      body.put("delegationSignature", "   ");
      body.put("executionSignature", "   ");

      mockMvc
          .perform(
              post("/marketplace/classes/1/reservations")
                  .with(userPrincipal(50L))
                  .contentType(APPLICATION_JSON)
                  .content(json(body)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_PURCHASE"));
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
          .willReturn(
              new CancelPendingReservationResult(
                  1L,
                  ReservationDisplayStatus.CANCEL_PENDING,
                  null,
                  "CANCEL_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_CANCEL")));

      mockMvc
          .perform(patch("/marketplace/me/reservations/1/cancel").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("CANCEL_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("CANCEL_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signedAt").value(1_749_527_600L));
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
          .willReturn(
              new CompleteReservationResult(
                  1L,
                  ReservationDisplayStatus.CONFIRM_PENDING,
                  null,
                  "CONFIRM_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_CONFIRM")));

      mockMvc
          .perform(patch("/marketplace/me/reservations/1/complete").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("CONFIRM_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("CONFIRM_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_CONFIRM"))
          .andExpect(
              jsonPath("$.data.web3.signatureMeta.signatureExpiresAt").value(1_749_527_760L));
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

  // ── PATCH /marketplace/me/reservations/{id}/deadline-refund ───────────

  @Nested
  @DisplayName("PATCH /marketplace/me/reservations/{id}/deadline-refund")
  class ClaimExpiredRefund {

    @Test
    @DisplayName("[CR-22] deadline refund 요청 시 200 + expired refund execution write 응답을 반환한다")
    void claimExpiredRefund_authenticated_returns200() throws Exception {
      given(claimExpiredRefundReservationUseCase.execute(any()))
          .willReturn(
              new ClaimExpiredRefundReservationResult(
                  1L,
                  ReservationDisplayStatus.DEADLINE_REFUND_PENDING,
                  null,
                  "DEADLINE_REFUND_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_EXPIRED_REFUND", false)));

      mockMvc
          .perform(patch("/marketplace/me/reservations/1/deadline-refund").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("DEADLINE_REFUND_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("DEADLINE_REFUND_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_EXPIRED_REFUND"))
          .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-write-1"))
          .andExpect(jsonPath("$.data.web3.signatureMeta").doesNotExist())
          .andExpect(jsonPath("$.data.web3.tokenMovement.fromRole").value("ESCROW"))
          .andExpect(jsonPath("$.data.web3.tokenMovement.toRole").value("BUYER"));
    }

    @Test
    @DisplayName("[CR-23] 인증 없이 deadline refund 요청하면 401을 반환한다")
    void claimExpiredRefund_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(patch("/marketplace/me/reservations/1/deadline-refund"))
          .andExpect(status().isUnauthorized());
    }
  }

  // ── POST /marketplace/me/reservations/{id}/web3/recover ────────────────

  @Nested
  @DisplayName("POST /marketplace/me/reservations/{id}/web3/recover")
  class RecoverReservationEscrow {

    @Test
    @DisplayName("[CR-24] recover 요청 시 200 + recovered execution write 응답을 반환한다")
    void recover_authenticated_returns200() throws Exception {
      given(recoverReservationEscrowUseCase.execute(any()))
          .willReturn(
              new RecoverReservationEscrowResult(
                  1L,
                  ReservationDisplayStatus.PURCHASE_PENDING,
                  null,
                  "PURCHASE_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_PURCHASE")));

      mockMvc
          .perform(post("/marketplace/me/reservations/1/web3/recover").with(userPrincipal(50L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("PURCHASE_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("PURCHASE_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_PURCHASE"))
          .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-write-1"))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signedAt").value(1_749_527_600L));
    }

    @Test
    @DisplayName("[CR-25] 인증 없이 recover 요청하면 401을 반환한다")
    void recover_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(post("/marketplace/me/reservations/1/web3/recover"))
          .andExpect(status().isUnauthorized());
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
