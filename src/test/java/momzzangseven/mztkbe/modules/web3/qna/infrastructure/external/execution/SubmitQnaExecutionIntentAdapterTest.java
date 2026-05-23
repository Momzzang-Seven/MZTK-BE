package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.transfer.QuestionRewardExecutionActionHandlerAdapter;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaExecutionIntentStatePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAdminRefundStateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAnswerUpdateSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaLocalDeleteSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaProjectionPersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionPublicationSyncPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaQuestionUpdateStatePersistencePort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaEscrowProperties;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardExecutionPayload;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetQuestionRewardIntentSnapshotUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.MarkQuestionRewardIntentSubmittedUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmitQnaExecutionIntentAdapter")
class SubmitQnaExecutionIntentAdapterTest {

  private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-04-07T12:00:00");
  private static final String AUTHORITY = "0x" + "1".repeat(40);
  private static final String TOKEN = "0x" + "2".repeat(40);
  private static final String ESCROW = "0x" + "3".repeat(40);
  private static final String DELEGATE = "0x" + "4".repeat(40);
  private static final String CALL_DATA = "0xabcdef01" + "0".repeat(128);

  @Mock private CreateExecutionIntentUseCase createExecutionIntentUseCase;
  @Mock private ManageQnaAnswerExecutionIntentRefPort refPersistencePort;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private QnaEscrowProperties qnaEscrowProperties;
  private SubmitQnaExecutionIntentAdapter adapter;

  @BeforeEach
  void setUp() {
    qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress(ESCROW);
    qnaEscrowProperties.setSigValidityDuration(900);

    adapter =
        new SubmitQnaExecutionIntentAdapter(
            createExecutionIntentUseCase, objectMapper, refPersistencePort, qnaEscrowProperties);
  }

  @ParameterizedTest
  @MethodSource("escrowActions")
  @DisplayName("QnA escrow draft calls and runtime actionPlan calls are identical")
  void submitQnaDraft_matchesQnaEscrowActionPlanCalls(
      QnaExecutionResourceType resourceType, QnaExecutionActionType actionType) throws Exception {
    QnaEscrowExecutionActionHandlerAdapter actionHandler = qnaEscrowHandler();
    ArgumentCaptor<CreateExecutionIntentCommand> commandCaptor =
        ArgumentCaptor.forClass(CreateExecutionIntentCommand.class);
    when(createExecutionIntentUseCase.execute(any()))
        .thenReturn(result(ExecutionResourceType.valueOf(resourceType.name())));

    adapter.submit(qnaDraft(resourceType, actionType));

    verify(createExecutionIntentUseCase).execute(commandCaptor.capture());
    ExecutionDraft createdDraft = commandCaptor.getValue().draft();
    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intentFromDraft(createdDraft));

