package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.Collectors;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

/**
 * Unit tests for {@link KmsSignerAdapter} — M-55 through M-60.
 *
 * <p>Uses {@link MockitoExtension} with a mocked {@link KmsClient}. Adapter is instantiated
 * directly via constructor injection (no Spring context).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KmsSignerAdapter 단위 테스트")
class KmsSignerAdapterTest {

  private static final BigInteger CURVE_N = Sign.CURVE_PARAMS.getN();
  private static final BigInteger CURVE_HALF_N = CURVE_N.shiftRight(1);

  /** Fixed 32-byte digest used across all tests. */
  private static final byte[] FIXTURE_DIGEST =
      Numeric.hexStringToByteArray(
          "deadbeef01234567deadbeef01234567deadbeef01234567deadbeef01234567");

  private static final String KMS_KEY_ID =
      "arn:aws:kms:us-east-1:123456789012:key/test-fixture-key";
  private static final String SPECIFIC_KEY_ID =
      "arn:aws:kms:us-east-1:123456789012:key/specific-key-id";

  private static ECKeyPair FIXTURE_KEY;
  private static String FIXTURE_ADDRESS;
  private static byte[] DER_BYTES;
  private static SignResponse STUB_RESPONSE;
  private static BigInteger FIXTURE_R;

  @Mock private KmsClient kmsClient;

  @BeforeAll
  static void setUpFixtures() throws IOException {
    // Find a deterministic key that produces a valid signature over FIXTURE_DIGEST
    FIXTURE_KEY = ECKeyPair.create(BigInteger.valueOf(12345L));
    FIXTURE_ADDRESS = "0x" + Keys.getAddress(FIXTURE_KEY.getPublicKey());

    Sign.SignatureData rawSig = Sign.signMessage(FIXTURE_DIGEST, FIXTURE_KEY, false);
    FIXTURE_R = new BigInteger(1, rawSig.getR());
    BigInteger rawS = new BigInteger(1, rawSig.getS());
    BigInteger sLow = rawS.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawS) : rawS;

    DER_BYTES = encodeDer(FIXTURE_R, sLow);
    STUB_RESPONSE = SignResponse.builder().signature(SdkBytes.fromByteArray(DER_BYTES)).build();
  }

  /** DER-encodes a {@code (r, s)} pair as {@code SEQUENCE { INTEGER r, INTEGER s }}. */
  static byte[] encodeDer(BigInteger r, BigInteger s) throws IOException {
    ASN1EncodableVector vec = new ASN1EncodableVector();
    vec.add(new ASN1Integer(r));
    vec.add(new ASN1Integer(s));
    return new DERSequence(vec).getEncoded();
  }

  // =========================================================================
  // A. Happy-path
  // =========================================================================

  @Nested
  @DisplayName("A. 정상 경로 (happy-path)")
  class HappyPath {

    @Test
    @DisplayName("[M-55] signDigest — 유효한 DER stub → Vrs 반환, r/s/v 정합성 확인")
    void signDigest_validDerStub_returnsWellFormedVrs() {
      // given
      when(kmsClient.sign(any(SignRequest.class))).thenReturn(STUB_RESPONSE);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);

      // when
      Vrs result = adapter.signDigest(KMS_KEY_ID, FIXTURE_DIGEST, FIXTURE_ADDRESS);

      // then
      assertThat(result).isNotNull();
      assertThat((int) result.v()).isIn(27, 28);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
      assertThat(new BigInteger(1, result.r())).isEqualTo(FIXTURE_R);
      assertThat(new BigInteger(1, result.s()).compareTo(CURVE_HALF_N)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName("[M-58] signDigest — SignRequest 필드 검증 (ArgumentCaptor)")
    void signDigest_capturedRequest_hasCorrectFields() {
      // given
      when(kmsClient.sign(any(SignRequest.class))).thenReturn(STUB_RESPONSE);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);
      ArgumentCaptor<SignRequest> captor = ArgumentCaptor.forClass(SignRequest.class);

      // when
      adapter.signDigest(SPECIFIC_KEY_ID, FIXTURE_DIGEST, FIXTURE_ADDRESS);

      // then
      verify(kmsClient).sign(captor.capture());
      SignRequest captured = captor.getValue();
      assertThat(captured.keyId()).isEqualTo(SPECIFIC_KEY_ID);
      assertThat(captured.messageType()).isEqualTo(MessageType.DIGEST);
      assertThat(captured.signingAlgorithm()).isEqualTo(SigningAlgorithmSpec.ECDSA_SHA_256);
      assertThat(Arrays.equals(captured.message().asByteArray(), FIXTURE_DIGEST)).isTrue();
    }

    @Test
    @DisplayName("[M-59] signDigest — kmsClient.sign()을 정확히 1회만 호출 (내부 재시도 없음)")
    void signDigest_callsKmsClientExactlyOnce() {
      // given
      when(kmsClient.sign(any(SignRequest.class))).thenReturn(STUB_RESPONSE);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);

      // when
      adapter.signDigest(KMS_KEY_ID, FIXTURE_DIGEST, FIXTURE_ADDRESS);

      // then
      verify(kmsClient, times(1)).sign(any(SignRequest.class));
      verifyNoMoreInteractions(kmsClient);
    }
  }

  // =========================================================================
  // B. 오류 전파
  // =========================================================================

  @Nested
  @DisplayName("B. 오류 전파 (error propagation)")
  class ErrorPropagation {

    @Test
    @DisplayName("[M-56] signDigest — KmsException → KmsSignFailedException (원인 보존)")
    void signDigest_kmsExceptionThrown_wrapsInKmsSignFailedException() {
      // given
      var kmsEx = KmsInvalidStateException.builder().message("key is pending deletion").build();
      when(kmsClient.sign(any(SignRequest.class))).thenThrow(kmsEx);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);

      // when / then
      assertThatThrownBy(() -> adapter.signDigest(KMS_KEY_ID, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(KmsSignFailedException.class)
          .hasMessageContaining("KMS Sign API failed")
          .satisfies(
              ex -> {
                KmsSignFailedException signEx = (KmsSignFailedException) ex;
                assertThat(signEx.getCause()).isSameAs(kmsEx);
                assertThat(signEx.getCode()).isEqualTo("WEB3_017");
              });
    }

    @Test
    @DisplayName("[M-57] signDigest — DER가 잘못된 address에서 복구 → SignatureRecoveryException 전파")
    void signDigest_wrongExpectedAddress_propagatesSignatureRecoveryException() {
      // given — use the valid DER stub but supply a wrong address
      when(kmsClient.sign(any(SignRequest.class))).thenReturn(STUB_RESPONSE);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);
      String wrongAddress = "0x" + "b".repeat(40);

      // when / then
      assertThatThrownBy(() -> adapter.signDigest(KMS_KEY_ID, FIXTURE_DIGEST, wrongAddress))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessageContaining("Failed to recover Ethereum address matching expected wallet")
          .satisfies(
              ex -> assertThat(((SignatureRecoveryException) ex).getCode()).isEqualTo("WEB3_016"));
    }
  }

  // =========================================================================
  // C. 로깅 위생
  // =========================================================================

  @Nested
  @DisplayName("C. 로깅 위생 (logging hygiene)")
  class LoggingHygiene {

    private ListAppender<ILoggingEvent> listAppender;
    private ch.qos.logback.classic.Logger logger;

    @AfterEach
    void detachAppender() {
      if (logger != null && listAppender != null) {
        logger.detachAppender(listAppender);
      }
    }

    @Test
    @DisplayName("[M-60] signDigest — KmsException 발생 시 WARN 로그에 digest/signature hex 미포함")
    void signDigest_onKmsException_warnLogDoesNotContainDigestOrSignatureHex() {
      // given
      var kmsEx = KmsInvalidStateException.builder().message("invalid state").build();
      when(kmsClient.sign(any(SignRequest.class))).thenThrow(kmsEx);
      KmsSignerAdapter adapter = new KmsSignerAdapter(kmsClient);

      // attach log appender
      logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(KmsSignerAdapter.class);
      listAppender = new ListAppender<>();
      listAppender.start();
      logger.addAppender(listAppender);

      // recognizable pattern in digest
      byte[] recognizableDigest = Numeric.hexStringToByteArray("aabbccdd" + "00".repeat(28));

      // when / then — exception expected
      assertThatThrownBy(
              () -> adapter.signDigest(KMS_KEY_ID, recognizableDigest, "0x" + "a".repeat(40)))
          .isInstanceOf(KmsSignFailedException.class);

      // assert log content
      String allMessages =
          listAppender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(Collectors.joining(" "));

      // must contain kmsKeyId and awsErrorCode fields
      assertThat(allMessages).contains("kmsKeyId=");
      assertThat(allMessages).contains("awsErrorCode=");

      // must NOT contain any long hex run (> 32 chars) that would indicate digest or DER bytes
      assertThat(allMessages).doesNotContainPattern("[0-9a-fA-F]{33,}");
    }
  }
}
