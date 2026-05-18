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
 * Unit tests for {@link TreasuryWallet} — covers provision/backfill factories, disable/archive
 * lifecycle transitions, assertSignable guard, and toBuilder immutability.
 *
 * <p>Covers test cases [M-38] .. [M-53] of the MOM-383 test plan (revised numbering); the inline
 * IDs below remain on their original numbering for stable diffs and trace back to the same
 * branches.
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
  // Section C2 — backfill factory (M-42 .. M-45)
  // =========================================================================

  @Nested
  @DisplayName("C2. backfill 팩토리 (legacy 행 KMS 백필)")
  class BackfillFactory {

    private static final LocalDateTime EARLIER = LocalDateTime.of(2024, 5, 1, 9, 0, 0);

    /**
     * Legacy row loaded by alias: id+walletAddress+createdAt 보존, kmsKeyId/status/keyOrigin null.
     */
    private TreasuryWallet legacyRow() {
      return TreasuryWallet.builder()
          .id(7L)
          .walletAlias(REWARD_ALIAS)
          .kmsKeyId(null)
          .walletAddress(WALLET_ADDRESS)
          .status(null)
          .keyOrigin(null)
          .disabledAt(null)
          .createdAt(EARLIER)
          .updatedAt(EARLIER)
          .build();
    }

    @Test
    @DisplayName("[M-42] backfill — id/walletAddress/createdAt 보존, KMS 필드 + updatedAt만 갱신")
    void backfill_legacyRow_preservesIdentityAndStampsKmsFields() {
      // when
      TreasuryWallet result =
          TreasuryWallet.backfill(legacyRow(), "fresh-kms-id", WALLET_ADDRESS, LATER_CLOCK);

      // then
      assertThat(result.getId()).isEqualTo(7L);
      assertThat(result.getWalletAlias()).isEqualTo(REWARD_ALIAS);
      assertThat(result.getWalletAddress()).isEqualTo(WALLET_ADDRESS);
      assertThat(result.getKmsKeyId()).isEqualTo("fresh-kms-id");
      assertThat(result.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(result.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
      assertThat(result.getCreatedAt()).isEqualTo(EARLIER);
      assertThat(result.getUpdatedAt()).isEqualTo(LATER_NOW);
    }

    @Test
    @DisplayName("[M-43] backfill — existing.kmsKeyId가 이미 있으면 TreasuryWalletStateException")
    void backfill_existingHasKmsKeyId_throwsState() {
      TreasuryWallet alreadyProvisioned = legacyRow().toBuilder().kmsKeyId("prev-id").build();

      assertThatThrownBy(
              () ->
                  TreasuryWallet.backfill(
                      alreadyProvisioned, "fresh-kms-id", WALLET_ADDRESS, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .satisfies(
              ex -> {
                TreasuryWalletStateException twse = (TreasuryWalletStateException) ex;
                assertThat(twse.getCode()).isEqualTo("TREASURY_001");
                assertThat(twse.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(twse.getMessage()).contains(REWARD_ALIAS).contains("prev-id");
              });
    }

    @Test
    @DisplayName("[M-45a] backfill — null existing → NullPointerException")
    void backfill_nullExisting_throwsNpe() {
      assertThatThrownBy(
              () -> TreasuryWallet.backfill(null, "fresh-kms-id", WALLET_ADDRESS, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("existing must not be null");
    }

    @Test
    @DisplayName("[M-45b] backfill — null kmsKeyId → NullPointerException")
    void backfill_nullKmsKeyId_throwsNpe() {
      assertThatThrownBy(
              () -> TreasuryWallet.backfill(legacyRow(), null, WALLET_ADDRESS, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("newKmsKeyId must not be null");
    }

    @Test
    @DisplayName("[M-45d] backfill — null newWalletAddress → NullPointerException")
    void backfill_nullNewWalletAddress_throwsNpe() {
      assertThatThrownBy(
              () -> TreasuryWallet.backfill(legacyRow(), "fresh-kms-id", null, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("newWalletAddress must not be null");
    }

    @Test
    @DisplayName("[M-45c] backfill — null clock → NullPointerException")
    void backfill_nullClock_throwsNpe() {
      assertThatThrownBy(
              () -> TreasuryWallet.backfill(legacyRow(), "fresh-kms-id", WALLET_ADDRESS, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("clock must not be null");
    }

    @Test
    @DisplayName("[M-46a] backfill — ACTIVE 상태 (V056 백필 후) 행은 정상적으로 backfill 가능")
    void backfill_existingActive_succeeds() {
      TreasuryWallet activeLegacy =
          legacyRow().toBuilder().status(TreasuryWalletStatus.ACTIVE).build();

      TreasuryWallet result =
          TreasuryWallet.backfill(activeLegacy, "fresh-kms-id", WALLET_ADDRESS, LATER_CLOCK);

      assertThat(result.getKmsKeyId()).isEqualTo("fresh-kms-id");
      assertThat(result.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(result.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
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

  // =========================================================================
  // Section G — replaceKey factory (MOM-444)
  // =========================================================================

  @Nested
  @DisplayName("G. replaceKey factory")
  class ReplaceKeyFactory {

    private static final String NEW_KMS = "arn:aws:kms:us-east-1:123456789012:key/new";
    private static final String NEW_ADDRESS = "0xCafeBabeCafeBabeCafeBabeCafeBabeCafeBabe";

    @Test
    @DisplayName("ACTIVE row 에 대해 kms_key_id / address 교체 + disabledAt null + status ACTIVE 유지")
    void replaceKey_fromActive_swapsKeyAndAddress() {
      TreasuryWallet existing = activeWallet();

      TreasuryWallet rotated =
          TreasuryWallet.replaceKey(existing, NEW_KMS, NEW_ADDRESS, LATER_CLOCK);

      assertThat(rotated.getKmsKeyId()).isEqualTo(NEW_KMS);
      assertThat(rotated.getWalletAddress()).isEqualTo(NEW_ADDRESS);
      assertThat(rotated.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(rotated.getDisabledAt()).isNull();
      assertThat(rotated.getKeyOrigin()).isEqualTo(TreasuryKeyOrigin.IMPORTED);
      assertThat(rotated.getId()).isEqualTo(existing.getId());
      assertThat(rotated.getWalletAlias()).isEqualTo(existing.getWalletAlias());
      assertThat(rotated.getCreatedAt()).isEqualTo(existing.getCreatedAt());
      assertThat(rotated.getUpdatedAt()).isEqualTo(LATER_NOW);
    }

    @Test
    @DisplayName("DISABLED row 도 ACTIVE 로 전이하고 disabledAt 을 null 로 리셋")
    void replaceKey_fromDisabled_clearsDisabledAtAndPromotesToActive() {
      TreasuryWallet disabled = activeWallet().disable(LATER_CLOCK);
      assertThat(disabled.getStatus()).isEqualTo(TreasuryWalletStatus.DISABLED);
      assertThat(disabled.getDisabledAt()).isNotNull();

      TreasuryWallet rotated =
          TreasuryWallet.replaceKey(disabled, NEW_KMS, NEW_ADDRESS, LATER_CLOCK);

      assertThat(rotated.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(rotated.getDisabledAt()).isNull();
      assertThat(rotated.getKmsKeyId()).isEqualTo(NEW_KMS);
    }

    @Test
    @DisplayName("ARCHIVED row 도 ACTIVE 로 전이")
    void replaceKey_fromArchived_promotesToActive() {
      TreasuryWallet archived = activeWallet().disable(LATER_CLOCK).archive(LATER_CLOCK);
      assertThat(archived.getStatus()).isEqualTo(TreasuryWalletStatus.ARCHIVED);

      TreasuryWallet rotated =
          TreasuryWallet.replaceKey(archived, NEW_KMS, NEW_ADDRESS, LATER_CLOCK);

      assertThat(rotated.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(rotated.getKmsKeyId()).isEqualTo(NEW_KMS);
    }

    @Test
    @DisplayName("existing.kmsKeyId == null 이면 거부 (backfill 경로를 사용해야 함)")
    void replaceKey_rejectsNullExistingKmsKeyId() {
      TreasuryWallet legacy = activeWallet().toBuilder().kmsKeyId(null).build();

      assertThatThrownBy(() -> TreasuryWallet.replaceKey(legacy, NEW_KMS, NEW_ADDRESS, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("no kmsKeyId")
          .hasMessageContaining("backfill");
    }

    @Test
    @DisplayName("newKmsKeyId == existing.kmsKeyId 이면 거부")
    void replaceKey_rejectsSameKmsKeyId() {
      TreasuryWallet existing = activeWallet();

      assertThatThrownBy(
              () ->
                  TreasuryWallet.replaceKey(
                      existing, existing.getKmsKeyId(), NEW_ADDRESS, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("same kmsKeyId");
    }

    @Test
    @DisplayName("null 인자는 모두 NullPointerException")
    void replaceKey_nullArgs_throw() {
      TreasuryWallet existing = activeWallet();

      assertThatThrownBy(() -> TreasuryWallet.replaceKey(null, NEW_KMS, NEW_ADDRESS, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> TreasuryWallet.replaceKey(existing, null, NEW_ADDRESS, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> TreasuryWallet.replaceKey(existing, NEW_KMS, null, LATER_CLOCK))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> TreasuryWallet.replaceKey(existing, NEW_KMS, NEW_ADDRESS, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  // =========================================================================
  // Section H — reEnable factory (MOM-444)
  // =========================================================================

  @Nested
  @DisplayName("H. reEnable factory")
  class ReEnableFactory {

    @Test
    @DisplayName("DISABLED → ACTIVE 로 전이, disabledAt null, kmsKeyId/address 불변")
    void reEnable_fromDisabled_promotesToActiveAndClearsDisabledAt() {
      TreasuryWallet disabled = activeWallet().disable(LATER_CLOCK);

      TreasuryWallet reactivated = TreasuryWallet.reEnable(disabled, LATER_CLOCK);

      assertThat(reactivated.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(reactivated.getDisabledAt()).isNull();
      assertThat(reactivated.getKmsKeyId()).isEqualTo(disabled.getKmsKeyId());
      assertThat(reactivated.getWalletAddress()).isEqualTo(disabled.getWalletAddress());
      assertThat(reactivated.getUpdatedAt()).isEqualTo(LATER_NOW);
    }

    @Test
    @DisplayName("ACTIVE status 는 거부")
    void reEnable_rejectsActive() {
      TreasuryWallet active = activeWallet();

      assertThatThrownBy(() -> TreasuryWallet.reEnable(active, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("ARCHIVED status 는 거부 (replaceKey 사용해야 함)")
    void reEnable_rejectsArchived() {
      TreasuryWallet archived = activeWallet().disable(LATER_CLOCK).archive(LATER_CLOCK);

      assertThatThrownBy(() -> TreasuryWallet.reEnable(archived, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("ARCHIVED");
    }

    @Test
    @DisplayName("existing.kmsKeyId == null 은 거부")
    void reEnable_rejectsNullKmsKeyId() {
      TreasuryWallet legacy =
          activeWallet().disable(LATER_CLOCK).toBuilder().kmsKeyId(null).build();

      assertThatThrownBy(() -> TreasuryWallet.reEnable(legacy, LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class)
          .hasMessageContaining("no kmsKeyId");
    }
  }

  // =========================================================================
  // Section I — backfill factory: non-cohort-v2 case coverage (MOM-444)
  // =========================================================================

  @Nested
  @DisplayName("I. backfill factory — 시그니처 확장 (MOM-444)")
  class BackfillFactoryExtended {

    private static final String NEW_KMS = "arn:aws:kms:us-east-1:123456789012:key/new";

    @Test
    @DisplayName("derived address 가 stored address 와 다르면 derived 로 덮어쓴다 (C10)")
    void backfill_differentAddress_overwritesStoredAddress() {
      TreasuryWallet legacy =
          TreasuryWallet.builder()
              .id(42L)
              .walletAlias(REWARD_ALIAS)
              .kmsKeyId(null)
              .walletAddress("0x0000000000000000000000000000000000000001")
              .status(TreasuryWalletStatus.ACTIVE)
              .keyOrigin(TreasuryKeyOrigin.IMPORTED)
              .createdAt(FIXED_NOW)
              .updatedAt(FIXED_NOW)
              .build();
      String derived = "0x0000000000000000000000000000000000000002";

      TreasuryWallet backfilled = TreasuryWallet.backfill(legacy, NEW_KMS, derived, LATER_CLOCK);

      assertThat(backfilled.getWalletAddress()).isEqualTo(derived);
      assertThat(backfilled.getKmsKeyId()).isEqualTo(NEW_KMS);
      assertThat(backfilled.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("DISABLED legacy row 도 ACTIVE 로 전이 + disabledAt null (C2/C11)")
    void backfill_disabledLegacyRow_promotesToActive() {
      TreasuryWallet legacyDisabled =
          TreasuryWallet.builder()
              .id(42L)
              .walletAlias(REWARD_ALIAS)
              .kmsKeyId(null)
              .walletAddress("0x0000000000000000000000000000000000000001")
              .status(TreasuryWalletStatus.DISABLED)
              .keyOrigin(TreasuryKeyOrigin.IMPORTED)
              .disabledAt(FIXED_NOW)
              .createdAt(FIXED_NOW)
              .updatedAt(FIXED_NOW)
              .build();

      TreasuryWallet backfilled =
          TreasuryWallet.backfill(
              legacyDisabled, NEW_KMS, legacyDisabled.getWalletAddress(), LATER_CLOCK);

      assertThat(backfilled.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(backfilled.getDisabledAt()).isNull();
      assertThat(backfilled.getKmsKeyId()).isEqualTo(NEW_KMS);
    }

    @Test
    @DisplayName("ARCHIVED legacy row 도 ACTIVE 로 전이 (C3/C12)")
    void backfill_archivedLegacyRow_promotesToActive() {
      TreasuryWallet legacyArchived =
          TreasuryWallet.builder()
              .id(42L)
              .walletAlias(REWARD_ALIAS)
              .kmsKeyId(null)
              .walletAddress("0x0000000000000000000000000000000000000001")
              .status(TreasuryWalletStatus.ARCHIVED)
              .keyOrigin(TreasuryKeyOrigin.IMPORTED)
              .createdAt(FIXED_NOW)
              .updatedAt(FIXED_NOW)
              .build();

      TreasuryWallet backfilled =
          TreasuryWallet.backfill(
              legacyArchived, NEW_KMS, legacyArchived.getWalletAddress(), LATER_CLOCK);

      assertThat(backfilled.getStatus()).isEqualTo(TreasuryWalletStatus.ACTIVE);
      assertThat(backfilled.getKmsKeyId()).isEqualTo(NEW_KMS);
    }

    @Test
    @DisplayName("existing.kmsKeyId != null 이면 거부 (replaceKey 사용해야 함)")
    void backfill_rejectsExistingKmsKeyId() {
      TreasuryWallet alreadyProvisioned = activeWallet();

      assertThatThrownBy(
              () ->
                  TreasuryWallet.backfill(
                      alreadyProvisioned, NEW_KMS, "0x" + "b".repeat(40), LATER_CLOCK))
          .isInstanceOf(TreasuryWalletStateException.class);
    }
  }
}
