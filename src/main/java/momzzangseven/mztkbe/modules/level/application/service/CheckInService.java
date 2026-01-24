package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckInUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import momzzangseven.mztkbe.modules.level.domain.model.AttendancePolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CheckInService implements CheckInUseCase {

  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

  private final AttendanceLogPort attendanceLogPort;
  private final GrantXpUseCase grantXpUseCase;
  private final AttendancePolicy attendancePolicy;
  private final ZoneId appZoneId;

  @Override
  public CheckInResult execute(Long userId) {
    LocalDateTime now = LocalDateTime.now();
    LocalDate todayKst = now.atZone(appZoneId).toLocalDate();

    if (attendanceLogPort.existsByUserIdAndAttendedDate(userId, todayKst)) {
      return CheckInResult.alreadyCheckedIn(todayKst);
    }

    attendanceLogPort.save(userId, todayKst);

    String checkinKey = "checkin:" + userId + ":" + todayKst.format(YYYYMMDD);
    GrantXpResult checkinXp =
        grantXpUseCase.execute(
            GrantXpCommand.of(userId, XpType.CHECK_IN, now, checkinKey, "attendance:" + todayKst));

    int grantedXp = checkinXp.grantedXp();

    List<LocalDate> recentDates = attendanceLogPort.findTop30AttendedDatesDesc(userId);
    int streakDays = attendancePolicy.calculateStreak(todayKst, recentDates);

    int bonusXp = 0;
    if (streakDays > 0 && streakDays % 7 == 0) {
      int cycle = streakDays / 7;
      String streakKey = "streak7:" + userId + ":cycle" + cycle;

      GrantXpResult bonus =
              grantXpUseCase.execute(
                      GrantXpCommand.of(userId, XpType.STREAK_7D, now, streakKey, "streak7:cycle" + cycle));
      bonusXp = bonus.grantedXp();
    }

    return CheckInResult.success(todayKst, grantedXp, bonusXp, streakDays);
  }
}
