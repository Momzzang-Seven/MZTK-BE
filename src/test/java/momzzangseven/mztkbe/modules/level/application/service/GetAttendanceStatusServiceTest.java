package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAttendanceStatusServiceTest {

  @Mock private AttendanceLogPort attendanceLogPort;

  private GetAttendanceStatusService service;

  @BeforeEach
  void setUp() {
    service = new GetAttendanceStatusService(attendanceLogPort, ZoneId.of("UTC"));
  }

  @Test
  void execute_shouldThrowWhenUserIdNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  @Test
  void execute_shouldReturnCurrentStatusAndStreak() {
    Long userId = 7L;
    LocalDate today = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDate();

    when(attendanceLogPort.existsByUserIdAndAttendedDate(userId, today)).thenReturn(true);
    when(attendanceLogPort.findTop30AttendedDatesDesc(userId))
        .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)));

    GetAttendanceStatusResult result = service.execute(userId);

    assertThat(result.today()).isEqualTo(today);
    assertThat(result.hasAttendedToday()).isTrue();
    assertThat(result.streakCount()).isEqualTo(3);
    verify(attendanceLogPort).findTop30AttendedDatesDesc(userId);
  }

  @Test
  void execute_shouldUseYesterdayCursorWhenNotAttendedToday() {
    Long userId = 9L;
    LocalDate today = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDate();
    LocalDate yesterday = today.minusDays(1);

    when(attendanceLogPort.existsByUserIdAndAttendedDate(userId, today)).thenReturn(false);
    when(attendanceLogPort.findTop30AttendedDatesDesc(userId)).thenReturn(List.of(yesterday));

    GetAttendanceStatusResult result = service.execute(userId);

    assertThat(result.hasAttendedToday()).isFalse();
    assertThat(result.streakCount()).isEqualTo(1);
  }

  @Test
  void execute_shouldReturnZeroStreakWhenNoAttendanceHistory() {
    Long userId = 11L;
    LocalDate today = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDate();

    when(attendanceLogPort.existsByUserIdAndAttendedDate(userId, today)).thenReturn(false);
    when(attendanceLogPort.findTop30AttendedDatesDesc(userId)).thenReturn(List.of());

    GetAttendanceStatusResult result = service.execute(userId);

    assertThat(result.streakCount()).isZero();
  }
}
