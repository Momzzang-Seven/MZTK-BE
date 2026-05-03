package momzzangseven.mztkbe.modules.web3.shared.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Result of {@link
 * momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignEip1559TxUseCase#sign}.
 *
 * <p>Wraps {@link SignedTx} so the cross-module return type can grow (e.g. carry a sign duration or
 * audit handle) without breaking callers.
 */
public record SignEip1559TxResult(SignedTx signedTx) {

  /** Compact constructor. */
  public SignEip1559TxResult {
    if (signedTx == null) {
      throw new Web3InvalidInputException("signedTx is required");
    }
  }
}
