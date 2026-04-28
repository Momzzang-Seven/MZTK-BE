package momzzangseven.mztkbe.modules.web3.treasury.domain.model;

/**
 * Provenance of the secp256k1 private key material backing a {@link TreasuryWallet}.
 *
 * <p>{@link #IMPORTED} is the only origin currently supported: the platform generates the key
 * locally, wraps it with the KMS-issued import token, and uploads it via {@code ImportKeyMaterial}
 * so the wallet address remains deterministic across the import. Future origins (e.g. KMS-native
 * generation without import) would extend this enum without changing the persistence schema.
 */
public enum TreasuryKeyOrigin {
  IMPORTED
}
