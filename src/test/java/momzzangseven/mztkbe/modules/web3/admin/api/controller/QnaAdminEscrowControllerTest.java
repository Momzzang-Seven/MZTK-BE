package momzzangseven.mztkbe.modules.web3.admin.api.controller;

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
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransactionStateInvalidException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.CalculateQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.CalculateQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ExecuteQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAdminExecutionConfigurationValidator;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAutoAcceptConfigurationValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.execution.internal-issuer.enabled=true"
    })
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("QnaAdminEscrowController direct EIP-1559 계약 테스트 (MockMvc + H2)")
class QnaAdminEscrowControllerTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @MockitoBean
  private CalculateQnaAdminSettlementReviewUseCase calculateQnaAdminSettlementReviewUseCase;

  @MockitoBean private ExecuteQnaAdminSettlementUseCase executeQnaAdminSettlementUseCase;
  @MockitoBean private CalculateQnaAdminRefundReviewUseCase calculateQnaAdminRefundReviewUseCase;
  @MockitoBean private ExecuteQnaAdminRefundUseCase executeQnaAdminRefundUseCase;

  @MockitoBean
  private QnaAdminExecutionConfigurationValidator qnaAdminExecutionConfigurationValidator;

  @MockitoBean private QnaAutoAcceptConfigurationValidator qnaAutoAcceptConfigurationValidator;

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
  @DisplayName("GET /admin/web3/qna/questions/{postId}/answers/{answerId}/settlement-review 성공")
  void getSettlementReview_success() throws Exception {
    given(
            calculateQnaAdminSettlementReviewUseCase.execute(
                any(CalculateQnaAdminSettlementReviewQuery.class)))
        .willReturn(sampleSettlementReview());

    mockMvc
        .perform(
            get("/admin/web3/qna/questions/101/answers/201/settlement-review")
                .with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(101))
        .andExpect(jsonPath("$.data.answerId").value(201))
        .andExpect(jsonPath("$.data.processable").value(true))
        .andExpect(
            jsonPath("$.data.authority.currentServerSignerAddress")
                .value("0x1111111111111111111111111111111111111111"))
        .andExpect(jsonPath("$.data.authority.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.authority.authorityModel").value("SERVER_RELAYER_ONLY"));

    verify(calculateQnaAdminSettlementReviewUseCase)
        .execute(any(CalculateQnaAdminSettlementReviewQuery.class));
  }

  @Test
  @DisplayName("POST /admin/web3/qna/questions/{postId}/answers/{answerId}/settle 성공")
  void settle_success() throws Exception {
    given(executeQnaAdminSettlementUseCase.execute(any()))
        .willReturn(sampleExecutionIntent("QNA_ADMIN_SETTLE"));

    mockMvc
        .perform(post("/admin/web3/qna/questions/101/answers/201/settle").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.actionType").value("QNA_ADMIN_SETTLE"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.execution.authorityModel").value("SERVER_RELAYER_ONLY"))
        .andExpect(jsonPath("$.data.signRequest").doesNotExist());

    verify(executeQnaAdminSettlementUseCase).execute(any());
  }

  @Test
  @DisplayName("GET /admin/web3/qna/questions/{postId}/refund-review 성공")
  void getRefundReview_success() throws Exception {
    given(
            calculateQnaAdminRefundReviewUseCase.execute(
                any(CalculateQnaAdminRefundReviewQuery.class)))
        .willReturn(sampleRefundReview());

    mockMvc
        .perform(get("/admin/web3/qna/questions/101/refund-review").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.postId").value(101))
        .andExpect(jsonPath("$.data.processable").value(true))
        .andExpect(jsonPath("$.data.answerConflictingActiveIntent").value(false))
        .andExpect(jsonPath("$.data.validations[0].code").value("ONCHAIN_QUESTION_HAS_ANSWERS"))
        .andExpect(jsonPath("$.data.validations[0].warning").value(true));

    verify(calculateQnaAdminRefundReviewUseCase)
        .execute(any(CalculateQnaAdminRefundReviewQuery.class));
  }

  @Test
  @DisplayName("POST /admin/web3/qna/questions/{postId}/refund 성공")
  void refund_success() throws Exception {
    given(executeQnaAdminRefundUseCase.execute(any()))
        .willReturn(sampleExecutionIntent("QNA_ADMIN_REFUND"));

    mockMvc
        .perform(post("/admin/web3/qna/questions/101/refund").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actionType").value("QNA_ADMIN_REFUND"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.execution.authorityModel").value("SERVER_RELAYER_ONLY"))
        .andExpect(jsonPath("$.data.signRequest").doesNotExist());

    verify(executeQnaAdminRefundUseCase).execute(any());
  }

  @Test
  @DisplayName("QnA admin endpoint 는 USER 권한이면 403")
  void qnaAdminEndpoint_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/qna/questions/101/answers/201/settlement-review")
                .with(userPrincipal(1L)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(calculateQnaAdminSettlementReviewUseCase);
  }

  @Test
  @DisplayName("QnA admin endpoint 는 인증 없으면 401")
  void qnaAdminEndpoint_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/qna/questions/101/refund"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(executeQnaAdminRefundUseCase);
  }

  @Test
  @DisplayName("settlement review usecase 가 invalid input 을 던지면 400")
  void getSettlementReview_invalidInput_returns400() throws Exception {
    given(
            calculateQnaAdminSettlementReviewUseCase.execute(
                any(CalculateQnaAdminSettlementReviewQuery.class)))
        .willThrow(new Web3InvalidInputException("postId must be positive"));

    mockMvc
        .perform(
            get("/admin/web3/qna/questions/101/answers/201/settlement-review")
                .with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  @Test
  @DisplayName("settle usecase 가 state conflict 를 던지면 409")
  void settle_conflict_returns409() throws Exception {
    given(executeQnaAdminSettlementUseCase.execute(any()))
        .willThrow(new Web3TransactionStateInvalidException("ACTIVE_QUESTION_INTENT_PRESENT"));

    mockMvc
        .perform(post("/admin/web3/qna/questions/101/answers/201/settle").with(adminPrincipal(9L)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("FAIL"));
  }

  private QnaAdminSettlementReviewResult sampleSettlementReview() {
    return new QnaAdminSettlementReviewResult(
        101L,
        201L,
        true,
        null,
        authority(),
        new QnaAdminLocalQuestionView(true, true, "OPEN", false, false, 7L, 50L, null),
        new QnaAdminLocalAnswerView(true, true, false, 22L),
        new QnaAdminOnchainQuestionView(true, "ANSWERED", 1),
        new QnaAdminOnchainAnswerView(true, true, false),
        true,
        true,
        false,
        false,
        List.of(new QnaAdminReviewValidationItem("RELAYER_NOT_REGISTERED", true, false, "ok")));
  }

  private QnaAdminRefundReviewResult sampleRefundReview() {
    return new QnaAdminRefundReviewResult(
        101L,
        true,
        null,
        authority(),
        new QnaAdminLocalQuestionView(true, true, "OPEN", false, false, 7L, 50L, null),
        new QnaAdminOnchainQuestionView(true, "ANSWERED", 2),
        false,
        false,
        List.of(
            new QnaAdminReviewValidationItem(
                "ONCHAIN_QUESTION_HAS_ANSWERS",
                true,
                true,
                "refund will move the question to DELETED_WITH_ANSWERS")));
  }

  private QnaAdminExecutionAuthorityView authority() {
    return new QnaAdminExecutionAuthorityView(
        "0x1111111111111111111111111111111111111111", true, false, "SERVER_RELAYER_ONLY");
  }

  private QnaExecutionIntentResult sampleExecutionIntent(String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "101", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            "intent-1", "PENDING_ONCHAIN", LocalDateTime.of(2026, 4, 20, 12, 0)),
        new QnaExecutionIntentResult.Execution("DIRECT_EIP1559", 1),
        null,
        false);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<SimpleGrantedAuthority> grantedAuthorities =
        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
