package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryKeyOrigin;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;

/**
 * Aggregate root representing a single KMS-backed treasury wallet.
 *
 * <p>Identity outside of persistence is the {@code walletAlias} (mapped 1:1 from {@link
 * TreasuryRole#toAlias()}). The aggregate enforces the lifecycle contract documented on {@link
 * TreasuryWalletStatus} as the Information Expert: callers cannot bypass the transitions because
 * the constructor is private and every mutator returns a new instance via {@link #toBuilder()}.
 *
 * <p>Time-dependent transitions take a {@link Clock} so that tests can control {@code disabledAt}
 * without freezing global wall-clock state.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TreasuryWallet {

  private final Long id;
  private final String walletAlias;
  private final String kmsKeyId;
  private final String walletAddress;
  private final TreasuryWalletStatus status;
  private final TreasuryKeyOrigin keyOrigin;
  private final LocalDateTime disabledAt;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  /**
   * Factory used by the provisioning service immediately after a successful KMS import + sanity
   * round-trip. Persists status={@link TreasuryWalletStatus#ACTIVE} and keyOrigin={@link
   * TreasuryKeyOrigin#IMPORTED}.
   *
   * @param walletAlias canonical alias, typically derived from {@link TreasuryRole#toAlias()}
   * @param kmsKeyId AWS KMS key id (CMK ARN or key id) bound to this wallet
   * @param walletAddress {@code 0x}-prefixed Ethereum address recovered from the imported key
   * @param role functional role; persisted indirectly via {@code walletAlias}
   * @param clock clock for {@code createdAt}/{@code updatedAt}
   * @return a new ACTIVE treasury wallet ready to be persisted
   */
  public static TreasuryWallet provision(
      String walletAlias, String kmsKeyId, String walletAddress, TreasuryRole role, Clock clock) {
    Objects.requireNonNull(walletAlias, "walletAlias must not be null");
    Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
    Objects.requireNonNull(walletAddress, "walletAddress must not be null");
    Objects.requireNonNull(role, "role must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    if (!walletAlias.equals(role.toAlias())) {
      throw new IllegalArgumentException(
          "walletAlias '" + walletAlias + "' does not match role alias '" + role.toAlias() + "'");
    }
    final LocalDateTime now = LocalDateTime.now(clock);
    return TreasuryWallet.builder()
        .walletAlias(walletAlias)
        .kmsKeyId(kmsKeyId)
        .walletAddress(walletAddress)
        .status(TreasuryWalletStatus.ACTIVE)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
        .disabledAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * Factory used when {@code existing.kmsKeyId == null} (legacy row before KMS migration). Mints a
   * fresh KMS key and binds it to the row, optionally overriding the stored {@code walletAddress}
   * with the {@code derivedAddress} the operator just imported. Forces status to {@link
   * TreasuryWalletStatus#ACTIVE} (allowing recovery from a legacy DISABLED/ARCHIVED state) and
   * clears {@code disabledAt}.
   *
   * <p>Per the MOM-444 non-cohort-v2 decision table, this factory covers cases C1/C2/C3 (legacy
   * row, same address as derived) and C10/C11/C12 (legacy row, different address — derived value
   * wins).
   *
   * @param existing legacy row with {@code kmsKeyId == null}
   * @param newKmsKeyId fresh KMS key id produced by the pre-mint phase
   * @param newWalletAddress {@code 0x}-prefixed address derived from the imported raw key (may
   *     equal or differ from {@code existing.walletAddress})
   * @param clock clock for {@code updatedAt}
   * @return a wallet ready to be {@code save()}d as an UPDATE
   * @throws TreasuryWalletStateException if {@code existing.kmsKeyId} is already set
   */
  public static TreasuryWallet backfill(
      TreasuryWallet existing, String newKmsKeyId, String newWalletAddress, Clock clock) {
    Objects.requireNonNull(existing, "existing must not be null");
    Objects.requireNonNull(newKmsKeyId, "newKmsKeyId must not be null");
    Objects.requireNonNull(newWalletAddress, "newWalletAddress must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    if (existing.kmsKeyId != null) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' is already provisioned with kmsKeyId="
              + existing.kmsKeyId);
    }
    return existing.toBuilder()
        .kmsKeyId(newKmsKeyId)
        .walletAddress(newWalletAddress)
        .status(TreasuryWalletStatus.ACTIVE)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
        .disabledAt(null)
        .updatedAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * Factory used by the ReplaceKey action (MOM-444). Overwrites the row's KMS key id and wallet
   * address with freshly-imported values and forces the status back to {@link
   * TreasuryWalletStatus#ACTIVE}, clearing {@code disabledAt}. Preserves identity columns ({@code
   * id}, {@code walletAlias}, {@code createdAt}).
   *
   * <p>This is the path taken when the operator re-provisions an alias with a *different* raw
   * private key (rotation, cases C7/C8/C9) or re-provisions an ARCHIVED alias with the same key
   * (case C6 — old KMS key is already scheduled for deletion, the row gets a fresh key).
   *
   * @param existing the row to be replaced; must already carry a {@code kmsKeyId}
   * @param newKmsKeyId fresh KMS key id produced by the pre-mint phase
   * @param newWalletAddress {@code 0x}-prefixed address derived from the new raw private key
   * @param clock clock for {@code updatedAt}
   * @return a wallet ready to be {@code save()}d as an UPDATE
   * @throws TreasuryWalletStateException if {@code existing.kmsKeyId} is null (use {@link
   *     #backfill}) or if {@code newKmsKeyId} equals {@code existing.kmsKeyId}
   */
  public static TreasuryWallet replaceKey(
      TreasuryWallet existing, String newKmsKeyId, String newWalletAddress, Clock clock) {
    Objects.requireNonNull(existing, "existing must not be null");
    Objects.requireNonNull(newKmsKeyId, "newKmsKeyId must not be null");
    Objects.requireNonNull(newWalletAddress, "newWalletAddress must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    if (existing.kmsKeyId == null) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' has no kmsKeyId — use backfill instead of replaceKey");
    }
    if (newKmsKeyId.equals(existing.kmsKeyId)) {
      throw new TreasuryWalletStateException(
          "replaceKey rejected — same kmsKeyId for alias '" + existing.walletAlias + "'");
    }
    return existing.toBuilder()
        .kmsKeyId(newKmsKeyId)
        .walletAddress(newWalletAddress)
        .status(TreasuryWalletStatus.ACTIVE)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
        .disabledAt(null)
        .updatedAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * Factory used by the ReEnableSameKey action (MOM-444 / C5). Promotes a DISABLED row back to
   * ACTIVE without swapping the KMS key. The KMS-side counterpart is a single {@code enableKey}
   * call published via {@code TreasuryWalletReactivatedEvent}.
   *
   * @param existing must have {@code status == DISABLED} and {@code kmsKeyId != null}
   * @param clock clock for {@code updatedAt}
   * @return a wallet ready to be {@code save()}d as an UPDATE
   * @throws TreasuryWalletStateException if status is not DISABLED or kmsKeyId is null
   */
  public static TreasuryWallet reEnable(TreasuryWallet existing, Clock clock) {
    Objects.requireNonNull(existing, "existing must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    if (existing.kmsKeyId == null) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + existing.walletAlias + "' has no kmsKeyId — reEnable rejected");
    }
    if (existing.status != TreasuryWalletStatus.DISABLED) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' cannot be re-enabled from status "
              + existing.status);
    }
    return existing.toBuilder()
        .status(TreasuryWalletStatus.ACTIVE)
        .disabledAt(null)
        .updatedAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * Transition {@link TreasuryWalletStatus#ACTIVE} → {@link TreasuryWalletStatus#DISABLED} and
   * stamp {@code disabledAt}.
   *
   * @throws TreasuryWalletStateException if the wallet is not currently ACTIVE
   */
  public TreasuryWallet disable(Clock clock) {
    Objects.requireNonNull(clock, "clock must not be null");
    if (status != TreasuryWalletStatus.ACTIVE) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + walletAlias + "' cannot be disabled from status " + status);
    }
    final LocalDateTime now = LocalDateTime.now(clock);
    return this.toBuilder()
        .status(TreasuryWalletStatus.DISABLED)
        .disabledAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * Transition {@link TreasuryWalletStatus#DISABLED} → {@link TreasuryWalletStatus#ARCHIVED}.
   *
   * @throws TreasuryWalletStateException if the wallet is not currently DISABLED
   */
  public TreasuryWallet archive(Clock clock) {
    Objects.requireNonNull(clock, "clock must not be null");
    if (status != TreasuryWalletStatus.DISABLED) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + walletAlias + "' cannot be archived from status " + status);
    }
    return this.toBuilder()
        .status(TreasuryWalletStatus.ARCHIVED)
        .updatedAt(LocalDateTime.now(clock))
        .build();
  }

  /**
   * Guard invoked by signing flows immediately before delegating to the KMS sign port.
   *
   * @throws TreasuryWalletStateException if the wallet is not currently ACTIVE
   */
  public void assertSignable() {
    if (status != TreasuryWalletStatus.ACTIVE) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '" + walletAlias + "' is not signable in status " + status);
    }
  }
}
