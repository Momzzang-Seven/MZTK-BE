package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;
import org.web3j.utils.Numeric;

@Component
@Slf4j
public class Eip1559TransactionCodecAdapter implements Eip1559TransactionCodecPort {

  @Override
  public DecodedSignedTransaction decodeAndVerify(
      String signedRawTransaction,
      UnsignedTxSnapshot expectedSnapshot,
      String expectedFingerprint) {
    if (signedRawTransaction == null || signedRawTransaction.isBlank()) {
      throw new Web3InvalidInputException("signedRawTransaction is required");
    }
    if (expectedSnapshot == null) {
      throw new Web3InvalidInputException("expectedSnapshot is required");
    }
    if (expectedFingerprint == null || expectedFingerprint.isBlank()) {
      throw new Web3InvalidInputException("expectedFingerprint is required");
    }

    RawTransaction decoded = TransactionDecoder.decode(signedRawTransaction);
    if (!(decoded instanceof SignedRawTransaction signedRawTx)) {
      throw new Web3InvalidInputException("signedRawTransaction must be signed");
    }

    String signerAddress = recoverSignerAddress(signedRawTx);

    UnsignedTxSnapshot actualSnapshot =
        new UnsignedTxSnapshot(
            transaction1559(signedRawTx).getChainId(),
            signerAddress,
            signedRawTx.getTo(),
            nullSafe(signedRawTx.getValue()),
            normalizeHex(signedRawTx.getData()),
            signedRawTx.getNonce().longValueExact(),
            nullSafe(signedRawTx.getGasLimit()),
            nullSafe(transaction1559(signedRawTx).getMaxPriorityFeePerGas()),
            nullSafe(transaction1559(signedRawTx).getMaxFeePerGas()));

    String actualFingerprint = computeFingerprint(actualSnapshot);
    if (!expectedFingerprint.equalsIgnoreCase(actualFingerprint)) {
      throw new Web3InvalidInputException("signedRawTransaction fingerprint mismatch");
    }
    if (!sameSnapshot(expectedSnapshot, actualSnapshot)) {
      throw new Web3InvalidInputException("signedRawTransaction field set mismatch");
    }

    return new DecodedSignedTransaction(
        signedRawTransaction,
        Hash.sha3(signedRawTransaction),
        signerAddress,
        actualSnapshot,
        actualFingerprint);
  }

  @Override
  public String computeFingerprint(UnsignedTxSnapshot snapshot) {
    if (snapshot == null) {
      throw new Web3InvalidInputException("snapshot is required");
    }
    String canonical =
        String.join(
            "|",
            Long.toString(snapshot.chainId()),
            normalizeAddress(snapshot.fromAddress()),
            normalizeAddress(snapshot.toAddress()),
            snapshot.valueWei().toString(),
            normalizeHex(snapshot.data()),
            Long.toString(snapshot.expectedNonce()),
            snapshot.gasLimit().toString(),
            snapshot.maxPriorityFeePerGas().toString(),
            snapshot.maxFeePerGas().toString());
    return Hash.sha3String(canonical);
  }

  private boolean sameSnapshot(UnsignedTxSnapshot expected, UnsignedTxSnapshot actual) {
    return expected.chainId() == actual.chainId()
        && normalizeAddress(expected.fromAddress()).equals(normalizeAddress(actual.fromAddress()))
        && normalizeAddress(expected.toAddress()).equals(normalizeAddress(actual.toAddress()))
        && expected.valueWei().compareTo(actual.valueWei()) == 0
        && normalizeHex(expected.data()).equals(normalizeHex(actual.data()))
        && expected.expectedNonce() == actual.expectedNonce()
        && expected.gasLimit().compareTo(actual.gasLimit()) == 0
        && expected.maxPriorityFeePerGas().compareTo(actual.maxPriorityFeePerGas()) == 0
        && expected.maxFeePerGas().compareTo(actual.maxFeePerGas()) == 0;
  }

  private String recoverSignerAddress(SignedRawTransaction signedRawTransaction) {
    try {
      return normalizeAddress(signedRawTransaction.getFrom());
    } catch (java.security.SignatureException e) {
      throw new Web3InvalidInputException("failed to recover signer address");
    }
  }

  private Transaction1559 transaction1559(SignedRawTransaction signedRawTransaction) {
    if (!(signedRawTransaction.getTransaction() instanceof Transaction1559 transaction1559)) {
      throw new Web3InvalidInputException("signedRawTransaction is not EIP-1559");
    }
    return transaction1559;
  }

  private static String normalizeAddress(String address) {
    if (address == null || address.isBlank()) {
      throw new Web3InvalidInputException("address is required");
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(address)).toLowerCase();
  }

  private static String normalizeHex(String value) {
    if (value == null || value.isBlank()) {
      return "0x";
    }
    return Numeric.prependHexPrefix(Numeric.cleanHexPrefix(value)).toLowerCase();
  }

  private static BigInteger nullSafe(BigInteger value) {
    return value == null ? BigInteger.ZERO : value;
  }
}
