package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QuestionLifecycleExecutionAdapter implements QuestionLifecycleExecutionPort {

  private final QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;

  @Override
  public boolean managesAcceptLifecycle() {
    return true;
  }

  @Override
  public boolean hasActiveQuestionIntent(Long postId) {
    return questionEscrowExecutionUseCase.hasActiveQuestionIntent(postId);
  }

  @Override
  public void precheckQuestionCreate(Long requesterUserId, Long rewardMztk) {
    questionEscrowExecutionUseCase.precheckQuestionCreate(
        new PrecheckQuestionCreateCommand(requesterUserId, rewardMztk));
  }

  @Override
  public Optional<QuestionExecutionWriteView> prepareQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.prepareQuestionCreate(
                new PrepareQuestionCreateCommand(
                    postId, requesterUserId, questionContent, rewardMztk))));
  }

  @Override
  public Optional<QuestionExecutionWriteView> recoverQuestionCreate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.recoverQuestionCreate(
                new PrepareQuestionCreateCommand(
                    postId, requesterUserId, questionContent, rewardMztk))));
  }

  @Override
  public Optional<QuestionExecutionWriteView> recoverQuestionUpdate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    return questionEscrowExecutionUseCase
        .recoverQuestionUpdate(
            new PrepareQuestionUpdateCommand(postId, requesterUserId, questionContent, rewardMztk))
        .map(this::toView);
  }

  @Override
  public Optional<QuestionExecutionWriteView> prepareQuestionUpdate(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.prepareQuestionUpdate(
                new PrepareQuestionUpdateCommand(
                    postId, requesterUserId, questionContent, rewardMztk))));
  }

  @Override
  public Optional<QuestionExecutionWriteView> prepareQuestionDelete(
      Long postId, Long requesterUserId, String questionContent, Long rewardMztk) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.prepareQuestionDelete(
                new PrepareQuestionDeleteCommand(
                    postId, requesterUserId, questionContent, rewardMztk))));
  }

  @Override
  public Optional<QuestionExecutionWriteView> prepareAnswerAccept(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long answerWriterUserId,
      String questionContent,
      String answerContent,
      Long rewardMztk) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.prepareAnswerAccept(
                new PrepareAnswerAcceptCommand(
                    postId,
                    answerId,
                    requesterUserId,
                    answerWriterUserId,
                    questionContent,
                    answerContent,
                    rewardMztk))));
  }

  private QuestionExecutionWriteView toView(
      momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult result) {
    return new QuestionExecutionWriteView(
        new QuestionExecutionWriteView.Resource(
            result.resource().type(), result.resource().id(), result.resource().status()),
        result.actionType(),
        new QuestionExecutionWriteView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new QuestionExecutionWriteView.Execution(
            result.execution().mode(), result.execution().signCount()),
        toSignRequest(result.signRequest()),
        result.existing());
  }

  private QuestionExecutionWriteView.SignRequest toSignRequest(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle bundle) {
    if (bundle == null) {
      return null;
    }
    return new QuestionExecutionWriteView.SignRequest(
        toAuthorization(bundle.authorization()),
        toSubmit(bundle.submit()),
        toTransaction(bundle.transaction()));
  }

  private QuestionExecutionWriteView.Authorization toAuthorization(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle
              .AuthorizationSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new QuestionExecutionWriteView.Authorization(
        request.chainId(),
        request.delegateTarget(),
        request.authorityNonce(),
        request.payloadHashToSign());
  }

  private QuestionExecutionWriteView.Submit toSubmit(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle.SubmitSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new QuestionExecutionWriteView.Submit(
        request.executionDigest(), request.deadlineEpochSeconds());
  }

  private QuestionExecutionWriteView.Transaction toTransaction(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle.TransactionSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new QuestionExecutionWriteView.Transaction(
        request.chainId(),
        request.fromAddress(),
        request.toAddress(),
        request.valueHex(),
        request.data(),
        request.nonce(),
        request.gasLimitHex(),
        request.maxPriorityFeePerGasHex(),
        request.maxFeePerGasHex(),
        request.expectedNonce());
  }
}
