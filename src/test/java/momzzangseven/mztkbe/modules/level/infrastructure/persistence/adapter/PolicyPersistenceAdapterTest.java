package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import momzzangseven.mztkbe.modules.level.domain.model.XpPolicy;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.LevelPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.XpPolicyEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.LevelPolicyJpaRepository;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.XpPolicyJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PolicyPersistenceAdapterTest {

  @Mock private LevelPolicyJpaRepository levelPolicyJpaRepository;
  @Mock private XpPolicyJpaRepository xpPolicyJpaRepository;

  @InjectMocks private PolicyPersistenceAdapter adapter;

  @Test
  void loadLevelPolicy_shouldReturnFirstMappedPolicy() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 28, 10, 0);
    when(levelPolicyJpaRepository.findActiveByLevel(eq(3), eq(at), any()))
        .thenReturn(
            List.of(
                LevelPolicyEntity.builder()
                    .id(1L)
                    .level(3)
                    .requiredXp(300)
                    .rewardMztk(12)
                    .effectiveFrom(at.minusDays(1))
                    .enabled(true)
                    .build()));

    LevelPolicy policy = adapter.loadLevelPolicy(3, at).orElseThrow();

    assertThat(policy.getLevel()).isEqualTo(3);
    assertThat(policy.getRequiredXp()).isEqualTo(300);
  }

  @Test
  void loadLevelPolicies_shouldPickLatestByLevelAndSortByLevel() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 28, 10, 0);
    when(levelPolicyJpaRepository.findActivePolicies(at))
        .thenReturn(
            List.of(
                LevelPolicyEntity.builder()
                    .id(1L)
                    .level(2)
                    .requiredXp(200)
                    .rewardMztk(5)
                    .effectiveFrom(at.minusDays(2))
                    .enabled(true)
                    .build(),
                LevelPolicyEntity.builder()
                    .id(2L)
                    .level(1)
                    .requiredXp(100)
                    .rewardMztk(3)
                    .effectiveFrom(at.minusDays(1))
                    .enabled(true)
                    .build(),
                LevelPolicyEntity.builder()
                    .id(3L)
                    .level(2)
                    .requiredXp(250)
                    .rewardMztk(7)
                    .effectiveFrom(at.minusHours(1))
                    .enabled(true)
                    .build()));

    List<LevelPolicy> policies = adapter.loadLevelPolicies(at);

    assertThat(policies).hasSize(2);
    assertThat(policies.get(0).getLevel()).isEqualTo(1);
    assertThat(policies.get(1).getLevel()).isEqualTo(2);
    assertThat(policies.get(1).getRequiredXp()).isEqualTo(250);
  }

  @Test
  void loadXpPolicies_shouldPickLatestByType() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 28, 10, 0);
    when(xpPolicyJpaRepository.findActivePolicies(at))
        .thenReturn(
            List.of(
                XpPolicyEntity.builder()
                    .id(1L)
                    .type(XpType.CHECK_IN)
                    .xpAmount(10)
                    .dailyCap(1)
                    .effectiveFrom(at.minusDays(2))
                    .enabled(true)
                    .build(),
                XpPolicyEntity.builder()
                    .id(2L)
                    .type(XpType.CHECK_IN)
                    .xpAmount(20)
                    .dailyCap(1)
                    .effectiveFrom(at.minusHours(1))
                    .enabled(true)
                    .build(),
                XpPolicyEntity.builder()
                    .id(3L)
                    .type(XpType.POST)
                    .xpAmount(30)
                    .dailyCap(2)
                    .effectiveFrom(at.minusHours(1))
                    .enabled(true)
                    .build()));

    List<XpPolicy> policies = adapter.loadXpPolicies(at);

    assertThat(policies).hasSize(2);
    assertThat(policies.get(0).getType()).isEqualTo(XpType.CHECK_IN);
    assertThat(policies.get(0).getXpAmount()).isEqualTo(20);
  }
}
