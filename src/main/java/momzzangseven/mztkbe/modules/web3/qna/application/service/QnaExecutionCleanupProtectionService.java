package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.FilterQnaExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionCleanupProtectionQueryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QnaExecutionCleanupProtectionService
    implements FilterQnaExecutionCleanupCandidatesUseCase {

  private final QnaExecutionCleanupProtectionQueryPort qnaExecutionCleanupProtectionQueryPort;

  @Override
  @Transactional(readOnly = true)
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    return qnaExecutionCleanupProtectionQueryPort.filterDeletableFinalizedIntentIds(
        candidateIntentIds);
  }
}
