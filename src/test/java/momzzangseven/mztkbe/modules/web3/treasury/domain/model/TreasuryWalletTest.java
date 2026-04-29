package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link TreasuryWallet} — covers provision factory, disable/archive lifecycle
 * transitions, assertSignable guard, and toBuilder immutability.
 *
 * <p>Covers test cases M-79 .. M-100 (Commit 1-6, Groups A–F).
 */
@DisplayName("TreasuryWallet 단위 테스트")
class TreasuryWalletTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-06-01T12:00:00Z"), ZoneOffset.UTC);
  private static final Clock LATER_CLOCK =
      Clock.fixed(Instant.parse("2024-06-01T13:00:00Z"), ZoneOffset.UTC);
  private static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);
  private static final LocalDateTime LATER_NOW = LocalDateTime.now(LATER_CLOCK);

  private static final String REWARD_ALIAS = TreasuryRole.REWARD.toAlias();
  private static final String KMS_KEY_ID = "arn:aws:kms:us-east-1:123456789012:key/test-key";
  private static final String WALLET_ADDRESS = "0xDeadBeefDeadBeefDeadBeefDeadBeefDeadBeef";

  /** Convenience factory producing a fresh ACTIVE wallet for transition tests. */
  private static TreasuryWallet activeWallet() {
    return TreasuryWallet.provision(
        REWARD_ALIAS, KMS_KEY_ID, WALLET_ADDRESS, TreasuryRole.REWARD, FIXED_CLOCK);
  }

  // =========================================================================
  // Section A — provision happy path
  // =========================================================================

  @Nested
  @DisplayName("A. provision 행복 경로")
  class ProvisionHappyPath {

    @Test
    @DisplayName("[M-79] provision — 유효한 입력으로 ACTIVE IMPORTED 지갑 생성")
    void provision_validInputs_createsActiveImportedWallet() {
      // when
      TreasuryWallet w =
          TreasuryWallet.provision(
              REWARD_ALIAS, KMS_KEY_ID, WALLET_ADDRESS, TreasuryRole.REWARD, FIXED_CLOCK);

      // then
      assertThat(w.getWalletAlias()).isEqualTo("reward-treasury");
      assertThat(w.getKmsKeyId()).isEqualTo(KMS_KEY_ID);
      assertThat(w.getWalletAddress()).isEqualTo(WALLET_ADDRESS);
      assertThat(w.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(w.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
      assertThat(w.getDisabledAt()).isNull();
      assertThat(w.getCreatedAt()).isEqualTo(FIXED_NOW);
      assertThat(w.getUpdatedAt()).isEqualTo(FIXED_NOW);
      assertThat(w.getId()).isNull();
    }

    @Test
    @DisplayName("[M-80] provision — SPONSOR 역할은 alias를 'sponsor-treasury'로 설정")
    void provision_sponsorRole_setsSponsorAlias() {
      // when
      TreasuryWallet w =
          TreasuryWallet.provision(
              TreasuryRole.SPONSOR.toAlias(),
              "arn:aws:kms:us-east-1:123456789012:key/sponsor-key",
              "0xCafe0000Cafe0000Cafe0000Cafe0000Cafe0000",
              TreasuryRole.SPONSOR,
              FIXED_CLOCK);

      // then
      assertThat(w.getWalletAlias()).isEqualTo("sponsor-treasury");
      assertThat(w.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    }
  }

  // =========================================================================
  // Section B — provision null argument guards
  // =========================================================================

  @Nested
  @DisplayName("B. provision null 인수 검증")
  class ProvisionNullGuards {

    @Test
    @DisplayName("[M-81] provision — null walletAlias → NullPointerException")
    void provision_nullWalletAlias_throwsNpe() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      null, KMS_KEY_ID, WALLET_ADDRESS, TreasuryRole.REWARD, FIXED_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("walletAlias must not be null");
    }

    @Test
    @DisplayName("[M-82] provision — null kmsKeyId → NullPointerException")
    void provision_nullKmsKeyId_throwsNpe() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      REWARD_ALIAS, null, WALLET_ADDRESS, TreasuryRole.REWARD, FIXED_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("kmsKeyId must not be null");
    }

    @Test
    @DisplayName("[M-83] provision — null walletAddress → NullPointerException")
    void provision_nullWalletAddress_throwsNpe() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      REWARD_ALIAS, KMS_KEY_ID, null, TreasuryRole.REWARD, FIXED_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("walletAddress must not be null");
    }

    @Test
    @DisplayName("[M-84] provision — null role → NullPointerException")
    void provision_nullRole_throwsNpe() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      REWARD_ALIAS, KMS_KEY_ID, WALLET_ADDRESS, null, FIXED_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("role must not be null");
    }

    @Test
    @DisplayName("[M-85] provision — null clock → NullPointerException")
    void provision_nullClock_throwsNpe() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      REWARD_ALIAS, KMS_KEY_ID, WALLET_ADDRESS, TreasuryRole.REWARD, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("clock must not be null");
    }
  }

  // =========================================================================
  // Section C — provision alias/role mismatch guard
  // =========================================================================

  @Nested
  @DisplayName("C. provision alias/role 불일치 검증")
  class ProvisionAliasMismatch {

    @Test
    @DisplayName("[M-86] provision — REWARD role에 SPONSOR alias 전달 시 IAE 발생")
    void provision_sponsorAliasWithRewardRole_throwsIllegalArgument() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      "sponsor-treasury",
                      KMS_KEY_ID,
                      "0x" + "a".repeat(40),
                      TreasuryRole.REWARD,
                      FIXED_CLOCK))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sponsor-treasury")
          .hasMessageContaining("reward-treasury");
    }

    @Test
    @DisplayName("[M-87] provision — SPONSOR role에 REWARD alias 전달 시 IAE 발생")
    void provision_rewardAliasWithSponsorRole_throwsIllegalArgument() {
      assertThatThrownBy(
              () ->
                  TreasuryWallet.provision(
                      TreasuryRole.REWARD.toAlias(),
                      KMS_KEY_ID,
                      "0x" + "a".repeat(40),
                      TreasuryRole.SPONSOR,
                      FIXED_CLOCK))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reward-treasury")
          .hasMessageContaining("sponsor-treasury");
    }
  }

  // =========================================================================
  // Section D — disable state transitions
  // =========================================================================

  @Nested
  @DisplayName("D. disable 상태 전이")
  class DisableTransitions {

    @Test
    @DisplayName("[M-88] disable — ACTIVE 지갑을 DISABLED로 전이, disabledAt/updatedAt 설정")
    void disable_activeWallet_transitionsToDisabledWithTimestamps() {
      // given
      TreasuryWallet active = activeWallet();

      // when
      TreasuryWallet disabled = active.disable(LATER_CLOCK);

      // then
      assertThat(disabled.getStatus()).isEqualTo(TreasuryWalletStatus.DISABLED);
      assertThat(disabled.getDisabledAt()).isEqualTo(LATER_NOW);
      assertThat(disabled.getUpdatedAt()).isEqualTo(LATER_NOW);
      assertThat(disabled.getWalletAlias()).isEqualTo(active.getWalletAlias());
      assertThat(disabled.getKmsKeyId()).isEqualTo(active.getKmsKeyId());
      assertThat(disabled.getWalletAddress()).isEqualTo(active.getWalletAddress());
      assertThat(disabled.getCreatedAt()).isEqualTo(active.getCreatedAt());
    }

    @Test
    @DisplayName("[M-89] disable — toBuilder 불변성: 원본 ACTIVE 인스턴스는 변경되지 않음")
    void disable_activeWallet_doesNotMutateOriginal() {
      // given
      TreasuryWallet active = activeWallet();

      // when
      TreasuryWallet disabled = active.disable(LATER_CLOCK);

      // then
      assertThat(active.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(active.getDisabledAt()).isNull();
      assertThat(active.getUpdatedAt()).isEqualTo(FIXED_NOW);
      assertThat(disabled).isNotSameAs(active);
    }

    @Test
    @DisplayName("[M-90] disable — DISABLED 지갑 재시도 시 TreasuryWalletStateException 발생")
    void disable_alreadyDisabledWallet_throwsTreasuryWalletStateException() {
      // given
      TreasuryWallet disabled = activeWallet().disable(FIXED_CLOCK);

      // then
      assertThatThrownBy(() -> disabled.disable(LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(twse.getMessage()).contains("DISABLED");
              });
    }

    @Test
    @DisplayName("[M-91] disable — ARCHIVED 지갑에 disable 시도 시 TreasuryWalletStateException 발생")
    void disable_archivedWallet_throwsTreasuryWalletStateException() {
      // given
      TreasuryWallet archived = activeWallet().disable(FIXED_CLOCK).archive(LATER_CLOCK);

      // then
      assertThatThrownBy(() -> archived.disable(FIXED_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getMessage()).contains("ARCHIVED");
              });
    }

    @Test
    @DisplayName("[M-92] disable — null clock → NullPointerException")
    void disable_nullClock_throwsNpe() {
      assertThatThrownBy(() -> activeWallet().disable(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("clock must not be null");
    }
  }

  // =========================================================================
  // Section E — archive state transitions
  // =========================================================================

  @Nested
  @DisplayName("E. archive 상태 전이")
  class ArchiveTransitions {

    @Test
    @DisplayName("[M-93] archive — DISABLED 지갑을 ARCHIVED로 전이, updatedAt 설정")
    void archive_disabledWallet_transitionsToArchivedWithTimestamp() {
      // given
      TreasuryWallet disabled = activeWallet().disable(FIXED_CLOCK);

      // when
      TreasuryWallet archived = disabled.archive(LATER_CLOCK);

      // then
      assertThat(archived.getStatus()).isEqualTo(TreasuryWalletStatus.ARCHIVED);
      assertThat(archived.getUpdatedAt()).isEqualTo(LATER_NOW);
      assertThat(archived.getDisabledAt()).isEqualTo(disabled.getDisabledAt());
      assertThat(archived.getCreatedAt()).isEqualTo(disabled.getCreatedAt());
      assertThat(archived.getWalletAlias()).isEqualTo(disabled.getWalletAlias());
    }

    @Test
    @DisplayName("[M-94] archive — toBuilder 불변성: 원본 DISABLED 인스턴스는 변경되지 않음")
    void archive_disabledWallet_doesNotMutateOriginal() {
      // given
      TreasuryWallet disabled = activeWallet().disable(FIXED_CLOCK);

      // when
      TreasuryWallet archived = disabled.archive(LATER_CLOCK);

      // then
      assertThat(disabled.getStatus()).isEqualTo(TreasuryWalletStatus.DISABLED);
      assertThat(disabled.getUpdatedAt()).isEqualTo(FIXED_NOW);
      assertThat(archived).isNotSameAs(disabled);
    }

    @Test
    @DisplayName("[M-95] archive — ACTIVE 지갑에 archive 시도 시 TreasuryWalletStateException 발생")
    void archive_activeWallet_throwsTreasuryWalletStateException() {
      assertThatThrownBy(() -> activeWallet().archive(FIXED_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getMessage()).contains("ACTIVE");
              });
    }

    @Test
    @DisplayName("[M-96] archive — ARCHIVED 지갑 재시도 시 TreasuryWalletStateException 발생")
    void archive_alreadyArchivedWallet_throwsTreasuryWalletStateException() {
      // given
      TreasuryWallet archived = activeWallet().disable(FIXED_CLOCK).archive(LATER_CLOCK);

      // then
      assertThatThrownBy(() -> archived.archive(FIXED_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getMessage()).contains("ARCHIVED");
              });
    }

    @Test
    @DisplayName("[M-97] archive — null clock → NullPointerException")
    void archive_nullClock_throwsNpe() {
      assertThatThrownBy(() -> activeWallet().disable(FIXED_CLOCK).archive(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("clock must not be null");
    }
  }

  // =========================================================================
  // Section F — assertSignable guard
  // =========================================================================

  @Nested
  @DisplayName("F. assertSignable 가드")
  class AssertSignable {

    @Test
    @DisplayName("[M-98] assertSignable — ACTIVE 지갑은 예외 없이 통과")
    void assertSignable_activeWallet_doesNotThrow() {
      assertThatNoException().isThrownBy(() -> activeWallet().assertSignable());
    }

    @Test
    @DisplayName("[M-99] assertSignable — DISABLED 지갑은 TreasuryWalletStateException 발생")
    void assertSignable_disabledWallet_throwsTreasuryWalletStateException() {
      // given
      TreasuryWallet disabled = activeWallet().disable(FIXED_CLOCK);

      // then
      assertThatThrownBy(disabled::assertSignable)
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(twse.getMessage()).contains("DISABLED");
              });
    }

    @Test
    @DisplayName("[M-100] assertSignable — ARCHIVED 지갑은 TreasuryWalletStateException 발생")
    void assertSignable_archivedWallet_throwsTreasuryWalletStateException() {
      // given
      TreasuryWallet archived = activeWallet().disable(FIXED_CLOCK).archive(LATER_CLOCK);

      // then
      assertThatThrownBy(archived::assertSignable)
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getMessage()).contains("ARCHIVED");
              });
    }
  }
}
