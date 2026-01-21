package momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.AttendanceLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceLogJpaRepository extends JpaRepository<AttendanceLogEntity, Long> {

    boolean existsByUserIdAndAttendedDate(Long userId, LocalDate attendedDate);

    List<AttendanceLogEntity> findByUserIdAndAttendedDateBetween(
            Long userId, LocalDate startDate, LocalDate endDate);

    List<AttendanceLogEntity> findTop7ByUserIdOrderByAttendedDateDesc(Long userId);
}
