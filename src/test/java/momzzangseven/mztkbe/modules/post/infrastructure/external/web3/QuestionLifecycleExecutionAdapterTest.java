package momzzangseven.mztkbe.modules.post.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SignRequestUnavailableReason;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.BeginQuestionUpdateStateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.MatchQuestionCreatePayloadCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QuestionUpdateStatePreparationResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.BeginQuestionUpdateStateUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaEscrowAbiEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionLifecycleExecutionAdapter unit test")
class QuestionLifecycleExecutionAdapterTest {

  private static final String TOKEN_ADDRESS = "0x2222222222222222222222222222222222222222";
  private static final String OTHER_TOKEN_ADDRESS = "0x4444444444444444444444444444444444444444";
  private static final long MOCK_SIGNED_AT = 1_700_000_000L;
  private static final byte[] MOCK_SIGNATURE_BYTES = new byte[65];

  @Mock private QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;
  @Mock private BeginQuestionUpdateStateUseCase beginQuestionUpdateStateUseCase;
  @Mock private CancelExecutionIntentUseCase cancelExecutionIntentUseCase;
  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;
  private final QnaEscrowAbiEncoder qnaEscrowAbiEncoder = new QnaEscrowAbiEncoder();
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private QuestionLifecycleExecutionAdapter adapter;

  @Test
  @DisplayName("precheckQuestionCreate delegates to the qna use case with mapped command")
  void precheckQuestionCreate_delegates() {
    adapter.precheckQuestionCreate(7L, 50L);

    verify(questionEscrowExecutionUseCase)
        .precheckQuestionCreate(new PrecheckQuestionCreateCommand(7L, 50L));
  }

