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
   * Factory used by the provisioning service when the supplied {@code walletAlias} already has a
   * row in the legacy schema (V055 rename) but no {@code kms_key_id} yet. Preserves the row
   * identity ({@code id}, {@code walletAddress}, {@code createdAt}) and only mutates the KMS-side
   * fields ({@code kmsKeyId}, {@code status}, {@code keyOrigin}, {@code updatedAt}).
   *
   * <p>The address must already match because the legacy row's address was seeded from the same
   * underlying private key the operator is now re-supplying — divergence indicates an operator
   * mistake (wrong key) and is rejected by the caller before reaching this factory.
   *
   * @param existing the legacy row loaded by alias; must have {@code kmsKeyId == null} and a
   *     non-null {@code walletAddress}
   * @param kmsKeyId fresh KMS key id produced by the provisioning flow
   * @param clock clock for {@code updatedAt}
   * @return a wallet ready to be {@code save()}d as an UPDATE of the same row
   */
  public static TreasuryWallet backfill(TreasuryWallet existing, String kmsKeyId, Clock clock) {
    Objects.requireNonNull(existing, "existing must not be null");
    Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
    Objects.requireNonNull(clock, "clock must not be null");
    if (existing.kmsKeyId != null) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' is already provisioned with kmsKeyId="
              + existing.kmsKeyId);
    }
    if (existing.walletAddress == null || existing.walletAddress.isBlank()) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' has no walletAddress on file — cannot backfill");
    }
    if (existing.status != null && existing.status != TreasuryWalletStatus.ACTIVE) {
      throw new TreasuryWalletStateException(
          "Treasury wallet '"
              + existing.walletAlias
              + "' has status="
              + existing.status
              + " — only legacy rows with null/ACTIVE status can be backfilled");
    }
    return existing.toBuilder()
        .kmsKeyId(kmsKeyId)
        .status(TreasuryWalletStatus.ACTIVE)
        .keyOrigin(TreasuryKeyOrigin.IMPORTED)
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
