package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalExternalDisconnectCleanupProperties;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalHardDeleteProperties;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalDisconnectCleanupService unit test")
class ExternalDisconnectCleanupServiceTest {

  @Mock private ExternalDisconnectTaskJpaRepository repository;

  private WithdrawalExternalDisconnectCleanupProperties cleanupProps;
  private WithdrawalHardDeleteProperties hardDeleteProps;
  private ExternalDisconnectCleanupService service;

  @BeforeEach
  void setUp() {
    cleanupProps = new WithdrawalExternalDisconnectCleanupProperties();
    hardDeleteProps = new WithdrawalHardDeleteProperties();
    service = new ExternalDisconnectCleanupService(repository, cleanupProps, hardDeleteProps);
  }

  @Test
  @DisplayName("cleanup clamps retention days and sums deleted rows")
  void cleanup_clampsRetentionAndDeletesBothStatuses() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    hardDeleteProps.setRetentionDays(30);
    cleanupProps.setSuccessRetentionDays(60);
    cleanupProps.setFailedRetentionDays(45);

    when(repository.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, now.minusDays(30)))
        .thenReturn(2);
    when(repository.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.FAILED, now.minusDays(30)))
        .thenReturn(3);

    int deleted = service.cleanup(now);

    assertThat(deleted).isEqualTo(5);
    verify(repository)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, now.minusDays(30));
    verify(repository)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.FAILED, now.minusDays(30));
  }

  @Test
  @DisplayName("cleanup skips failed cleanup when failed retention is disabled")
  void cleanup_skipsFailedStatusWhenRetentionDisabled() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    hardDeleteProps.setRetentionDays(30);
    cleanupProps.setSuccessRetentionDays(10);
    cleanupProps.setFailedRetentionDays(0);

    when(repository.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, now.minusDays(10)))
        .thenReturn(4);

    int deleted = service.cleanup(now);

    assertThat(deleted).isEqualTo(4);
    verify(repository)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, now.minusDays(10));
    verify(repository, never())
        .deleteByStatusAndUpdatedAtBefore(
            org.mockito.ArgumentMatchers.eq(ExternalDisconnectStatus.FAILED),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
  }

  @Test
  @DisplayName("cleanup throws when configured retention is invalid")
  void cleanup_withNonPositiveRetention_throwsIllegalArgumentException() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    hardDeleteProps.setRetentionDays(30);
    cleanupProps.setSuccessRetentionDays(0);
    cleanupProps.setFailedRetentionDays(7);

    assertThatThrownBy(() -> service.cleanup(now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retentionDays must be > 0");
  }

  @Test
  @DisplayName("cleanup throws when hard-delete retention is invalid")
  void cleanup_withInvalidHardDeleteRetention_throwsIllegalArgumentException() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    hardDeleteProps.setRetentionDays(0);
    cleanupProps.setSuccessRetentionDays(7);
    cleanupProps.setFailedRetentionDays(0);

    assertThatThrownBy(() -> service.cleanup(now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hardDelete retentionDays must be > 0");

    verifyNoInteractions(repository);
  }
}
