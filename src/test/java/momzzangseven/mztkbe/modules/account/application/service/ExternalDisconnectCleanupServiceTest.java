package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectCleanupPolicyPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadHardDeletePolicyPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalDisconnectCleanupService unit test")
class ExternalDisconnectCleanupServiceTest {

  @Mock private ExternalDisconnectTaskPort externalDisconnectTaskPort;
  @Mock private LoadExternalDisconnectCleanupPolicyPort cleanupPolicyPort;
  @Mock private LoadHardDeletePolicyPort hardDeletePolicyPort;

  private ExternalDisconnectCleanupService service;

  @BeforeEach
  void setUp() {
    service =
        new ExternalDisconnectCleanupService(
            externalDisconnectTaskPort, cleanupPolicyPort, hardDeletePolicyPort);
  }

  @Test
  @DisplayName("cleanup clamps retention days and sums deleted rows")
  void cleanup_clampsRetentionAndDeletesBothStatuses() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    when(hardDeletePolicyPort.getRetentionDays()).thenReturn(30);
    when(cleanupPolicyPort.getSuccessRetentionDays()).thenReturn(60);
    when(cleanupPolicyPort.getFailedRetentionDays()).thenReturn(45);

    when(externalDisconnectTaskPort.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, now.minusDays(30)))
        .thenReturn(2);
    when(externalDisconnectTaskPort.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.FAILED, now.minusDays(30)))
        .thenReturn(3);

    int deleted = service.cleanup(now);

    assertThat(deleted).isEqualTo(5);
    verify(externalDisconnectTaskPort)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, now.minusDays(30));
    verify(externalDisconnectTaskPort)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.FAILED, now.minusDays(30));
  }

  @Test
  @DisplayName("cleanup skips failed cleanup when failed retention is disabled")
  void cleanup_skipsFailedStatusWhenRetentionDisabled() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    when(hardDeletePolicyPort.getRetentionDays()).thenReturn(30);
    when(cleanupPolicyPort.getSuccessRetentionDays()).thenReturn(10);
    when(cleanupPolicyPort.getFailedRetentionDays()).thenReturn(0);

    when(externalDisconnectTaskPort.deleteByStatusAndUpdatedAtBefore(
            ExternalDisconnectStatus.SUCCESS, now.minusDays(10)))
        .thenReturn(4);

    int deleted = service.cleanup(now);

    assertThat(deleted).isEqualTo(4);
    verify(externalDisconnectTaskPort)
        .deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, now.minusDays(10));
    verify(externalDisconnectTaskPort, never())
        .deleteByStatusAndUpdatedAtBefore(
            org.mockito.ArgumentMatchers.eq(ExternalDisconnectStatus.FAILED),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
  }

  @Test
  @DisplayName("cleanup throws when configured retention is invalid")
  void cleanup_withNonPositiveRetention_throwsIllegalArgumentException() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    when(hardDeletePolicyPort.getRetentionDays()).thenReturn(30);
    when(cleanupPolicyPort.getSuccessRetentionDays()).thenReturn(0);

    assertThatThrownBy(() -> service.cleanup(now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("retentionDays must be > 0");
  }

  @Test
  @DisplayName("cleanup throws when hard-delete retention is invalid")
  void cleanup_withInvalidHardDeleteRetention_throwsIllegalArgumentException() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 9, 0);
    when(hardDeletePolicyPort.getRetentionDays()).thenReturn(0);
    when(cleanupPolicyPort.getSuccessRetentionDays()).thenReturn(7);

    assertThatThrownBy(() -> service.cleanup(now))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("hardDelete retentionDays must be > 0");

    verifyNoInteractions(externalDisconnectTaskPort);
  }
}
