package momzzangseven.mztkbe.modules.web3.eip7702.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.SignDigestPort;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.AuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.Eip7702Fields;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.TreasurySigner;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignEip7702TxService")
class SignEip7702TxServiceTest {

  @Mock private SignDigestPort signDigestPort;

  @InjectMocks private SignEip7702TxService service;

  private static byte[] filled32(byte val) {
    byte[] buf = new byte[32];
    Arrays.fill(buf, val);
    return buf;
  }

  private static AuthorizationTuple authTuple() {
    return new AuthorizationTuple(
        10L,
        "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        BigInteger.ZERO,
        (byte) 0,
        filled32((byte) 0x11),
        filled32((byte) 0x22));
  }

  private static Eip7702Fields fixture() {
    return new Eip7702Fields(
        10L,
        BigInteger.ONE,
        BigInteger.valueOf(1_000_000_000L),
        BigInteger.valueOf(2_000_000_000L),
        BigInteger.valueOf(120_000L),
        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        BigInteger.ZERO,
        "0x",
        List.of(authTuple()));
  }

  private static TreasurySigner signer() {
    return new TreasurySigner(
        "sponsor-treasury", "alias/sponsor-treasury", "0x3333333333333333333333333333333333333333");
  }

  private static byte[] padded(int low) {
    byte[] out = new byte[32];
    out[31] = (byte) low;
    return out;
  }

  @Test
  @DisplayName("encoder digest 를 SignDigestPort 로 위임하고 signer 의 kmsKeyId/address 를 전달한다")
  void sign_buildsDigestAndCallsPortWithKmsKey() {
    Eip7702Fields fields = fixture();
    TreasurySigner signer = signer();

    Vrs canned = new Vrs(padded(1), padded(2), (byte) 27);
    when(signDigestPort.signDigest(
            eq(signer.kmsKeyId()), any(byte[].class), eq(signer.walletAddress())))
        .thenReturn(canned);

    service.sign(fields, signer);

    ArgumentCaptor<byte[]> digestCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(signDigestPort)
        .signDigest(eq(signer.kmsKeyId()), digestCaptor.capture(), eq(signer.walletAddress()));

    byte[] expectedDigest = Eip7702TxEncoder.digest(Eip7702TxEncoder.buildUnsigned(fields));
    assertThat(digestCaptor.getValue()).isEqualTo(expectedDigest);
  }

  @Test
  @DisplayName("assembleSigned 결과의 rawTx/txHash 를 그대로 반환한다")
  void sign_returnsAssembledSignedTx() {
    Eip7702Fields fields = fixture();
    TreasurySigner signer = signer();

    Vrs canned = new Vrs(padded(1), padded(2), (byte) 27);
    when(signDigestPort.signDigest(
            eq(signer.kmsKeyId()), any(byte[].class), eq(signer.walletAddress())))
        .thenReturn(canned);

    SignedTx out = service.sign(fields, signer);

    SignedTx expected = Eip7702TxEncoder.assembleSigned(fields, canned);
    assertThat(out.rawTx()).isEqualTo(expected.rawTx());
    assertThat(out.txHash()).isEqualTo(expected.txHash());
    assertThat(out.rawTx()).startsWith("0x04");
  }

  @Test
  @DisplayName("signer 가 null 이면 NPE 를 던진다")
  void sign_throws_whenSignerIsNull() {
    Eip7702Fields fields = fixture();

    assertThatThrownBy(() -> service.sign(fields, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("SignDigestPort 가 KmsSignFailedException 을 던지면 그대로 전파한다")
  void sign_propagates_portFailure() {
    Eip7702Fields fields = fixture();
    TreasurySigner signer = signer();

    KmsSignFailedException expected = new KmsSignFailedException("kms throttled");
    when(signDigestPort.signDigest(
            eq(signer.kmsKeyId()), any(byte[].class), eq(signer.walletAddress())))
        .thenThrow(expected);

    assertThatThrownBy(() -> service.sign(fields, signer))
        .isExactlyInstanceOf(KmsSignFailedException.class)
        .isSameAs(expected);
  }
}
