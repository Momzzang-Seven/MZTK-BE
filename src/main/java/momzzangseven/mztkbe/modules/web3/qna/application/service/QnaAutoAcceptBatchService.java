package momzzangseven.mztkbe.modules.web3.qna.application.service;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.RunQnaAutoAcceptBatchResult;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.ScheduleNextQnaAutoAcceptResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.RunQnaAutoAcceptBatchUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.ScheduleNextQnaAutoAcceptUseCase;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAutoAcceptPolicyPort;

@Slf4j
public class QnaAutoAcceptBatchService implements RunQnaAutoAcceptBatchUseCase {

  private final ScheduleNextQnaAutoAcceptUseCase scheduleNextQnaAutoAcceptUseCase;
  private final LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort;

  public QnaAutoAcceptBatchService(
      ScheduleNextQnaAutoAcceptUseCase scheduleNextQnaAutoAcceptUseCase,
      LoadQnaAutoAcceptPolicyPort loadQnaAutoAcceptPolicyPort) {
    this.scheduleNextQnaAutoAcceptUseCase = scheduleNextQnaAutoAcceptUseCase;
    this.loadQnaAutoAcceptPolicyPort = loadQnaAutoAcceptPolicyPort;
  }

  @Override
  public RunQnaAutoAcceptBatchResult runBatch(Instant now) {
    int scheduledCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    int batchSize = loadQnaAutoAcceptPolicyPort.loadPolicy().batchSize();

    for (int i = 0; i < batchSize; i++) {
      try {
        ScheduleNextQnaAutoAcceptResult result = scheduleNextQnaAutoAcceptUseCase.scheduleNext(now);
        if (result.isExhausted()) {
          break;
        }
        if (result.isSkipped()) {
          skippedCount++;
          break;
        }
        scheduledCount++;
      } catch (RuntimeException e) {
        failedCount++;
        log.error("qna auto-accept batch aborted due to candidate scheduling failure", e);
        break;
      }
    }

    return new RunQnaAutoAcceptBatchResult(scheduledCount, skippedCount, failedCount);
  }
}
