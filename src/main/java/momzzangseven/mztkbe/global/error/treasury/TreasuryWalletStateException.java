package momzzangseven.mztkbe.global.error.treasury;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when a {@code TreasuryWallet} state transition is requested from a status that does not
 * allow it (e.g. {@code disable()} on an already-disabled wallet, {@code archive()} on an active
 * wallet, or {@code assertSignable()} on a non-active wallet).
 */
public class TreasuryWalletStateException extends BusinessException {

  /**
   * @param message human-readable description of the invalid transition
   */
  public TreasuryWalletStateException(String message) {
    super(ErrorCode.TREASURY_WALLET_INVALID_STATE, message);
  }
}
