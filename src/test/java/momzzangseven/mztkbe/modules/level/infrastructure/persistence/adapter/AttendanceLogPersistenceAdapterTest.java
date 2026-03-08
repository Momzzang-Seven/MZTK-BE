package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.AttendanceLogEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.AttendanceLogJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceLogPersistenceAdapterTest {

  @Mock private AttendanceLogJpaRepository attendanceLogJpaRepository;

  @InjectMocks private AttendanceLogPersistenceAdapter adapter;

  @Test
  void save_shouldPersistUserIdAndDate() {
    LocalDate attendedDate = LocalDate.of(2026, 2, 28);

    adapter.save(1L, attendedDate);

    ArgumentCaptor<AttendanceLogEntity> captor = ArgumentCaptor.forClass(AttendanceLogEntity.class);
    verify(attendanceLogJpaRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    assertThat(captor.getValue().getAttendedDate()).isEqualTo(attendedDate);
  }

  @Test
  void queryMethods_shouldDelegateAndMapDates() {
    LocalDate d1 = LocalDate.of(2026, 2, 27);
    LocalDate d2 = LocalDate.of(2026, 2, 28);
    when(attendanceLogJpaRepository.existsByUserIdAndAttendedDate(1L, d2)).thenReturn(true);
    when(attendanceLogJpaRepository.findTop30ByUserIdOrderByAttendedDateDesc(1L))
        .thenReturn(
            List.of(
                AttendanceLogEntity.builder().userId(1L).attendedDate(d2).build(),
                AttendanceLogEntity.builder().userId(1L).attendedDate(d1).build()));
    when(attendanceLogJpaRepository.findByUserIdAndAttendedDateBetweenOrderByAttendedDateAsc(
            eq(1L), eq(d1), eq(d2)))
        .thenReturn(List.of(AttendanceLogEntity.builder().userId(1L).attendedDate(d1).build()));

    boolean exists = adapter.existsByUserIdAndAttendedDate(1L, d2);
    List<LocalDate> top = adapter.findTop30AttendedDatesDesc(1L);
    List<LocalDate> between = adapter.findAttendedDatesBetween(1L, d1, d2);

    assertThat(exists).isTrue();
    assertThat(top).containsExactly(d2, d1);
    assertThat(between).containsExactly(d1);
  }
}
