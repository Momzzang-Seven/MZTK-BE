package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import java.util.Locale;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;

/**
 * Query carried into {@code ListTreasuryWalletsUseCase}. Holds the optional lifecycle filter
 * already parsed as a {@link TreasuryWalletStatus}; controllers must build the query through {@link
 * #fromStatusName(String)} so domain enum types do not leak into the {@code api} layer.
 *
 * @param statusFilter null when the caller wants every wallet; otherwise the lifecycle state to
 *     match
 */
public record ListTreasuryWalletsQuery(TreasuryWalletStatus statusFilter) {

  private static final ListTreasuryWalletsQuery ALL = new ListTreasuryWalletsQuery(null);

  /**
   * Parse a status filter string (e.g. {@code "ACTIVE"}) into a typed query. Returns the "no
   * filter" query for null / blank input. Throws {@link IllegalArgumentException} on any other
   * unrecognised value — the controller layer relies on {@code GlobalExceptionHandler} mapping this
   * to HTTP 400 so the wire contract stays "give a valid enum name or omit the param".
   *
   * <p>Matching is case-insensitive: callers may pass {@code active} or {@code ACTIVE} — the
   * canonical enum name is the authoritative form on the wire.
   */
  public static ListTreasuryWalletsQuery fromStatusName(String rawStatus) {
    if (rawStatus == null || rawStatus.isBlank()) {
      return ALL;
    }
    try {
      TreasuryWalletStatus parsed =
          TreasuryWalletStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
      return new ListTreasuryWalletsQuery(parsed);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Invalid treasury wallet status filter: '" + rawStatus + "'", ex);
    }
  }
}
