package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.Eip7702ChainPort;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

/** Type-4(EIP-7702) raw transaction encoder/signature helper. */
public final class Eip7702TransactionEncoder {

  private static final byte TX_TYPE = 0x04;

  private Eip7702TransactionEncoder() {}

  public static SignedPayload signAndEncode(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<Eip7702ChainPort.AuthorizationTuple> authorizationList,
      String sponsorPrivateKeyHex) {
    validate(
        chainId,
        nonce,
        maxPriorityFeePerGas,
        maxFeePerGas,
        gasLimit,
        to,
        value,
        data,
        authorizationList,
        sponsorPrivateKeyHex);

    List<RlpType> unsignedFields =
        buildUnsignedFields(
            chainId,
            nonce,
            maxPriorityFeePerGas,
            maxFeePerGas,
            gasLimit,
            to,
            value,
            data,
            authorizationList);

    byte[] unsignedEncoded = prependTxType(RlpEncoder.encode(new RlpList(unsignedFields)));
    Credentials sponsorCredentials = Credentials.create(sponsorPrivateKeyHex);
    Sign.SignatureData signatureData =
        Sign.signMessage(unsignedEncoded, sponsorCredentials.getEcKeyPair());

    List<RlpType> signedFields = new ArrayList<>(unsignedFields);
    int v = Byte.toUnsignedInt(signatureData.getV()[0]);
    int yParity = v >= 27 ? v - 27 : v;
    signedFields.add(RlpString.create(yParity));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getR())));
    signedFields.add(RlpString.create(Bytes.trimLeadingZeroes(signatureData.getS())));

    byte[] signedTxBytes = prependTxType(RlpEncoder.encode(new RlpList(signedFields)));
    String rawTx = Numeric.toHexString(signedTxBytes);
    return new SignedPayload(rawTx, Hash.sha3(rawTx));
  }

  private static void validate(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<Eip7702ChainPort.AuthorizationTuple> authorizationList,
      String sponsorPrivateKeyHex) {
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
    if (value == null || value.signum() < 0) {
      throw new Web3InvalidInputException("value must be >= 0");
    }
    if (data == null || data.isBlank()) {
      throw new Web3InvalidInputException("data is required");
    }
    if (authorizationList == null || authorizationList.isEmpty()) {
      throw new Web3InvalidInputException("authorizationList is required");
    }
    if (sponsorPrivateKeyHex == null || sponsorPrivateKeyHex.isBlank()) {
      throw new Web3InvalidInputException("sponsorPrivateKeyHex is required");
    }
  }

  private static List<RlpType> buildUnsignedFields(
      long chainId,
      BigInteger nonce,
      BigInteger maxPriorityFeePerGas,
      BigInteger maxFeePerGas,
      BigInteger gasLimit,
      String to,
      BigInteger value,
      String data,
      List<Eip7702ChainPort.AuthorizationTuple> authorizationList) {
    List<RlpType> fields = new ArrayList<>();
    fields.add(RlpString.create(chainId));
    fields.add(RlpString.create(nonce));
    fields.add(RlpString.create(maxPriorityFeePerGas));
    fields.add(RlpString.create(maxFeePerGas));
    fields.add(RlpString.create(gasLimit));
    fields.add(RlpString.create(Numeric.hexStringToByteArray(to)));
    fields.add(RlpString.create(value));
    fields.add(RlpString.create(Numeric.hexStringToByteArray(data)));

    // accessList (empty)
    fields.add(new RlpList());

    // authorizationList
    fields.add(encodeAuthorizationList(authorizationList));
    return fields;
  }

  private static RlpList encodeAuthorizationList(
      List<Eip7702ChainPort.AuthorizationTuple> authList) {
    List<RlpType> tuples = new ArrayList<>();

    for (Eip7702ChainPort.AuthorizationTuple auth : authList) {
      List<RlpType> tuple = new ArrayList<>();
      tuple.add(RlpString.create(auth.chainId()));
      tuple.add(RlpString.create(Numeric.hexStringToByteArray(auth.address())));
      tuple.add(RlpString.create(auth.nonce()));
      tuple.add(RlpString.create(auth.yParity()));
      tuple.add(RlpString.create(auth.r()));
      tuple.add(RlpString.create(auth.s()));
      tuples.add(new RlpList(tuple));
    }

    return new RlpList(tuples);
  }

  private static byte[] prependTxType(byte[] rlpEncoded) {
    return ByteBuffer.allocate(1 + rlpEncoded.length).put(TX_TYPE).put(rlpEncoded).array();
  }

  public record SignedPayload(String rawTx, String txHash) {}
}
