package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.Eip1559TransactionCodecAdapter;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.utils.Numeric;

class ExecutionEip1559SigningAdapterTest {

  private final ExecutionEip1559SigningAdapter adapter = new ExecutionEip1559SigningAdapter();
  private final Eip1559TransactionCodecPort codec = new Eip1559TransactionCodecAdapter();

  @Test
  void sign_returnsSignedRawTransactionForGenericContractCall() {
    String signingKeyHex = Numeric.toHexStringNoPrefixZeroPadded(BigInteger.valueOf(77L), 64);
    String signerAddress =
        Credentials.create(ECKeyPair.create(BigInteger.valueOf(77L))).getAddress().toLowerCase();
    String contractAddress = "0x" + "1".repeat(40);
    String calldata = "0x12345678";

    ExecutionEip1559SigningPort.SignedTransaction signed =
        adapter.sign(
            new ExecutionEip1559SigningPort.SignCommand(
                11155111L,
                9L,
                BigInteger.valueOf(210_000),
                contractAddress,
                BigInteger.ZERO,
                calldata,
                BigInteger.valueOf(2_000_000_000L),
                BigInteger.valueOf(40_000_000_000L),
                signingKeyHex));

    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            11155111L,
            signerAddress,
            contractAddress,
            BigInteger.ZERO,
            calldata,
            9L,
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(40_000_000_000L));

    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        codec.decodeAndVerify(
            signed.rawTransaction(), snapshot, codec.computeFingerprint(snapshot));

    assertThat(decoded.signerAddress()).isEqualTo(signerAddress);
    assertThat(decoded.txHash()).isEqualTo(signed.txHash());
    assertThat(decoded.unsignedTxSnapshot()).isEqualTo(snapshot);
  }
}
