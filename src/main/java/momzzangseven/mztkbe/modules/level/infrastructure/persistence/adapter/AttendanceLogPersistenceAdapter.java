package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.level.application.port.out.AttendanceLogPort;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.AttendanceLogEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.repository.AttendanceLogJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceLogPersistenceAdapter implements AttendanceLogPort {

  private final AttendanceLogJpaRepository attendanceLogJpaRepository;

  @Override
  public boolean existsByUserIdAndAttendedDate(Long userId, LocalDate attendedDate) {
    return attendanceLogJpaRepository.existsByUserIdAndAttendedDate(userId, attendedDate);
  }

  @Override
  public void save(Long userId, LocalDate attendedDate) {
    attendanceLogJpaRepository.save(
        AttendanceLogEntity.builder()
            .userId(userId)
            .attendedDate(attendedDate)
            .createdAt(LocalDateTime.now())
            .build());
  }

  @Override
  public List<LocalDate> findTop30AttendedDatesDesc(Long userId) {
    return attendanceLogJpaRepository.findTop30ByUserIdOrderByAttendedDateDesc(userId).stream()
        .map(AttendanceLogEntity::getAttendedDate)
        .toList();
  }

  @Override
  public List<LocalDate> findAttendedDatesBetween(
      Long userId, LocalDate startDate, LocalDate endDate) {
    return attendanceLogJpaRepository
        .findByUserIdAndAttendedDateBetweenOrderByAttendedDateAsc(userId, startDate, endDate)
        .stream()
        .map(AttendanceLogEntity::getAttendedDate)
        .toList();
  }
}
