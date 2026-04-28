package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ProvisionTreasuryKeyResult;

/**
 * Operator-invoked entry point that creates a KMS-backed treasury wallet from a raw secp256k1
 * private key. Sequence:
 *
 * <ol>
 *   <li>Derive the wallet address from the supplied private key and verify it matches {@code
 *       expectedAddress}.
 *   <li>Look up an existing row on alias or address; reject collisions.
 *   <li>Drive the AWS KMS lifecycle: {@code CreateKey(EXTERNAL)}, {@code GetParametersForImport},
 *       RSA-OAEP-SHA-256 wrap, {@code ImportKeyMaterial}, {@code CreateAlias}.
 *   <li>Run a {@code Sign}/recover sanity round-trip against the freshly provisioned key to ensure
 *       the imported material round-trips before the row is committed.
 *   <li>Persist the {@code TreasuryWallet} aggregate and zeroize the in-memory key material.
 * </ol>
 */
public interface ProvisionTreasuryKeyUseCase {

  ProvisionTreasuryKeyResult execute(ProvisionTreasuryKeyCommand command);
}
