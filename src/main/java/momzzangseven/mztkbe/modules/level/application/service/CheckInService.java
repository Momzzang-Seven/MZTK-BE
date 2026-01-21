package momzzangseven.mztkbe.modules.level.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.dto.CheckInResult;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpCommand;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.CheckInUseCase;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import momzzangseven.mztkbe.modules.level.domain.model.XpType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CheckInService implements CheckInUseCase {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final AttendanceLogPort attendanceLogPort;
    private final GrantXpUseCase grantXpUseCase;
    private final ZoneId appZoneId;

    @Override
    public CheckInResult execute(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate todayKst = now.atZone(appZoneId).toLocalDate();

        // 1) 오늘 출석 중복 방지 (SSOT)
        if (attendanceLogPort.existsByUserIdAndAttendedDate(userId, todayKst)) {
            return CheckInResult.alreadyCheckedIn(todayKst);
        }

        // 2) 출석 사실 저장 (여기서 출석 성공 확정)
        attendanceLogPort.save(userId, todayKst);

        // 3) 출석 XP 지급
        String checkinKey = "checkin:" + userId + ":" + todayKst.format(YYYYMMDD);
        GrantXpResult checkinXp =
                grantXpUseCase.execute(
                        GrantXpCommand.of(userId, XpType.CHECK_IN, now, checkinKey, "attendance:" + todayKst));

        int grantedXp = checkinXp.grantedXp();

        // 4) streak 계산 (최근 7개 기준, 오늘 포함 연속일)
        int streakDays = calculateStreakDays(todayKst, attendanceLogPort.findTop7AttendedDatesDesc(userId));

        int bonusXp = 0;
        if (streakDays >= 7) {
            String streakKey = "streak7:" + userId + ":" + todayKst.format(YYYYMMDD);
            GrantXpResult bonus =
                    grantXpUseCase.execute(
                            GrantXpCommand.of(userId, XpType.STREAK_7D, now, streakKey, "streak7:" + todayKst));
            bonusXp = bonus.grantedXp();
        }

        return CheckInResult.success(todayKst, grantedXp, bonusXp, streakDays);
    }

    private int calculateStreakDays(LocalDate today, List<LocalDate> recentDesc) {
        Set<LocalDate> set = new HashSet<>(recentDesc);
        int streak = 0;
        LocalDate cursor = today;
        while (set.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
            if (streak >= 7) break;
        }
        return streak;
    }
}
