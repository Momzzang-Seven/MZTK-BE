package momzzangseven.mztkbe.modules.account.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectPolicyPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ExternalDisconnectRetryService {

  private final ExternalDisconnectTaskPort externalDisconnectTaskPort;
  private final LoadExternalDisconnectPolicyPort policyPort;
  private final ExternalDisconnectExecutor executor;

  /**
   * Process a batch of due tasks.
   *
   * @return number of tasks picked up for processing
   */
  public int runBatch() {
    LocalDateTime now = LocalDateTime.now();
    List<ExternalDisconnectTask> tasks =
        externalDisconnectTaskPort.findDueTasks(now, policyPort.getBatchSize());
    for (ExternalDisconnectTask task : tasks) {
      processOne(task);
    }
    return tasks.size();
  }

  private void processOne(ExternalDisconnectTask task) {
    if (task.getStatus() != ExternalDisconnectStatus.PENDING) {
      return;
    }

    int attemptNumber = task.getAttemptCount() + 1;
    try {
      executor.disconnect(task.getProvider(), task.getProviderUserId(), task.getEncryptedToken());

      externalDisconnectTaskPort.save(task.markSuccess(attemptNumber));
    } catch (Exception e) {
      String error = e.getClass().getSimpleName() + ": " + e.getMessage();
      log.warn(
          "External disconnect retry failed: taskId={}, userId={}, provider={}, attempt={}, "
              + "error={}",
          task.getId(),
          task.getUserId(),
          task.getProvider(),
          attemptNumber,
          error,
          e);
      if (attemptNumber >= policyPort.getMaxAttempts()) {
        externalDisconnectTaskPort.save(task.markFailedTerminal(attemptNumber, error));
        return;
      }

      long delayMillis = computeBackoffMillis(attemptNumber);
      LocalDateTime nextAttemptAt = LocalDateTime.now().plus(delayMillis, ChronoUnit.MILLIS);
      externalDisconnectTaskPort.save(task.scheduleRetry(attemptNumber, nextAttemptAt, error));
    }
  }

  private long computeBackoffMillis(int attemptNumber) {
    // attemptNumber starts at 1 for the immediate attempt on withdrawal.
    // After the Nth failed attempt, schedule retry with initialBackoff * 2^(N-1), clamped.
    int exponent = Math.max(0, attemptNumber - 1);
    long multiplier = 1L << Math.min(exponent, 30);

    long base = policyPort.getInitialBackoff();
    long candidate;
    try {
      candidate = Math.multiplyExact(base, multiplier);
    } catch (ArithmeticException e) {
      candidate = Long.MAX_VALUE;
    }

    return Math.min(candidate, policyPort.getMaxBackoff());
  }
}
