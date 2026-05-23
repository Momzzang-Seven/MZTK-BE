package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3.EscrowTransactionAdapter;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetMarketplaceAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminEscrowReviewView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminExecutionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminSettlementReason;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceMarketplaceAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetMarketplaceAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config.MarketplaceAdminExecutionConfigurationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.execution.internal.enabled=true",
      "web3.marketplace.admin.enabled=true"
    })
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("MarketplaceAdminEscrowController direct EIP-1559 계약 테스트 (MockMvc + H2)")
class MarketplaceAdminEscrowControllerTest {

  @Autowired protected MockMvc mockMvc;

  @MockitoBean private GetMarketplaceAdminRefundReviewUseCase getRefundReviewUseCase;
  @MockitoBean private GetMarketplaceAdminSettlementReviewUseCase getSettlementReviewUseCase;
  @MockitoBean private ForceMarketplaceAdminRefundUseCase forceRefundUseCase;
  @MockitoBean private ForceMarketplaceAdminSettlementUseCase forceSettlementUseCase;

  @MockitoBean
  private MarketplaceAdminExecutionConfigurationValidator
      marketplaceAdminExecutionConfigurationValidator;

  @MockitoBean private EscrowTransactionAdapter escrowTransactionAdapter;

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

  @Test
  @DisplayName("GET /admin/web3/marketplace/reservations/{reservationId}/refund-review 성공")
  void getRefundReview_success() throws Exception {
    given(getRefundReviewUseCase.execute(any(GetMarketplaceAdminRefundReviewQuery.class)))
        .willReturn(new GetMarketplaceAdminRefundReviewResult(sampleReview()));

    mockMvc
        .perform(
            get("/admin/web3/marketplace/reservations/77/refund-review").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.reservationId").value(77))
        .andExpect(jsonPath("$.data.processable").value(true))
        .andExpect(jsonPath("$.data.adminExecutionPhase").value("IDLE"))
        .andExpect(jsonPath("$.data.authority.requiresUserSignature").value(false));

    ArgumentCaptor<GetMarketplaceAdminRefundReviewQuery> captor =
        ArgumentCaptor.forClass(GetMarketplaceAdminRefundReviewQuery.class);
    verify(getRefundReviewUseCase).execute(captor.capture());
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
  }

  @Test
  @DisplayName("GET /admin/web3/marketplace/reservations/{reservationId}/settlement-review 성공")
  void getSettlementReview_success() throws Exception {
    given(getSettlementReviewUseCase.execute(any(GetMarketplaceAdminSettlementReviewQuery.class)))
        .willReturn(new GetMarketplaceAdminSettlementReviewResult(sampleReview()));

    mockMvc
        .perform(
            get("/admin/web3/marketplace/reservations/77/settlement-review")
                .with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.reservationId").value(77))
        .andExpect(jsonPath("$.data.adminExecutionPhase").value("IDLE"));

    ArgumentCaptor<GetMarketplaceAdminSettlementReviewQuery> captor =
        ArgumentCaptor.forClass(GetMarketplaceAdminSettlementReviewQuery.class);
    verify(getSettlementReviewUseCase).execute(captor.capture());
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
  }

  @Test
  @DisplayName("POST /admin/web3/marketplace/reservations/{reservationId}/refund 성공")
  void refund_success() throws Exception {
    given(forceRefundUseCase.execute(any(ForceMarketplaceAdminRefundCommand.class)))
        .willReturn(
            new ForceMarketplaceAdminRefundResult(sampleExecution("MARKETPLACE_ADMIN_REFUND")));

    mockMvc
        .perform(
            post("/admin/web3/marketplace/reservations/77/refund")
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reasonCode": "TRAINER_TIMEOUT",
                      "memo": "memo",
                      "confirmManualRefund": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.actionType").value("MARKETPLACE_ADMIN_REFUND"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false));

    ArgumentCaptor<ForceMarketplaceAdminRefundCommand> captor =
        ArgumentCaptor.forClass(ForceMarketplaceAdminRefundCommand.class);
    verify(forceRefundUseCase).execute(captor.capture());
    assertThat(captor.getValue().operatorId()).isEqualTo(9L);
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
    assertThat(captor.getValue().reasonCode())
        .isEqualTo(MarketplaceAdminRefundReason.TRAINER_TIMEOUT);
    assertThat(captor.getValue().memo()).isEqualTo("memo");
    assertThat(captor.getValue().confirmManualRefund()).isFalse();
  }

  @Test
  @DisplayName("POST /admin/web3/marketplace/reservations/{reservationId}/settle 성공")
  void settle_success() throws Exception {
    given(forceSettlementUseCase.execute(any(ForceMarketplaceAdminSettlementCommand.class)))
        .willReturn(
            new ForceMarketplaceAdminSettlementResult(sampleExecution("MARKETPLACE_ADMIN_SETTLE")));

    mockMvc
        .perform(
            post("/admin/web3/marketplace/reservations/77/settle")
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reasonCode": "ADMIN_MANUAL_SETTLE",
                      "memo": "memo",
                      "confirmEarlySettle": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.actionType").value("MARKETPLACE_ADMIN_SETTLE"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false));

    ArgumentCaptor<ForceMarketplaceAdminSettlementCommand> captor =
        ArgumentCaptor.forClass(ForceMarketplaceAdminSettlementCommand.class);
    verify(forceSettlementUseCase).execute(captor.capture());
    assertThat(captor.getValue().operatorId()).isEqualTo(9L);
    assertThat(captor.getValue().reservationId()).isEqualTo(77L);
    assertThat(captor.getValue().reasonCode())
        .isEqualTo(MarketplaceAdminSettlementReason.ADMIN_MANUAL_SETTLE);
    assertThat(captor.getValue().memo()).isEqualTo("memo");
    assertThat(captor.getValue().confirmEarlySettle()).isTrue();
  }

  @Test
  @DisplayName("marketplace admin endpoint 는 USER 권한이면 403")
  void marketplaceAdminEndpoint_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/marketplace/reservations/77/refund-review").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(getRefundReviewUseCase);
  }

  @Test
  @DisplayName("marketplace admin endpoint 는 인증이 없으면 401")
  void marketplaceAdminEndpoint_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/admin/web3/marketplace/reservations/77/refund-review"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getRefundReviewUseCase);
  }

  @Test
  @DisplayName("review 후 execute까지 같은 admin surface에서 호출된다")
  void reviewThenExecuteFlow_success() throws Exception {
    given(getRefundReviewUseCase.execute(any(GetMarketplaceAdminRefundReviewQuery.class)))
        .willReturn(new GetMarketplaceAdminRefundReviewResult(sampleReview()));
    given(forceRefundUseCase.execute(any(ForceMarketplaceAdminRefundCommand.class)))
        .willReturn(
            new ForceMarketplaceAdminRefundResult(sampleExecution("MARKETPLACE_ADMIN_REFUND")));

    mockMvc
        .perform(
            get("/admin/web3/marketplace/reservations/77/refund-review").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.processable").value(true));

    mockMvc
        .perform(
            post("/admin/web3/marketplace/reservations/77/refund")
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reasonCode": "TRAINER_TIMEOUT",
                      "memo": "operator memo",
                      "confirmManualRefund": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actionType").value("MARKETPLACE_ADMIN_REFUND"))
        .andExpect(jsonPath("$.data.adminExecutionPhase").value("QUEUED_FOR_SERVER_RELAYER"));

    verify(getRefundReviewUseCase).execute(any(GetMarketplaceAdminRefundReviewQuery.class));
    verify(forceRefundUseCase).execute(any(ForceMarketplaceAdminRefundCommand.class));
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return principal(userId, "ROLE_ADMIN");
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return principal(userId, "ROLE_USER");
  }

  private RequestPostProcessor principal(Long userId, String authority) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority(authority)));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private MarketplaceAdminEscrowReviewView sampleReview() {
    return new MarketplaceAdminEscrowReviewView(
        77L,
        true,
        null,
        null,
        "APPROVED",
        "LOCKED",
        new MarketplaceAdminEscrowReviewView.Participant(
            10L, "0x1111111111111111111111111111111111111111"),
        new MarketplaceAdminEscrowReviewView.Participant(
            20L, "0x2222222222222222222222222222222222222222"),
        new MarketplaceAdminEscrowReviewView.Token(
            "0x3333333333333333333333333333333333333333", java.math.BigInteger.TEN, "MZT"),
        LocalDateTime.of(2026, 1, 2, 3, 4, 5),
        LocalDateTime.of(2026, 1, 2, 3, 4, 6),
        12L,
        "IDLE",
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        null,
        new MarketplaceAdminEscrowReviewView.Authority(
            false, "SERVER_RELAYER_ONLY", false, null, false, "UNCHECKED", false, false),
        null,
        null,
        List.of(),
        List.of());
  }

  private MarketplaceAdminExecutionView sampleExecution(String actionType) {
    return new MarketplaceAdminExecutionView(
        77L,
        actionType,
        "0xorder",
        actionType.equals("MARKETPLACE_ADMIN_REFUND")
            ? "ADMIN_REFUND_PENDING"
            : "ADMIN_SETTLE_PENDING",
        actionType.equals("MARKETPLACE_ADMIN_REFUND")
            ? "ADMIN_REFUND_PENDING"
            : "ADMIN_SETTLE_PENDING",
        new MarketplaceAdminExecutionView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 1, 2, 3, 4, 5)),
        new MarketplaceAdminExecutionView.Execution("EIP1559", false, "SERVER_RELAYER_ONLY"),
        "QUEUED_FOR_SERVER_RELAYER",
        2_000L,
        "/admin/web3/marketplace/reservations/77/refund-review",
        false);
  }
}
