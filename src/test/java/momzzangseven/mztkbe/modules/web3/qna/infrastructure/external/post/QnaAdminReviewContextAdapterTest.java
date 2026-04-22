package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRelayerRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionIntentStateView;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaAdminReviewContextAdapter unit test")
class QnaAdminReviewContextAdapterTest {

  @Mock private GetPostContextUseCase getPostContextUseCase;
  @Mock private GetAnswerSummaryUseCase getAnswerSummaryUseCase;
  @Mock private GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;
  @Mock private LoadQnaAnswerIdsPort loadQnaAnswerIdsPort;
  @Mock private QnaProjectionPersistencePort qnaProjectionPersistencePort;
  @Mock private LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  @Mock private ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase;
  @Mock private QnaContractCallSupport qnaContractCallSupport;
  @Mock private LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;

  private QnaAdminReviewContextAdapter adapter;

  @BeforeEach
  void setUp() {
    QnaEscrowProperties qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x1111111111111111111111111111111111111111");
    adapter =
        new QnaAdminReviewContextAdapter(
            getPostContextUseCase,
            getAnswerSummaryUseCase,
            getAnswerSummaryForUpdateUseCase,
            loadQnaAnswerIdsPort,
            qnaProjectionPersistencePort,
            loadQnaExecutionIntentStatePort,
            probeExecutionSignerCapabilityUseCase,
            qnaContractCallSupport,
            loadExecutionInternalIssuerPolicyPort,
            qnaEscrowProperties);

    when(getPostContextUseCase.getPostContext(101L))
        .thenReturn(
            Optional.of(
                new GetPostContextUseCase.PostContext(
                    101L, 7L, false, true, "질문 본문", 50L, false, PostStatus.OPEN, null)));
    when(qnaProjectionPersistencePort.findQuestionByPostId(101L))
        .thenReturn(
            Optional.of(
                QnaQuestionProjection.create(
                    101L,
                    7L,
                    QnaEscrowIdCodec.questionId(101L),
                    "0x2222222222222222222222222222222222222222",
                    new BigInteger("50000000000000000000"),
                    QnaContentHashFactory.hash("질문 본문"))));
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType.QUESTION,
            "101"))
        .thenReturn(Optional.empty());
    when(probeExecutionSignerCapabilityUseCase.execute())
        .thenReturn(
            ExecutionSignerCapabilityView.ready(
                "sponsor-treasury", "0x3333333333333333333333333333333333333333"));
    when(qnaContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x3333333333333333333333333333333333333333"))
        .thenReturn(true);
    when(loadExecutionInternalIssuerPolicyPort.loadPolicy())
        .thenReturn(
            new LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy(
                true, true, true));
  }

  @Test
  @DisplayName("refund review loads active answer intents from both local answers and projections")
  void loadRefund_includesLocalAndProjectedAnswerIds() {
    when(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(101L)).thenReturn(List.of(301L));
    when(qnaProjectionPersistencePort.findAnswersByPostId(101L))
        .thenReturn(List.of(answerProjection(201L)));
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResources(
            momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType.ANSWER,
            List.of("301", "201")))
        .thenReturn(activeAnswerIntents());

    var context = adapter.loadRefund(101L);

    assertThat(context.activeAnswerIntents())
        .extracting(QnaExecutionIntentStateView::executionIntentId)
        .containsExactly("intent-local-answer", "intent-projected-answer");
  }

  @Test
  @DisplayName("review context soft-fails when relayer check throws")
  void loadRefund_returnsAuthorityEvenWhenRelayerCheckFails() {
    when(loadQnaAnswerIdsPort.loadAnswerIdsByPostId(101L)).thenReturn(List.of());
    when(qnaProjectionPersistencePort.findAnswersByPostId(101L)).thenReturn(List.of());
    when(loadQnaExecutionIntentStatePort.loadLatestActiveByResources(
            momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType.ANSWER,
            List.of()))
        .thenReturn(Map.of());
    when(qnaContractCallSupport.isRelayerRegistered(
            "0x1111111111111111111111111111111111111111",
            "0x3333333333333333333333333333333333333333"))
        .thenThrow(new RuntimeException("rpc down"));

    assertThatCode(() -> adapter.loadRefund(101L)).doesNotThrowAnyException();

    var context = adapter.loadRefund(101L);

    assertThat(context.authority().serverSigner().signable()).isTrue();
    assertThat(context.authority().relayerRegistered()).isFalse();
    assertThat(context.authority().relayerRegistrationStatus())
        .isEqualTo(QnaAdminRelayerRegistrationStatus.CHECK_FAILED);
  }

  private Map<String, QnaExecutionIntentStateView> activeAnswerIntents() {
    Map<String, QnaExecutionIntentStateView> result = new LinkedHashMap<>();
    result.put(
        "301",
        new QnaExecutionIntentStateView(
            "intent-local-answer",
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            ExecutionIntentStatus.AWAITING_SIGNATURE));
    result.put(
        "201",
        new QnaExecutionIntentStateView(
            "intent-projected-answer",
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            ExecutionIntentStatus.PENDING_ONCHAIN));
    return result;
  }

  private QnaAnswerProjection answerProjection(Long answerId) {
    return QnaAnswerProjection.create(
        answerId,
        101L,
        QnaEscrowIdCodec.questionId(101L),
        QnaEscrowIdCodec.answerId(answerId),
        22L,
        "0x" + "a".repeat(64));
  }
}
