package momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class XpPolicyEntityTest {

  @Test
  void fromAndToDomain_shouldMapPolicyFields() {
    LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
    LocalDateTime to = LocalDateTime.of(2026, 12, 31, 0, 0);
    XpPolicy domain =
        XpPolicy.builder()
            .id(1L)
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .dailyCap(1)
            .effectiveFrom(from)
            .effectiveTo(to)
            .enabled(true)
            .build();

    XpPolicyEntity entity = XpPolicyEntity.from(domain);
    XpPolicy mapped = entity.toDomain();

    assertThat(mapped.getType()).isEqualTo(XpType.CHECK_IN);
    assertThat(mapped.getXpAmount()).isEqualTo(10);
    assertThat(mapped.getDailyCap()).isEqualTo(1);
    assertThat(mapped.getEffectiveFrom()).isEqualTo(from);
    assertThat(mapped.getEffectiveTo()).isEqualTo(to);
    assertThat(mapped.isEnabled()).isTrue();
  }

  @Test
  void onCreate_shouldSetCreatedAt() {
    XpPolicyEntity entity =
        XpPolicyEntity.builder()
            .type(XpType.CHECK_IN)
            .xpAmount(10)
            .dailyCap(1)
            .effectiveFrom(LocalDateTime.of(2026, 1, 1, 0, 0))
            .enabled(true)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }
}
