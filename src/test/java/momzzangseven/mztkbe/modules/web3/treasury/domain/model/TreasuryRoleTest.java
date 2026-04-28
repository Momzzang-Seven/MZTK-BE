package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TreasuryRole} — verifies the canonical alias mapping produced by {@link
 * TreasuryRole#toAlias()}.
 *
 * <p>Covers test cases M-101 .. M-103 (Commit 1-6, Group G).
 */
@DisplayName("TreasuryRole 단위 테스트")
class TreasuryRoleTest {

  // =========================================================================
  // Section G — toAlias canonical mapping
  // =========================================================================

  @Nested
  @DisplayName("G. toAlias 정규 alias 매핑")
  class ToAliasMapping {

    @Test
    @DisplayName("[M-101] REWARD.toAlias() — 'reward-treasury' 반환")
    void toAlias_reward_returnsRewardTreasury() {
      // when
      String alias = TreasuryRole.REWARD.toAlias();

      // then
      assertThat(alias).isEqualTo("reward-treasury");
    }

    @Test
    @DisplayName("[M-102] SPONSOR.toAlias() — 'sponsor-treasury' 반환")
    void toAlias_sponsor_returnsSponsorTreasury() {
      // when
      String alias = TreasuryRole.SPONSOR.toAlias();

      // then
      assertThat(alias).isEqualTo("sponsor-treasury");
    }

    @Test
    @DisplayName("[M-103] 각 역할은 고유한 alias 생성 (충돌 없음)")
    void toAlias_eachRole_producesDistinctAlias() {
      // when
      String rewardAlias = TreasuryRole.REWARD.toAlias();
      String sponsorAlias = TreasuryRole.SPONSOR.toAlias();

      // then
      assertThat(rewardAlias).isNotEqualTo(sponsorAlias);
    }
  }
}
