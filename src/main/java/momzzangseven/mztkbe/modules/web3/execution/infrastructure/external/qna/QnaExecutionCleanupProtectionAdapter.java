package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.qna;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaExecutionCleanupProtectionAdapter implements ExecutionIntentCleanupProtectionPort {

  private final FilterQnaExecutionCleanupCandidatesUseCase
      filterQnaExecutionCleanupCandidatesUseCase;

  @Override
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    return filterQnaExecutionCleanupCandidatesUseCase.filterDeletableFinalizedIntentIds(
        candidateIntentIds);
  }
}
