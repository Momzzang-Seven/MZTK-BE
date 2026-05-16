package momzzangseven.mztkbe.modules.marketplace.reservation.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.global.pagination.CursorSlice;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionResumeView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationDetailUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetTrainerReservationsUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
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
  @MockitoBean private RecoverReservationEscrowUseCase recoverReservationEscrowUseCase;

  // ── fixtures ────────────────────────────────────────────────────────────

  /** Non-null enrichment fixture values — validates the trainer-facing response contract. */
  private static final String SAMPLE_CLASS_TITLE = "스트레칭 클래스";

  private static final int SAMPLE_PRICE = 30000;
  private static final String SAMPLE_THUMB = "thumb/stretch.jpg";
  private static final String SAMPLE_TRAINER_NICK = "trainer-nick";
  private static final String SAMPLE_USER_NICK = "user-nick";

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
        SAMPLE_CLASS_TITLE,
        SAMPLE_PRICE,
        SAMPLE_THUMB,
        SAMPLE_TRAINER_NICK,
        SAMPLE_USER_NICK,
        web3ExecutionView("MARKETPLACE_CLASS_CANCEL", "TRAINER_REJECT", true, false));
  }

  private ReservationSummaryResult repairedSummaryResult() {
    return ReservationSummaryResult.from(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .slotId(10L)
            .trainerId(100L)
            .userId(50L)
            .reservationDate(LocalDate.of(2025, 6, 10))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.USER_CANCELLED)
            .userRequest("부탁드립니다")
            .build(),
        SAMPLE_CLASS_TITLE,
        SAMPLE_PRICE,
        SAMPLE_THUMB,
        SAMPLE_TRAINER_NICK,
        SAMPLE_USER_NICK,
        web3ExecutionView(
            "USER_CANCELLED", "MARKETPLACE_CLASS_CANCEL", "TRAINER_REJECT", false, false));
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
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build(),
        SAMPLE_CLASS_TITLE,
        SAMPLE_PRICE,
        SAMPLE_THUMB,
        SAMPLE_TRAINER_NICK,
        SAMPLE_USER_NICK,
        web3ExecutionView("MARKETPLACE_CLASS_CANCEL", "TRAINER_REJECT", true, false));
  }

  private GetReservationResult repairedDetailResult() {
    return GetReservationResult.from(
        momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation.builder()
            .id(1L)
            .userId(50L)
            .trainerId(100L)
            .slotId(10L)
            .reservationDate(LocalDate.of(2025, 6, 10))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.USER_CANCELLED)
            .userRequest("부탁드립니다")
            .orderId("order-abc")
            .txHash("0xcancel")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build(),
        SAMPLE_CLASS_TITLE,
        SAMPLE_PRICE,
        SAMPLE_THUMB,
        SAMPLE_TRAINER_NICK,
        SAMPLE_USER_NICK,
        web3ExecutionView(
            "USER_CANCELLED", "MARKETPLACE_CLASS_CANCEL", "TRAINER_REJECT", false, false));
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
            "intent-public-2",
            "PENDING_ONCHAIN",
            LocalDateTime.of(2025, 6, 10, 9, 55),
            1_749_527_700L),
        new ReservationExecutionResumeView.Execution("EIP7702", 1),
        new ReservationExecutionResumeView.Transaction(88L, "PENDING", "0xdef"),
        viewerAction,
        viewerCanExecute,
        viewerCanRecover);
  }

  private ReservationExecutionWriteView web3WriteView(String actionType) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
        actionType,
        "0xorderkey",
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-write-2",
            "AWAITING_SIGNATURE",
            LocalDateTime.of(2025, 6, 10, 9, 55),
            1_749_527_700L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        new ReservationExecutionWriteView.SignRequest(
            new ReservationExecutionWriteView.Authorization(
                10L, "0xdelegate", 8L, "0xauthorizationHash"),
            new ReservationExecutionWriteView.Submit("0xexecutionDigest", 1_749_527_760L),
            null),
        null,
        false,
        new ReservationExecutionWriteView.SignatureMeta(1_749_527_600L, 1_749_527_760L),
        new ReservationExecutionWriteView.TokenMovement(
            "0xtoken", "50000000000000000000", "ESCROW", "0xescrow", "BUYER", "0xbuyer"));
  }

  // ── GET /marketplace/trainer/reservations ──────────────────────────────

  @Nested
  @DisplayName("GET /marketplace/trainer/reservations")
  class GetTrainerReservations {

    @Test
    @DisplayName("[TC-01] 인증된 트레이너는 예약 목록을 조회할 수 있다 — 200 + 목록 반환")
    void getTrainerReservations_authenticated_returns200() throws Exception {
      given(getTrainerReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(summaryResult()), false, null));

      mockMvc
          .perform(get("/marketplace/trainer/reservations").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations[0].reservationId").value(1))
          .andExpect(jsonPath("$.data.reservations[0].status").value("PENDING"))
          // enrichment contract — trainer sees class info, own nickname, and booker nickname
          .andExpect(jsonPath("$.data.reservations[0].classTitle").value(SAMPLE_CLASS_TITLE))
          .andExpect(jsonPath("$.data.reservations[0].priceAmount").value(SAMPLE_PRICE))
          .andExpect(jsonPath("$.data.reservations[0].trainerNickname").value(SAMPLE_TRAINER_NICK))
          .andExpect(jsonPath("$.data.reservations[0].thumbnailFinalObjectKey").value(SAMPLE_THUMB))
          .andExpect(jsonPath("$.data.reservations[0].userNickname").value(SAMPLE_USER_NICK))
          // web3 read hydration contract
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.actionType")
                  .value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.executionIntent.id")
                  .value("intent-public-2"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.executionIntent.expiresAtEpochSeconds")
                  .value(1_749_527_700L))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.transaction.id").value(88))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.transaction.status").value("PENDING"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.transaction.txHash").value("0xdef"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.viewerAction").value("TRAINER_REJECT"))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanExecute").value(true))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanRecover").value(false))
          // cursor contract
          .andExpect(jsonPath("$.data.hasNext").value(false))
          .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("[TC-01b] chain read repair가 반영된 목록 결과를 같은 API 응답에 노출한다")
    void getTrainerReservations_repairedReadResult_returnsRepairedStatus() throws Exception {
      given(getTrainerReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(repairedSummaryResult()), false, null));

      mockMvc
          .perform(get("/marketplace/trainer/reservations").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations[0].reservationId").value(1))
          .andExpect(jsonPath("$.data.reservations[0].status").value("USER_CANCELLED"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.resource.status")
                  .value("USER_CANCELLED"))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.actionType")
                  .value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.reservations[0].web3Execution.viewerCanExecute").value(false))
          .andExpect(
              jsonPath("$.data.reservations[0].web3Execution.viewerCanRecover").value(false));
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
      given(getTrainerReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(), false, null));

      mockMvc
          .perform(
              get("/marketplace/trainer/reservations?status=APPROVED").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations").isArray())
          .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("[TC-04b] trainer in-flight status 필터도 API 파라미터로 허용한다")
    void getTrainerReservations_withInFlightStatusFilter_returns200() throws Exception {
      given(getTrainerReservationsUseCase.execute(any()))
          .willReturn(new CursorSlice<>(List.of(), false, null));

      mockMvc
          .perform(
              get("/marketplace/trainer/reservations?status=REJECT_PENDING")
                  .with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservations").isArray())
          .andExpect(jsonPath("$.data.hasNext").value(false));
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
          .andExpect(jsonPath("$.data.trainerId").value(100))
          // enrichment contract
          .andExpect(jsonPath("$.data.classTitle").value(SAMPLE_CLASS_TITLE))
          .andExpect(jsonPath("$.data.priceAmount").value(SAMPLE_PRICE))
          .andExpect(jsonPath("$.data.thumbnailFinalObjectKey").value(SAMPLE_THUMB))
          .andExpect(jsonPath("$.data.trainerNickname").value(SAMPLE_TRAINER_NICK))
          .andExpect(jsonPath("$.data.userNickname").value(SAMPLE_USER_NICK))
          // web3 read hydration contract
          .andExpect(jsonPath("$.data.web3Execution.actionType").value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.web3Execution.executionIntent.id").value("intent-public-2"))
          .andExpect(
              jsonPath("$.data.web3Execution.executionIntent.expiresAtEpochSeconds")
                  .value(1_749_527_700L))
          .andExpect(jsonPath("$.data.web3Execution.transaction.id").value(88))
          .andExpect(jsonPath("$.data.web3Execution.transaction.status").value("PENDING"))
          .andExpect(jsonPath("$.data.web3Execution.transaction.txHash").value("0xdef"))
          .andExpect(jsonPath("$.data.web3Execution.viewerAction").value("TRAINER_REJECT"))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanExecute").value(true))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanRecover").value(false));
    }

    @Test
    @DisplayName("[TC-05b] chain read repair가 반영된 상세 결과를 같은 API 응답에 노출한다")
    void getDetail_repairedReadResult_returnsRepairedStatus() throws Exception {
      given(getReservationDetailUseCase.execute(any())).willReturn(repairedDetailResult());

      mockMvc
          .perform(get("/marketplace/trainer/reservations/1").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("USER_CANCELLED"))
          .andExpect(jsonPath("$.data.txHash").value("0xcancel"))
          .andExpect(jsonPath("$.data.web3Execution.resource.status").value("USER_CANCELLED"))
          .andExpect(jsonPath("$.data.web3Execution.actionType").value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanExecute").value(false))
          .andExpect(jsonPath("$.data.web3Execution.viewerCanRecover").value(false));
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
    @DisplayName("[TC-10] 정상 반려 요청 시 200 + trainer reject execution write 응답을 반환한다")
    void reject_withReason_returns200() throws Exception {
      given(rejectReservationUseCase.execute(any()))
          .willReturn(
              new RejectReservationResult(
                  1L,
                  ReservationStatus.REJECT_PENDING,
                  "REJECT_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_CANCEL")));

      mockMvc
          .perform(
              patch("/marketplace/trainer/reservations/1/reject")
                  .with(trainerPrincipal(100L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("rejectionReason", "일정 불가"))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("REJECT_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("REJECT_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-write-2"))
          .andExpect(
              jsonPath("$.data.web3.executionIntent.expiresAtEpochSeconds").value(1_749_527_700L))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signedAt").value(1_749_527_600L))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signatureExpiresAt").value(1_749_527_760L))
          .andExpect(jsonPath("$.data.web3.tokenMovement.fromRole").value("ESCROW"))
          .andExpect(jsonPath("$.data.web3.tokenMovement.toRole").value("BUYER"));
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

  // ── POST /marketplace/trainer/reservations/{id}/web3/recover ──────────

  @Nested
  @DisplayName("POST /marketplace/trainer/reservations/{id}/web3/recover")
  class RecoverReservationEscrow {

    @Test
    @DisplayName("[TC-15] 트레이너 recover 요청 시 200 + trainer execution write 응답을 반환한다")
    void recover_authenticated_returns200() throws Exception {
      given(recoverReservationEscrowUseCase.execute(any()))
          .willReturn(
              new RecoverReservationEscrowResult(
                  1L,
                  ReservationStatus.REJECT_PENDING,
                  "REJECT_PENDING",
                  web3WriteView("MARKETPLACE_CLASS_CANCEL")));

      mockMvc
          .perform(
              post("/marketplace/trainer/reservations/1/web3/recover").with(trainerPrincipal(100L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"))
          .andExpect(jsonPath("$.data.reservationId").value(1))
          .andExpect(jsonPath("$.data.status").value("REJECT_PENDING"))
          .andExpect(jsonPath("$.data.escrowStatus").value("REJECT_PENDING"))
          .andExpect(jsonPath("$.data.web3.actionType").value("MARKETPLACE_CLASS_CANCEL"))
          .andExpect(jsonPath("$.data.web3.executionIntent.id").value("intent-write-2"))
          .andExpect(jsonPath("$.data.web3.signatureMeta.signedAt").value(1_749_527_600L));
    }

    @Test
    @DisplayName("[TC-16] 인증 없이 트레이너 recover 요청하면 401을 반환한다")
    void recover_unauthenticated_returns401() throws Exception {
      mockMvc
          .perform(post("/marketplace/trainer/reservations/1/web3/recover"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[TC-17] 일반 유저가 트레이너 recover 요청하면 403을 반환한다")
    void recover_userRole_returns403() throws Exception {
      mockMvc
          .perform(
              post("/marketplace/trainer/reservations/1/web3/recover").with(userPrincipal(50L)))
          .andExpect(status().isForbidden());
    }
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private RequestPostProcessor trainerPrincipal(Long trainerId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            trainerId, null, List.of(new SimpleGrantedAuthority("ROLE_TRAINER")));
    return SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
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
