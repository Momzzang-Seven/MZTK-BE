package momzzangseven.mztkbe.modules.web3.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignEip1559TxService")
class SignEip1559TxServiceTest {

  @Mock private SignDigestPort signDigestPort;

  @InjectMocks private SignEip1559TxService service;

  private static Eip1559Fields fixture() {
    return new Eip1559Fields(
        10L,
        7L,
        BigInteger.valueOf(1_000_000_000L),
        BigInteger.valueOf(2_000_000_000L),
        BigInteger.valueOf(60_000L),
        "0x1111111111111111111111111111111111111111",
        BigInteger.ZERO,
        "0xa9059cbb0000000000000000000000002222222222222222222222222222222222222222"
            + "00000000000000000000000000000000000000000000000000000000000003e8");
  }

  private static byte[] padded(int low) {
    byte[] out = new byte[32];
    out[31] = (byte) low;
    return out;
  }

  @Test
  @DisplayName("encoder digest 를 SignDigestPort 로 위임하고 signed envelope 을 조립한다")
  void sign_delegatesDigestAndAssembles() {
    Eip1559Fields fields = fixture();
    String kmsKeyId = "alias/reward-treasury";
    String signerAddress = "0x3333333333333333333333333333333333333333";

    Vrs canned = new Vrs(padded(1), padded(2), (byte) 27);
    when(signDigestPort.signDigest(eq(kmsKeyId), any(byte[].class), eq(signerAddress)))
        .thenReturn(canned);

    SignedTx out = service.sign(fields, kmsKeyId, signerAddress);

    ArgumentCaptor<byte[]> digestCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(signDigestPort).signDigest(eq(kmsKeyId), digestCaptor.capture(), eq(signerAddress));

    byte[] expectedDigest = Eip1559TxEncoder.digest(Eip1559TxEncoder.buildUnsigned(fields));
    assertThat(digestCaptor.getValue()).isEqualTo(expectedDigest);

    SignedTx expected = Eip1559TxEncoder.assembleSigned(fields, canned);
    assertThat(out.rawTx()).isEqualTo(expected.rawTx());
    assertThat(out.txHash()).isEqualTo(expected.txHash());
    assertThat(out.rawTx()).startsWith("0x02");
  }

  @Test
  @DisplayName("SignDigestPort 가 KmsSignFailedException 을 던지면 그대로 전파한다")
  void sign_propagatesKmsSignFailedExceptionUnchanged() {
    Eip1559Fields fields = fixture();
    String kmsKeyId = "alias/reward-treasury";
    String signerAddress = "0x3333333333333333333333333333333333333333";

    KmsSignFailedException expected = new KmsSignFailedException("kms throttled");
    when(signDigestPort.signDigest(eq(kmsKeyId), any(byte[].class), eq(signerAddress)))
        .thenThrow(expected);

    assertThatThrownBy(() -> service.sign(fields, kmsKeyId, signerAddress))
        .isExactlyInstanceOf(KmsSignFailedException.class)
        .isSameAs(expected);
  }
}
