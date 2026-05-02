package momzzangseven.mztkbe.modules.web3.shared.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignDigestResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxCommand;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignEip1559TxResult;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.SignDigestUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.Eip1559TxCodecPort;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignEip1559TxService (shared)")
class SignEip1559TxServiceTest {

  private static final String KMS_KEY_ID = "alias/reward-treasury";
  private static final String SIGNER_ADDRESS = "0x3333333333333333333333333333333333333333";

  @Mock private Eip1559TxCodecPort codec;
  @Mock private SignDigestUseCase signDigestUseCase;

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
  @DisplayName("codec build → digest → SignDigestUseCase 위임 → assemble 순으로 호출한다")
  void sign_orchestratesCodecAndDigestUseCase() {
    Eip1559Fields fields = fixture();
    byte[] unsigned = new byte[] {(byte) 0x02, 0x01};
    byte[] digestBytes = padded(9);
    SignedTx assembled = new SignedTx("0xdeadbeef", "0x" + "d".repeat(64));

    when(codec.buildUnsigned(fields)).thenReturn(unsigned);
    when(codec.digest(unsigned)).thenReturn(digestBytes);
    when(signDigestUseCase.execute(any(SignDigestCommand.class)))
        .thenReturn(new SignDigestResult(padded(1), padded(2), (byte) 27));
    when(codec.assembleSigned(any(Eip1559Fields.class), any(Vrs.class))).thenReturn(assembled);

    SignEip1559TxResult result =
        service.sign(new SignEip1559TxCommand(fields, KMS_KEY_ID, SIGNER_ADDRESS));

    ArgumentCaptor<SignDigestCommand> digestCmdCaptor =
        ArgumentCaptor.forClass(SignDigestCommand.class);
    verify(signDigestUseCase).execute(digestCmdCaptor.capture());
    SignDigestCommand sentToDigest = digestCmdCaptor.getValue();
    assertThat(sentToDigest.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(sentToDigest.expectedAddress()).isEqualTo(SIGNER_ADDRESS);
    assertThat(sentToDigest.digest()).isEqualTo(digestBytes);

    ArgumentCaptor<Vrs> vrsCaptor = ArgumentCaptor.forClass(Vrs.class);
    verify(codec).assembleSigned(any(Eip1559Fields.class), vrsCaptor.capture());
    Vrs vrs = vrsCaptor.getValue();
    assertThat(vrs.v()).isEqualTo((byte) 27);

    assertThat(result.signedTx().rawTx()).isEqualTo(assembled.rawTx());
    assertThat(result.signedTx().txHash()).isEqualTo(assembled.txHash());
  }

  @Test
  @DisplayName("SignDigestUseCase 가 KmsSignFailedException 을 던지면 그대로 전파한다")
  void sign_propagatesKmsSignFailedExceptionUnchanged() {
    Eip1559Fields fields = fixture();
    when(codec.buildUnsigned(fields)).thenReturn(new byte[] {(byte) 0x02});
    when(codec.digest(any(byte[].class))).thenReturn(padded(9));
    KmsSignFailedException expected = new KmsSignFailedException("kms throttled");
    when(signDigestUseCase.execute(any(SignDigestCommand.class))).thenThrow(expected);

    assertThatThrownBy(
            () ->
                service.sign(new SignEip1559TxCommand(fields, KMS_KEY_ID, SIGNER_ADDRESS)))
        .isExactlyInstanceOf(KmsSignFailedException.class)
        .isSameAs(expected);
  }

  @Test
  @DisplayName("SignDigestUseCase 가 SignatureRecoveryException 을 던지면 그대로 전파한다")
  void sign_propagatesSignatureRecoveryExceptionUnchanged() {
    Eip1559Fields fields = fixture();
    when(codec.buildUnsigned(fields)).thenReturn(new byte[] {(byte) 0x02});
    when(codec.digest(any(byte[].class))).thenReturn(padded(9));
    SignatureRecoveryException expected = new SignatureRecoveryException("recover mismatch");
    when(signDigestUseCase.execute(any(SignDigestCommand.class))).thenThrow(expected);

    assertThatThrownBy(
            () ->
                service.sign(new SignEip1559TxCommand(fields, KMS_KEY_ID, SIGNER_ADDRESS)))
        .isExactlyInstanceOf(SignatureRecoveryException.class)
        .isSameAs(expected);
  }
}
