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
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceQnaAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewQuery;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetQnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminRefundUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.ForceQnaAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminRefundReviewUseCase;
import momzzangseven.mztkbe.modules.web3.admin.application.port.in.GetQnaAdminSettlementReviewUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminLocalQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainAnswerView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminOnchainQuestionView;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRefundReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRelayerRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminSettlementReviewResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAdminExecutionConfigurationValidator;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAutoAcceptConfigurationValidator;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
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
      "web3.execution.internal.enabled=true",
      "web3.qna.admin.enabled=true"
    })
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("QnaAdminEscrowController direct EIP-1559 계약 테스트 (MockMvc + H2)")
class QnaAdminEscrowControllerTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @MockitoBean private GetQnaAdminSettlementReviewUseCase getQnaAdminSettlementReviewUseCase;
  @MockitoBean private ForceQnaAdminSettlementUseCase forceQnaAdminSettlementUseCase;
  @MockitoBean private GetQnaAdminRefundReviewUseCase getQnaAdminRefundReviewUseCase;
  @MockitoBean private ForceQnaAdminRefundUseCase forceQnaAdminRefundUseCase;

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
    given(getQnaAdminSettlementReviewUseCase.execute(any(GetQnaAdminSettlementReviewQuery.class)))
        .willReturn(new GetQnaAdminSettlementReviewResult(sampleSettlementReview()));

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
            jsonPath("$.data.authority.serverSigner.signerAddress")
                .value("0x1111111111111111111111111111111111111111"))
        .andExpect(jsonPath("$.data.authority.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.authority.authorityModel").value("SERVER_RELAYER_ONLY"));

    verify(getQnaAdminSettlementReviewUseCase).execute(any(GetQnaAdminSettlementReviewQuery.class));
  }

  @Test
  @DisplayName("POST /admin/web3/qna/questions/{postId}/answers/{answerId}/settle 성공")
  void settle_success() throws Exception {
    given(forceQnaAdminSettlementUseCase.execute(any()))
        .willReturn(new ForceQnaAdminSettlementResult(sampleExecutionIntent("QNA_ADMIN_SETTLE")));

    mockMvc
        .perform(post("/admin/web3/qna/questions/101/answers/201/settle").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.actionType").value("QNA_ADMIN_SETTLE"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.execution.authorityModel").value("SERVER_RELAYER_ONLY"))
        .andExpect(jsonPath("$.data.signRequest").doesNotExist());

    verify(forceQnaAdminSettlementUseCase).execute(any());
  }

  @Test
  @DisplayName("GET /admin/web3/qna/questions/{postId}/refund-review 성공")
  void getRefundReview_success() throws Exception {
    given(getQnaAdminRefundReviewUseCase.execute(any(GetQnaAdminRefundReviewQuery.class)))
        .willReturn(new GetQnaAdminRefundReviewResult(sampleRefundReview()));

    mockMvc
        .perform(get("/admin/web3/qna/questions/101/refund-review").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.postId").value(101))
        .andExpect(jsonPath("$.data.processable").value(true))
        .andExpect(jsonPath("$.data.answerConflictingActiveIntent").value(false))
        .andExpect(jsonPath("$.data.validations[0].code").value("ONCHAIN_QUESTION_HAS_ANSWERS"))
        .andExpect(jsonPath("$.data.validations[0].warning").value(true));

    verify(getQnaAdminRefundReviewUseCase).execute(any(GetQnaAdminRefundReviewQuery.class));
  }

  @Test
  @DisplayName("POST /admin/web3/qna/questions/{postId}/refund 성공")
  void refund_success() throws Exception {
    given(forceQnaAdminRefundUseCase.execute(any()))
        .willReturn(new ForceQnaAdminRefundResult(sampleExecutionIntent("QNA_ADMIN_REFUND")));

    mockMvc
        .perform(post("/admin/web3/qna/questions/101/refund").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.actionType").value("QNA_ADMIN_REFUND"))
        .andExpect(jsonPath("$.data.executionIntent.id").value("intent-1"))
        .andExpect(jsonPath("$.data.execution.requiresUserSignature").value(false))
        .andExpect(jsonPath("$.data.execution.authorityModel").value("SERVER_RELAYER_ONLY"))
        .andExpect(jsonPath("$.data.signRequest").doesNotExist());

    verify(forceQnaAdminRefundUseCase).execute(any());
  }

  @Test
  @DisplayName("QnA admin endpoint 는 USER 권한이면 403")
  void qnaAdminEndpoint_forbiddenForUser_returns403() throws Exception {
    mockMvc
        .perform(
            get("/admin/web3/qna/questions/101/answers/201/settlement-review")
                .with(userPrincipal(1L)))
        .andExpect(status().isForbidden());

    verifyNoInteractions(getQnaAdminSettlementReviewUseCase);
  }

  @Test
  @DisplayName("QnA admin endpoint 는 인증 없으면 401")
  void qnaAdminEndpoint_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(post("/admin/web3/qna/questions/101/refund"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(forceQnaAdminRefundUseCase);
  }

  @Test
  @DisplayName("settlement review usecase 가 invalid input 을 던지면 400")
  void getSettlementReview_invalidInput_returns400() throws Exception {
    given(getQnaAdminSettlementReviewUseCase.execute(any(GetQnaAdminSettlementReviewQuery.class)))
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
    given(forceQnaAdminSettlementUseCase.execute(any()))
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
        List.of(
            new QnaAdminReviewValidationItem(
                QnaAdminReviewValidationCode.RELAYER_NOT_REGISTERED, true, false, "ok")));
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
                QnaAdminReviewValidationCode.ONCHAIN_QUESTION_HAS_ANSWERS,
                true,
                true,
                "refund will move the question to DELETED_WITH_ANSWERS")));
  }

  private QnaExecutionIntentResult sampleExecutionIntent(String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "101", "201"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.of(2026, 1, 2, 3, 4, 5)),
        new QnaExecutionIntentResult.Execution("EIP1559", 1),
        null,
        false);
  }

  private QnaAdminExecutionAuthorityView authority() {
    return new QnaAdminExecutionAuthorityView(
        ExecutionSignerCapabilityView.ready(
            "sponsor-treasury", "0x1111111111111111111111111111111111111111"),
        true,
        QnaAdminRelayerRegistrationStatus.REGISTERED,
        false,
        "SERVER_RELAYER_ONLY");
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
