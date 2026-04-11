package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.SubmitQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaAnswerProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.model.QnaQuestionProjection;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class AnswerEscrowExecutionService implements AnswerEscrowExecutionUseCase {

  private final LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  private final SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @Override
  public QnaExecutionIntentResult prepareAnswerCreate(PrepareAnswerCreateCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    String answerHash = QnaContentHashFactory.hash(command.answerContent());
    QnaQuestionProjection question =
        findOrBootstrapQuestion(
                command.postId(),
                command.questionWriterUserId(),
                command.questionContent(),
                rewardContext)
            .syncAnswerCount(command.activeAnswerCount());
    qnaProjectionPersistencePort.saveQuestion(question);
    qnaProjectionPersistencePort.saveAnswer(
        findOrBootstrapAnswer(
                command.answerId(),
                command.postId(),
                command.requesterUserId(),
                command.answerContent())
            .updateContentHash(answerHash));

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            answerHash));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerUpdate(PrepareAnswerUpdateCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    String answerHash = QnaContentHashFactory.hash(command.answerContent());
    qnaProjectionPersistencePort.saveQuestion(
        findOrBootstrapQuestion(
                command.postId(),
                command.questionWriterUserId(),
                command.questionContent(),
                rewardContext)
            .syncAnswerCount(command.activeAnswerCount()));
    qnaProjectionPersistencePort.saveAnswer(
        findOrBootstrapAnswer(
                command.answerId(),
                command.postId(),
                command.requesterUserId(),
                command.answerContent())
            .updateContentHash(answerHash));

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_UPDATE,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            QnaContentHashFactory.hash(command.questionContent()),
            answerHash));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerDelete(PrepareAnswerDeleteCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    qnaProjectionPersistencePort.saveQuestion(
        findOrBootstrapQuestion(
                command.postId(),
                command.questionWriterUserId(),
                command.questionContent(),
                rewardContext)
            .syncAnswerCount(command.activeAnswerCount()));
    qnaProjectionPersistencePort.deleteAnswerByAnswerId(command.answerId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.ANSWER,
            String.valueOf(command.answerId()),
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            command.requesterUserId(),
            command.questionWriterUserId(),
            command.postId(),
            command.answerId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            QnaContentHashFactory.hash(command.questionContent()),
            null));
  }

  private QnaQuestionProjection findOrBootstrapQuestion(
      Long postId, Long questionWriterUserId, String questionContent, RewardContext rewardContext) {
    return qnaProjectionPersistencePort
        .findQuestionByPostIdForUpdate(postId)
        .orElseGet(
            () ->
                QnaQuestionProjection.create(
                    postId,
                    questionWriterUserId,
                    QnaEscrowIdCodec.questionId(postId),
                    rewardContext.tokenAddress(),
                    rewardContext.amountWei(),
                    QnaContentHashFactory.hash(questionContent)));
  }

  private QnaAnswerProjection findOrBootstrapAnswer(
      Long answerId, Long postId, Long requesterUserId, String answerContent) {
    return qnaProjectionPersistencePort
        .findAnswerByAnswerIdForUpdate(answerId)
        .orElseGet(
            () ->
                QnaAnswerProjection.create(
                    answerId,
                    postId,
                    QnaEscrowIdCodec.questionId(postId),
                    QnaEscrowIdCodec.answerId(answerId),
                    requesterUserId,
                    QnaContentHashFactory.hash(answerContent)));
  }

  private RewardContext loadRewardContext(Long rewardMztk) {
    LoadQnaRewardTokenConfigPort.RewardTokenConfig config =
        loadQnaRewardTokenConfigPort.loadRewardTokenConfig();
    BigInteger amountWei = QnaEscrowIdCodec.toAmountWei(rewardMztk, config.decimals());
    return new RewardContext(config.tokenContractAddress(), amountWei);
  }

  private QnaExecutionIntentResult submit(QnaEscrowExecutionRequest request) {
    return submitQnaExecutionDraftPort.submit(buildQnaExecutionDraftPort.build(request));
  }

  private record RewardContext(String tokenAddress, BigInteger amountWei) {}
}
