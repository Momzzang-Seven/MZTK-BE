package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.config.LevelRetentionProperties;
import momzzangseven.mztkbe.modules.level.application.port.in.PurgeLevelDataUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelRetentionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeLevelDataService implements PurgeLevelDataUseCase {

  private final LevelRetentionPort levelRetentionPort;
  private final LevelRetentionProperties props;

  @Override
  @Transactional
  public int execute(LocalDateTime now) {
    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }
    int retentionDays = props.getRetentionDays();
    if (retentionDays <= 0) {
      throw new IllegalArgumentException("level.retention.retention-days must be > 0");
    }
    int batchSize = props.getBatchSize();
    if (batchSize <= 0) {
      throw new IllegalArgumentException("level.retention.batch-size must be > 0");
    }

    LocalDateTime cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
    int deletedTotal = 0;
    while (true) {
      int deletedLedger = levelRetentionPort.deleteXpLedgerBefore(cutoff, batchSize);
      int deletedHistories = levelRetentionPort.deleteLevelUpHistoriesBefore(cutoff, batchSize);
      int deleted = deletedLedger + deletedHistories;
      if (deleted <= 0) {
        break;
      }
      deletedTotal += deleted;
    }

    if (deletedTotal > 0) {
      log.info("Purged level data: deletedRows={}, cutoff={}", deletedTotal, cutoff);
    }
    return deletedTotal;
  }
}

