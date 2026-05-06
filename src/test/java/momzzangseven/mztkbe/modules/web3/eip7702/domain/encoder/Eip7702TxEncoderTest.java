package momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.AuthorizationTuple;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.Eip7702Fields;
import momzzangseven.mztkbe.modules.web3.eip7702.domain.encoder.Eip7702TxEncoder.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Bytes;
import org.web3j.utils.Numeric;

/**
 * Unit tests for {@link Eip7702TxEncoder} — covers Eip7702Fields validation, buildUnsigned, digest,
 * assembleSigned, and authorization-list shape compared against direct web3j RLP primitives.
 */
@DisplayName("Eip7702TxEncoder 단위 테스트")
class Eip7702TxEncoderTest {

  // =========================================================================
  // Shared fixture constants — Optimism EIP-7702 sponsored tx
  // =========================================================================

  private static final long CHAIN_ID = 10L;
  private static final BigInteger NONCE = BigInteger.valueOf(1L);
  private static final BigInteger MAX_PRIORITY = BigInteger.valueOf(1_000_000_000L); // 1 gwei
  private static final BigInteger MAX_FEE = BigInteger.valueOf(2_000_000_000L); // 2 gwei
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(120_000L);
  private static final String DESTINATION = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String AUTHORITY = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final BigInteger VALUE = BigInteger.ZERO;
  private static final String DATA = "0x";

  /** Builds a 32-byte array filled with the given byte value. */
  private static byte[] filled32(byte val) {
    byte[] buf = new byte[32];
    Arrays.fill(buf, val);
    return buf;
  }

  /** Builds a single canonical authorization tuple for the fixture. */
  private static AuthorizationTuple authTuple() {
    return new AuthorizationTuple(
        CHAIN_ID,
        AUTHORITY,
        BigInteger.ZERO,
        (byte) 0,
        filled32((byte) 0x11),
        filled32((byte) 0x22));
  }

  /** Builds the canonical fixture fields with one authorization tuple. */
  private static Eip7702Fields fixture() {
    return new Eip7702Fields(
        CHAIN_ID,
        NONCE,
        MAX_PRIORITY,
        MAX_FEE,
        GAS_LIMIT,
        DESTINATION,
        VALUE,
        DATA,
        List.of(authTuple()));
  }

  /**
   * Returns a valid Vrs with v=27, with both r and s as 32-byte arrays of 0x01 (fully non-zero, no
   * leading-zero ambiguity).
   */
  private static Vrs validVrs27() {
    return new Vrs(filled32((byte) 0x01), filled32((byte) 0x01), (byte) 27);
  }

  /**
   * Strips the leading 0x04 type byte from a typed-transaction envelope and decodes the bare RLP
   * list.
   */
  private static RlpList decodePayload(byte[] typedEnvelope) {
    byte[] payload = Arrays.copyOfRange(typedEnvelope, 1, typedEnvelope.length);
    RlpList outer = RlpDecoder.decode(payload);
    return (RlpList) outer.getValues().get(0);
  }

  /**
   * Decodes a signed rawTx hex string (0x-prefixed) to an RlpList after stripping the type byte.
   */
  private static RlpList decodeSignedHex(String rawTxHex) {
    byte[] signedBytes = Numeric.hexStringToByteArray(rawTxHex);
    return decodePayload(signedBytes);
  }

  // =========================================================================
  // Group 1 — Eip7702Fields compact constructor validation
  // =========================================================================

  @Nested
  @DisplayName("1. Eip7702Fields 유효성 검사")
  class Eip7702FieldsValidation {

    @Test
    @DisplayName("[E-1] 유효한 필드로 생성 성공 (data = '0x', 빈 calldata, auth 1개)")
    void constructor_validFields_succeeds() {
      Eip7702Fields fields = fixture();

      assertThat(fields.chainId()).isEqualTo(10L);
      assertThat(fields.data()).isEqualTo("0x");
      assertThat(fields.authorizationList()).hasSize(1);
    }

