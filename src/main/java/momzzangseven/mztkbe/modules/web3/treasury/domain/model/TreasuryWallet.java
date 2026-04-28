package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;

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
