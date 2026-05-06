package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaQuestionUpdateConfirmationSyncAdapter unit test")
class QnaQuestionUpdateConfirmationSyncAdapterTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;
  @Mock private QnaEscrowExecutionActionHandlerAdapter actionHandlerAdapter;

  @InjectMocks private QnaQuestionUpdateConfirmationSyncAdapter adapter;

  @Test
  @DisplayName("confirmed question update intent is replayed through the qna action handler")
  void syncConfirmedQuestionUpdateReplaysConfirmedQuestionUpdate() {
    ExecutionIntent intent = confirmedIntent(ExecutionActionType.QNA_QUESTION_UPDATE);
    ExecutionActionPlan plan =
        new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.USER_TO_SERVER, List.of());
    when(executionIntentPersistencePort.findByPublicId("intent-1")).thenReturn(Optional.of(intent));
    when(actionHandlerAdapter.buildActionPlan(intent)).thenReturn(plan);

    boolean result = adapter.syncConfirmedQuestionUpdate("intent-1");

    assertThat(result).isTrue();
    verify(actionHandlerAdapter).afterExecutionConfirmed(intent, plan);
  }

  @Test
  @DisplayName("non question update intent is skipped")
  void syncConfirmedQuestionUpdateSkipsOtherAction() {
    ExecutionIntent intent = confirmedIntent(ExecutionActionType.QNA_QUESTION_DELETE);
    when(executionIntentPersistencePort.findByPublicId("intent-1")).thenReturn(Optional.of(intent));

    boolean result = adapter.syncConfirmedQuestionUpdate("intent-1");

    assertThat(result).isFalse();
    verify(actionHandlerAdapter, never()).afterExecutionConfirmed(any(), any());
  }

  private ExecutionIntent confirmedIntent(ExecutionActionType actionType) {
    return ExecutionIntent.create(
            "intent-1",
            "root-1",
            1,
            ExecutionResourceType.QUESTION,
            "101",
            actionType,
            7L,
            null,
            ExecutionMode.EIP7702,
            "0x" + "e".repeat(64),
            "{}",
            "0x" + "1".repeat(40),
            1L,
            "0x" + "2".repeat(40),
            LocalDateTime.of(2026, 4, 12, 10, 5),
            "0x" + "3".repeat(64),
            "0x" + "4".repeat(64),
            null,
            null,
            BigInteger.ZERO,
            LocalDate.of(2026, 4, 12),
            LocalDateTime.of(2026, 4, 12, 10, 0))
        .toBuilder()
        .submittedTxId(99L)
        .build()
        .markPendingOnchain(99L, LocalDateTime.of(2026, 4, 12, 10, 1))
        .confirm(LocalDateTime.of(2026, 4, 12, 10, 2));
  }
}
