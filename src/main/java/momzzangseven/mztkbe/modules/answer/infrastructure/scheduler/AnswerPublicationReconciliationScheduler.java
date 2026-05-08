package momzzangseven.mztkbe.modules.answer.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.answer.application.port.in.ReconcileAnswerPublicationUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerPublicationReconciliationScheduler {

  private final ReconcileAnswerPublicationUseCase reconcileAnswerPublicationUseCase;

  @Scheduled(fixedDelayString = "${answer.lifecycle.reconciliation.fixed-delay-ms:300000}")
  public void reconcileAnswerPublication() {
    try {
      var result = reconcileAnswerPublicationUseCase.reconcile(100);
      if (result.total() > 0) {
        log.info(
            "answer publication reconciliation completed: confirmedSubmits={}, terminalSubmitFailures={}, confirmedUpdates={}, terminalUpdateFailures={}, confirmedDeletes={}, terminalDeleteRollbacks={}",
            result.confirmedSubmits(),
            result.terminalSubmitFailures(),
            result.confirmedUpdates(),
            result.terminalUpdateFailures(),
            result.confirmedDeletes(),
            result.terminalDeleteRollbacks());
      }
    } catch (Exception e) {
      log.error("answer publication reconciliation failed", e);
    }
  }
}
