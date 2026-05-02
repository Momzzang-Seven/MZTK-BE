package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.Eip1559TransactionCodecPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip1559SigningPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter.Eip1559TransactionCodecAdapter;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.domain.encoder.Eip1559TxEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionEip1559SigningAdapter (KMS-backed)")
class ExecutionEip1559SigningAdapterTest {

  private static final long CHAIN_ID = 11155111L;
  private static final long NONCE = 9L;
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(210_000);
  private static final BigInteger MAX_PRIORITY_FEE = BigInteger.valueOf(2_000_000_000L);
  private static final BigInteger MAX_FEE = BigInteger.valueOf(40_000_000_000L);
  private static final String CONTRACT_ADDRESS = "0x" + "1".repeat(40);
  private static final String CALLDATA = "0x12345678";
  private static final String KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String WALLET_ALIAS = "test-sponsor";

  @Mock private SignDigestPort signDigestPort;

  @Test
  @DisplayName(
      "sign builds digest from Eip1559Fields and forwards (kmsKeyId, digest, walletAddress) to SignDigestPort")
  void sign_buildsDigestAndDelegatesToSignDigestPort() throws Exception {
    BigInteger privateKey = BigInteger.valueOf(77L);
    ECKeyPair keyPair = ECKeyPair.create(privateKey);
    String walletAddress = Credentials.create(keyPair).getAddress().toLowerCase();

    Eip1559TxEncoder.Eip1559Fields fields =
        new Eip1559TxEncoder.Eip1559Fields(
            CHAIN_ID,
            NONCE,
            MAX_PRIORITY_FEE,
            MAX_FEE,
            GAS_LIMIT,
            CONTRACT_ADDRESS,
            BigInteger.ZERO,
            CALLDATA);
    byte[] expectedUnsigned = Eip1559TxEncoder.buildUnsigned(fields);
    byte[] expectedDigest = Eip1559TxEncoder.digest(expectedUnsigned);

    Sign.SignatureData realSig = Sign.signMessage(expectedDigest, keyPair, false);
    Vrs realVrs = new Vrs(realSig.getR(), realSig.getS(), realSig.getV()[0]);
    when(signDigestPort.signDigest(eq(KMS_KEY_ID), any(byte[].class), eq(walletAddress)))
        .thenReturn(realVrs);

    ExecutionEip1559SigningAdapter adapter = new ExecutionEip1559SigningAdapter(signDigestPort);

    ExecutionEip1559SigningPort.SignedTransaction signed =
        adapter.sign(
            new ExecutionEip1559SigningPort.SignCommand(
                CHAIN_ID,
                NONCE,
                GAS_LIMIT,
                CONTRACT_ADDRESS,
                BigInteger.ZERO,
                CALLDATA,
                MAX_PRIORITY_FEE,
                MAX_FEE,
                new TreasurySigner(WALLET_ALIAS, KMS_KEY_ID, walletAddress)));

    Eip1559TxEncoder.SignedTx expected = Eip1559TxEncoder.assembleSigned(fields, realVrs);
    assertThat(signed.rawTransaction()).isEqualTo(expected.rawTx());
    assertThat(signed.txHash()).isEqualTo(expected.txHash());

    ArgumentCaptor<byte[]> digestCaptor = ArgumentCaptor.forClass(byte[].class);
    org.mockito.Mockito.verify(signDigestPort)
        .signDigest(eq(KMS_KEY_ID), digestCaptor.capture(), eq(walletAddress));
    assertThat(digestCaptor.getValue()).isEqualTo(expectedDigest);

    Eip1559TransactionCodecPort codec = new Eip1559TransactionCodecAdapter();
    UnsignedTxSnapshot snapshot =
        new UnsignedTxSnapshot(
            CHAIN_ID,
            walletAddress,
            CONTRACT_ADDRESS,
            BigInteger.ZERO,
            CALLDATA,
            NONCE,
            GAS_LIMIT,
            MAX_PRIORITY_FEE,
            MAX_FEE);
    Eip1559TransactionCodecPort.DecodedSignedTransaction decoded =
        codec.decodeAndVerify(
            signed.rawTransaction(), snapshot, codec.computeFingerprint(snapshot));
    assertThat(decoded.signerAddress()).isEqualTo(walletAddress);
    assertThat(decoded.txHash()).isEqualTo(signed.txHash());
  }
}
