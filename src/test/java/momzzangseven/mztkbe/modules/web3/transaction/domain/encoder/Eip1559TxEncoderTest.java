package momzzangseven.mztkbe.modules.web3.transaction.domain.encoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Arrays;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.Vrs;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.adapter.Erc20TransferCalldataEncoder;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.Eip1559Fields;
import momzzangseven.mztkbe.modules.web3.transaction.domain.encoder.Eip1559TxEncoder.SignedTx;
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
 * Unit tests for {@link Eip1559TxEncoder} — covers Eip1559Fields validation, buildUnsigned, digest,
 * assembleSigned, and parity asserted against web3j's RawTransaction +
 * TransactionEncoder.signMessage path.
 *
 * <p>Covers test cases [M-1] .. [M-41] from docs/test/MOM-384/eip1559-tx-encoder.md.
 */
@DisplayName("Eip1559TxEncoder 단위 테스트")
class Eip1559TxEncoderTest {

  // =========================================================================
  // Shared fixture constants — Optimism ERC-20 transfer
  // =========================================================================

  private static final long CHAIN_ID = 10L;
  private static final long NONCE = 1L;
  private static final BigInteger MAX_PRIORITY = BigInteger.valueOf(1_000_000_000L); // 1 gwei
  private static final BigInteger MAX_FEE = BigInteger.valueOf(2_000_000_000L); // 2 gwei
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(65_000L);
  private static final String TOKEN_CONTRACT = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String RECIPIENT = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final BigInteger AMOUNT_WEI =
      BigInteger.valueOf(1_000_000_000_000_000_000L); // 1 MZTK
  private static final BigInteger VALUE = BigInteger.ZERO;

  // Calldata derived once from Erc20TransferCalldataEncoder — deterministic
  private static final String DATA =
      Erc20TransferCalldataEncoder.encodeTransferData(RECIPIENT, AMOUNT_WEI);

