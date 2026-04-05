package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.Web3ContractPort;
import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter.Eip1559TransferSigner;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

class Eip1559TransactionCodecAdapterTest {

  private final Eip1559TransactionCodecAdapter adapter = new Eip1559TransactionCodecAdapter();

  @Test
  void decodeAndVerify_returnsDecodedTransaction_whenSnapshotMatches() {
    String privateKey = "4f3edf983ac636a65a842ce7c78d9aa706d3b113bce036f3f4f6383a6d3f8d31";
    String fromAddress = Credentials.create(privateKey).getAddress().toLowerCase();
    String tokenContract = "0x" + "1".repeat(40);
    String receiver = "0x" + "2".repeat(40);

    Web3ContractPort.SignedTransaction signedTransaction =
        Eip1559TransferSigner.signTransfer(
            new Web3ContractPort.SignTransferCommand(
                privateKey,
                tokenContract,
                receiver,
                BigInteger.valueOf(1234),
                5L,
                11155111L,
                BigInteger.valueOf(80_000),
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(50_000_000_000L)));

    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            11155111L,
            fromAddress,
            tokenContract,
            BigInteger.ZERO,
            Eip1559TransferSigner.encodeTransferData(receiver, BigInteger.valueOf(1234)),
            5L,
            BigInteger.valueOf(80_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(50_000_000_000L));

    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        adapter.decodeAndVerify(
            signedTransaction.rawTx(), snapshot, adapter.computeFingerprint(snapshot));

    assertThat(decoded.signerAddress()).isEqualTo(fromAddress);
    assertThat(decoded.txHash()).isEqualTo(signedTransaction.txHash());
    assertThat(decoded.unsignedTxSnapshot()).isEqualTo(snapshot);
  }
}
