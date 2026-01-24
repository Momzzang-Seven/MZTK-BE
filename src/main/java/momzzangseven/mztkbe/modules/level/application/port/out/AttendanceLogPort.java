package momzzangseven.mztkbe.modules.level.application.port.out;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceLogPort {

  boolean existsByUserIdAndAttendedDate(Long userId, LocalDate attendedDate);

  void save(Long userId, LocalDate attendedDate);

  List<LocalDate> findTop30AttendedDatesDesc(Long userId);

  List<LocalDate> findAttendedDatesBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
