package momzzangseven.mztkbe.modules.verification.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.verification.domain.vo.VerificationKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationGrantXpAdapterTest {

  @Mock private GrantXpUseCase grantXpUseCase;

  private VerificationGrantXpAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new VerificationGrantXpAdapter(
            grantXpUseCase,
            Clock.fixed(Instant.parse("2026-03-13T00:30:00Z"), ZoneId.of("UTC")),
            ZoneId.of("Asia/Seoul"));
  }

  @Test
  @DisplayName("photo verification은 workout:photo-verification idempotency key를 사용한다")
  void usesPhotoVerificationIdempotencyKey() {
    when(grantXpUseCase.execute(any()))
        .thenReturn(GrantXpResult.granted(100, 100, 1, java.time.LocalDate.of(2026, 3, 13)));

    int granted =
        adapter.grantWorkoutXp(
            1L,
            VerificationKind.WORKOUT_PHOTO,
            "verification-photo-1",
            "workout-photo-verification:verification-photo-1");

    assertThat(granted).isEqualTo(100);

    ArgumentCaptor<GrantXpCommand> captor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase).execute(captor.capture());

    GrantXpCommand command = captor.getValue();
    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.xpType()).isEqualTo(XpType.WORKOUT);
    assertThat(command.occurredAt()).isEqualTo(LocalDateTime.of(2026, 3, 13, 9, 30));
    assertThat(command.idempotencyKey())
        .isEqualTo("workout:photo-verification:verification-photo-1");
    assertThat(command.sourceRef()).isEqualTo("workout-photo-verification:verification-photo-1");
  }

  @Test
  @DisplayName("record verification은 workout:record-verification idempotency key를 사용한다")
  void usesRecordVerificationIdempotencyKey() {
    when(grantXpUseCase.execute(any()))
        .thenReturn(GrantXpResult.granted(100, 100, 1, java.time.LocalDate.of(2026, 3, 13)));

    adapter.grantWorkoutXp(
        1L,
        VerificationKind.WORKOUT_RECORD,
        "verification-record-1",
        "workout-record-verification:verification-record-1");

    ArgumentCaptor<GrantXpCommand> captor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase).execute(captor.capture());

    assertThat(captor.getValue().idempotencyKey())
        .isEqualTo("workout:record-verification:verification-record-1");
  }
}
