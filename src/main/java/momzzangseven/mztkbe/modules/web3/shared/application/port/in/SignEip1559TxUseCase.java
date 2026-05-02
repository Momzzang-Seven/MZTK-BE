package momzzangseven.mztkbe.modules.web3.shared.application.port.in;

import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;

/**
 * Cross-module entry point for signing an EIP-1559 (Type-2) transaction with an externally-held
 * key (AWS KMS).
 *
 * <p>Sibling web3 sub-modules (transaction, execution, ...) depend on this in-port via the
 * {@code web3/shared/application/port/in/} package — implementations and codec adapters under
 * {@code web3/shared/infrastructure/adapter/} stay private to this module per ARCHITECTURE.md's
 * cross-module rules.
 */
public interface SignEip1559TxUseCase {

  /** Build, digest, sign, and assemble an EIP-1559 transaction in one round-trip. */
  SignEip1559TxResult sign(SignEip1559TxCommand command);
}
