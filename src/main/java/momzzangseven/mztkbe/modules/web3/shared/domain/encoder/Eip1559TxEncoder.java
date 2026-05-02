package momzzangseven.mztkbe.modules.web3.shared.domain.encoder;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

/**
 * Pure RLP encoder for Type-2 (EIP-1559) transactions.
 *
 * <p>Signature bytes are supplied externally (KMS or local signer) via {@link Vrs}; this class
 * never touches private-key material. The encoder produces three artifacts:
 *
 * <ol>
 *   <li>{@link #buildUnsigned(Eip1559Fields)} — the unsigned typed envelope {@code 0x02 ‖
 *       rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data,
 *       accessList=[]])}.
 *   <li>{@link #digest(byte[])} — keccak256 of the unsigned bytes, the message a signer (e.g. AWS
 *       KMS) consumes as DIGEST.
 *   <li>{@link #assembleSigned(Eip1559Fields, Vrs)} — the signed envelope {@code 0x02 ‖ rlp([...,
 *       yParity, r, s])}, packaged as {@link SignedTx}.
 * </ol>
 */
public final class Eip1559TxEncoder {

  public static final byte TX_TYPE = 0x02;

  // 0x-prefixed hex with an even number of hex chars; "0x" alone permitted for empty calldata.
  private static final Pattern HEX_DATA_PATTERN = Pattern.compile("^0x([0-9a-fA-F]{2})*$");

  private Eip1559TxEncoder() {}

  /** Builds the unsigned EIP-1559 typed envelope bytes. */
  public static byte[] buildUnsigned(Eip1559Fields fields) {
    if (fields == null) {
      throw new Web3InvalidInputException("fields is required");
    }
    return prependTxType(RlpEncoder.encode(new RlpList(buildUnsignedFields(fields))));
  }

  /** Returns keccak256 of the unsigned bytes — the digest a remote signer (KMS) signs. */
  public static byte[] digest(byte[] unsigned) {
    if (unsigned == null || unsigned.length == 0) {
      throw new Web3InvalidInputException("unsigned bytes are required");
    }
    return Hash.sha3(unsigned);
  }

  /** Assembles the signed envelope from validated fields plus an externally produced signature. */
  public static SignedTx assembleSigned(Eip1559Fields fields, Vrs sig) {
    if (fields == null) {
      throw new Web3InvalidInputException("fields is required");
    }
    if (sig == null) {
      throw new Web3InvalidInputException("sig is required");
    }

    List<RlpType> unsignedFields = buildUnsignedFields(fields);

    int v = Byte.toUnsignedInt(sig.v());
    // yParity = v - 27 by Vrs invariant (v ∈ {27,28}); zero-fork branch is defensive against
    // producers that already pre-flatten v.
    int yParity = v >= 27 ? v - 27 : v;

    List<RlpType> signedFields = new ArrayList<>(unsignedFields);
    signedFields.add(RlpString.create(yParity));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(sig.r())));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(sig.s())));

    byte[] signedTxBytes = prependTxType(RlpEncoder.encode(new RlpList(signedFields)));
    String rawTx = Numeric.toHexString(signedTxBytes);
    String txHash = Hash.sha3(rawTx);
    return new SignedTx(rawTx, txHash);
  }

  private static List<RlpType> buildUnsignedFields(Eip1559Fields fields) {
    List<RlpType> rlpFields = new ArrayList<>();
    rlpFields.add(RlpString.create(fields.chainId()));
    rlpFields.add(RlpString.create(fields.nonce()));
    rlpFields.add(RlpString.create(fields.maxPriorityFeePerGas()));
    rlpFields.add(RlpString.create(fields.maxFeePerGas()));
    rlpFields.add(RlpString.create(fields.gasLimit()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.to())));
    rlpFields.add(RlpString.create(fields.value()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.data())));
    // accessList (empty)
    rlpFields.add(new RlpList());
    return rlpFields;
  }

  private static byte[] prependTxType(byte[] rlpEncoded) {
    return ByteBuffer.allocate(1 + rlpEncoded.length).put(TX_TYPE).put(rlpEncoded).array();
  }

  /**
   * Validated field set for an EIP-1559 transaction.
   *
   * @param chainId positive chain identifier
   * @param nonce sender nonce, {@code >= 0}
   * @param maxPriorityFeePerGas tip cap in wei, {@code > 0}
   * @param maxFeePerGas fee cap in wei, {@code > 0} and {@code >= maxPriorityFeePerGas}
   * @param gasLimit gas limit, {@code > 0}
   * @param to 0x-prefixed 20-byte recipient address (validated via {@link EvmAddress#of(String)})
   * @param value transferred wei, {@code >= 0}
   * @param data 0x-prefixed hex calldata; {@code "0x"} is allowed for an empty payload
   */
  public record Eip1559Fields(
      long chainId,
      long nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data) {

    /** Compact constructor — enforces EIP-1559 field invariants. */
    public Eip1559Fields {
      if (chainId <= 0) {
        throw new Web3InvalidInputException("chainId must be positive");
      }
      if (nonce < 0) {
        throw new Web3InvalidInputException("nonce must be >= 0");
      }
      if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
      }
      if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
        throw new Web3InvalidInputException("maxFeePerGas must be > 0");
      }
      if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
        throw new Web3InvalidInputException("maxFeePerGas must be >= maxPriorityFeePerGas");
      }
      if (gasLimit == null || gasLimit.signum() <= 0) {
        throw new Web3InvalidInputException("gasLimit must be > 0");
      }
      if (to == null || to.isBlank()) {
        throw new Web3InvalidInputException("to is required");
      }
      EvmAddress.of(to);
      if (value == null || value.signum() < 0) {
        throw new Web3InvalidInputException("value must be >= 0");
      }
      if (data == null || !HEX_DATA_PATTERN.matcher(data).matches()) {
        throw new Web3InvalidInputException("data must be 0x-prefixed hex");
      }
    }
  }

  /**
   * Signed envelope output.
   *
   * @param rawTx 0x-prefixed hex of the type-2 signed transaction bytes
   * @param txHash keccak256 of {@link #rawTx}, 0x-prefixed hex
   */
  public record SignedTx(String rawTx, String txHash) {

    /** Compact constructor — both fields must be non-blank. */
    public SignedTx {
      if (rawTx == null || rawTx.isBlank()) {
        throw new Web3InvalidInputException("rawTx is required");
      }
      if (txHash == null || txHash.isBlank()) {
        throw new Web3InvalidInputException("txHash is required");
      }
    }
  }
}