  @Test
  @DisplayName("prepareQuestionCreate delegates to the qna use case with mapped command")
  void prepareQuestionCreate_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionCreate(any()))
        .willReturn(questionIntent("QNA_QUESTION_CREATE", "intent-create"));

    var result = adapter.prepareQuestionCreate(10L, 7L, "질문 내용", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionCreate(new PrepareQuestionCreateCommand(10L, 7L, "질문 내용", 50L));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_QUESTION_CREATE");
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-create");
  }

  @Test
  @DisplayName("cancelSignableIntent delegates to execution cancellation use case")
  void cancelSignableIntent_delegates() {
    given(cancelExecutionIntentUseCase.cancelIfSignable(any())).willReturn(true);

    boolean result = adapter.cancelSignableIntent("intent-create", "question create bind failed");

    assertThat(result).isTrue();
    verify(cancelExecutionIntentUseCase)
        .cancelIfSignable(
            new CancelExecutionIntentCommand(
                "intent-create", "QUESTION_LIFECYCLE_BIND_FAILED", "question create bind failed"));
  }

  @Test
  @DisplayName("loadQuestionCreateIntent restores owner-bound execution intent")
  void loadQuestionCreateIntent_delegates() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용");
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.QUESTION,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                ExecutionActionType.QNA_QUESTION_CREATE,
                payloadHash(payloadSnapshotJson),
                payloadSnapshotJson,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                1_776_129_600L,
                ExecutionMode.EIP7702,
                2,
                null,
                null,
                null,
                null,
                null));
    given(questionEscrowExecutionUseCase.matchesQuestionCreatePayload(any())).willReturn(true);
    given(questionEscrowExecutionUseCase.signatureMetaForSignedAt(MOCK_SIGNED_AT))
        .willReturn(
            new QnaExecutionIntentResult.SignatureMeta(MOCK_SIGNED_AT, MOCK_SIGNED_AT + 900));

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().actionType()).isEqualTo("QNA_QUESTION_CREATE");
    assertThat(result.orElseThrow().executionIntent().id()).isEqualTo("intent-create");
    assertThat(result.orElseThrow().signRequestUnavailableReason()).isNull();
    // §MOM-393 AWAITING_SIGNATURE reload must surface the stored signedAt so the FE can keep
    // signing the existing intent without going through a fresh prepare.
    assertThat(result.orElseThrow().signatureMeta()).isNotNull();
    assertThat(result.orElseThrow().signatureMeta().signedAt()).isEqualTo(MOCK_SIGNED_AT);
    assertThat(result.orElseThrow().signatureMeta().signatureExpiresAt())
        .isEqualTo(MOCK_SIGNED_AT + 900);
    verify(getExecutionIntentUseCase).execute(new GetExecutionIntentQuery(7L, "intent-create"));
    ArgumentCaptor<MatchQuestionCreatePayloadCommand> commandCaptor =
        ArgumentCaptor.forClass(MatchQuestionCreatePayloadCommand.class);
    verify(questionEscrowExecutionUseCase).matchesQuestionCreatePayload(commandCaptor.capture());
    MatchQuestionCreatePayloadCommand command = commandCaptor.getValue();
    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.questionContent()).isEqualTo("질문 내용");
    assertThat(command.rewardMztk()).isEqualTo(50L);
    assertThat(command.payload().postId()).isEqualTo(10L);
    assertThat(command.payload().tokenAddress()).isEqualTo(TOKEN_ADDRESS);
  }

  @Test
  @DisplayName("loadQuestionCreateIntent exposes hidden sign request reason")
  void loadQuestionCreateIntent_exposesSignRequestUnavailableReason() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용");
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.QUESTION,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                ExecutionActionType.QNA_QUESTION_CREATE,
                payloadHash(payloadSnapshotJson),
                payloadSnapshotJson,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                1_776_129_600L,
                ExecutionMode.EIP7702,
                2,
                null,
                SignRequestUnavailableReason.EIP7702_DEADLINE_TOO_CLOSE,
                null,
                null,
                null));
    given(questionEscrowExecutionUseCase.matchesQuestionCreatePayload(any())).willReturn(true);

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().signRequest()).isNull();
    assertThat(result.orElseThrow().signRequestUnavailableReason())
        .isEqualTo("EIP7702_DEADLINE_TOO_CLOSE");
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects non-question resource")
  void loadQuestionCreateIntentRejectsNonQuestionResource() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용");
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.ANSWER,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                ExecutionActionType.QNA_QUESTION_CREATE,
                payloadHash(payloadSnapshotJson),
                payloadSnapshotJson,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                1_776_129_600L,
                ExecutionMode.EIP7702,
                2,
                null,
                null,
                null,
                null,
                null));

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isEmpty();
    verify(questionEscrowExecutionUseCase, never()).matchesQuestionCreatePayload(any());
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects mismatched payload hash")
  void loadQuestionCreateIntentRejectsMismatchedPayloadHash() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용");
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.QUESTION,
                "10",
                ExecutionResourceStatus.PENDING_EXECUTION,
                ExecutionActionType.QNA_QUESTION_CREATE,
                "0x" + "0".repeat(64),
                payloadSnapshotJson,
                "intent-create",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 4, 14, 10, 0),
                1_776_129_600L,
                ExecutionMode.EIP7702,
                2,
                null,
                null,
                null,
                null,
                null));

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isEmpty();
    verify(questionEscrowExecutionUseCase, never()).matchesQuestionCreatePayload(any());
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects mismatched reward amount")
  void loadQuestionCreateIntentRejectsMismatchedRewardAmount() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용", 51L, TOKEN_ADDRESS, null);
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(questionCreateIntentResult(payloadSnapshotJson));
    given(questionEscrowExecutionUseCase.matchesQuestionCreatePayload(any())).willReturn(false);

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects mismatched reward token")
  void loadQuestionCreateIntentRejectsMismatchedRewardToken() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용", 50L, OTHER_TOKEN_ADDRESS, null);
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(questionCreateIntentResult(payloadSnapshotJson));
    given(questionEscrowExecutionUseCase.matchesQuestionCreatePayload(any())).willReturn(false);

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "질문 내용", 50L);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("loadQuestionCreateIntent rejects mismatched call data")
  void loadQuestionCreateIntentRejectsMismatchedCallData() {
    String payloadSnapshotJson = questionCreatePayload("질문 내용", 50L, TOKEN_ADDRESS, "0x1234");
    given(getExecutionIntentUseCase.execute(any()))
        .willReturn(questionCreateIntentResult(payloadSnapshotJson));
    given(questionEscrowExecutionUseCase.matchesQuestionCreatePayload(any())).willReturn(false);

    var result = adapter.loadQuestionCreateIntent(10L, 7L, "intent-create", "변경된 질문 내용", 50L);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("beginQuestionUpdateState delegates with hashed content")
  void beginQuestionUpdateState_delegates() {
    String hash = QnaContentHashFactory.hash("수정된 질문 내용");
    given(beginQuestionUpdateStateUseCase.begin(any()))
        .willReturn(new QuestionUpdateStatePreparationResult(10L, 1L, "token", hash));

    var result = adapter.beginQuestionUpdateState(10L, 7L, "수정된 질문 내용");

    verify(beginQuestionUpdateStateUseCase)
        .begin(new BeginQuestionUpdateStateCommand(10L, 7L, hash));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().updateVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("prepareQuestionUpdate delegates to the qna use case with mapped command")
  void prepareQuestionUpdate_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionUpdate(any()))
        .willReturn(questionIntent("QNA_QUESTION_UPDATE", "intent-update"));

    var result = adapter.prepareQuestionUpdate(10L, 7L, "수정된 질문 내용", 50L, 1L, "token");

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionUpdate(
            new PrepareQuestionUpdateCommand(10L, 7L, "수정된 질문 내용", 50L, 1L, "token"));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().resource().type()).isEqualTo("QUESTION");
  }

  @Test
  @DisplayName("prepareQuestionDelete delegates to the qna use case with mapped command")
  void prepareQuestionDelete_delegates() {
    given(questionEscrowExecutionUseCase.prepareQuestionDelete(any()))
        .willReturn(questionIntent("QNA_QUESTION_DELETE", "intent-delete"));

    var result = adapter.prepareQuestionDelete(10L, 7L, "삭제될 질문", 50L);

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionDelete(new PrepareQuestionDeleteCommand(10L, 7L, "삭제될 질문", 50L));
    assertThat(result).isPresent();
  }

  @Test
  @DisplayName("prepareAnswerAccept delegates to the qna use case with mapped command")
  void prepareAnswerAccept_delegates() {
    given(questionEscrowExecutionUseCase.prepareAnswerAccept(any()))
        .willReturn(questionIntent("QNA_ANSWER_ACCEPT", "intent-accept"));

    var result = adapter.prepareAnswerAccept(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L);

    verify(questionEscrowExecutionUseCase)
        .prepareAnswerAccept(
            new PrepareAnswerAcceptCommand(10L, 20L, 7L, 8L, "질문 내용", "답변 내용", 100L));
    assertThat(result).isPresent();
    assertThat(result.orElseThrow().execution().mode()).isEqualTo("EIP7702");
  }

  private QnaExecutionIntentResult questionIntent(String actionType, String intentId) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "10", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0), 1_776_129_600L),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }

  private GetExecutionIntentResult questionCreateIntentResult(String payloadSnapshotJson) {
    return new GetExecutionIntentResult(
        ExecutionResourceType.QUESTION,
        "10",
        ExecutionResourceStatus.PENDING_EXECUTION,
        ExecutionActionType.QNA_QUESTION_CREATE,
        payloadHash(payloadSnapshotJson),
        payloadSnapshotJson,
        "intent-create",
        ExecutionIntentStatus.AWAITING_SIGNATURE,
        LocalDateTime.of(2026, 4, 14, 10, 0),
        1_776_129_600L,
        ExecutionMode.EIP7702,
        2,
        null,
        null,
        null,
        null,
        null);
  }

  private String questionCreatePayload(String questionContent) {
    return questionCreatePayload(questionContent, 50L, TOKEN_ADDRESS, null);
  }

  private String questionCreatePayload(
      String questionContent, Long rewardMztk, String tokenAddress) {
    return questionCreatePayload(questionContent, rewardMztk, tokenAddress, null);
  }

  private String questionCreatePayload(
      String questionContent, Long rewardMztk, String tokenAddress, String callDataOverride) {
    try {
      String questionHash = QnaContentHashFactory.hash(questionContent);
      BigInteger amountWei = BigInteger.valueOf(rewardMztk);
      // §MOM-393 — production realism: broadcast callData 는 9-arg (server-sig 봉입) 형식.
      // fixture 가 7-arg 로 만들던 시절에는 stored vs expected 비교가 거짓 통과했다.
      String callData =
          callDataOverride == null
              ? qnaEscrowAbiEncoder.encode(
                  QnaExecutionActionType.QNA_QUESTION_CREATE,
                  QnaEscrowIdCodec.questionId(10L),
                  null,
                  tokenAddress,
                  amountWei,
                  questionHash,
                  null,
                  MOCK_SIGNED_AT,
                  MOCK_SIGNATURE_BYTES)
              : callDataOverride;
      return new ObjectMapper()
          .writeValueAsString(
              new QnaEscrowExecutionPayload(
                  QnaExecutionActionType.QNA_QUESTION_CREATE,
                  10L,
                  null,
                  "0x" + "1".repeat(40),
                  tokenAddress,
                  amountWei,
                  questionHash,
                  null,
                  "0x" + "3".repeat(40),
                  callData,
                  null,
                  null,
                  null,
                  null,
                  MOCK_SIGNED_AT,
                  Numeric.toHexString(MOCK_SIGNATURE_BYTES)));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  // §MOM-393 — stored payloadHash 는 server-sig-independent IdempotencyView 의 SHA-256.
  // production 의 QnaExecutionDraftBuilderAdapter#build() 가 hashHex(payload.idempotencyView()) 로
  // 산출하므로 fixture 도 동일한 산출 방식을 따라야 matchesPayloadHash 검증이 통과한다.
  private String payloadHash(String payloadSnapshotJson) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      QnaEscrowExecutionPayload payload =
          mapper.readValue(payloadSnapshotJson, QnaEscrowExecutionPayload.class);
      String viewJson = mapper.writeValueAsString(payload.idempotencyView());
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(viewJson.getBytes(StandardCharsets.UTF_8));
      return Numeric.toHexString(digest);
    } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