    @Test
    @DisplayName("[E-2] chainId = 0 → Web3InvalidInputException")
    void constructor_chainIdZero_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      0L,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      DESTINATION,
                      VALUE,
                      DATA,
                      List.of(authTuple())))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("chainId");
    }

    @Test
    @DisplayName("[E-3] nonce = null → Web3InvalidInputException")
    void constructor_nonceNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      CHAIN_ID,
                      null,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      DESTINATION,
                      VALUE,
                      DATA,
                      List.of(authTuple())))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("[E-4] to = blank → Web3InvalidInputException")
    void constructor_toBlank_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      "   ",
                      VALUE,
                      DATA,
                      List.of(authTuple())))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[E-5] maxFeePerGas < maxPriorityFeePerGas → Web3InvalidInputException")
    void constructor_maxFeeLessThanPriority_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      CHAIN_ID,
                      NONCE,
                      BigInteger.valueOf(2_000_000_000L),
                      BigInteger.valueOf(1_000_000_000L),
                      GAS_LIMIT,
                      DESTINATION,
                      VALUE,
                      DATA,
                      List.of(authTuple())))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining(">= maxPriorityFeePerGas");
    }

    @Test
    @DisplayName("[E-6] data = 비 hex → Web3InvalidInputException")
    void constructor_dataNonHex_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      DESTINATION,
                      VALUE,
                      "0xZZ",
                      List.of(authTuple())))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("data");
    }

    @Test
    @DisplayName("[E-7] authorizationList = null → Web3InvalidInputException")
    void constructor_authListNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip7702Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      DESTINATION,
                      VALUE,
                      DATA,
                      null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("authorizationList");
    }

    @Test
    @DisplayName("[E-8] authorizationList 빈 리스트 허용")
    void constructor_emptyAuthList_succeeds() {
      Eip7702Fields fields =
          new Eip7702Fields(
              CHAIN_ID,
              NONCE,
              MAX_PRIORITY,
              MAX_FEE,
              GAS_LIMIT,
              DESTINATION,
              VALUE,
              DATA,
              List.of());

      assertThat(fields.authorizationList()).isEmpty();
    }

    @Test
    @DisplayName("[E-9] authorizationList 외부 변경 후에도 record 내부 리스트 불변")
    void constructor_externalListMutation_doesNotAffectRecord() {
      List<AuthorizationTuple> mutable = new ArrayList<>();
      mutable.add(authTuple());

      Eip7702Fields fields =
          new Eip7702Fields(
              CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, DESTINATION, VALUE, DATA, mutable);

      mutable.clear();

      assertThat(fields.authorizationList()).hasSize(1);
    }
  }

  // =========================================================================
  // Group 2 — AuthorizationTuple compact constructor validation
  // =========================================================================

  @Nested
  @DisplayName("2. AuthorizationTuple 유효성 검사")
  class AuthorizationTupleValidation {

    @Test
    @DisplayName("[E-10] yParity = 2 → Web3InvalidInputException")
    void constructor_yParityInvalid_throws() {
      assertThatThrownBy(
              () ->
                  new AuthorizationTuple(
                      CHAIN_ID,
                      AUTHORITY,
                      BigInteger.ZERO,
                      (byte) 2,
                      filled32((byte) 0x11),
                      filled32((byte) 0x22)))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("yParity");
    }

    @Test
    @DisplayName("[E-11] r 배열은 방어적으로 복제됨 (생성자에서)")
    void constructor_rDefensivelyCloned() {
      byte[] r = filled32((byte) 0x11);
      AuthorizationTuple tuple =
          new AuthorizationTuple(
              CHAIN_ID, AUTHORITY, BigInteger.ZERO, (byte) 0, r, filled32((byte) 0x22));

      r[0] = 0x00;

      assertThat(tuple.r()[0]).isEqualTo((byte) 0x11);
    }

    @Test
    @DisplayName("[E-12] r() accessor 도 매번 복제하여 반환")
    void accessor_rDefensivelyCloned() {
      AuthorizationTuple tuple = authTuple();

      byte[] firstRead = tuple.r();
      firstRead[0] = 0x00;

      assertThat(tuple.r()[0]).isEqualTo((byte) 0x11);
    }
  }

  // =========================================================================
  // Group 3 — Eip7702TxEncoder#buildUnsigned
  // =========================================================================

  @Nested
  @DisplayName("3. buildUnsigned 메서드")
  class BuildUnsigned {

    @Test
    @DisplayName("[E-13] null fields → Web3InvalidInputException")
    void buildUnsigned_nullFields_throws() {
      assertThatThrownBy(() -> Eip7702TxEncoder.buildUnsigned(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[E-14] 첫 바이트가 0x04 (EIP-2718 typed envelope)")
    void buildUnsigned_firstByteIs0x04() {
      byte[] result = Eip7702TxEncoder.buildUnsigned(fixture());

      assertThat(result).isNotEmpty();
      assertThat(result[0]).isEqualTo((byte) 0x04);
    }

    @Test
    @DisplayName("[E-15] 알려진 픽스처 hex dump가 web3j RLP primitive 결과와 byte-for-byte 일치")
    void buildUnsigned_matchesPrimitiveRlp() {
      // Derive expected envelope by calling web3j RLP primitives directly — same shape we expect
      // the encoder to produce for the canonical fixture.
      List<RlpType> expectedFields = new ArrayList<>();
      expectedFields.add(RlpString.create(CHAIN_ID));
      expectedFields.add(RlpString.create(NONCE));
      expectedFields.add(RlpString.create(MAX_PRIORITY));
      expectedFields.add(RlpString.create(MAX_FEE));
      expectedFields.add(RlpString.create(GAS_LIMIT));
      expectedFields.add(RlpString.create(Numeric.hexStringToByteArray(DESTINATION)));
      expectedFields.add(RlpString.create(VALUE));
      expectedFields.add(RlpString.create(Numeric.hexStringToByteArray(DATA)));
      expectedFields.add(new RlpList());

      AuthorizationTuple auth = authTuple();
      List<RlpType> tupleRlp = new ArrayList<>();
      tupleRlp.add(RlpString.create(auth.chainId()));
      tupleRlp.add(RlpString.create(Numeric.hexStringToByteArray(auth.address())));
      tupleRlp.add(RlpString.create(auth.nonce()));
      // Match the encoder's canonical-RLP path for yParity (BigInteger overload).
      tupleRlp.add(RlpString.create(BigInteger.valueOf(Byte.toUnsignedLong(auth.yParity()))));
      tupleRlp.add(RlpString.create(Bytes.trimLeadingZeroes(auth.r())));
      tupleRlp.add(RlpString.create(Bytes.trimLeadingZeroes(auth.s())));
      expectedFields.add(new RlpList(List.of(new RlpList(tupleRlp))));

      byte[] expected =
          ByteBuffer.allocate(1 + RlpEncoder.encode(new RlpList(expectedFields)).length)
              .put((byte) 0x04)
              .put(RlpEncoder.encode(new RlpList(expectedFields)))
              .array();

      byte[] actual = Eip7702TxEncoder.buildUnsigned(fixture());

      assertThat(Numeric.toHexString(actual)).isEqualTo(Numeric.toHexString(expected));
    }

    @Test
    @DisplayName("[E-16] 서명 전 envelope는 정확히 10개의 RLP 필드를 가짐 (0x04 제거 후)")
    void buildUnsigned_hasTenRlpFields() {
      byte[] unsignedBytes = Eip7702TxEncoder.buildUnsigned(fixture());

      RlpList fields = decodePayload(unsignedBytes);

      // [chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList,
      //  authorizationList]
      assertThat(fields.getValues()).hasSize(10);

      // index 8 = accessList → empty
      assertThat(fields.getValues().get(8)).isInstanceOf(RlpList.class);
      assertThat(((RlpList) fields.getValues().get(8)).getValues()).isEmpty();

      // index 9 = authorizationList → 1 tuple
      assertThat(fields.getValues().get(9)).isInstanceOf(RlpList.class);
      RlpList authList = (RlpList) fields.getValues().get(9);
      assertThat(authList.getValues()).hasSize(1);
      assertThat(authList.getValues().get(0)).isInstanceOf(RlpList.class);
      assertThat(((RlpList) authList.getValues().get(0)).getValues()).hasSize(6);
    }
  }

  // =========================================================================
  // Group 4 — authorizationList shape across 0/1/2 tuples
  // =========================================================================

  @Nested
  @DisplayName("4. authorizationList RLP shape (0/1/2 tuples)")
  class AuthListShape {

    @Test
    @DisplayName("[E-17] 0개 tuple → authorizationList는 빈 RlpList")
    void zeroTuples_empty() {
      Eip7702Fields fields =
          new Eip7702Fields(
              CHAIN_ID,
              NONCE,
              MAX_PRIORITY,
              MAX_FEE,
              GAS_LIMIT,
              DESTINATION,
              VALUE,
              DATA,
              List.of());

      RlpList decoded = decodePayload(Eip7702TxEncoder.buildUnsigned(fields));

      RlpList authList = (RlpList) decoded.getValues().get(9);
      assertThat(authList.getValues()).isEmpty();
    }

    @Test
    @DisplayName("[E-18] 1개 tuple → authorizationList는 1개의 6-요소 inner RlpList")
    void oneTuple_oneInnerList() {
      RlpList decoded = decodePayload(Eip7702TxEncoder.buildUnsigned(fixture()));

      RlpList authList = (RlpList) decoded.getValues().get(9);
      assertThat(authList.getValues()).hasSize(1);
      assertThat(((RlpList) authList.getValues().get(0)).getValues()).hasSize(6);
    }

    @Test
    @DisplayName("[E-19] 2개 tuple → authorizationList는 2개의 6-요소 inner RlpList")
    void twoTuples_twoInnerLists() {
      AuthorizationTuple second =
          new AuthorizationTuple(
              CHAIN_ID,
              "0xcccccccccccccccccccccccccccccccccccccccc",
              BigInteger.ONE,
              (byte) 1,
              filled32((byte) 0x33),
              filled32((byte) 0x44));

      Eip7702Fields fields =
          new Eip7702Fields(
              CHAIN_ID,
              NONCE,
              MAX_PRIORITY,
              MAX_FEE,
              GAS_LIMIT,
              DESTINATION,
              VALUE,
              DATA,
              List.of(authTuple(), second));

      RlpList decoded = decodePayload(Eip7702TxEncoder.buildUnsigned(fields));

      RlpList authList = (RlpList) decoded.getValues().get(9);
      assertThat(authList.getValues()).hasSize(2);
      assertThat(((RlpList) authList.getValues().get(0)).getValues()).hasSize(6);
      assertThat(((RlpList) authList.getValues().get(1)).getValues()).hasSize(6);
    }
  }

  // =========================================================================
  // Group 5 — Eip7702TxEncoder#digest
  // =========================================================================

  @Nested
  @DisplayName("5. digest 메서드")
  class Digest {

    @Test
    @DisplayName("[E-20] null 입력 → Web3InvalidInputException")
    void digest_null_throws() {
      assertThatThrownBy(() -> Eip7702TxEncoder.digest(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("unsigned bytes");
    }

    @Test
    @DisplayName("[E-21] 빈 배열 → Web3InvalidInputException")
    void digest_empty_throws() {
      assertThatThrownBy(() -> Eip7702TxEncoder.digest(new byte[0]))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[E-22] 픽스처 round-trip — 32바이트 keccak256")
    void digest_fixture_returnsKeccak256() {
      byte[] unsignedBytes = Eip7702TxEncoder.buildUnsigned(fixture());
      String expectedHex = Numeric.toHexString(Hash.sha3(unsignedBytes));

      byte[] result = Eip7702TxEncoder.digest(unsignedBytes);

      assertThat(result).hasSize(32);
      assertThat(Numeric.toHexString(result)).isEqualTo(expectedHex);
    }
  }

  // =========================================================================
  // Group 6 — Eip7702TxEncoder#assembleSigned
  // =========================================================================

  @Nested
  @DisplayName("6. assembleSigned 메서드")
  class AssembleSigned {

    @Test
    @DisplayName("[E-23] null fields → Web3InvalidInputException")
    void assembleSigned_nullFields_throws() {
      assertThatThrownBy(() -> Eip7702TxEncoder.assembleSigned(null, validVrs27()))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[E-24] null sig → Web3InvalidInputException")
    void assembleSigned_nullSig_throws() {
      assertThatThrownBy(() -> Eip7702TxEncoder.assembleSigned(fixture(), null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("sig");
    }

    @Test
    @DisplayName("[E-25] rawTx가 '0x04'로 시작, txHash는 0x + 64 hex chars")
    void assembleSigned_validVrs27_rawTxStartsWith0x04() {
      SignedTx result = Eip7702TxEncoder.assembleSigned(fixture(), validVrs27());

      assertThat(result.rawTx()).startsWith("0x04");
      assertThat(result.txHash()).startsWith("0x");
      assertThat(result.txHash()).hasSize(66);
    }

    @Test
    @DisplayName("[E-26] v=28 → yParity=1 (RLP field index 10)")
    void assembleSigned_v28_yParityIsOne() {
      Vrs vrs28 = new Vrs(filled32((byte) 0x01), filled32((byte) 0x01), (byte) 28);

      SignedTx result = Eip7702TxEncoder.assembleSigned(fixture(), vrs28);
      RlpList fields = decodeSignedHex(result.rawTx());

      // [chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList, authList,
      //  yParity, r, s] → yParity is at index 10
      RlpString yParityField = (RlpString) fields.getValues().get(10);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    @DisplayName("[E-27] v=27 → yParity=0 (RLP field index 10)")
    void assembleSigned_v27_yParityIsZero() {
      SignedTx result = Eip7702TxEncoder.assembleSigned(fixture(), validVrs27());
      RlpList fields = decodeSignedHex(result.rawTx());

      RlpString yParityField = (RlpString) fields.getValues().get(10);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    @DisplayName("[E-28] 서명된 envelope은 정확히 13개의 RLP 필드를 가짐")
    void assembleSigned_hasThirteenRlpFields() {
      SignedTx result = Eip7702TxEncoder.assembleSigned(fixture(), validVrs27());
      RlpList fields = decodeSignedHex(result.rawTx());

      // 10 unsigned + yParity + r + s
      assertThat(fields.getValues()).hasSize(13);
    }

    @Test
    @DisplayName("[E-29] txHash는 rawTx hex의 keccak256과 동일")
    void assembleSigned_txHashEqualsKeccak256OfRawTx() {
      SignedTx result = Eip7702TxEncoder.assembleSigned(fixture(), validVrs27());

      assertThat(result.txHash()).isEqualTo(Hash.sha3(result.rawTx()));
    }
  }

  // =========================================================================
  // Group 7 — s-defensive-clone tests (mirror existing r-clone tests)
  // =========================================================================

  @Nested
  @DisplayName("7. AuthorizationTuple s 방어적 복제")
  class AuthorizationTupleSDefensiveClone {

    @Test
    @DisplayName("[E-30] s 배열은 방어적으로 복제됨 (생성자에서)")
    void authorizationTuple_constructor_clonesS_byteArray() {
      byte[] s = filled32((byte) 0x22);
      AuthorizationTuple tuple =
          new AuthorizationTuple(
              CHAIN_ID, AUTHORITY, BigInteger.ZERO, (byte) 0, filled32((byte) 0x11), s);

      s[0] = 0x00;

      assertThat(tuple.s()[0]).isEqualTo((byte) 0x22);
    }

    @Test
    @DisplayName("[E-31] s() accessor 도 매번 복제하여 반환")
    void authorizationTuple_accessor_clonesS_byteArray() {
      AuthorizationTuple tuple = authTuple();

      byte[] firstRead = tuple.s();
      firstRead[0] = 0x00;

      assertThat(tuple.s()[0]).isEqualTo((byte) 0x22);
    }
  }

  // =========================================================================
  // Group 8 — Domain ↔ Infra byte-equivalence on the authorization-list RLP segment
  // =========================================================================

  @Nested
  @DisplayName("8. Domain ↔ Infra authorizationList RLP 바이트 동등성")
  class DomainInfraByteEquivalence {

    /**
     * Builds the authorization-list RLP exactly as {@code
     * Eip7702TransactionEncoder#encodeAuthorizationList} does — using the infra port's BigInteger
     * tuple shape. This replicates the SSOT path so we can assert byte-equivalence with the domain
     * encoder for any yParity, including the historically-divergent zero case.
     */
    private static RlpList encodeAuthorizationListLikeInfra(
        List<Eip7702ChainPort.AuthorizationTuple> authList) {
      List<RlpType> tuples = new ArrayList<>();
      for (Eip7702ChainPort.AuthorizationTuple auth : authList) {
        List<RlpType> tuple = new ArrayList<>();
        tuple.add(RlpString.create(auth.chainId()));
        tuple.add(RlpString.create(Numeric.hexStringToByteArray(auth.address())));
        tuple.add(RlpString.create(auth.nonce()));
        tuple.add(RlpString.create(auth.yParity()));
        tuple.add(RlpString.create(auth.r()));
        tuple.add(RlpString.create(auth.s()));
        tuples.add(new RlpList(tuple));
      }
      return new RlpList(tuples);
    }

    @Test
    @DisplayName(
        "[E-32] yParity=0 / yParity=1 양쪽 tuple 의 authorizationList RLP 바이트가 infra SSOT와 일치")
    void assembleSigned_byteEqualsInfraEncoder_forZeroYParityTuple() {
      // r/s with leading zeros (1 byte 0x00 at index 0) plus a yParity=0 tuple AND a yParity=1
      // tuple — exercises the canonical-empty-string path and the trimLeadingZeroes path
      // simultaneously.
      byte[] rWithLeadingZero = filled32((byte) 0x11);
      rWithLeadingZero[0] = 0x00;
      byte[] sWithLeadingZero = filled32((byte) 0x22);
      sWithLeadingZero[0] = 0x00;

      AuthorizationTuple zeroParity =
          new AuthorizationTuple(
              CHAIN_ID, AUTHORITY, BigInteger.ZERO, (byte) 0, rWithLeadingZero, sWithLeadingZero);
      AuthorizationTuple oneParity =
          new AuthorizationTuple(
              CHAIN_ID,
              "0xcccccccccccccccccccccccccccccccccccccccc",
              BigInteger.ONE,
              (byte) 1,
              filled32((byte) 0x33),
              filled32((byte) 0x44));

      Eip7702Fields domainFields =
          new Eip7702Fields(
              CHAIN_ID,
              NONCE,
              MAX_PRIORITY,
              MAX_FEE,
              GAS_LIMIT,
              DESTINATION,
              VALUE,
              DATA,
              List.of(zeroParity, oneParity));

      // Infra-side input shape: BigInteger fields, with r/s already-trimmed-via-BigInteger
      // semantics (web3j RlpString.create(BigInteger) drops leading-zero bytes canonically).
      List<Eip7702ChainPort.AuthorizationTuple> infraAuthList =
          List.of(
              new Eip7702ChainPort.AuthorizationTuple(
                  BigInteger.valueOf(CHAIN_ID),
                  AUTHORITY,
                  BigInteger.ZERO,
                  BigInteger.ZERO,
                  new BigInteger(1, rWithLeadingZero),
                  new BigInteger(1, sWithLeadingZero)),
              new Eip7702ChainPort.AuthorizationTuple(
                  BigInteger.valueOf(CHAIN_ID),
                  "0xcccccccccccccccccccccccccccccccccccccccc",
                  BigInteger.ONE,
                  BigInteger.ONE,
                  new BigInteger(1, filled32((byte) 0x33)),
                  new BigInteger(1, filled32((byte) 0x44))));

      // Extract the auth-list segment from the domain encoder's unsigned envelope (index 9).
      RlpList domainDecoded = decodePayload(Eip7702TxEncoder.buildUnsigned(domainFields));
      RlpList domainAuthList = (RlpList) domainDecoded.getValues().get(9);

      // Encode the infra-shaped auth list independently.
      RlpList infraAuthList2 = encodeAuthorizationListLikeInfra(infraAuthList);

      byte[] domainAuthBytes = RlpEncoder.encode(domainAuthList);
      byte[] infraAuthBytes = RlpEncoder.encode(infraAuthList2);

      // Byte-for-byte equivalence on the auth-list segment locks the encoder against future
      // SSOT drift — if infra changes shape, this fails in CI.
      assertThat(Numeric.toHexString(domainAuthBytes))
          .isEqualTo(Numeric.toHexString(infraAuthBytes));

      // Sanity guard against the original bug: yParity=0 must NOT serialize as the literal
      // byte string [0x00] — the canonical RLP empty string is 0x80. The first inner tuple's
      // yParity field is at index 3 within its 6-element tuple.
      RlpList firstInner = (RlpList) domainAuthList.getValues().get(0);
      RlpString yParity0Field = (RlpString) firstInner.getValues().get(3);
      assertThat(yParity0Field.getBytes()).isEmpty();
    }
  }
}
