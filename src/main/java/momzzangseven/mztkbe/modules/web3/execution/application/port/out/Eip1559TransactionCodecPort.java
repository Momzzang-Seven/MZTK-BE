package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;

public interface Eip1559TransactionCodecPort {

  record DecodedSignedTransaction(
      String rawTransaction,
      String txHash,
      String signerAddress,
      UnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint) {}

  String computeFingerprint(UnsignedTxSnapshot snapshot);

  DecodedSignedTransaction decodeAndVerify(
      String signedRawTransaction, UnsignedTxSnapshot unsignedTxSnapshot, String fingerprint);
}
