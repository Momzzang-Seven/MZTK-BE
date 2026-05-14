package momzzangseven.mztkbe.modules.web3.treasury.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TreasuryRole} — verifies the canonical alias mapping produced by {@link
 * TreasuryRole#toAlias()}.
 *
 * <p>Covers test cases M-101 .. M-106 (Commit 1-6, Group G).
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

    @Test
    @DisplayName("[M-104] QNA_SIGNER.toAlias() — 'qna-signer-treasury' 반환")
    void toAlias_qnaSigner_returnsQnaSignerTreasury() {
      // when
      String alias = TreasuryRole.QNA_SIGNER.toAlias();

      // then
      assertThat(alias).isEqualTo("qna-signer-treasury");
    }

    @Test
    @DisplayName("[M-105] QNA_SIGNER의 alias는 REWARD, SPONSOR alias와 충돌하지 않음")
    void toAlias_qnaSigner_doesNotCollideWithRewardOrSponsor() {
      // given
      String qnaSignerAlias = TreasuryRole.QNA_SIGNER.toAlias();

      // when
      String rewardAlias = TreasuryRole.REWARD.toAlias();
      String sponsorAlias = TreasuryRole.SPONSOR.toAlias();

      // then
      assertThat(qnaSignerAlias).isNotEqualTo(rewardAlias);
      assertThat(qnaSignerAlias).isNotEqualTo(sponsorAlias);
    }

    @Test
    @DisplayName("[M-106] 모든 TreasuryRole 값의 toAlias()는 null/blank를 반환하지 않음")
    void toAlias_allRoles_returnNonBlankAndDistinctAliases() {
      // given
      List<String> aliases =
          Arrays.stream(TreasuryRole.values())
              .map(TreasuryRole::toAlias)
              .collect(Collectors.toList());

      // then
      assertThat(aliases).allSatisfy(alias -> assertThat(alias).isNotNull().isNotBlank());

      Set<String> uniqueAliases = Set.copyOf(aliases);
      assertThat(uniqueAliases).hasSameSizeAs(aliases);
    }
  }
}
