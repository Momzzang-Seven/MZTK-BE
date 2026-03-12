package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AttendanceLogEntityTest {

  @Test
  void builder_shouldSetFields() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 2, 28, 10, 0);

    AttendanceLogEntity entity =
        AttendanceLogEntity.builder()
            .id(1L)
            .userId(10L)
            .attendedDate(LocalDate.of(2026, 2, 28))
            .createdAt(createdAt)
            .build();

    assertThat(entity.getId()).isEqualTo(1L);
    assertThat(entity.getUserId()).isEqualTo(10L);
    assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void onCreate_shouldSetCreatedAtWhenNull() {
    AttendanceLogEntity entity =
        AttendanceLogEntity.builder().userId(10L).attendedDate(LocalDate.of(2026, 2, 28)).build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
