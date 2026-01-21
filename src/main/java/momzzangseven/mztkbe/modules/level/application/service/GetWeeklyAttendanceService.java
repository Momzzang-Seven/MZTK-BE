package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetWeeklyAttendanceResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetWeeklyAttendanceUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetWeeklyAttendanceService implements GetWeeklyAttendanceUseCase {

  private final AttendanceLogPort attendanceLogPort;
  private final ZoneId appZoneId;

  @Override
  public GetWeeklyAttendanceResult execute(Long userId) {
    LocalDate today = LocalDate.now(appZoneId);
    // '최근 7일' 기준: 오늘 포함해서 6일 전까지 (총 7일)
    LocalDate startDate = today.minusDays(6);

    List<LocalDate> attendedDates =
        attendanceLogPort.findAttendedDatesBetween(userId, startDate, today);

    return GetWeeklyAttendanceResult.of(startDate, today, attendedDates);
  }
}