    assertThat(actionPlan.calls()).containsExactlyElementsOf(createdDraft.calls());
  }

  @Test
  @DisplayName("legacy/direct QNA_ANSWER_ACCEPT draft and handler calls are identical")
  void submitQnaAnswerAccept_matchesLegacyQuestionRewardActionPlanCalls() throws Exception {
    QuestionRewardExecutionPayload payload =
        new QuestionRewardExecutionPayload(
            101L,
            202L,
            7L,
            8L,
            AUTHORITY,
            "0x" + "5".repeat(40),
            TOKEN,
            BigInteger.valueOf(500),
            CALL_DATA);
    List<ExecutionDraftCall> draftCalls =
        List.of(new ExecutionDraftCall(TOKEN, BigInteger.ZERO, CALL_DATA));
    ExecutionIntent intent =
        intent(
            ExecutionResourceType.QUESTION,
            "101",
            ExecutionActionType.QNA_ANSWER_ACCEPT,
            objectMapper.writeValueAsString(payload));
    QuestionRewardExecutionActionHandlerAdapter actionHandler =
        new QuestionRewardExecutionActionHandlerAdapter(
            objectMapper,
            mock(GetQuestionRewardIntentSnapshotUseCase.class),
            mock(MarkQuestionRewardIntentSubmittedUseCase.class));

    ExecutionActionPlan actionPlan = actionHandler.buildActionPlan(intent);

    assertThat(actionPlan.calls()).containsExactlyElementsOf(draftCalls);
  }

  @Test
  @DisplayName("server-sig draft propagates signedAt + signatureExpiresAt into signatureMeta")
  void submit_serverSigDraft_returnsSignatureMetaWithExpectedExpiry() throws Exception {
    when(createExecutionIntentUseCase.execute(any(CreateExecutionIntentCommand.class)))
        .thenReturn(result(ExecutionResourceType.QUESTION));

    QnaExecutionIntentResult intentResult =
        adapter.submit(
            qnaDraft(
                QnaExecutionResourceType.QUESTION,
                QnaExecutionActionType.QNA_QUESTION_CREATE,
                1_768_224_000L));

    assertThat(intentResult.signatureMeta()).isNotNull();
    assertThat(intentResult.signatureMeta().signedAt()).isEqualTo(1_768_224_000L);
    assertThat(intentResult.signatureMeta().signatureExpiresAt()).isEqualTo(1_768_224_900L);
  }

  @Test
  @DisplayName("admin draft (no signedAt) yields null signatureMeta")
  void submit_adminDraft_yieldsNullSignatureMeta() throws Exception {
    when(createExecutionIntentUseCase.execute(any(CreateExecutionIntentCommand.class)))
        .thenReturn(result(ExecutionResourceType.QUESTION));

    QnaExecutionIntentResult intentResult =
        adapter.submit(
            qnaDraft(
                QnaExecutionResourceType.QUESTION, QnaExecutionActionType.QNA_ADMIN_SETTLE, null));

    assertThat(intentResult.signatureMeta()).isNull();
  }

  private static Stream<Arguments> escrowActions() {
    return Stream.of(
        Arguments.of(QnaExecutionResourceType.QUESTION, QnaExecutionActionType.QNA_QUESTION_CREATE),
        Arguments.of(QnaExecutionResourceType.ANSWER, QnaExecutionActionType.QNA_ANSWER_SUBMIT),
        Arguments.of(QnaExecutionResourceType.QUESTION, QnaExecutionActionType.QNA_ANSWER_ACCEPT));
  }

  private QnaExecutionDraft qnaDraft(
      QnaExecutionResourceType resourceType, QnaExecutionActionType actionType)
      throws JsonProcessingException {
    return qnaDraft(resourceType, actionType, null);
  }

  private QnaExecutionDraft qnaDraft(
      QnaExecutionResourceType resourceType, QnaExecutionActionType actionType, Long signedAt)
      throws JsonProcessingException {
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            actionType,
            101L,
            actionType == QnaExecutionActionType.QNA_QUESTION_CREATE ? null : 202L,
            AUTHORITY,
            TOKEN,
            BigInteger.valueOf(500),
            "0x" + "a".repeat(64),
            "0x" + "b".repeat(64),
            ESCROW,
            CALL_DATA);
    return new QnaExecutionDraft(
        resourceType,
        resourceType == QnaExecutionResourceType.QUESTION ? "101" : "202",
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        actionType,
        7L,
        8L,
        "root-" + actionType.name().toLowerCase(),
        "0x" + "c".repeat(64),
        objectMapper.writeValueAsString(payload),
        List.of(new QnaExecutionDraftCall(ESCROW, BigInteger.ZERO, CALL_DATA)),
        true,
        AUTHORITY,
        12L,
        DELEGATE,
        "0x" + "d".repeat(64),
        null,
        null,
        signedAt,
        EXPIRES_AT);
  }

  private QnaEscrowExecutionActionHandlerAdapter qnaEscrowHandler() {
    return new QnaEscrowExecutionActionHandlerAdapter(
        objectMapper,
        mock(QnaProjectionPersistencePort.class),
        mock(QnaAcceptStateSyncPort.class),
        mock(QnaAdminRefundStateSyncPort.class),
        mock(QnaQuestionPublicationSyncPort.class),
        mock(QnaAnswerPublicationSyncPort.class),
        mock(QnaAnswerUpdateSyncPort.class),
        mock(LoadQnaExecutionIntentStatePort.class),
        mock(QnaQuestionUpdateStatePersistencePort.class),
        mock(QnaLocalDeleteSyncPort.class));
  }

  private CreateExecutionIntentResult result(ExecutionResourceType resourceType) {
    return new CreateExecutionIntentResult(
        resourceType,
        "resource-1",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-1",
        ExecutionIntentStatus.SIGNED,
        EXPIRES_AT,
        1L,
        ExecutionMode.EIP7702,
        2,
        null,
        false);
  }

  private ExecutionIntent intentFromDraft(ExecutionDraft draft) {
    return intent(
        ExecutionResourceType.valueOf(draft.resourceType().name()),
        draft.resourceId(),
        ExecutionActionType.valueOf(draft.actionType().name()),
        draft.payloadSnapshotJson());
  }

  private ExecutionIntent intent(
      ExecutionResourceType resourceType,
      String resourceId,
      ExecutionActionType actionType,
      String payloadSnapshotJson) {
    return ExecutionIntent.create(
        "intent-1",
        "root-1",
        1,
        resourceType,
        resourceId,
        actionType,
        7L,
        8L,
        ExecutionMode.EIP7702,
        "0x" + "e".repeat(64),
        payloadSnapshotJson,
        AUTHORITY,
        12L,
        DELEGATE,
        EXPIRES_AT,
        "0x" + "f".repeat(64),
        "0x" + "9".repeat(64),
        null,
        null,
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 7),
        EXPIRES_AT.minusMinutes(5));
  }
}
