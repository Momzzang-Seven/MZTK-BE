package momzzangseven.mztkbe.modules.web3.shared.domain.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.SignatureRecoveryException;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Unit tests for {@link DerToVrsConverter}, {@link Vrs}, and {@link SignatureRecoveryException}.
 *
 * <p>All tests are pure unit tests with no Spring context, no database, and no mocks — {@code
 * DerToVrsConverter} is a static utility and all tests exercise it with real secp256k1 fixtures.
 */
@DisplayName("DerToVrsConverter 단위 테스트")
class DerToVrsConverterTest {

  /** secp256k1 curve order {@code n}. */
  private static final BigInteger CURVE_N = Sign.CURVE_PARAMS.getN();

  /** {@code n / 2} — EIP-2 low-s upper bound. */
  private static final BigInteger CURVE_HALF_N = CURVE_N.shiftRight(1);

  /** Fixed 32-byte digest used across happy-path tests. */
  private static final byte[] DIGEST_32 =
      Numeric.hexStringToByteArray(
          "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");

  private static ECKeyPair KEY_PAIR_V27;
  private static ECKeyPair KEY_PAIR_V28;
  private static byte[] DER_LOW_S_V27;
  private static byte[] DER_LOW_S_V28;
  private static String EXPECTED_ADDRESS_V27;
  private static String EXPECTED_ADDRESS_V28;
  private static BigInteger S_LOW_V27;
  private static BigInteger R_V27;

  /**
   * Initialises deterministic key-pair fixtures.
   *
   * <p>Searches for a private key whose corresponding secp256k1 signature over {@link #DIGEST_32}
   * recovers with {@code v = 27} (even-Y public key). The search increments a small integer
   * candidate until the expected v is produced, then repeats for v = 28.
   */
  @BeforeAll
  static void setUpFixtures() throws IOException {
    KEY_PAIR_V27 = findKeyPairForV(27);
    KEY_PAIR_V28 = findKeyPairForV(28);

    Sign.SignatureData sigDataV27 = Sign.signMessage(DIGEST_32, KEY_PAIR_V27, false);
    R_V27 = new BigInteger(1, sigDataV27.getR());
    BigInteger rawS = new BigInteger(1, sigDataV27.getS());
    S_LOW_V27 = rawS.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawS) : rawS;
    DER_LOW_S_V27 = encodeDer(R_V27, S_LOW_V27);

