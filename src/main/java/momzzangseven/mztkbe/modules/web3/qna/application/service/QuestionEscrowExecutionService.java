package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.PrecheckQuestionFundingPort;
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
public class QuestionEscrowExecutionService implements QuestionEscrowExecutionUseCase {

  private final PrecheckQuestionFundingPort precheckQuestionFundingPort;
  private final LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  private final QnaProjectionPersistencePort qnaProjectionPersistencePort;
  private final BuildQnaExecutionDraftPort buildQnaExecutionDraftPort;
  private final SubmitQnaExecutionDraftPort submitQnaExecutionDraftPort;

  @Override
  public void precheckQuestionCreate(PrecheckQuestionCreateCommand command) {
    command.validate();
    precheckQuestionFundingPort.precheck(command);
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionCreate(PrepareQuestionCreateCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    String questionHash = QnaContentHashFactory.hash(command.questionContent());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            questionHash,
            null));
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionUpdate(PrepareQuestionUpdateCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    String questionHash = QnaContentHashFactory.hash(command.questionContent());
    requireQuestionProjection(command.postId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_UPDATE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            questionHash,
            null));
  }

  @Override
  public QnaExecutionIntentResult prepareQuestionDelete(PrepareQuestionDeleteCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    QnaQuestionProjection question = requireQuestionProjection(command.postId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            command.requesterUserId(),
            null,
            command.postId(),
            null,
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            null));
  }

  @Override
  public QnaExecutionIntentResult prepareAnswerAccept(PrepareAnswerAcceptCommand command) {
    command.validate();

    RewardContext rewardContext = loadRewardContext(command.rewardMztk());
    QnaQuestionProjection question = requireQuestionProjection(command.postId());
    QnaAnswerProjection answer = requireAnswerProjection(command.answerId());

    return submit(
        new QnaEscrowExecutionRequest(
            QnaExecutionResourceType.QUESTION,
            String.valueOf(command.postId()),
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            command.requesterUserId(),
            command.answerWriterUserId(),
            command.postId(),
            command.answerId(),
            null,
            rewardContext.tokenAddress(),
            rewardContext.amountWei(),
            question.getQuestionHash(),
            answer.getContentHash()));
  }

  private QnaQuestionProjection requireQuestionProjection(Long postId) {
    return qnaProjectionPersistencePort
        .findQuestionByPostIdForUpdate(postId)
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "question is not registered onchain yet: postId=" + postId));
  }

  private QnaAnswerProjection requireAnswerProjection(Long answerId) {
    return qnaProjectionPersistencePort
        .findAnswerByAnswerIdForUpdate(answerId)
        .orElseThrow(
            () ->
                new Web3InvalidInputException(
                    "answer is not registered onchain yet: answerId=" + answerId));
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
