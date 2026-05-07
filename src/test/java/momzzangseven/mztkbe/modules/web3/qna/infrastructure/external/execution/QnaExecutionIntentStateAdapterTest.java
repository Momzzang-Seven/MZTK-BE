package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QnaExecutionIntentStateAdapter unit test")
class QnaExecutionIntentStateAdapterTest {

  @Mock private ExecutionIntentPersistencePort executionIntentPersistencePort;

  @InjectMocks private QnaExecutionIntentStateAdapter adapter;

  @Test
  @DisplayName("conflict guard checks any active intent with a different action")
  void hasConflictingActiveIntentUsesDifferentActionLookup() {
    when(executionIntentPersistencePort.existsActiveByResourceAndActionTypeNotForUpdate(
            ExecutionResourceType.QUESTION, "11", ExecutionActionType.QNA_QUESTION_UPDATE))
        .thenReturn(true);

    boolean result =
        adapter.hasConflictingActiveIntent(
            QnaExecutionResourceType.QUESTION, "11", QnaExecutionActionType.QNA_QUESTION_UPDATE);

    assertThat(result).isTrue();
    verify(executionIntentPersistencePort)
        .existsActiveByResourceAndActionTypeNotForUpdate(
            ExecutionResourceType.QUESTION, "11", ExecutionActionType.QNA_QUESTION_UPDATE);
  }
}