    Sign.SignatureData sigDataV28 = Sign.signMessage(DIGEST_32, KEY_PAIR_V28, false);
    BigInteger rV28 = new BigInteger(1, sigDataV28.getR());
    BigInteger rawSV28 = new BigInteger(1, sigDataV28.getS());
    BigInteger sLowV28 = rawSV28.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawSV28) : rawSV28;
    DER_LOW_S_V28 = encodeDer(rV28, sLowV28);

    EXPECTED_ADDRESS_V27 = "0x" + Keys.getAddress(KEY_PAIR_V27.getPublicKey());
    EXPECTED_ADDRESS_V28 = "0x" + Keys.getAddress(KEY_PAIR_V28.getPublicKey());
  }

  /**
   * Searches for an {@link ECKeyPair} whose signature over {@link #DIGEST_32} recovers with the
   * specified {@code targetV} (27 or 28).
   */
  private static ECKeyPair findKeyPairForV(int targetV) {
    for (int seed = 1; seed < 10_000; seed++) {
      ECKeyPair candidate = ECKeyPair.create(BigInteger.valueOf(seed));
      Sign.SignatureData sig = Sign.signMessage(DIGEST_32, candidate, false);
      int vRaw = sig.getV()[0] & 0xFF;
      // web3j SignatureData v can be 0/1 (recovery id) or 27/28 depending on version
      int effectiveV = vRaw < 27 ? vRaw + 27 : vRaw;
      if (effectiveV == targetV) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "Could not find key pair for v=" + targetV + " within 10000 tries");
  }

  /**
   * DER-encodes a {@code (r, s)} pair as an ASN.1 {@code SEQUENCE { INTEGER r, INTEGER s }}.
   *
   * @param r the r component
   * @param s the s component
   * @return DER-encoded bytes
   */
  static byte[] encodeDer(BigInteger r, BigInteger s) throws IOException {
    ASN1EncodableVector vec = new ASN1EncodableVector();
    vec.add(new ASN1Integer(r));
    vec.add(new ASN1Integer(s));
    return new DERSequence(vec).getEncoded();
  }

  // =========================================================================
  // Section A — happy-path round-trip
  // =========================================================================

  @Nested
  @DisplayName("A. 정상 변환 (happy-path round-trip)")
  class HappyPath {

    @Test
    @DisplayName("[M-1] 알려진 픽스처에서 v=27 반환")
    void convert_knownFixture_yieldsV27() {
      // given / when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, EXPECTED_ADDRESS_V27);

      // then
      assertThat(result.v()).isEqualTo((byte) 27);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
      assertThat(new BigInteger(1, result.r())).isEqualTo(R_V27);
      assertThat(new BigInteger(1, result.s())).isEqualTo(S_LOW_V27);
    }

    @Test
    @DisplayName("[M-2] 알려진 픽스처에서 v=28 반환")
    void convert_knownFixture_yieldsV28() {
      // given / when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V28, DIGEST_32, EXPECTED_ADDRESS_V28);

      // then
      assertThat(result.v()).isEqualTo((byte) 28);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }
  }

  // =========================================================================
  // Section B — low-s EIP-2 correction
  // =========================================================================

  @Nested
  @DisplayName("B. EIP-2 low-s 보정")
  class LowSCorrection {

    @Test
    @DisplayName("[M-3] high-s 입력을 low-s로 변환")
    void convert_highS_flipsToLowS() throws IOException {
      // given
      BigInteger sHigh = CURVE_N.subtract(S_LOW_V27);
      byte[] derHighS = encodeDer(R_V27, sHigh);

      // when
      Vrs result = DerToVrsConverter.convert(derHighS, DIGEST_32, EXPECTED_ADDRESS_V27);

      // then
      BigInteger returnedS = new BigInteger(1, result.s());
      assertThat(returnedS.compareTo(CURVE_HALF_N)).isLessThanOrEqualTo(0);
      assertThat(returnedS).isEqualTo(S_LOW_V27);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }

    @Test
    @DisplayName("[M-4] 이미 low-s인 입력은 변환하지 않음")
    void convert_alreadyLowS_unchanged() {
      // given / when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, EXPECTED_ADDRESS_V27);

      // then
      assertThat(new BigInteger(1, result.s())).isEqualTo(S_LOW_V27);
      assertThat(new BigInteger(1, result.s()).compareTo(CURVE_HALF_N)).isLessThanOrEqualTo(0);
    }

    @Test
    @DisplayName(
        "[M-5] s == CURVE_HALF_N (경계값)은 뒤집지 않음 — recovery 실패로 SignatureRecoveryException 발생")
    void convert_sEqualsHalfN_notFlipped_recoveryFails() throws IOException {
      // given — r=1 is not a valid secp256k1 point so recovery will fail;
      // the important thing is the exception message is the recovery failure,
      // NOT the DER parse failure (which would mean the boundary code-path was wrong).
      byte[] derHalfN = encodeDer(BigInteger.ONE, CURVE_HALF_N);

      // when / then
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(derHalfN, DIGEST_32, "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessageContaining("Failed to recover Ethereum address matching expected wallet")
          .hasMessageNotContaining("Malformed DER ECDSA signature");
    }
  }

  // =========================================================================
  // Section C — recovery-id fallback / failure
  // =========================================================================

  @Nested
  @DisplayName("C. recovery-id 실패 케이스")
  class RecoveryFailure {

    @Test
    @DisplayName("[M-6] 잘못된 digest → SignatureRecoveryException")
    void convert_wrongDigest_throwsRecoveryException() {
      // given
      byte[] wrongDigest = new byte[32]; // all zeros

      // when / then
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(DER_LOW_S_V27, wrongDigest, EXPECTED_ADDRESS_V27))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessageContaining("Failed to recover Ethereum address matching expected wallet")
          .satisfies(
              ex -> {
                SignatureRecoveryException sre = (SignatureRecoveryException) ex;
                assertThat(sre.getCode()).isEqualTo("WEB3_016");
              });
    }

    @Test
    @DisplayName("[M-7] 올바른 서명, 잘못된 expectedAddress → SignatureRecoveryException")
    void convert_wrongExpectedAddress_throwsRecoveryException() {
      // given
      String wrongAddress = "0x" + "b".repeat(40);

      // when / then
      assertThatThrownBy(() -> DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, wrongAddress))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessageContaining("Failed to recover Ethereum address matching expected wallet");
    }
  }

  // =========================================================================
  // Section D — address normalization
  // =========================================================================

  @Nested
  @DisplayName("D. 주소 정규화")
  class AddressNormalisation {

    @Test
    @DisplayName("[M-8] EIP-55 체크섬 주소 (mixed-case) 성공")
    void convert_eip55ChecksumAddress_succeeds() {
      // given
      String checksumAddress = Keys.toChecksumAddress(EXPECTED_ADDRESS_V27);

      // when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, checksumAddress);

      // then
      assertThat(result.v()).isEqualTo((byte) 27);
    }

    @Test
    @DisplayName("[M-9] 0x 접두사 없는 주소 성공")
    void convert_addressWithoutHexPrefix_succeeds() {
      // given
      String addressNoPrefix =
          Keys.getAddress(KEY_PAIR_V27.getPublicKey()); // 40-char lowercase hex

      // when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, addressNoPrefix);

      // then
      assertThat(result.v()).isEqualTo((byte) 27);
    }

    @Test
    @DisplayName("[M-10] null expectedAddress → SignatureRecoveryException")
    void convert_nullExpectedAddress_throwsRecoveryException() {
      // given — use a valid DER so that decodeAndNormalise() succeeds and
      // normaliseAddress(null) is reached (convert() calls DER decode first)
      assertThatThrownBy(() -> DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, null))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("expectedAddress must not be null")
          .satisfies(
              ex -> {
                SignatureRecoveryException sre = (SignatureRecoveryException) ex;
                assertThat(sre.getCode()).isEqualTo("WEB3_016");
                assertThat(sre.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              });
    }

    @Test
    @DisplayName("[M-11] 0x 접두사 + 전체 대문자 주소 성공")
    void convert_uppercaseHexAddress_succeeds() {
      // given
      String upperAddress = "0x" + Keys.getAddress(KEY_PAIR_V27.getPublicKey()).toUpperCase();

      // when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, upperAddress);

      // then
      assertThat(result.v()).isEqualTo((byte) 27);
    }
  }

  // =========================================================================
  // Section E — malformed DER inputs
  // =========================================================================

  @Nested
  @DisplayName("E. 잘못된 DER 입력")
  class MalformedDer {

    @Test
    @DisplayName("[M-12] 빈 바이트 배열 → SignatureRecoveryException (null sequence path)")
    void convert_emptyBytes_throwsSignatureRecoveryException() {
      // given / when / then
      // BouncyCastle's ASN1InputStream.readObject() returns null for an empty byte array,
      // so the sequence == null guard fires and produces the "2-element" error message.
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(new byte[0], new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("DER signature is not a 2-element ASN.1 sequence (r, s)");
    }

    @Test
    @DisplayName("[M-13] 임의의 쓰레기 바이트 → 'Malformed DER ECDSA signature'")
    void convert_garbageBytes_throwsMalformedDer() {
      // given
      byte[] garbage = {(byte) 0xFF, 0x12, 0x34, 0x56, 0x78, (byte) 0xAB, (byte) 0xCD};

      // when / then
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(garbage, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("Malformed DER ECDSA signature")
          .satisfies(ex -> assertThat(ex.getCause()).isNotNull());
    }

    @Test
    @DisplayName("[M-14] DER SEQUENCE에 요소 1개 → '2-element ASN.1 sequence' 오류")
    void convert_derSequenceOneElement_throwsSequenceError() throws IOException {
      // given
      byte[] derOneElement = new DERSequence(new ASN1Integer(BigInteger.TEN)).getEncoded();

      // when / then
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(derOneElement, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("DER signature is not a 2-element ASN.1 sequence (r, s)");
    }

    @Test
    @DisplayName("[M-15] DER SEQUENCE에 요소 3개 → '2-element ASN.1 sequence' 오류")
    void convert_derSequenceThreeElements_throwsSequenceError() throws IOException {
      // given
      ASN1EncodableVector v = new ASN1EncodableVector();
      v.add(new ASN1Integer(BigInteger.ONE));
      v.add(new ASN1Integer(BigInteger.TWO));
      v.add(new ASN1Integer(BigInteger.TEN));
      byte[] derThreeElements = new DERSequence(v).getEncoded();

      // when / then
      assertThatThrownBy(
              () ->
                  DerToVrsConverter.convert(derThreeElements, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("DER signature is not a 2-element ASN.1 sequence (r, s)");
    }

    @Test
    @DisplayName("[M-16] DER SEQUENCE에 OCTET STRING 요소 → ClassCastException 래핑")
    void convert_derSequenceNonIntegerElement_throwsMalformedDerWithCastCause() throws IOException {
      // given
      ASN1EncodableVector v = new ASN1EncodableVector();
      v.add(new DEROctetString(new byte[] {0x01, 0x02}));
      v.add(new ASN1Integer(BigInteger.TEN));
      byte[] derBadType = new DERSequence(v).getEncoded();

      // when / then
      assertThatThrownBy(
              () -> DerToVrsConverter.convert(derBadType, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("Malformed DER ECDSA signature")
          .satisfies(ex -> assertThat(ex.getCause()).isInstanceOf(ClassCastException.class));
    }

    @Test
    @DisplayName(
        "[M-17] null derSignature → SignatureRecoveryException(\"Malformed DER ECDSA signature\")")
    void convert_nullDerSignature_throwsMalformedDerException() {
      assertThatThrownBy(() -> DerToVrsConverter.convert(null, new byte[32], "0x" + "a".repeat(40)))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("Malformed DER ECDSA signature");
    }

    @Test
    @DisplayName("[M-17b] null digest → SignatureRecoveryException(digest length guard)")
    void convert_nullDigest_throwsSignatureRecoveryException() {
      assertThatThrownBy(() -> DerToVrsConverter.convert(DER_LOW_S_V27, null, EXPECTED_ADDRESS_V27))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("digest must be a non-null 32-byte array");
    }

    @Test
    @DisplayName("[M-17c] non-32-byte digest → SignatureRecoveryException(digest length guard)")
    void convert_shortDigest_throwsSignatureRecoveryException() {
      byte[] shortDigest = new byte[16];

      assertThatThrownBy(
              () -> DerToVrsConverter.convert(DER_LOW_S_V27, shortDigest, EXPECTED_ADDRESS_V27))
          .isInstanceOf(SignatureRecoveryException.class)
          .hasMessage("digest must be a non-null 32-byte array");
    }
  }

  // =========================================================================
  // Section F — Vrs byte-length invariants
  // =========================================================================

  @Nested
  @DisplayName("F. Vrs 바이트 길이 불변")
  class VrsByteLength {

    @Test
    @DisplayName("[M-18] r과 s가 정확히 32바이트")
    void convert_standardFixture_rAndSExactly32Bytes() {
      // given / when
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, EXPECTED_ADDRESS_V27);

      // then
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }

    @Test
    @DisplayName("[M-19] Numeric.toBytesPadded가 BigInteger.ONE을 31개 0x00 + 0x01로 패딩")
    void numericToBytesPadded_paddingBehavior_producesLeadingZeros() {
      // This directly validates the padding contract that DerToVrsConverter relies on.
      byte[] padded = Numeric.toBytesPadded(BigInteger.ONE, 32);

      assertThat(padded).hasSize(32);
      // First 31 bytes must be 0x00
      for (int i = 0; i < 31; i++) {
        assertThat(padded[i]).isEqualTo((byte) 0x00);
      }
      assertThat(padded[31]).isEqualTo((byte) 0x01);
    }

    @Test
    @DisplayName("[M-19b] Vrs는 생성자/접근자 양쪽에서 r·s를 방어적 복사")
    void vrs_defensiveCopy_protectsAgainstExternalMutation() {
      // given
      Vrs result = DerToVrsConverter.convert(DER_LOW_S_V27, DIGEST_32, EXPECTED_ADDRESS_V27);
      byte[] firstAccessR = result.r();

      // when — caller mutates the array returned by accessor
      firstAccessR[0] = (byte) 0xFF;
      byte[] secondAccessR = result.r();

      // then — internal state is unchanged
      assertThat(secondAccessR[0]).isNotEqualTo((byte) 0xFF);
      assertThat(secondAccessR).isNotSameAs(firstAccessR);
    }

    @Test
    @DisplayName("[M-19c] Vrs.equals는 내용 기반(Arrays.equals), hashCode는 일관됨")
    void vrs_equalsAndHashCode_isContentBased() {
      byte[] r = new byte[32];
      byte[] s = new byte[32];
      r[31] = 1;
      s[31] = 2;
      Vrs a = new Vrs(r, s, (byte) 27);
      Vrs b = new Vrs(r.clone(), s.clone(), (byte) 27);
      Vrs c = new Vrs(r, s, (byte) 28);

      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
      assertThat(a).isNotEqualTo(c);
      assertThat(a.toString()).contains("v=27");
    }
  }

  // =========================================================================
  // Section G — utility-class guard
  // =========================================================================

  @Nested
  @DisplayName("G. 유틸리티 클래스 생성 금지")
  class UtilityClassGuard {

    @Test
    @DisplayName("[M-23] DerToVrsConverter는 리플렉션으로도 인스턴스화 불가")
    void derToVrsConverter_reflectiveInstantiation_throwsUnsupportedOperation()
        throws NoSuchMethodException {
      // given
      Constructor<?> ctor = DerToVrsConverter.class.getDeclaredConstructors()[0];
      ctor.setAccessible(true);

      // when / then
      assertThatThrownBy(ctor::newInstance)
          .isInstanceOf(InvocationTargetException.class)
          .satisfies(
              ex ->
                  assertThat(((InvocationTargetException) ex).getTargetException())
                      .isInstanceOf(UnsupportedOperationException.class)
                      .hasMessage("Utility class"));
    }
  }

  // =========================================================================
  // Section H — digest edge cases
  // =========================================================================

  @Nested
  @DisplayName("H. digest 엣지 케이스")
  class DigestEdgeCases {

    @Test
    @DisplayName("[M-24] 32바이트 올-제로 digest로 서명 성공")
    void convert_allZeroDigest_succeeds() throws IOException {
      // given
      byte[] zeroDigest = new byte[32];
      ECKeyPair pair = findKeyPairForDigest(KEY_PAIR_V27, zeroDigest, 27);
      Sign.SignatureData sig = Sign.signMessage(zeroDigest, pair, false);
      BigInteger r = new BigInteger(1, sig.getR());
      BigInteger rawS = new BigInteger(1, sig.getS());
      BigInteger s = rawS.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawS) : rawS;
      byte[] der = encodeDer(r, s);
      String address = "0x" + Keys.getAddress(pair.getPublicKey());

      // when
      Vrs result = DerToVrsConverter.convert(der, zeroDigest, address);

      // then
      assertThat((int) result.v()).isIn(27, 28);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }

    @Test
    @DisplayName("[M-25] 32바이트 올-0xFF digest로 서명 성공")
    void convert_allMaxByteDigest_succeeds() throws IOException {
      // given
      byte[] maxDigest = new byte[32];
      for (int i = 0; i < 32; i++) {
        maxDigest[i] = (byte) 0xFF;
      }
      ECKeyPair pair = findAnyKeyPairForDigest(maxDigest);
      Sign.SignatureData sig = Sign.signMessage(maxDigest, pair, false);
      BigInteger r = new BigInteger(1, sig.getR());
      BigInteger rawS = new BigInteger(1, sig.getS());
      BigInteger s = rawS.compareTo(CURVE_HALF_N) > 0 ? CURVE_N.subtract(rawS) : rawS;
      byte[] der = encodeDer(r, s);
      String address = "0x" + Keys.getAddress(pair.getPublicKey());

      // when
      Vrs result = DerToVrsConverter.convert(der, maxDigest, address);

      // then
      assertThat((int) result.v()).isIn(27, 28);
      assertThat(result.r()).hasSize(32);
      assertThat(result.s()).hasSize(32);
    }

    /**
     * Finds a key pair that recovers with the given target v for a specific digest, or falls back
     * to the provided pair if no specific v is required.
     */
    private ECKeyPair findKeyPairForDigest(ECKeyPair preferred, byte[] digest, int targetV) {
      Sign.SignatureData sig = Sign.signMessage(digest, preferred, false);
      int vRaw = sig.getV()[0] & 0xFF;
      int effectiveV = vRaw < 27 ? vRaw + 27 : vRaw;
      if (effectiveV == targetV) {
        return preferred;
      }
      // Try a few alternatives
      for (int seed = 1; seed < 10_000; seed++) {
        ECKeyPair candidate = ECKeyPair.create(BigInteger.valueOf(seed));
        Sign.SignatureData s2 = Sign.signMessage(digest, candidate, false);
        int v2Raw = s2.getV()[0] & 0xFF;
        int ev = v2Raw < 27 ? v2Raw + 27 : v2Raw;
        if (ev == targetV) {
          return candidate;
        }
      }
      return preferred;
    }

    /** Returns any valid key pair for the given digest (any v). */
    private ECKeyPair findAnyKeyPairForDigest(byte[] digest) {
      return ECKeyPair.create(BigInteger.valueOf(1));
    }
  }
}
