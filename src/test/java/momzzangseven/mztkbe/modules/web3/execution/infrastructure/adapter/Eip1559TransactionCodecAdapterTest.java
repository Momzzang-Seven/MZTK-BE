package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.shared.application.util.Erc20TransferCalldataEncoder;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

class Eip1559TransactionCodecAdapterTest {

  private final Eip1559TransactionCodecAdapter adapter = new Eip1559TransactionCodecAdapter();

  @Test
  void decodeAndVerify_returnsDecodedTransaction_whenSnapshotMatches() {
    String fromAddress =
        Credentials.create(ECKeyPair.create(BigInteger.TEN)).getAddress().toLowerCase();
    String tokenContract = "0x" + "1".repeat(40);
    String receiver = "0x" + "2".repeat(40);

    String calldata =
        Erc20TransferCalldataEncoder.encodeTransferData(receiver, BigInteger.valueOf(1234));
    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            11155111L,
            BigInteger.valueOf(5L),
            BigInteger.valueOf(80_000),
            tokenContract,
            BigInteger.ZERO,
            calldata,
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L));
    Credentials credentials = Credentials.create(ECKeyPair.create(BigInteger.TEN));
    byte[] signedBytes = TransactionEncoder.signMessage(rawTransaction, credentials);
    String rawTx = Numeric.toHexString(signedBytes);
    String txHash = Hash.sha3(rawTx);

    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            11155111L,
            fromAddress,
            tokenContract,
            BigInteger.ZERO,
            calldata,
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L));

    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        adapter.decodeAndVerify(rawTx, snapshot, adapter.computeFingerprint(snapshot));

    assertThat(decoded.signerAddress()).isEqualTo(fromAddress);
    assertThat(decoded.txHash()).isEqualTo(txHash);
    assertThat(decoded.unsignedTxSnapshot()).isEqualTo(snapshot);
  }
}
