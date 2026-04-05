package momzzangseven.mztkbe.modules.web3.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.QuestionRewardIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntent;
import momzzangseven.mztkbe.modules.web3.transfer.domain.model.QuestionRewardIntentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuestionRewardExecutionActionHandlerTest {

  @Mock private Eip7702TransactionCodecPort eip7702TransactionCodecPort;
  @Mock private QuestionRewardIntentPersistencePort questionRewardIntentPersistencePort;

  private ObjectMapper objectMapper;
  private QuestionRewardExecutionActionHandler handler;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    handler =
        new QuestionRewardExecutionActionHandler(
            objectMapper, eip7702TransactionCodecPort, questionRewardIntentPersistencePort);
  }

  @Test
  void supports_qnaAnswerAccept() {
    assertThat(handler.supports(ExecutionActionType.QNA_ANSWER_ACCEPT)).isTrue();
    assertThat(handler.supports(ExecutionActionType.TRANSFER_SEND)).isFalse();
  }

  @Test
  void beforeExecute_logsLegacyIntentAndMarksSubmittedAfterBroadcast() throws Exception {
    ExecutionIntent intent = executionIntent();
    QuestionRewardIntent legacyIntent =
        QuestionRewardIntent.builder()
            .postId(101L)
            .acceptedCommentId(201L)
            .fromUserId(7L)
            .toUserId(22L)
            .amountWei(BigInteger.valueOf(500))
            .status(QuestionRewardIntentStatus.PREPARE_REQUIRED)
            .build();
    when(questionRewardIntentPersistencePort.findByPostId(101L))
        .thenReturn(Optional.of(legacyIntent));
    when(eip7702TransactionCodecPort.encodeTransferData(
            "0x" + "2".repeat(40), BigInteger.valueOf(500)))
        .thenReturn("0x1234");

    ExecutionActionPlan plan = handler.buildActionPlan(intent);
    handler.beforeExecute(intent, plan);
    handler.afterTransactionSubmitted(intent, plan, Web3TxStatus.PENDING);

    assertThat(plan.referenceType()).isEqualTo(Web3ReferenceType.USER_TO_USER);
    verify(questionRewardIntentPersistencePort)
        .updateStatusIfCurrentIn(
            101L,
            QuestionRewardIntentStatus.SUBMITTED,
            EnumSet.of(QuestionRewardIntentStatus.PREPARE_REQUIRED));
  }

  @Test
  void beforeExecute_doesNotThrowWhenLegacyIntentMissing() throws Exception {
    ExecutionIntent intent = executionIntent();
    when(questionRewardIntentPersistencePort.findByPostId(101L)).thenReturn(Optional.empty());
    when(eip7702TransactionCodecPort.encodeTransferData(
            "0x" + "2".repeat(40), BigInteger.valueOf(500)))
        .thenReturn("0x1234");

    ExecutionActionPlan plan = handler.buildActionPlan(intent);
    handler.beforeExecute(intent, plan);

    verify(questionRewardIntentPersistencePort).findByPostId(101L);
    verifyNoMoreInteractions(questionRewardIntentPersistencePort);
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
            BigInteger.valueOf(500));

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
