package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.web3;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

/** Authorization signature helper for EIP-7702 tuple hash/signature parsing. */
public final class Eip7702AuthorizationHelper {

  private static final byte MAGIC = 0x05;

  private Eip7702AuthorizationHelper() {}

  public static byte[] buildSigningHash(long chainId, String delegateTarget, BigInteger nonce) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (delegateTarget == null || delegateTarget.isBlank()) {
      throw new Web3InvalidInputException("delegateTarget is required");
    }
    if (nonce == null || nonce.signum() < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }

    RlpList rlpList =
        new RlpList(
            RlpString.create(BigInteger.valueOf(chainId)),
            RlpString.create(Numeric.hexStringToByteArray(delegateTarget)),
            RlpString.create(nonce));
    byte[] rlpEncoded = RlpEncoder.encode(rlpList);

    byte[] message = ByteBuffer.allocate(1 + rlpEncoded.length).put(MAGIC).put(rlpEncoded).array();
    return Hash.sha3(message);
  }

  public static String buildSigningHashHex(long chainId, String delegateTarget, BigInteger nonce) {
    return Numeric.toHexString(buildSigningHash(chainId, delegateTarget, nonce));
  }

  public static boolean verifySigner(
      long chainId,
      String delegateTarget,
      BigInteger nonce,
      String signatureHex,
      String expectedAddress) {
    byte[] hash = buildSigningHash(chainId, delegateTarget, nonce);
    String recovered = recoverAddress(hash, signatureHex);
    return recovered.equalsIgnoreCase(expectedAddress);
  }

  public static Eip7702ChainPort.AuthorizationTuple toAuthorizationTuple(
      long chainId, String delegateTarget, BigInteger nonce, String signatureHex) {
    SignatureParts parts = parseSignature(signatureHex);
    int yParity = parts.v() >= 27 ? parts.v() - 27 : parts.v();

    return new Eip7702ChainPort.AuthorizationTuple(
        BigInteger.valueOf(chainId),
        delegateTarget,
        nonce,
        BigInteger.valueOf(yParity),
        new BigInteger(1, parts.r()),
        new BigInteger(1, parts.s()));
  }

  public static String recoverAddress(byte[] hash, String signatureHex) {
    SignatureParts parts = parseSignature(signatureHex);
    int recId = parts.v() >= 27 ? parts.v() - 27 : parts.v();
    if (recId < 0 || recId > 3) {
      throw new Web3InvalidInputException("invalid signature recovery id");
    }

    ECDSASignature ecdsaSignature =
        new ECDSASignature(new BigInteger(1, parts.r()), new BigInteger(1, parts.s()));
    BigInteger publicKey = Sign.recoverFromSignature(recId, ecdsaSignature, hash);
    if (publicKey == null) {
      throw new Web3InvalidInputException("failed to recover signer address");
    }
    return "0x" + Keys.getAddress(publicKey).toLowerCase(Locale.ROOT);
  }

  private static SignatureParts parseSignature(String signatureHex) {
    if (signatureHex == null || signatureHex.isBlank()) {
      throw new Web3InvalidInputException("signature is required");
    }
    byte[] signatureBytes = Numeric.hexStringToByteArray(signatureHex);
    if (signatureBytes.length != 65) {
      throw new Web3InvalidInputException("signature must be 65 bytes");
    }

    byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
    byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
    int v = Byte.toUnsignedInt(signatureBytes[64]);
    return new SignatureParts(r, s, v);
  }

  private record SignatureParts(byte[] r, byte[] s, int v) {}
}
