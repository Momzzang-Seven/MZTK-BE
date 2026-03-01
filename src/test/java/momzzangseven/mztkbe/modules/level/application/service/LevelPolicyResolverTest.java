package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.level.MaxLevelReachedException;
import momzzangseven.mztkbe.modules.level.application.port.out.PolicyPort;
import momzzangseven.mztkbe.modules.level.domain.model.LevelPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LevelPolicyResolverTest {

  @Mock private PolicyPort policyPort;

  private LevelPolicyResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new LevelPolicyResolver(policyPort);
  }

  @Test
  void resolveNextLevelInfo_shouldReturnPolicyInfoWhenPolicyExists() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(policyPort.loadLevelPolicy(3, at)).thenReturn(Optional.of(policy(3, 120, 7)));

    LevelPolicyResolver.NextLevelPolicyInfo info = resolver.resolveNextLevelInfo(3, at);

    assertThat(info.requiredXpForNext()).isEqualTo(120);
    assertThat(info.rewardMztkForNext()).isEqualTo(7);
  }

  @Test
  void resolveNextLevelInfo_shouldReturnZerosWhenCurrentLevelIsMax() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(policyPort.loadLevelPolicy(11, at)).thenReturn(Optional.empty());
    when(policyPort.loadLevelPolicies(at)).thenReturn(List.of(policy(10, 300, 20)));

    LevelPolicyResolver.NextLevelPolicyInfo info = resolver.resolveNextLevelInfo(11, at);

    assertThat(info.requiredXpForNext()).isZero();
    assertThat(info.rewardMztkForNext()).isZero();
  }

  @Test
  void resolveLevelUpPolicy_shouldThrowMaxLevelReachedExceptionWhenAtMaxLevel() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(policyPort.loadLevelPolicy(11, at)).thenReturn(Optional.empty());
    when(policyPort.loadLevelPolicies(at)).thenReturn(List.of(policy(10, 300, 20)));

    assertThatThrownBy(() -> resolver.resolveLevelUpPolicy(11, at))
        .isInstanceOf(MaxLevelReachedException.class)
        .hasMessageContaining("Max level reached");
  }

  @Test
  void resolveLevelUpPolicy_shouldThrowIllegalStateWhenPolicyMissingAndNotMax() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(policyPort.loadLevelPolicy(3, at)).thenReturn(Optional.empty());
    when(policyPort.loadLevelPolicies(at)).thenReturn(List.of(policy(10, 300, 20)));

    assertThatThrownBy(() -> resolver.resolveLevelUpPolicy(3, at))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("policy not found");
  }

  @Test
  void resolveLevelUpPolicy_shouldThrowWhenPolicyListIsEmpty() {
    LocalDateTime at = LocalDateTime.of(2026, 2, 26, 10, 0);
    when(policyPort.loadLevelPolicy(3, at)).thenReturn(Optional.empty());
    when(policyPort.loadLevelPolicies(at)).thenReturn(List.of());

    assertThatThrownBy(() -> resolver.resolveLevelUpPolicy(3, at))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No level policies configured");
  }

  private LevelPolicy policy(int level, int requiredXp, int rewardMztk) {
    return LevelPolicy.builder()
        .id((long) level)
        .level(level)
        .requiredXp(requiredXp)
        .rewardMztk(rewardMztk)
        .build();
  }
}
