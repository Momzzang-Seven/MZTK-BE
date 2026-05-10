package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.qna;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("QnaExecutionCleanupProtectionAdapter")
@ExtendWith(MockitoExtension.class)
class QnaExecutionCleanupProtectionAdapterTest {

  @Mock private FilterQnaExecutionCleanupCandidatesUseCase filterUseCase;

  @Test
  @DisplayName("delegates cleanup protection decision to the qna use case")
  void filterDeletableFinalizedIntentIdsDelegatesToQnaUseCase() {
    QnaExecutionCleanupProtectionAdapter adapter =
        new QnaExecutionCleanupProtectionAdapter(filterUseCase);
    List<Long> candidates = List.of(1L, 2L, 3L);
    given(filterUseCase.filterDeletableFinalizedIntentIds(candidates)).willReturn(List.of(2L));

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(candidates);

    assertThat(result).containsExactly(2L);
    verify(filterUseCase).filterDeletableFinalizedIntentIds(candidates);
  }
}
