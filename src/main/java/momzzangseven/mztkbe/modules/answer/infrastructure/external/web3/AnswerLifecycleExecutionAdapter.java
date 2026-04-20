package momzzangseven.mztkbe.modules.answer.infrastructure.external.web3;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnUserExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnUserExecutionEnabled
public class AnswerLifecycleExecutionAdapter implements AnswerLifecycleExecutionPort {

  private final AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase;

  @Override
  public boolean hasActiveAnswerIntent(Long answerId) {
    return answerEscrowExecutionUseCase.hasActiveAnswerIntent(answerId);
  }

  @Override
  public void precheckAnswerCreate(Long postId, String questionContent) {
    answerEscrowExecutionUseCase.precheckAnswerCreate(
        new momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckAnswerCreateCommand(
            postId, questionContent));
  }

  @Override
  public Optional<AnswerExecutionWriteView> prepareAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    return Optional.of(
        toView(
            answerEscrowExecutionUseCase.prepareAnswerCreate(
                new PrepareAnswerCreateCommand(
                    postId,
                    answerId,
                    requesterUserId,
                    questionWriterUserId,
                    questionContent,
                    rewardMztk,
                    answerContent,
                    activeAnswerCount))));
  }

  @Override
  public Optional<AnswerExecutionWriteView> recoverAnswerCreate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    return Optional.of(
        toView(
            answerEscrowExecutionUseCase.recoverAnswerCreate(
                new PrepareAnswerCreateCommand(
                    postId,
                    answerId,
                    requesterUserId,
                    questionWriterUserId,
                    questionContent,
                    rewardMztk,
                    answerContent,
                    activeAnswerCount))));
  }

  @Override
  public Optional<AnswerExecutionWriteView> recoverAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    return answerEscrowExecutionUseCase
        .recoverAnswerUpdate(
            new PrepareAnswerUpdateCommand(
                postId,
                answerId,
                requesterUserId,
                questionWriterUserId,
                questionContent,
                rewardMztk,
                answerContent,
                activeAnswerCount))
        .map(this::toView);
  }

  @Override
  public Optional<AnswerExecutionWriteView> prepareAnswerUpdate(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      String answerContent,
      int activeAnswerCount) {
    return Optional.of(
        toView(
            answerEscrowExecutionUseCase.prepareAnswerUpdate(
                new PrepareAnswerUpdateCommand(
                    postId,
                    answerId,
                    requesterUserId,
                    questionWriterUserId,
                    questionContent,
                    rewardMztk,
                    answerContent,
                    activeAnswerCount))));
  }

  @Override
  public Optional<AnswerExecutionWriteView> prepareAnswerDelete(
      Long postId,
      Long answerId,
      Long requesterUserId,
      Long questionWriterUserId,
      String questionContent,
      Long rewardMztk,
      int activeAnswerCount) {
    return Optional.of(
        toView(
            answerEscrowExecutionUseCase.prepareAnswerDelete(
                new PrepareAnswerDeleteCommand(
                    postId,
                    answerId,
                    requesterUserId,
                    questionWriterUserId,
                    questionContent,
                    rewardMztk,
                    activeAnswerCount))));
  }

  private AnswerExecutionWriteView toView(
      momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult result) {
    return new AnswerExecutionWriteView(
        new AnswerExecutionWriteView.Resource(
            result.resource().type(), result.resource().id(), result.resource().status()),
        result.actionType(),
        new AnswerExecutionWriteView.ExecutionIntent(
            result.executionIntent().id(),
            result.executionIntent().status(),
            result.executionIntent().expiresAt()),
        new AnswerExecutionWriteView.Execution(
            result.execution().mode(), result.execution().signCount()),
        toSignRequest(result.signRequest()),
        result.existing());
  }

  private AnswerExecutionWriteView.SignRequest toSignRequest(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle bundle) {
    if (bundle == null) {
      return null;
    }
    return new AnswerExecutionWriteView.SignRequest(
        toAuthorization(bundle.authorization()),
        toSubmit(bundle.submit()),
        toTransaction(bundle.transaction()));
  }

  private AnswerExecutionWriteView.Authorization toAuthorization(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle
              .AuthorizationSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new AnswerExecutionWriteView.Authorization(
        request.chainId(),
        request.delegateTarget(),
        request.authorityNonce(),
        request.payloadHashToSign());
  }

  private AnswerExecutionWriteView.Submit toSubmit(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle.SubmitSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new AnswerExecutionWriteView.Submit(
        request.executionDigest(), request.deadlineEpochSeconds());
  }

  private AnswerExecutionWriteView.Transaction toTransaction(
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle.TransactionSignRequest
          request) {
    if (request == null) {
      return null;
    }
    return new AnswerExecutionWriteView.Transaction(
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
