package momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder;

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
 * Pure RLP encoder for Type-4 (EIP-7702) transactions.
 *
 * <p>Signature bytes are supplied externally (KMS or local signer) via {@link Vrs}; this class
 * never touches private-key material. The encoder produces three artifacts:
 *
 * <ol>
 *   <li>{@link #buildUnsigned(Eip7702Fields)} — the unsigned typed envelope {@code 0x04 ‖
 *       rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, destination, value,
 *       data, accessList=[], authorizationList])}.
 *   <li>{@link #digest(byte[])} — keccak256 of the unsigned bytes, the message a signer (e.g. AWS
 *       KMS) consumes as DIGEST.
 *   <li>{@link #assembleSigned(Eip7702Fields, Vrs)} — the signed envelope {@code 0x04 ‖ rlp([...,
 *       yParity, r, s])}, packaged as {@link SignedTx}.
 * </ol>
 */
public final class Eip7702TxEncoder {

  public static final byte TX_TYPE = 0x04;

  // 0x-prefixed hex with an even number of hex chars; "0x" alone permitted for empty calldata.
  private static final Pattern HEX_DATA_PATTERN = Pattern.compile("^0x([0-9a-fA-F]{2})*$");

  private Eip7702TxEncoder() {}

  /** Builds the unsigned EIP-7702 typed envelope bytes. */
  public static byte[] buildUnsigned(Eip7702Fields fields) {
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
  public static SignedTx assembleSigned(Eip7702Fields fields, Vrs sig) {
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

  private static List<RlpType> buildUnsignedFields(Eip7702Fields fields) {
    List<RlpType> rlpFields = new ArrayList<>();
    rlpFields.add(RlpString.create(fields.chainId()));
    rlpFields.add(RlpString.create(fields.nonce()));
    rlpFields.add(RlpString.create(fields.maxPriorityFeePerGas()));
    rlpFields.add(RlpString.create(fields.maxFeePerGas()));
    rlpFields.add(RlpString.create(fields.gasLimit()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.to())));
    rlpFields.add(RlpString.create(fields.value()));
    rlpFields.add(RlpString.create(Numeric.hexStringToByteArray(fields.data())));
    // accessList (empty) — v1 codepath does not populate access list entries
    rlpFields.add(new RlpList());
    // authorizationList — list of [chainId, address, nonce, yParity, r, s] tuples
    rlpFields.add(encodeAuthorizationList(fields.authorizationList()));
    return rlpFields;
  }

  private static RlpList encodeAuthorizationList(List<AuthorizationTuple> authList) {
    List<RlpType> tuples = new ArrayList<>();
    for (AuthorizationTuple auth : authList) {
      List<RlpType> tuple = new ArrayList<>();
      tuple.add(RlpString.create(auth.chainId()));
      tuple.add(RlpString.create(Numeric.hexStringToByteArray(auth.address())));
      tuple.add(RlpString.create(auth.nonce()));
      // Force BigInteger overload: web3j's RlpString.create(byte) emits a literal 1-byte string
      // [0x00] for yParity=0, but on-chain parsers expect canonical RLP empty string (0x80).
      // BigInteger.ZERO routes through the canonical path (0→0x80, 1→0x01).
      tuple.add(RlpString.create(BigInteger.valueOf(Byte.toUnsignedLong(auth.yParity()))));
      tuple.add(RlpString.create(Bytes.trimLeadingZeroes(auth.r())));
      tuple.add(RlpString.create(Bytes.trimLeadingZeroes(auth.s())));
      tuples.add(new RlpList(tuple));
    }
    return new RlpList(tuples);
  }

  private static byte[] prependTxType(byte[] rlpEncoded) {
    return ByteBuffer.allocate(1 + rlpEncoded.length).put(TX_TYPE).put(rlpEncoded).array();
  }

  /**
   * Validated field set for an EIP-7702 transaction.
   *
   * @param chainId positive chain identifier
   * @param nonce sender nonce in wei, {@code >= 0}
   * @param maxPriorityFeePerGas tip cap in wei, {@code > 0}
   * @param maxFeePerGas fee cap in wei, {@code > 0} and {@code >= maxPriorityFeePerGas}
   * @param gasLimit gas limit, {@code > 0}
   * @param to 0x-prefixed 20-byte destination address (validated via {@link EvmAddress#of(String)})
   * @param value transferred wei, {@code >= 0}
   * @param data 0x-prefixed hex calldata; {@code "0x"} is allowed for an empty payload
   * @param authorizationList list of authorization tuples; non-null, may be empty
   */
  public record Eip7702Fields(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<AuthorizationTuple> authorizationList) {

    /** Compact constructor — enforces EIP-7702 field invariants and freezes the auth list. */
    public Eip7702Fields {
      if (chainId <= 0) {
        throw new Web3InvalidInputException("chainId must be positive");
      }
      if (nonce == null || nonce.signum() < 0) {
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
      if (authorizationList == null) {
        throw new Web3InvalidInputException("authorizationList is required");
      }
      // Defensive immutability — callers cannot mutate the list after record creation.
      authorizationList = List.copyOf(authorizationList);
    }
  }

  /**
   * One element of the EIP-7702 authorization list.
   *
   * <p>{@code r} and {@code s} are 32-byte big-endian arrays, defensively cloned at construction
   * and at every accessor call to prevent retroactive mutation by caller-held references.
   *
   * @param chainId positive chain identifier the authorization is bound to
   * @param address 0x-prefixed 20-byte authority address (validated via {@link
   *     EvmAddress#of(String)})
   * @param nonce authority nonce, {@code >= 0}
   * @param yParity recovery parity, {@code 0} or {@code 1}
   * @param r 32-byte big-endian {@code r}
   * @param s 32-byte big-endian {@code s}
   */
  @SuppressWarnings("ArrayRecordComponent")
  public record AuthorizationTuple(
      long chainId, String address, BigInteger nonce, byte yParity, byte[] r, byte[] s) {

    /** Compact constructor — enforces invariants and defensively clones {@code r} / {@code s}. */
    public AuthorizationTuple {
      if (chainId <= 0) {
        throw new Web3InvalidInputException("chainId must be positive");
      }
      if (address == null || address.isBlank()) {
        throw new Web3InvalidInputException("address is required");
      }
      EvmAddress.of(address);
      if (nonce == null || nonce.signum() < 0) {
        throw new Web3InvalidInputException("nonce must be >= 0");
      }
      if (yParity != 0 && yParity != 1) {
        throw new Web3InvalidInputException("yParity must be 0 or 1");
      }
      if (r == null || r.length == 0) {
        throw new Web3InvalidInputException("r is required");
      }
      if (s == null || s.length == 0) {
        throw new Web3InvalidInputException("s is required");
      }
      r = r.clone();
      s = s.clone();
    }

    @Override
    public byte[] r() {
      return r.clone();
    }

    @Override
    public byte[] s() {
      return s.clone();
    }
  }

  /**
   * Signed envelope output.
   *
   * @param rawTx 0x-prefixed hex of the type-4 signed transaction bytes
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
