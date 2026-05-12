package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaRewardTokenConfigPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaEscrowAbiEncoder;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
public class QuestionLifecycleExecutionAdapter implements QuestionLifecycleExecutionPort {

  private final QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;
  private final BeginQuestionUpdateStateUseCase beginQuestionUpdateStateUseCase;
  private final CancelExecutionIntentUseCase cancelExecutionIntentUseCase;
  private final GetExecutionIntentUseCase getExecutionIntentUseCase;
  private final LoadQnaRewardTokenConfigPort loadQnaRewardTokenConfigPort;
  private final QnaEscrowAbiEncoder qnaEscrowAbiEncoder;
  private final ObjectMapper objectMapper;

  @Override
  public boolean managesAcceptLifecycle() {
    return true;
  }

  @Override
  public boolean managesQuestionCreateLifecycle() {
    return true;
  }

  @Override
  public boolean hasActiveQuestionIntent(Long postId) {
    return questionEscrowExecutionUseCase.hasActiveQuestionIntent(postId);
  }

  @Override
  public boolean cancelSignableIntent(String executionIntentId, String reason) {
    return cancelExecutionIntentUseCase.cancelIfSignable(
        new CancelExecutionIntentCommand(
            executionIntentId, "QUESTION_LIFECYCLE_BIND_FAILED", reason));
  }

  @Override
  public Optional<QuestionExecutionWriteView> loadQuestionCreateIntent(
      Long postId,
      Long requesterUserId,
      String executionIntentId,
      String questionContent,
      Long rewardMztk) {
    GetExecutionIntentResult result =
        getExecutionIntentUseCase.execute(
            new GetExecutionIntentQuery(requesterUserId, executionIntentId));
    if (!"QUESTION".equals(result.resourceType().name())
        || !String.valueOf(postId).equals(result.resourceId())
        || !"QNA_QUESTION_CREATE".equals(result.actionType().name())
        || !matchesPayloadHash(result.payloadHash(), result.payloadSnapshotJson())
        || !matchesQuestionCreatePayload(
            postId, questionContent, rewardMztk, result.payloadSnapshotJson())) {
      return Optional.empty();
    }
    return Optional.of(toView(result));
  }

  @Override
  public void precheckQuestionCreate(Long requesterUserId, Long rewardMztk) {
    questionEscrowExecutionUseCase.precheckQuestionCreate(
        new PrecheckQuestionCreateCommand(requesterUserId, rewardMztk));
  }

  @Override
  public Optional<QuestionUpdateStatePreparation> beginQuestionUpdateState(
      Long postId, Long requesterUserId, String questionContent) {
    String expectedQuestionHash = QnaContentHashFactory.hash(questionContent);
    return Optional.of(
            beginQuestionUpdateStateUseCase.begin(
                new BeginQuestionUpdateStateCommand(postId, requesterUserId, expectedQuestionHash)))
        .map(
            result ->
                new QuestionUpdateStatePreparation(
                    result.postId(),
                    result.updateVersion(),
                    result.updateToken(),
                    result.expectedQuestionHash()));
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
      Long postId,
      Long requesterUserId,
      String questionContent,
      Long rewardMztk,
      Long questionUpdateVersion,
      String questionUpdateToken) {
    return Optional.of(
        toView(
            questionEscrowExecutionUseCase.prepareQuestionUpdate(
                new PrepareQuestionUpdateCommand(
                    postId,
                    requesterUserId,
                    questionContent,
                    rewardMztk,
                    questionUpdateVersion,
                    questionUpdateToken))));
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
        result.existing(),
        toSignatureMeta(result.signatureMeta()));
  }

  private QuestionExecutionWriteView.SignatureMeta toSignatureMeta(
      momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult.SignatureMeta
          meta) {
    if (meta == null) {
      return null;
    }
    return new QuestionExecutionWriteView.SignatureMeta(meta.signedAt(), meta.signatureExpiresAt());
  }

  private QuestionExecutionWriteView toView(GetExecutionIntentResult result) {
    return new QuestionExecutionWriteView(
        new QuestionExecutionWriteView.Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        "QNA_QUESTION_CREATE",
        new QuestionExecutionWriteView.ExecutionIntent(
            result.executionIntentId(), result.executionIntentStatus().name(), result.expiresAt()),
        new QuestionExecutionWriteView.Execution(result.mode().name(), result.signCount()),
        toSignRequest(result.signRequest()),
        true);
  }

  private boolean matchesQuestionCreatePayload(
      Long postId, String questionContent, Long rewardMztk, String payloadSnapshotJson) {
    if (payloadSnapshotJson == null || payloadSnapshotJson.isBlank()) {
      return false;
    }
    try {
      QnaEscrowExecutionPayload payload =
          objectMapper.readValue(payloadSnapshotJson, QnaEscrowExecutionPayload.class);
      LoadQnaRewardTokenConfigPort.RewardTokenConfig rewardTokenConfig =
          loadQnaRewardTokenConfigPort.loadRewardTokenConfig();
      String expectedQuestionHash = QnaContentHashFactory.hash(questionContent);
      BigInteger expectedAmountWei =
          QnaEscrowIdCodec.toAmountWei(rewardMztk, rewardTokenConfig.decimals());
      String expectedTokenAddress = EvmAddress.of(rewardTokenConfig.tokenContractAddress()).value();
      String payloadTokenAddress = EvmAddress.of(payload.tokenAddress()).value();
      String expectedCallData =
          qnaEscrowAbiEncoder.encode(
              QnaExecutionActionType.QNA_QUESTION_CREATE,
              QnaEscrowIdCodec.questionId(postId),
              null,
              expectedTokenAddress,
              expectedAmountWei,
              expectedQuestionHash,
              null);
      return payload.actionType() == QnaExecutionActionType.QNA_QUESTION_CREATE
          && postId.equals(payload.postId())
          && payload.answerId() == null
          && expectedQuestionHash.equals(payload.questionHash())
          && payload.contentHash() == null
          && expectedAmountWei.equals(payload.amountWei())
          && expectedTokenAddress.equals(payloadTokenAddress)
          && expectedCallData.equals(payload.callData());
    } catch (JsonProcessingException e) {
      throw new Web3InvalidInputException("invalid qna question create payload snapshot");
    }
  }

  private boolean matchesPayloadHash(String payloadHash, String payloadSnapshotJson) {
    if (payloadHash == null
        || payloadHash.isBlank()
        || payloadSnapshotJson == null
        || payloadSnapshotJson.isBlank()) {
      return false;
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(payloadSnapshotJson.getBytes(StandardCharsets.UTF_8));
      return payloadHash.equals(Numeric.toHexString(digest));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 digest is unavailable", e);
    }
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
