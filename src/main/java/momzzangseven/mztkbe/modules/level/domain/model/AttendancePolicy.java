package momzzangseven.mztkbe.modules.level.domain.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AttendancePolicy {
  public int calculateStreak(LocalDate cursor, List<LocalDate> recentDates) {
    if (recentDates == null || recentDates.isEmpty()) {
      return 0;
    }

    Set<LocalDate> attendedSet = new HashSet<>(recentDates);
    int streak = 0;
    while (attendedSet.contains(cursor)) {
      streak++;
      cursor = cursor.minusDays(1);
    }
    return streak;
  }
}
