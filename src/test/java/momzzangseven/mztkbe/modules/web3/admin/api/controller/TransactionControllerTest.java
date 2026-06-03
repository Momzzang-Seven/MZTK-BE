package momzzangseven.mztkbe.modules.web3.admin.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.AdminWeb3TransactionView;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionItemResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.BulkRequeueAdminWeb3TransactionsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.RequeueAdminWeb3TransactionResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetSponsorNonceSlotsUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(properties = "web3.reward-token.enabled=true")
@DisplayName("TransactionController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class TransactionControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private GetSponsorNonceSlotsUseCase getSponsorNonceSlotsUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.admin.application.port.in
          .RequeueAdminWeb3TransactionUseCase
      requeueAdminWeb3TransactionUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.admin.application.port.in
          .BulkRequeueAdminWeb3TransactionsUseCase
      bulkRequeueAdminWeb3TransactionsUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.admin.application.port.in
          .LoadAdminWeb3TransactionsUseCase
      loadAdminWeb3TransactionsUseCase;

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 성공")
  void markSucceeded_success() throws Exception {
    given(markTransactionSucceededUseCase.execute(any(MarkTransactionSucceededCommand.class)))
        .willReturn(
            new MarkTransactionSucceededResult(
                1L, "SUCCEEDED", "PENDING", "0xabc123", "https://explorer.example/tx/0xabc123"));

    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.transactionId").value(1))
        .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

    verify(markTransactionSucceededUseCase).execute(any(MarkTransactionSucceededCommand.class));
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded USER 권한이면 403")
  void markSucceeded_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 인증 없으면 401")
  void markSucceeded_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/transactions/1/mark-succeeded"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded 필수 값 누락이면 400")
  void markSucceeded_blankTxHash_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", " ",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded explorerUrl 공백이면 400")
  void markSucceeded_blankExplorerUrl_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", " ",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded reason 공백이면 400")
  void markSucceeded_blankReason_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", " ",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded evidence 공백이면 400")
  void markSucceeded_blankEvidence_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/mark-succeeded principal이 null이면 401")
  void markSucceeded_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/1/mark-succeeded")
                .with(nullAdminPrincipal())
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "txHash", "0xabc123",
                            "explorerUrl", "https://explorer.example/tx/0xabc123",
                            "reason", "manual-confirmation",
                            "evidence", "ops-ticket-1234"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(markTransactionSucceededUseCase);
  }

  @Test
  @DisplayName("GET /admin/web3/transactions 성공")
  void getTransactions_success() throws Exception {
    given(loadAdminWeb3TransactionsUseCase.execute(any()))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new AdminWeb3TransactionView(
                        11L,
                        "idem-11",
                        "LEVEL_UP_REWARD",
                        "reward-11",
                        "EIP1559",
                        null,
                        7L,
                        "0x" + "a".repeat(40),
                        "0x" + "b".repeat(40),
                        "CREATED",
                        null,
                        Web3TxFailureReason.KMS_DESCRIBE_TERMINAL.code(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.parse("2026-06-02T10:00:00"),
                        LocalDateTime.parse("2026-06-02T10:05:00"))),
                PageRequest.of(0, 50),
                1));

    mockMvc
        .perform(
            get("/admin/web3/transactions")
                .with(adminPrincipal(9L))
                .param("failureReason", "KMS_DESCRIBE_TERMINAL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].transactionId").value(11))
        .andExpect(jsonPath("$.data.content[0].txType").value("EIP1559"))
        .andExpect(jsonPath("$.data.content[0].failureReason").value("KMS_DESCRIBE_TERMINAL"));

    verify(loadAdminWeb3TransactionsUseCase).execute(any());
  }

  @Test
  @DisplayName("GET /admin/web3/transactions 인증 없으면 401")
  void getTransactions_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/admin/web3/transactions")).andExpect(status().isUnauthorized());

    verifyNoInteractions(loadAdminWeb3TransactionsUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/requeue 성공")
  void requeue_success() throws Exception {
    given(requeueAdminWeb3TransactionUseCase.execute(any(RequeueAdminWeb3TransactionCommand.class)))
        .willReturn(
            new RequeueAdminWeb3TransactionResult(
                5L, "CREATED", "CREATED", "KMS_DESCRIBE_TERMINAL", true));

    mockMvc
        .perform(
            post("/admin/web3/transactions/5/requeue")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("reason", "IAM restored", "evidence", "ops-1234"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.transactionId").value(5))
        .andExpect(jsonPath("$.data.requeued").value(true))
        .andExpect(jsonPath("$.data.originalFailureReason").value("KMS_DESCRIBE_TERMINAL"));

    verify(requeueAdminWeb3TransactionUseCase)
        .execute(any(RequeueAdminWeb3TransactionCommand.class));
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/requeue 성공")
  void bulkRequeue_success() throws Exception {
    given(
            bulkRequeueAdminWeb3TransactionsUseCase.execute(
                any(BulkRequeueAdminWeb3TransactionsCommand.class)))
        .willReturn(
            new BulkRequeueAdminWeb3TransactionsResult(
                3,
                2,
                1,
                0,
                1,
                0,
                List.of(
                    new BulkRequeueAdminWeb3TransactionItemResult(
                        5L, "REQUEUED", "CREATED", "CREATED", "KMS_DESCRIBE_TERMINAL", null),
                    new BulkRequeueAdminWeb3TransactionItemResult(
                        6L,
                        "REJECTED",
                        "UNCONFIRMED",
                        "UNCONFIRMED",
                        "RECEIPT_TIMEOUT",
                        "requeue requires CREATED status: current=UNCONFIRMED"))));

    mockMvc
        .perform(
            post("/admin/web3/transactions/requeue")
                .with(adminPrincipal(9L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionIds", List.of(5, 6, 5),
                            "reason", "IAM restored",
                            "evidence", "ops-1234"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.requested").value(3))
        .andExpect(jsonPath("$.data.unique").value(2))
        .andExpect(jsonPath("$.data.items[0].result").value("REQUEUED"))
        .andExpect(jsonPath("$.data.items[1].result").value("REJECTED"));

    verify(bulkRequeueAdminWeb3TransactionsUseCase)
        .execute(any(BulkRequeueAdminWeb3TransactionsCommand.class));
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/requeue USER 권한이면 403")
  void requeue_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/5/requeue")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("reason", "IAM restored", "evidence", "ops-1234"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(requeueAdminWeb3TransactionUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/requeue TRAINER 권한이면 403")
  void bulkRequeue_forbiddenForTrainer_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/requeue")
                .with(trainerPrincipal(3L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionIds", List.of(5, 6),
                            "reason", "IAM restored",
                            "evidence", "ops-1234"))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(bulkRequeueAdminWeb3TransactionsUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/requeue 인증 없으면 401")
  void bulkRequeue_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/requeue")
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "transactionIds", List.of(5, 6),
                            "reason", "IAM restored",
                            "evidence", "ops-1234"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(bulkRequeueAdminWeb3TransactionsUseCase);
  }

  @Test
  @DisplayName("POST /admin/web3/transactions/{txId}/requeue 인증 없으면 401")
  void requeue_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/admin/web3/transactions/5/requeue")
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("reason", "IAM restored", "evidence", "ops-1234"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(requeueAdminWeb3TransactionUseCase);
  }

  @Test
  @DisplayName("GET /admin/web3/nonce-slots 성공")
  void getNonceSlots_success() throws Exception {
    String sponsor = "0x" + "a".repeat(40);
    given(getSponsorNonceSlotsUseCase.execute(any(GetSponsorNonceSlotsQuery.class)))
        .willReturn(
            new GetSponsorNonceSlotsResult(
                84532L,
                sponsor,
                0,
                100,
                false,
                List.of(
                    new SponsorNonceSlotAdminView(
                        84532L,
                        sponsor,
                        51L,
                        "OPERATOR_REVIEW_REQUIRED",
                        2,
                        100L,
                        200L,
                        "0x" + "b".repeat(64),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "RECEIPT_TIMEOUT_900S",
                        null,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        0,
                        LocalDateTime.parse("2026-05-25T12:00:00"),
                        LocalDateTime.parse("2026-05-25T12:00:00")))));

    mockMvc
        .perform(
            get("/admin/web3/nonce-slots")
                .with(adminPrincipal(9L))
                .param("chainId", "84532")
                .param("fromAddress", sponsor.toUpperCase()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.chainId").value(84532))
        .andExpect(jsonPath("$.data.fromAddress").value(sponsor))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(100))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.slots[0].nonce").value(51))
        .andExpect(jsonPath("$.data.slots[0].status").value("OPERATOR_REVIEW_REQUIRED"))
        .andExpect(jsonPath("$.data.slots[0].blocking").value(true))
        .andExpect(jsonPath("$.data.slots[0].lowestBlockingSlot").value(true))
        .andExpect(jsonPath("$.data.slots[0].severity").value("BLOCKING"))
        .andExpect(
            jsonPath("$.data.slots[0].operatorAction")
                .value("FOLLOW_SPONSOR_NONCE_REPLACEMENT_RUNBOOK"))
        .andExpect(jsonPath("$.data.slots[0].stuckReason").value("RECEIPT_TIMEOUT_900S"));

    verify(getSponsorNonceSlotsUseCase).execute(any(GetSponsorNonceSlotsQuery.class));
  }

  @Test
  @DisplayName("GET /admin/web3/nonce-slots USER 권한이면 403")
  void getNonceSlots_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/nonce-slots")
                .with(userPrincipal(1L))
                .param("chainId", "84532")
                .param("fromAddress", "0x" + "a".repeat(40)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(getSponsorNonceSlotsUseCase);
  }

  @Test
  @DisplayName("GET /admin/web3/nonce-slots 인증 없으면 401")
  void getNonceSlots_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/nonce-slots")
                .param("chainId", "84532")
                .param("fromAddress", "0x" + "a".repeat(40)))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getSponsorNonceSlotsUseCase);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor trainerPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_TRAINER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
