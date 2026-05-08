package momzzangseven.mztkbe.modules.answer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPreparationCleanupPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.PublishAnswerDeletedEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CleanupAnswerPreparationService")
class CleanupAnswerPreparationServiceTest {

  @Mock private AnswerPreparationCleanupPort cleanupPort;
  @Mock private PublishAnswerDeletedEventPort publishAnswerDeletedEventPort;

  @InjectMocks private CleanupAnswerPreparationService service;

  @Test
  @DisplayName("cleanup skips the batch when another worker owns the advisory lock")
  void cleanupSkipsWhenLockIsNotAcquired() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);
    given(cleanupPort.tryAcquirePreparationCleanupLock()).willReturn(false);

    var result = service.cleanupExpiredPreparations(now, 100);

    assertThat(result.total()).isZero();
    verify(cleanupPort).tryAcquirePreparationCleanupLock();
    verifyNoMoreInteractions(cleanupPort, publishAnswerDeletedEventPort);
  }

  @Test
  @DisplayName("cleanup publishes delete events only for create reservations actually deleted")
  void cleanupPublishesOnlyActuallyDeletedCreateReservations() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 8, 12, 0);
    given(cleanupPort.tryAcquirePreparationCleanupLock()).willReturn(true);
    given(cleanupPort.findExpiredCreatePreparationAnswerIds(now, 100))
        .willReturn(List.of(10L, 11L));
    given(cleanupPort.deleteCreatePreparationAnswers(List.of(10L, 11L))).willReturn(List.of(11L));
    given(cleanupPort.expireDeletePreparations(now, 100)).willReturn(2);
    given(cleanupPort.expireUpdatePreparations(now, 100)).willReturn(3);

    var result = service.cleanupExpiredPreparations(now, 100);

    assertThat(result.createReservationsDeleted()).isEqualTo(1);
    assertThat(result.deletePreparationsExpired()).isEqualTo(2);
    assertThat(result.updatePreparationsExpired()).isEqualTo(3);
    verify(publishAnswerDeletedEventPort).publish(argThat(event -> event.answerId().equals(11L)));
    verify(publishAnswerDeletedEventPort, never())
        .publish(argThat(event -> event.answerId().equals(10L)));
  }
}
