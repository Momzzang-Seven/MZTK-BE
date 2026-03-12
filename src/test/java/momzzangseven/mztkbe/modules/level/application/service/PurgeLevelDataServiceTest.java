package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.application.config.LevelRetentionProperties;
import momzzangseven.mztkbe.modules.level.application.port.out.LevelRetentionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeLevelDataServiceTest {

  @Mock private LevelRetentionPort levelRetentionPort;

  private LevelRetentionProperties props;
  private PurgeLevelDataService service;

  @BeforeEach
  void setUp() {
    props = new LevelRetentionProperties();
    props.setRetentionDays(30);
    props.setBatchSize(100);
    service = new PurgeLevelDataService(levelRetentionPort, props);
  }

  @Test
  void execute_shouldThrowWhenNowIsNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("now is required");
  }

  @Test
  void execute_shouldThrowWhenRetentionDaysInvalid() {
    props.setRetentionDays(0);

    assertThatThrownBy(() -> service.execute(LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retention-days must be > 0");
  }

  @Test
  void execute_shouldThrowWhenBatchSizeInvalid() {
    props.setBatchSize(0);

    assertThatThrownBy(() -> service.execute(LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batch-size must be > 0");
  }

  @Test
  void execute_shouldLoopUntilNoRowsDeleted() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(levelRetentionPort.deleteXpLedgerBefore(any(), eq(100))).thenReturn(2).thenReturn(0);
    when(levelRetentionPort.deleteLevelUpHistoriesBefore(any(), eq(100)))
        .thenReturn(3)
        .thenReturn(0);

    int deleted = service.execute(now);

    assertThat(deleted).isEqualTo(5);
  }
}
