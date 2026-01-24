package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.GetAttendanceStatusResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetAttendanceStatusUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import momzzangseven.mztkbe.modules.level.domain.model.AttendancePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAttendanceStatusService implements GetAttendanceStatusUseCase {

  private final AttendanceLogPort attendanceLogPort;
  private final ZoneId appZoneId;

  @Override
  public GetAttendanceStatusResult execute(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("userId is required");
    }

    LocalDate today = ZonedDateTime.now(appZoneId).toLocalDate();
    boolean hasAttendedToday = attendanceLogPort.existsByUserIdAndAttendedDate(userId, today);

    LocalDate cursor = hasAttendedToday ? today : today.minusDays(1);
    List<LocalDate> recentDates = attendanceLogPort.findTop30AttendedDatesDesc(userId);

    int streak = AttendancePolicy.calculateStreak(cursor, recentDates);

    return GetAttendanceStatusResult.of(today, hasAttendedToday, streak);
  }
}
