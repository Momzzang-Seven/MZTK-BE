package momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Arrays;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.SignedTx;
import momzzangseven.mztkbe.modules.web3.shared.application.util.Erc20TransferCalldataEncoder;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.utils.Numeric;

/**
 * Unit tests for {@link EIP1559CodecAdapter} — covers Eip1559Fields validation, buildUnsigned,
 * digest, assembleSigned, and parity asserted against web3j's RawTransaction +
 * TransactionEncoder.signMessage path.
 *
 * <p>Re-homed from {@code transaction.domain.encoder.Eip1559TxEncoderTest} as the codec moved to
 * the shared module's infrastructure adapter (see PR #150 follow-up commit 3).
 */
@DisplayName("EIP1559CodecAdapter 단위 테스트")
class EIP1559CodecAdapterTest {

  // =========================================================================
  // Shared fixture constants — Optimism ERC-20 transfer
  // =========================================================================

  private static final long CHAIN_ID = 10L;
  private static final long NONCE = 1L;
  private static final BigInteger MAX_PRIORITY = BigInteger.valueOf(1_000_000_000L);
  private static final BigInteger MAX_FEE = BigInteger.valueOf(2_000_000_000L);
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(65_000L);
  private static final String TOKEN_CONTRACT = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String RECIPIENT = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final BigInteger AMOUNT_WEI = BigInteger.valueOf(1_000_000_000_000_000_000L);
  private static final BigInteger VALUE = BigInteger.ZERO;

  private static final String DATA =
      Erc20TransferCalldataEncoder.encodeTransferData(RECIPIENT, AMOUNT_WEI);

  private final EIP1559CodecAdapter codec = new EIP1559CodecAdapter();

  private static Eip1559Fields fixture() {
    return new Eip1559Fields(
        CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
  }

  private static Vrs validVrs27() {
    byte[] r = new byte[32];
    byte[] s = new byte[32];
    Arrays.fill(r, (byte) 0x01);
    Arrays.fill(s, (byte) 0x01);
    return new Vrs(r, s, (byte) 27);
  }

  private static RlpList decodePayload(byte[] typedEnvelope) {
    byte[] payload = Arrays.copyOfRange(typedEnvelope, 1, typedEnvelope.length);
    RlpList outer = RlpDecoder.decode(payload);
    return (RlpList) outer.getValues().get(0);
  }

  private static RlpList decodeSignedHex(String rawTxHex) {
    byte[] signedBytes = Numeric.hexStringToByteArray(rawTxHex);
    return decodePayload(signedBytes);
  }

  // =========================================================================
  // Group 1 — Eip1559Fields compact constructor validation
  // =========================================================================

  @Nested
  @DisplayName("1. Eip1559Fields 유효성 검사")
  class Eip1559FieldsValidation {

    @Test
    @DisplayName("[M-1] 유효한 필드로 생성 성공 (data = '0x', 빈 calldata)")
    void constructor_validFieldsWithEmptyData_succeeds() {
      Eip1559Fields fields =
          new Eip1559Fields(
              CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, "0x");
      assertThat(fields.data()).isEqualTo("0x");
      assertThat(fields.chainId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("[M-2] chainId = 0 → Web3InvalidInputException")
    void constructor_chainIdZero_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      0L, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("chainId");
    }

    @Test
    @DisplayName("[M-3] chainId = -1 → Web3InvalidInputException")
    void constructor_chainIdNegative_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      -1L, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("chainId");
    }

    @Test
    @DisplayName("[M-4] nonce = -1 → Web3InvalidInputException")
    void constructor_nonceNegative_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, -1L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("[M-5] nonce = 0 → 생성 성공")
    void constructor_nonceZero_succeeds() {
      Eip1559Fields fields =
          new Eip1559Fields(
              CHAIN_ID, 0L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
      assertThat(fields.nonce()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[M-6] maxPriorityFeePerGas = null → Web3InvalidInputException")
    void constructor_maxPriorityNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, null, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxPriorityFeePerGas");
    }

    @Test
    @DisplayName("[M-7] maxPriorityFeePerGas = 0 → Web3InvalidInputException")
    void constructor_maxPriorityZero_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      BigInteger.ZERO,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxPriorityFeePerGas");
    }

    @Test
    @DisplayName("[M-8] maxFeePerGas = null → Web3InvalidInputException")
    void constructor_maxFeeNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, null, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxFeePerGas");
    }

    @Test
    @DisplayName("[M-9] maxFeePerGas = 0 → Web3InvalidInputException")
    void constructor_maxFeeZero_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      BigInteger.ZERO,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxFeePerGas");
    }

    @Test
    @DisplayName("[M-10] maxFeePerGas < maxPriorityFeePerGas → Web3InvalidInputException")
    void constructor_maxFeeLessThanMaxPriority_throws() {
      BigInteger highPriority = BigInteger.valueOf(2_000_000_000L);
      BigInteger lowFee = BigInteger.valueOf(1_000_000_000L);
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      highPriority,
                      lowFee,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxFeePerGas")
          .hasMessageContaining(">= maxPriorityFeePerGas");
    }

    @Test
    @DisplayName("[M-11] maxFeePerGas == maxPriorityFeePerGas → 생성 성공")
    void constructor_maxFeeEqualsMaxPriority_succeeds() {
      BigInteger equalFee = BigInteger.valueOf(1_000_000_000L);
      new Eip1559Fields(
          CHAIN_ID, NONCE, equalFee, equalFee, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
    }

    @Test
    @DisplayName("[M-12] gasLimit = null → Web3InvalidInputException")
    void constructor_gasLimitNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, null, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("gasLimit");
    }

    @Test
    @DisplayName("[M-13] gasLimit = 0 → Web3InvalidInputException")
    void constructor_gasLimitZero_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      BigInteger.ZERO,
                      TOKEN_CONTRACT,
                      VALUE,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("gasLimit");
    }

