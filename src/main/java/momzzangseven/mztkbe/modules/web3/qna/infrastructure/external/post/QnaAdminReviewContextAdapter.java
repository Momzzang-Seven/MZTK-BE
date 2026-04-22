package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.post;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryForUpdateUseCase;
import momzzangseven.mztkbe.modules.answer.application.port.in.GetAnswerSummaryUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminReviewContextPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAnswerIdsPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
public class QnaAdminReviewContextAdapter implements LoadQnaAdminReviewContextPort {

  private final GetPostContextUseCase getPostContextUseCase;
  private final GetAnswerSummaryUseCase getAnswerSummaryUseCase;
  private final GetAnswerSummaryForUpdateUseCase getAnswerSummaryForUpdateUseCase;
  private final LoadQnaAnswerIdsPort loadQnaAnswerIdsPort;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final LoadQnaExecutionIntentStatePort loadQnaExecutionIntentStatePort;
  private final LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;
  private final QnaEscrowProperties qnaEscrowProperties;

  @Override
  @Transactional(readOnly = true)
  public SettlementContext loadSettlement(Long postId, Long answerId) {
    return new SettlementContext(
        getPostContextUseCase.getPostContext(postId).map(this::toLocalQuestion),
        getAnswerSummaryUseCase.getAnswerSummary(answerId).map(this::toLocalAnswer),
        qnaProjectionPersistencePort.findQuestionByPostId(postId),
        qnaProjectionPersistencePort.findAnswerByAnswerId(answerId),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.QUESTION, String.valueOf(postId)),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.ANSWER, String.valueOf(answerId)),
        loadAuthority(),
        loadExecutionInternalIssuerPolicyPort.loadPolicy());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SettlementContext loadSettlementForUpdate(Long postId, Long answerId) {
    Optional<LocalAnswer> localAnswer =
        getAnswerSummaryForUpdateUseCase
            .getAnswerSummaryForUpdate(answerId)
            .map(this::toLocalAnswer);
    Optional<LocalQuestion> localQuestion =
        getPostContextUseCase.getPostContextForUpdate(postId).map(this::toLocalQuestion);
    return new SettlementContext(
        localQuestion,
        localAnswer,
        qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(postId),
        qnaProjectionPersistencePort.findAnswerByAnswerIdForUpdate(answerId),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResourceForUpdate(
            QnaExecutionResourceType.QUESTION, String.valueOf(postId)),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResourceForUpdate(
            QnaExecutionResourceType.ANSWER, String.valueOf(answerId)),
        loadAuthority(),
        loadExecutionInternalIssuerPolicyPort.loadPolicy());
  }

  @Override
  @Transactional(readOnly = true)
  public RefundContext loadRefund(Long postId) {
    List<String> answerResourceIds = loadRefundAnswerResourceIds(postId, false);
    return new RefundContext(
        getPostContextUseCase.getPostContext(postId).map(this::toLocalQuestion),
        qnaProjectionPersistencePort.findQuestionByPostId(postId),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResource(
            QnaExecutionResourceType.QUESTION, String.valueOf(postId)),
        List.copyOf(
            loadQnaExecutionIntentStatePort
                .loadLatestActiveByResources(QnaExecutionResourceType.ANSWER, answerResourceIds)
                .values()),
        loadAuthority(),
        loadExecutionInternalIssuerPolicyPort.loadPolicy());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public RefundContext loadRefundForUpdate(Long postId) {
    List<String> answerResourceIds = loadRefundAnswerResourceIds(postId, true);
    return new RefundContext(
        getPostContextUseCase.getPostContextForUpdate(postId).map(this::toLocalQuestion),
        qnaProjectionPersistencePort.findQuestionByPostIdForUpdate(postId),
        loadQnaExecutionIntentStatePort.loadLatestActiveByResourceForUpdate(
            QnaExecutionResourceType.QUESTION, String.valueOf(postId)),
        List.copyOf(
            loadQnaExecutionIntentStatePort
                .loadLatestActiveByResourcesForUpdate(
                    QnaExecutionResourceType.ANSWER, answerResourceIds)
                .values()),
        loadAuthority(),
        loadExecutionInternalIssuerPolicyPort.loadPolicy());
  }

  private ExecutionAuthority loadAuthority() {
    String signerAddress = loadQnaAdminSignerAddressPort.loadSignerAddress();
    boolean relayerRegistered =
        qnaContractCallSupport.isRelayerRegistered(
            qnaEscrowProperties.getQnaContractAddress(), signerAddress);
    return new ExecutionAuthority(signerAddress, relayerRegistered);
  }

  private List<String> loadRefundAnswerResourceIds(Long postId, boolean forUpdate) {
    Set<String> answerResourceIds =
        new LinkedHashSet<>(
            loadQnaAnswerIdsPort.loadAnswerIdsByPostId(postId).stream()
                .map(String::valueOf)
                .toList());
    List<Long> projectionAnswerIds =
        (forUpdate
                ? qnaProjectionPersistencePort.findAnswersByPostIdForUpdate(postId)
                : qnaProjectionPersistencePort.findAnswersByPostId(postId))
            .stream().map(answer -> answer.getAnswerId()).toList();
    projectionAnswerIds.stream().map(String::valueOf).forEach(answerResourceIds::add);
    return List.copyOf(answerResourceIds);
  }

  private LocalQuestion toLocalQuestion(GetPostContextUseCase.PostContext postContext) {
    return new LocalQuestion(
        postContext.postId(),
        postContext.writerId(),
        postContext.questionPost(),
        LoadQnaAdminReviewContextPort.LocalQuestionStatus.fromExternalName(
            postContext.status() == null ? null : postContext.status().name()),
        postContext.solved(),
        postContext.answerLocked(),
        postContext.content(),
        postContext.reward(),
        postContext.acceptedAnswerId());
  }

  private LocalAnswer toLocalAnswer(GetAnswerSummaryUseCase.AnswerSummary answerSummary) {
    return new LocalAnswer(
        answerSummary.answerId(),
        answerSummary.postId(),
        answerSummary.userId(),
        answerSummary.content(),
        answerSummary.accepted());
  }
}
