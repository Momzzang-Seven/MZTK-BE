package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetWeeklyAttendanceServiceTest {

  @Mock private AttendanceLogPort attendanceLogPort;

  private GetWeeklyAttendanceService service;

  @BeforeEach
  void setUp() {
    service = new GetWeeklyAttendanceService(attendanceLogPort, ZoneId.of("UTC"));
  }

  @Test
  void execute_shouldLoadSevenDayWindow() {
    Long userId = 1L;
    LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    LocalDate start = today.minusDays(6);
    List<LocalDate> attended = List.of(start, today);

    when(attendanceLogPort.findAttendedDatesBetween(userId, start, today)).thenReturn(attended);

    GetWeeklyAttendanceResult result = service.execute(userId);

    assertThat(result.range().from()).isEqualTo(start);
    assertThat(result.range().to()).isEqualTo(today);
    assertThat(result.attendedCount()).isEqualTo(2);
    verify(attendanceLogPort).findAttendedDatesBetween(userId, start, today);
  }
}
