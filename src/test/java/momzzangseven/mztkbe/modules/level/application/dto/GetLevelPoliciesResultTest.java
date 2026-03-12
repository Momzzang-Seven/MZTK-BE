package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class GetLevelPoliciesResultTest {

  @Test
  void builder_shouldKeepLists() {
    List<LevelPolicyItem> levelPolicies =
        List.of(
            LevelPolicyItem.builder()
                .currentLevel(1)
                .toLevel(2)
                .requiredXp(100)
                .rewardMztk(3)
                .build());
    List<XpPolicyItem> xpPolicies =
        List.of(XpPolicyItem.builder().type(XpType.CHECK_IN).xpAmount(10).dailyCap(1).build());

    GetLevelPoliciesResult result =
        GetLevelPoliciesResult.builder()
            .levelPolicies(levelPolicies)
            .xpPolicies(xpPolicies)
            .build();

    assertThat(result.levelPolicies()).containsExactlyElementsOf(levelPolicies);
    assertThat(result.xpPolicies()).containsExactlyElementsOf(xpPolicies);
  }
}