    @Test
    @DisplayName("[M-14] to = null → Web3InvalidInputException")
    void constructor_toNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, null, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-15] to = blank → Web3InvalidInputException")
    void constructor_toBlank_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, "   ", VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-16] to = 너무 짧은 주소 → Web3InvalidInputException")
    void constructor_toTooShort_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, "0xdeadbeef", VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-17] to = 비 hex 문자열 → Web3InvalidInputException")
    void constructor_toNonHex_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      "not-an-address",
                      VALUE,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-18] value = null → Web3InvalidInputException")
    void constructor_valueNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      null,
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("value");
    }

    @Test
    @DisplayName("[M-19] value 음수 → Web3InvalidInputException")
    void constructor_valueNegative_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      BigInteger.ONE.negate(),
                      DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("value");
    }

    @Test
    @DisplayName("[M-20] value = 0 → 생성 성공")
    void constructor_valueZero_succeeds() {
      new Eip1559Fields(
          CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, BigInteger.ZERO, DATA);
    }

    @Test
    @DisplayName("[M-21] data = null → Web3InvalidInputException")
    void constructor_dataNull_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("data");
    }

    @Test
    @DisplayName("[M-22] data = '0x' 접두사 없음 → Web3InvalidInputException")
    void constructor_dataWithoutHexPrefix_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      "deadbeef"))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("data");
    }

    @Test
    @DisplayName("[M-22a] data = '0x' + 비 hex 문자 → Web3InvalidInputException")
    void constructor_dataWithNonHexChars_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      "0xZZ"))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("data");
    }

    @Test
    @DisplayName("[M-22b] data = '0x' + 홀수 hex 길이 → Web3InvalidInputException")
    void constructor_dataWithOddHexLength_throws() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID,
                      NONCE,
                      MAX_PRIORITY,
                      MAX_FEE,
                      GAS_LIMIT,
                      TOKEN_CONTRACT,
                      VALUE,
                      "0xabc"))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("data");
    }
  }

  // =========================================================================
  // Group 2 — buildUnsigned
  // =========================================================================

  @Nested
  @DisplayName("2. buildUnsigned 메서드")
  class BuildUnsigned {

    @Test
    @DisplayName("[M-23] null fields → Web3InvalidInputException")
    void buildUnsigned_nullFields_throws() {
      assertThatThrownBy(() -> codec.buildUnsigned(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[M-24] 첫 번째 바이트가 0x02 (EIP-2718 typed envelope)")
    void buildUnsigned_validFixture_firstByteIs0x02() {
      byte[] result = codec.buildUnsigned(fixture());
      assertThat(result).isNotEmpty();
      assertThat(result[0]).isEqualTo((byte) 0x02);
    }

    @Test
    @DisplayName("[M-25] 알려진 픽스처 hex dump와 byte-for-byte 일치")
    void buildUnsigned_validFixture_matchesLegacyEncoderHex() {
      RawTransaction rawTx =
          RawTransaction.createTransaction(
              CHAIN_ID,
              BigInteger.valueOf(NONCE),
              GAS_LIMIT,
              TOKEN_CONTRACT,
              VALUE,
              DATA,
              MAX_PRIORITY,
              MAX_FEE);
      byte[] expected = TransactionEncoder.encode(rawTx);

      byte[] actual = codec.buildUnsigned(fixture());

      assertThat(Numeric.toHexString(actual)).isEqualTo(Numeric.toHexString(expected));
    }

    @Test
    @DisplayName("[M-26] unsigned bytes는 9개의 RLP 필드를 가짐")
    void buildUnsigned_validFixture_hasNineRlpFields() {
      byte[] unsignedBytes = codec.buildUnsigned(fixture());
      RlpList fields = decodePayload(unsignedBytes);
      assertThat(fields.getValues()).hasSize(9);
      assertThat(fields.getValues().get(8)).isInstanceOf(RlpList.class);
      RlpList accessList = (RlpList) fields.getValues().get(8);
      assertThat(accessList.getValues()).isEmpty();
    }
  }

  // =========================================================================
  // Group 3 — digest
  // =========================================================================

  @Nested
  @DisplayName("3. digest 메서드")
  class Digest {

    @Test
    @DisplayName("[M-27] null 입력 → Web3InvalidInputException")
    void digest_nullInput_throws() {
      assertThatThrownBy(() -> codec.digest(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("unsigned bytes");
    }

    @Test
    @DisplayName("[M-28] 빈 배열 입력 → Web3InvalidInputException")
    void digest_emptyArray_throws() {
      assertThatThrownBy(() -> codec.digest(new byte[0]))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-29] 픽스처 round-trip digest — 32바이트 keccak256")
    void digest_validFixture_returns32ByteKeccak256() {
      byte[] unsignedBytes = codec.buildUnsigned(fixture());
      String expectedHex = Numeric.toHexString(Hash.sha3(unsignedBytes));

      byte[] digestResult = codec.digest(unsignedBytes);

      assertThat(digestResult).hasSize(32);
      assertThat(Numeric.toHexString(digestResult)).isEqualTo(expectedHex);
    }

    @Test
    @DisplayName("[M-30] 다른 nonce → 다른 digest")
    void digest_differentNonces_produceDifferentDigests() {
      Eip1559Fields fieldsNonce1 =
          new Eip1559Fields(
              CHAIN_ID, 1L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
      Eip1559Fields fieldsNonce2 =
          new Eip1559Fields(
              CHAIN_ID, 2L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);

      byte[] digest1 = codec.digest(codec.buildUnsigned(fieldsNonce1));
      byte[] digest2 = codec.digest(codec.buildUnsigned(fieldsNonce2));

      assertThat(digest1).isNotEqualTo(digest2);
    }
  }

  // =========================================================================
  // Group 4 — assembleSigned
  // =========================================================================

  @Nested
  @DisplayName("4. assembleSigned 메서드")
  class AssembleSigned {

    @Test
    @DisplayName("[M-31] null fields → Web3InvalidInputException")
    void assembleSigned_nullFields_throws() {
      assertThatThrownBy(() -> codec.assembleSigned(null, validVrs27()))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[M-32] null sig → Web3InvalidInputException")
    void assembleSigned_nullSig_throws() {
      assertThatThrownBy(() -> codec.assembleSigned(fixture(), null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("sig");
    }

    @Test
    @DisplayName("[M-33] rawTx가 '0x02'로 시작하고 txHash 가 0x + 64 hex chars")
    void assembleSigned_validVrs27_envelopeShape() {
      SignedTx result = codec.assembleSigned(fixture(), validVrs27());
      assertThat(result.rawTx()).startsWith("0x02");
      assertThat(result.txHash()).startsWith("0x");
      assertThat(result.txHash()).hasSize(66);
    }

    @Test
    @DisplayName("[M-34] v=27 → yParity=0")
    void assembleSigned_vIs27_yParityIsZero() {
      SignedTx result = codec.assembleSigned(fixture(), validVrs27());
      RlpList fields = decodeSignedHex(result.rawTx());
      RlpString yParityField = (RlpString) fields.getValues().get(9);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    @DisplayName("[M-35] v=28 → yParity=1")
    void assembleSigned_vIs28_yParityIsOne() {
      byte[] r = new byte[32];
      byte[] s = new byte[32];
      Arrays.fill(r, (byte) 0x01);
      Arrays.fill(s, (byte) 0x01);
      Vrs vrs28 = new Vrs(r, s, (byte) 28);

      SignedTx result = codec.assembleSigned(fixture(), vrs28);
      RlpList fields = decodeSignedHex(result.rawTx());
      RlpString yParityField = (RlpString) fields.getValues().get(9);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    @DisplayName("[M-101] v < 27 (이미 평탄화된 yParity) → yParity = v 그대로")
    void assembleSigned_vBelow27_yParityEqualsVAsIs() {
      byte[] r = new byte[32];
      byte[] s = new byte[32];
      Arrays.fill(r, (byte) 0x01);
      Arrays.fill(s, (byte) 0x01);
      Vrs vrsFlat = new Vrs(r, s, (byte) 0);

      SignedTx result = codec.assembleSigned(fixture(), vrsFlat);
      RlpList fields = decodeSignedHex(result.rawTx());
      RlpString yParityField = (RlpString) fields.getValues().get(9);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ZERO);
      assertThat(result.rawTx()).startsWith("0x02");
    }

    @Test
    @DisplayName("[M-36] 서명된 envelope은 정확히 12개의 RLP 필드")
    void assembleSigned_validVrs27_hasExactlyTwelveRlpFields() {
      SignedTx result = codec.assembleSigned(fixture(), validVrs27());
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);
      assertThat(fields.getValues()).hasSize(12);
    }

    @Test
    @DisplayName("[M-37] txHash는 rawTx hex의 keccak256과 동일")
    void assembleSigned_validVrs27_txHashEqualsKeccak256OfRawTx() {
      SignedTx result = codec.assembleSigned(fixture(), validVrs27());
      String expectedHash = Hash.sha3(result.rawTx());
      assertThat(result.txHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("[M-38] r leading zero 가 RLP에서 제거됨")
    void assembleSigned_rWithLeadingZero_rIsStrippedInRlp() {
      byte[] r = new byte[32];
      r[0] = 0x00;
      Arrays.fill(r, 1, 32, (byte) 0xAB);
      byte[] s = new byte[32];
      Arrays.fill(s, (byte) 0x01);
      Vrs vrs = new Vrs(r, s, (byte) 27);

      SignedTx result = codec.assembleSigned(fixture(), vrs);
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);
      RlpString rField = (RlpString) fields.getValues().get(10);
      byte[] decodedR = rField.getBytes();
      assertThat(decodedR[0]).isNotEqualTo((byte) 0x00);
      byte[] expected = new byte[31];
      Arrays.fill(expected, (byte) 0xAB);
      assertThat(decodedR).isEqualTo(expected);
    }

    @Test
    @DisplayName("[M-39] s leading zero 가 RLP에서 제거됨")
    void assembleSigned_sWithLeadingZero_sIsStrippedInRlp() {
      byte[] r = new byte[32];
      Arrays.fill(r, (byte) 0x01);
      byte[] s = new byte[32];
      s[0] = 0x00;
      Arrays.fill(s, 1, 32, (byte) 0xAB);
      Vrs vrs = new Vrs(r, s, (byte) 27);

      SignedTx result = codec.assembleSigned(fixture(), vrs);
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);
      RlpString sField = (RlpString) fields.getValues().get(11);
      byte[] decodedS = sField.getBytes();
      assertThat(decodedS[0]).isNotEqualTo((byte) 0x00);
      byte[] expected = new byte[31];
      Arrays.fill(expected, (byte) 0xAB);
      assertThat(decodedS).isEqualTo(expected);
    }

    @Test
    @DisplayName("[M-40] assembleSigned 후 원본 Eip1559Fields 인스턴스 불변")
    void assembleSigned_afterCall_fieldsInstanceIsUnmodified() {
      Eip1559Fields fields = fixture();
      long nonceBefore = fields.nonce();
      String dataBefore = fields.data();

      codec.assembleSigned(fields, validVrs27());

      assertThat(fields.nonce()).isEqualTo(nonceBefore);
      assertThat(fields.data()).isEqualTo(dataBefore);
    }
  }

  // =========================================================================
  // Group 5 — web3j parity
  // =========================================================================

  @Nested
  @DisplayName("5. web3j 표준 EIP-1559 서명 경로와 parity 검증")
  class ParityTest {

    private static final String PRIVATE_KEY_HEX = "0x" + "1".repeat(64);

    @Test
    @DisplayName("[M-41] codec이 web3j 표준 서명 경로와 동일한 rawTx/txHash 생성")
    void assembleSigned_producesIdenticalRawTxAndTxHashAsWeb3jStandardPath() {
      String transferData = Erc20TransferCalldataEncoder.encodeTransferData(RECIPIENT, AMOUNT_WEI);
      RawTransaction rawTransaction =
          RawTransaction.createTransaction(
              CHAIN_ID,
              BigInteger.valueOf(NONCE),
              GAS_LIMIT,
              TOKEN_CONTRACT,
              BigInteger.ZERO,
              transferData,
              MAX_PRIORITY,
              MAX_FEE);
      ECKeyPair keyPair = ECKeyPair.create(Numeric.toBigInt(PRIVATE_KEY_HEX));
      org.web3j.crypto.Credentials credentials = org.web3j.crypto.Credentials.create(keyPair);
      byte[] legacyBytes = TransactionEncoder.signMessage(rawTransaction, credentials);
      String legacyRawTx = Numeric.toHexString(legacyBytes);
      String legacyTxHash = Hash.sha3(legacyRawTx);

      Eip1559Fields fields = fixture();
      byte[] unsignedBytes = codec.buildUnsigned(fields);
      byte[] digestBytes = codec.digest(unsignedBytes);

      Sign.SignatureData sig = Sign.signMessage(digestBytes, keyPair, false);
      byte v = sig.getV()[0];
      byte[] rPadded = leftPad32(sig.getR());
      byte[] sPadded = leftPad32(sig.getS());
      Vrs vrs = new Vrs(rPadded, sPadded, v);

      SignedTx newSigned = codec.assembleSigned(fields, vrs);

      assertThat(newSigned.rawTx()).isEqualToIgnoringCase(legacyRawTx);
      assertThat(newSigned.txHash()).isEqualToIgnoringCase(legacyTxHash);
    }

    private static byte[] leftPad32(byte[] src) {
      if (src.length == 32) {
        return src;
      }
      byte[] padded = new byte[32];
      System.arraycopy(src, 0, padded, 32 - src.length, src.length);
      return padded;
    }
  }
}
