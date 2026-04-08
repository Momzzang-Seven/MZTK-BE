package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.GetQuestionRewardIntentSnapshotQuery;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.MarkQuestionRewardIntentSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardExecutionPayload;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.QuestionRewardIntentSnapshotResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.GetQuestionRewardIntentSnapshotUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.in.MarkQuestionRewardIntentSubmittedUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardExecutionActionHandlerTest {

  @Mock private GetQuestionRewardIntentSnapshotUseCase getQuestionRewardIntentSnapshotUseCase;
  @Mock private MarkQuestionRewardIntentSubmittedUseCase markQuestionRewardIntentSubmittedUseCase;

  private ObjectMapper objectMapper;
  private QuestionRewardExecutionActionHandlerAdapter handler;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    handler =
        new QuestionRewardExecutionActionHandlerAdapter(
            objectMapper,
            getQuestionRewardIntentSnapshotUseCase,
            markQuestionRewardIntentSubmittedUseCase);
  }

  @Test
  void supports_qnaAnswerAccept() {
    assertThat(handler.supports(ExecutionActionType.QNA_ANSWER_ACCEPT)).isTrue();
    assertThat(handler.supports(ExecutionActionType.TRANSFER_SEND)).isFalse();
  }

  @Test
  void beforeExecute_logsLegacyIntentAndMarksSubmittedAfterBroadcast() throws Exception {
    ExecutionIntent intent = executionIntent();
    when(getQuestionRewardIntentSnapshotUseCase.execute(
            new GetQuestionRewardIntentSnapshotQuery(101L)))
        .thenReturn(new QuestionRewardIntentSnapshotResult(true, "PREPARE_REQUIRED"));

    ExecutionActionPlan plan = handler.buildActionPlan(intent);
    handler.beforeExecute(intent, plan);
    handler.afterTransactionSubmitted(intent, plan, "PENDING");

    assertThat(plan.referenceType()).isEqualTo("USER_TO_USER");
    assertThat(plan.calls()).hasSize(1);
    assertThat(plan.calls().getFirst().data()).isEqualTo("0x1234");
    verify(markQuestionRewardIntentSubmittedUseCase)
        .execute(new MarkQuestionRewardIntentSubmittedCommand(101L));
  }

  @Test
  void beforeExecute_doesNotThrowWhenLegacyIntentMissing() throws Exception {
    ExecutionIntent intent = executionIntent();
    when(getQuestionRewardIntentSnapshotUseCase.execute(
            new GetQuestionRewardIntentSnapshotQuery(101L)))
        .thenReturn(new QuestionRewardIntentSnapshotResult(false, null));

    ExecutionActionPlan plan = handler.buildActionPlan(intent);
    handler.beforeExecute(intent, plan);

    verify(getQuestionRewardIntentSnapshotUseCase)
        .execute(new GetQuestionRewardIntentSnapshotQuery(101L));
    verifyNoMoreInteractions(markQuestionRewardIntentSubmittedUseCase);
  }

  private ExecutionIntent executionIntent() throws Exception {
    QuestionRewardExecutionPayload payload =
        new QuestionRewardExecutionPayload(
            101L,
            201L,
            7L,
            22L,
            "0x" + "1".repeat(40),
            "0x" + "2".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.valueOf(500),
            "0x1234");

    return ExecutionIntent.create(
        "intent-1",
        "domain:QUESTION_REWARD:101:7",
        1,
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionActionType.QNA_ANSWER_ACCEPT,
        7L,
        22L,
        ExecutionMode.EIP1559,
        "0x" + "a".repeat(64),
        objectMapper.writeValueAsString(payload),
        null,
        null,
        null,
        LocalDateTime.now().plusMinutes(5),
        null,
        null,
        new UnsignedTxSnapshot(
            11155111L,
            "0x" + "1".repeat(40),
            "0x" + "3".repeat(40),
            BigInteger.ZERO,
            "0x1234",
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L)),
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        LocalDate.of(2026, 4, 6),
        LocalDateTime.now());
  }
}
