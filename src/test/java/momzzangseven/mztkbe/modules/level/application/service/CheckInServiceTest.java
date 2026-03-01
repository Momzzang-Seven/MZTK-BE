package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

  @Mock private AttendanceLogPort attendanceLogPort;
  @Mock private GrantXpUseCase grantXpUseCase;

  private CheckInService service;

  @BeforeEach
  void setUp() {
    service = new CheckInService(attendanceLogPort, grantXpUseCase, ZoneId.of("Asia/Seoul"));
  }

  @Test
  void execute_shouldReturnAlreadyCheckedInWhenTodayAttendanceExists() {
    when(attendanceLogPort.existsByUserIdAndAttendedDate(any(), any())).thenReturn(true);

    CheckInResult result = service.execute(1L);

    assertThat(result.success()).isFalse();
    assertThat(result.message()).isEqualTo("ALREADY_CHECKED_IN");
    verify(attendanceLogPort, never()).save(any(), any());
    verify(grantXpUseCase, never()).execute(any());
  }

  @Test
  void execute_shouldGrantCheckInAndBonusXpWhenStreakIsMultipleOfSeven() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    when(attendanceLogPort.existsByUserIdAndAttendedDate(any(), any())).thenReturn(false);
    when(attendanceLogPort.findTop30AttendedDatesDesc(1L))
        .thenReturn(
            List.of(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(3),
                today.minusDays(4),
                today.minusDays(5),
                today.minusDays(6)));
    when(grantXpUseCase.execute(any()))
        .thenReturn(GrantXpResult.granted(10, 10, 1, today))
        .thenReturn(GrantXpResult.granted(20, 10, 1, today));

    CheckInResult result = service.execute(1L);

    assertThat(result.success()).isTrue();
    assertThat(result.streakDays()).isEqualTo(7);
    assertThat(result.grantedXp()).isEqualTo(10);
    assertThat(result.bonusXp()).isEqualTo(20);

    ArgumentCaptor<GrantXpCommand> captor = ArgumentCaptor.forClass(GrantXpCommand.class);
    verify(grantXpUseCase, times(2)).execute(captor.capture());
    assertThat(captor.getAllValues().getFirst().idempotencyKey()).startsWith("checkin:");
    assertThat(captor.getAllValues().get(1).idempotencyKey()).startsWith("streak7:");
  }

  @Test
  void execute_shouldNotGrantBonusWhenStreakIsNotMultipleOfSeven() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    when(attendanceLogPort.existsByUserIdAndAttendedDate(any(), any())).thenReturn(false);
    when(attendanceLogPort.findTop30AttendedDatesDesc(1L))
        .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2)));
    when(grantXpUseCase.execute(any())).thenReturn(GrantXpResult.granted(10, 10, 1, today));

    CheckInResult result = service.execute(1L);

    assertThat(result.success()).isTrue();
    assertThat(result.streakDays()).isEqualTo(3);
    assertThat(result.bonusXp()).isZero();
    verify(grantXpUseCase, times(1)).execute(any());
  }
}