  /** Builds the canonical fixture fields for the Optimism ERC-20 transfer scenario. */
  private static Eip1559Fields fixture() {
    return new Eip1559Fields(
        CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
  }

  /**
   * Returns a valid Vrs with v=27, with both r and s as 32-byte arrays of 0x01 (fully non-zero, no
   * leading-zero ambiguity).
   */
  private static Vrs validVrs27() {
    byte[] r = new byte[32];
    byte[] s = new byte[32];
    Arrays.fill(r, (byte) 0x01);
    Arrays.fill(s, (byte) 0x01);
    return new Vrs(r, s, (byte) 27);
  }

  /**
   * Strips the leading 0x02 type byte from a typed-transaction envelope and decodes the bare RLP
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
  // Group 1 — Eip1559Fields compact constructor validation
  // =========================================================================

  @Nested
  @DisplayName("1. Eip1559Fields 유효성 검사")
  class Eip1559FieldsValidation {

    @Test
    @DisplayName("[M-1] 유효한 필드로 생성 성공 (data = '0x', 빈 calldata)")
    void constructor_validFieldsWithEmptyData_succeeds() {
      // when
      Eip1559Fields fields =
          new Eip1559Fields(
              CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, "0x");

      // then
      assertThat(fields.data()).isEqualTo("0x");
      assertThat(fields.chainId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("[M-2] chainId = 0 → Web3InvalidInputException (chainId 포함)")
    void constructor_chainIdZero_throwsWithChainIdMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      0L, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("chainId");
    }

    @Test
    @DisplayName("[M-3] chainId = -1 → Web3InvalidInputException (chainId 포함)")
    void constructor_chainIdNegative_throwsWithChainIdMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      -1L, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("chainId");
    }

    @Test
    @DisplayName("[M-4] nonce = -1 → Web3InvalidInputException (nonce 포함)")
    void constructor_nonceNegative_throwsWithNonceMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, -1L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("nonce");
    }

    @Test
    @DisplayName("[M-5] nonce = 0 → 생성 성공, nonce() = 0")
    void constructor_nonceZero_succeedsWithNonceZero() {
      // when
      Eip1559Fields fields =
          new Eip1559Fields(
              CHAIN_ID, 0L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);

      // then
      assertThat(fields.nonce()).isEqualTo(0L);
    }

    @Test
    @DisplayName("[M-6] maxPriorityFeePerGas = null → Web3InvalidInputException")
    void constructor_maxPriorityNull_throwsWithMaxPriorityMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, null, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxPriorityFeePerGas");
    }

    @Test
    @DisplayName("[M-7] maxPriorityFeePerGas = 0 → Web3InvalidInputException")
    void constructor_maxPriorityZero_throwsWithMaxPriorityMessage() {
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
    void constructor_maxFeeNull_throwsWithMaxFeeMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, null, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("maxFeePerGas");
    }

    @Test
    @DisplayName("[M-9] maxFeePerGas = 0 → Web3InvalidInputException")
    void constructor_maxFeeZero_throwsWithMaxFeeMessage() {
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
    void constructor_maxFeeLessThanMaxPriority_throwsWithBothMessages() {
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
      // should not throw
      new Eip1559Fields(
          CHAIN_ID, NONCE, equalFee, equalFee, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
    }

    @Test
    @DisplayName("[M-12] gasLimit = null → Web3InvalidInputException")
    void constructor_gasLimitNull_throwsWithGasLimitMessage() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, null, TOKEN_CONTRACT, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("gasLimit");
    }

    @Test
    @DisplayName("[M-13] gasLimit = 0 → Web3InvalidInputException")
    void constructor_gasLimitZero_throwsWithGasLimitMessage() {
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
    void constructor_toNull_throwsInvalidInput() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, null, VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-15] to = blank → Web3InvalidInputException")
    void constructor_toBlank_throwsInvalidInput() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, "   ", VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-16] to = 너무 짧은 주소 '0xdeadbeef' → Web3InvalidInputException")
    void constructor_toTooShort_throwsInvalidInput() {
      assertThatThrownBy(
              () ->
                  new Eip1559Fields(
                      CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, "0xdeadbeef", VALUE, DATA))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-17] to = 비 hex 문자열 → Web3InvalidInputException")
    void constructor_toNonHex_throwsInvalidInput() {
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
    void constructor_valueNull_throwsWithValueMessage() {
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
    void constructor_valueNegative_throwsWithValueMessage() {
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
      // should not throw — the fixture already uses VALUE = BigInteger.ZERO
      new Eip1559Fields(
          CHAIN_ID, NONCE, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, BigInteger.ZERO, DATA);
    }

    @Test
    @DisplayName("[M-21] data = null → Web3InvalidInputException")
    void constructor_dataNull_throwsWithDataMessage() {
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
    void constructor_dataWithoutHexPrefix_throwsWithDataMessage() {
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
  }

  // =========================================================================
  // Group 2 — Eip1559TxEncoder#buildUnsigned
  // =========================================================================

  @Nested
  @DisplayName("2. buildUnsigned 메서드")
  class BuildUnsigned {

    @Test
    @DisplayName("[M-23] null fields → Web3InvalidInputException (fields 포함)")
    void buildUnsigned_nullFields_throwsWithFieldsMessage() {
      assertThatThrownBy(() -> Eip1559TxEncoder.buildUnsigned(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[M-24] 첫 번째 바이트가 0x02 (EIP-2718 typed envelope)")
    void buildUnsigned_validFixture_firstByteIs0x02() {
      // when
      byte[] result = Eip1559TxEncoder.buildUnsigned(fixture());

      // then
      assertThat(result).isNotEmpty();
      assertThat(result[0]).isEqualTo((byte) 0x02);
    }

    @Test
    @DisplayName("[M-25] 알려진 픽스처 hex dump와 byte-for-byte 일치")
    void buildUnsigned_validFixture_matchesLegacyEncoderHex() {
      // Derive expected bytes using web3j's EIP-1559 unsigned encoding path:
      // RawTransaction.createTransaction(chainId, nonce, gasLimit, to, value, data,
      //   maxPriority, maxFee) + TransactionEncoder.encode(rawTx) (no-signature overload).
      // For EIP-1559 transactions, encode(rawTx) prepends the 0x02 type byte and RLP-encodes
      // the 9 unsigned fields — identical to what Eip1559TxEncoder.buildUnsigned produces.
      // NOTE: TransactionEncoder.encode(rawTx, long chainId) is for legacy txs only and
      // throws CryptoWeb3jException for EIP-1559 transactions.
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

      // when
      byte[] actual = Eip1559TxEncoder.buildUnsigned(fixture());

      // then — compare as hex strings for readable failure messages
      assertThat(Numeric.toHexString(actual)).isEqualTo(Numeric.toHexString(expected));
    }

    @Test
    @DisplayName("[M-26] 서명되지 않은 바이트가 9개의 RLP 필드를 가짐 (0x02 제거 후)")
    void buildUnsigned_validFixture_hasNineRlpFields() {
      // given
      byte[] unsignedBytes = Eip1559TxEncoder.buildUnsigned(fixture());

      // when — strip 0x02, decode bare RLP
      RlpList fields = decodePayload(unsignedBytes);

      // then — [chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList]
      assertThat(fields.getValues()).hasSize(9);

      // index 8 = accessList → empty RlpList
      assertThat(fields.getValues().get(8)).isInstanceOf(RlpList.class);
      RlpList accessList = (RlpList) fields.getValues().get(8);
      assertThat(accessList.getValues()).isEmpty();
    }
  }

  // =========================================================================
  // Group 3 — Eip1559TxEncoder#digest
  // =========================================================================

  @Nested
  @DisplayName("3. digest 메서드")
  class Digest {

    @Test
    @DisplayName("[M-27] null 입력 → Web3InvalidInputException (unsigned bytes 포함)")
    void digest_nullInput_throwsWithUnsignedBytesMessage() {
      assertThatThrownBy(() -> Eip1559TxEncoder.digest(null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("unsigned bytes");
    }

    @Test
    @DisplayName("[M-28] 빈 배열 입력 → Web3InvalidInputException")
    void digest_emptyArray_throwsInvalidInput() {
      assertThatThrownBy(() -> Eip1559TxEncoder.digest(new byte[0]))
          .isInstanceOf(Web3InvalidInputException.class);
    }

    @Test
    @DisplayName("[M-29] 픽스처 round-trip digest — 32바이트, 알려진 keccak256")
    void digest_validFixture_returns32ByteKeccak256() {
      // given
      byte[] unsignedBytes = Eip1559TxEncoder.buildUnsigned(fixture());

      // Derive expected keccak256 from the same unsigned bytes using web3j Hash
      String expectedHex = Numeric.toHexString(Hash.sha3(unsignedBytes));

      // when
      byte[] digestResult = Eip1559TxEncoder.digest(unsignedBytes);

      // then
      assertThat(digestResult).hasSize(32);
      assertThat(Numeric.toHexString(digestResult)).isEqualTo(expectedHex);
    }

    @Test
    @DisplayName("[M-30] 다른 nonce 픽스처는 서로 다른 digest를 생성")
    void digest_differentNonces_produceDifferentDigests() {
      // given
      Eip1559Fields fieldsNonce1 =
          new Eip1559Fields(
              CHAIN_ID, 1L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);
      Eip1559Fields fieldsNonce2 =
          new Eip1559Fields(
              CHAIN_ID, 2L, MAX_PRIORITY, MAX_FEE, GAS_LIMIT, TOKEN_CONTRACT, VALUE, DATA);

      // when
      byte[] digest1 = Eip1559TxEncoder.digest(Eip1559TxEncoder.buildUnsigned(fieldsNonce1));
      byte[] digest2 = Eip1559TxEncoder.digest(Eip1559TxEncoder.buildUnsigned(fieldsNonce2));

      // then
      assertThat(digest1).isNotEqualTo(digest2);
    }
  }

  // =========================================================================
  // Group 4 — Eip1559TxEncoder#assembleSigned
  // =========================================================================

  @Nested
  @DisplayName("4. assembleSigned 메서드")
  class AssembleSigned {

    @Test
    @DisplayName("[M-31] null fields → Web3InvalidInputException (fields 포함)")
    void assembleSigned_nullFields_throwsWithFieldsMessage() {
      assertThatThrownBy(() -> Eip1559TxEncoder.assembleSigned(null, validVrs27()))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("fields");
    }

    @Test
    @DisplayName("[M-32] null sig → Web3InvalidInputException (sig 포함)")
    void assembleSigned_nullSig_throwsWithSigMessage() {
      assertThatThrownBy(() -> Eip1559TxEncoder.assembleSigned(fixture(), null))
          .isInstanceOf(Web3InvalidInputException.class)
          .hasMessageContaining("sig");
    }

    @Test
    @DisplayName("[M-33] rawTx가 '0x02'로 시작하고, txHash는 0x + 64 hex chars")
    void assembleSigned_validVrs27_rawTxStartsWith0x02AndTxHashCorrectLength() {
      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), validVrs27());

      // then
      assertThat(result.rawTx()).startsWith("0x02");
      assertThat(result.txHash()).startsWith("0x");
      assertThat(result.txHash()).hasSize(66); // "0x" + 64 hex chars
    }

    @Test
    @DisplayName("[M-34] v=27 → yParity=0 (10번째 RLP 필드, index 9)")
    void assembleSigned_vIs27_yParityIsZeroInRlp() {
      // given
      Vrs vrs27 = validVrs27();

      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), vrs27);
      RlpList fields = decodeSignedHex(result.rawTx());

      // then — field index 9 = yParity
      RlpString yParityField = (RlpString) fields.getValues().get(9);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ZERO);
    }

    @Test
    @DisplayName("[M-35] v=28 → yParity=1 (10번째 RLP 필드, index 9)")
    void assembleSigned_vIs28_yParityIsOneInRlp() {
      // given
      byte[] r = new byte[32];
      byte[] s = new byte[32];
      Arrays.fill(r, (byte) 0x01);
      Arrays.fill(s, (byte) 0x01);
      Vrs vrs28 = new Vrs(r, s, (byte) 28);

      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), vrs28);
      RlpList fields = decodeSignedHex(result.rawTx());

      // then — field index 9 = yParity
      RlpString yParityField = (RlpString) fields.getValues().get(9);
      assertThat(yParityField.asPositiveBigInteger()).isEqualTo(BigInteger.ONE);
    }

    @Test
    @DisplayName("[M-36] 서명된 envelope은 정확히 12개의 RLP 필드를 가짐")
    void assembleSigned_validVrs27_hasExactlyTwelveRlpFields() {
      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), validVrs27());
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);

      // then — [chainId, nonce, maxPriority, maxFee, gasLimit, to, value, data, accessList,
      //          yParity, r, s]
      assertThat(fields.getValues()).hasSize(12);
    }

    @Test
    @DisplayName("[M-37] txHash는 rawTx hex의 keccak256과 동일")
    void assembleSigned_validVrs27_txHashEqualsKeccak256OfRawTx() {
      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), validVrs27());

      // then
      String expectedHash = Hash.sha3(result.rawTx());
      assertThat(result.txHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("[M-38] r에 leading zero가 있을 때 RLP에서 제거됨 (trimLeadingZeroes)")
    void assembleSigned_rWithLeadingZero_rIsStrippedInRlp() {
      // given — r = [0x00, 0xAB, 0xAB, ... 0xAB] (31 × 0xAB after the leading zero)
      byte[] r = new byte[32];
      r[0] = 0x00;
      Arrays.fill(r, 1, 32, (byte) 0xAB);

      byte[] s = new byte[32];
      Arrays.fill(s, (byte) 0x01);
      Vrs vrs = new Vrs(r, s, (byte) 27);

      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), vrs);
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);

      // then — field index 10 = r; must not start with 0x00
      RlpString rField = (RlpString) fields.getValues().get(10);
      byte[] decodedR = rField.getBytes();
      assertThat(decodedR[0]).isNotEqualTo((byte) 0x00);
      // Content: 31 bytes of 0xAB
      byte[] expected = new byte[31];
      Arrays.fill(expected, (byte) 0xAB);
      assertThat(decodedR).isEqualTo(expected);
    }

    @Test
    @DisplayName("[M-39] s에 leading zero가 있을 때 RLP에서 제거됨 (trimLeadingZeroes)")
    void assembleSigned_sWithLeadingZero_sIsStrippedInRlp() {
      // given — s = [0x00, 0xAB, ... 0xAB] (31 × 0xAB after the leading zero)
      byte[] r = new byte[32];
      Arrays.fill(r, (byte) 0x01);

      byte[] s = new byte[32];
      s[0] = 0x00;
      Arrays.fill(s, 1, 32, (byte) 0xAB);
      Vrs vrs = new Vrs(r, s, (byte) 27);

      // when
      SignedTx result = Eip1559TxEncoder.assembleSigned(fixture(), vrs);
      byte[] signedBytes = Numeric.hexStringToByteArray(result.rawTx());
      RlpList fields = decodePayload(signedBytes);

      // then — field index 11 = s; must not start with 0x00
      RlpString sField = (RlpString) fields.getValues().get(11);
      byte[] decodedS = sField.getBytes();
      assertThat(decodedS[0]).isNotEqualTo((byte) 0x00);
      // Content: 31 bytes of 0xAB
      byte[] expected = new byte[31];
      Arrays.fill(expected, (byte) 0xAB);
      assertThat(decodedS).isEqualTo(expected);
    }

    @Test
    @DisplayName("[M-40] assembleSigned 호출 후 원본 Eip1559Fields 인스턴스가 변경되지 않음 (불변성)")
    void assembleSigned_afterCall_fieldsInstanceIsUnmodified() {
      // given
      Eip1559Fields fields = fixture();
      long nonceBefore = fields.nonce();
      String dataBefore = fields.data();

      // when
      Eip1559TxEncoder.assembleSigned(fields, validVrs27());

      // then
      assertThat(fields.nonce()).isEqualTo(nonceBefore);
      assertThat(fields.data()).isEqualTo(dataBefore);
    }
  }

  // =========================================================================
  // Group 5 — Parity test against direct web3j EIP-1559 signing path
  // =========================================================================

  @Nested
  @DisplayName("5. web3j 표준 EIP-1559 서명 경로와 parity 검증")
  class ParityTest {

    /**
     * Deterministic private key used solely for this parity test — never touches real funds. Same
     * key as web3j docs sample extended to 64 hex chars.
     */
    private static final String PRIVATE_KEY_HEX = "0x" + "1".repeat(64);

    @Test
    @DisplayName("[M-41] assembleSigned가 web3j 표준 EIP-1559 서명 경로와 동일한 rawTx/txHash 생성")
    void assembleSigned_producesIdenticalRawTxAndTxHashAsWeb3jStandardPath() {
      // given — web3j-standard path: RawTransaction + TransactionEncoder.signMessage(.., chainId)
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

      // given — new encoder path
      Eip1559Fields fields = fixture();
      byte[] unsignedBytes = Eip1559TxEncoder.buildUnsigned(fields);
      byte[] digestBytes = Eip1559TxEncoder.digest(unsignedBytes);

      // Sign the digest — false = already-hashed, do NOT double-hash
      Sign.SignatureData sig = Sign.signMessage(digestBytes, keyPair, false);

      byte v = sig.getV()[0];
      // Pad r and s to 32 bytes (Sign may return shorter arrays)
      byte[] rPadded = leftPad32(sig.getR());
      byte[] sPadded = leftPad32(sig.getS());
      Vrs vrs = new Vrs(rPadded, sPadded, v);

      SignedTx newSigned = Eip1559TxEncoder.assembleSigned(fields, vrs);
      String newRawTx = newSigned.rawTx();
      String newTxHash = newSigned.txHash();

      // then
      assertThat(newRawTx).isEqualToIgnoringCase(legacyRawTx);
      assertThat(newTxHash).isEqualToIgnoringCase(legacyTxHash);
    }

    /** Left-pads a byte array to exactly 32 bytes with zero bytes. */
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
