package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

@DisplayName("QnaEscrowAbiEncoder unit test")
class QnaEscrowAbiEncoderTest {

  private final QnaEscrowAbiEncoder encoder = new QnaEscrowAbiEncoder();

  @Test
  @DisplayName("encodes adminSettle with questionHash and contentHash")
  void encodeAdminSettle_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String answerId = "0x" + "0".repeat(63) + "2";
    String questionHash = "0x" + "a".repeat(64);
    String contentHash = "0x" + "b".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ADMIN_SETTLE,
            questionId,
            answerId,
            null,
            BigInteger.ZERO,
            questionHash,
            contentHash);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "adminSettle",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(answerId)),
                    new Bytes32(Numeric.hexStringToByteArray(questionHash)),
                    new Bytes32(Numeric.hexStringToByteArray(contentHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes adminRefund with questionId only")
  void encodeAdminRefund_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ADMIN_REFUND,
            questionId,
            null,
            null,
            BigInteger.ZERO,
            null,
            null);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "adminRefund",
                List.of(new Bytes32(Numeric.hexStringToByteArray(questionId))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes createQuestion with question hash payload (server-sig-free baseline)")
  void encodeCreateQuestion_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String token = "0x1111111111111111111111111111111111111111";
    BigInteger amountWei = new BigInteger("50000000000000000000");
    String questionHash = "0x" + "a".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            questionId,
            null,
            token,
            amountWei,
            questionHash,
            null);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "createQuestion",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Address(token),
                    new Uint256(amountWei),
                    new Bytes32(Numeric.hexStringToByteArray(questionHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes acceptAnswer with questionHash and contentHash (baseline)")
  void encodeAcceptAnswer_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String answerId = "0x" + "0".repeat(63) + "2";
    String questionHash = "0x" + "a".repeat(64);
    String contentHash = "0x" + "b".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            questionId,
            answerId,
            null,
            BigInteger.ZERO,
            questionHash,
            contentHash);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "acceptAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(answerId)),
                    new Bytes32(Numeric.hexStringToByteArray(questionHash)),
                    new Bytes32(Numeric.hexStringToByteArray(contentHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes submitAnswer with answer content hash (baseline)")
  void encodeSubmitAnswer_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String answerId = "0x" + "0".repeat(63) + "2";
    String contentHash = "0x" + "b".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            questionId,
            answerId,
            null,
            BigInteger.ZERO,
            null,
            contentHash);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "submitAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(answerId)),
                    new Bytes32(Numeric.hexStringToByteArray(contentHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes updateQuestion with new question hash (baseline)")
  void encodeUpdateQuestion_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String questionHash = "0x" + "a".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_UPDATE,
            questionId,
            null,
            null,
            BigInteger.ZERO,
            questionHash,
            null);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "updateQuestion",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(questionHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes deleteQuestion (baseline)")
  void encodeDeleteQuestion_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            questionId,
            null,
            null,
            BigInteger.ZERO,
            null,
            null);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "deleteQuestion",
                List.of(new Bytes32(Numeric.hexStringToByteArray(questionId))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes updateAnswer with new content hash (baseline)")
  void encodeUpdateAnswer_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String answerId = "0x" + "0".repeat(63) + "2";
    String contentHash = "0x" + "b".repeat(64);

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_UPDATE,
            questionId,
            answerId,
            null,
            BigInteger.ZERO,
            null,
            contentHash);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "updateAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(answerId)),
                    new Bytes32(Numeric.hexStringToByteArray(contentHash))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("encodes deleteAnswer (baseline)")
  void encodeDeleteAnswer_matchesContractSignature() {
    String questionId = "0x" + "0".repeat(63) + "1";
    String answerId = "0x" + "0".repeat(63) + "2";

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            questionId,
            answerId,
            null,
            BigInteger.ZERO,
            null,
            null);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "deleteAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(questionId)),
                    new Bytes32(Numeric.hexStringToByteArray(answerId))),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  // ===========================================================================================
  // 9-arg server-sig overload tests (B1 — MOM-393)
  // ===========================================================================================

  private static final String FIX_QUESTION_ID = "0x" + "0".repeat(63) + "1";
  private static final String FIX_ANSWER_ID = "0x" + "0".repeat(63) + "2";
  private static final String FIX_TOKEN = "0x1111111111111111111111111111111111111111";
  private static final BigInteger FIX_REWARD = new BigInteger("50000000000000000000");
  private static final String FIX_QUESTION_HASH = "0x" + "a".repeat(64);
  private static final String FIX_CONTENT_HASH = "0x" + "b".repeat(64);
  private static final long FIX_SIGNED_AT = 1_768_224_000L;

  private static byte[] sampleSignature() {
    byte[] sig = new byte[65];
    for (int i = 0; i < sig.length; i++) {
      sig[i] = (byte) (i + 1);
    }
    return sig;
  }

  @Test
  @DisplayName("E-1101 encodes createQuestion with signedAt + signature appended")
  void encodeCreateQuestion_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_CREATE,
            FIX_QUESTION_ID,
            null,
            FIX_TOKEN,
            FIX_REWARD,
            FIX_QUESTION_HASH,
            null,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "createQuestion",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Address(FIX_TOKEN),
                    new Uint256(FIX_REWARD),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_HASH)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1102 encodes updateQuestion with signedAt + signature appended")
  void encodeUpdateQuestion_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_UPDATE,
            FIX_QUESTION_ID,
            null,
            null,
            BigInteger.ZERO,
            FIX_QUESTION_HASH,
            null,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "updateQuestion",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_HASH)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1103 encodes deleteQuestion with signedAt + signature appended")
  void encodeDeleteQuestion_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            FIX_QUESTION_ID,
            null,
            null,
            BigInteger.ZERO,
            null,
            null,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "deleteQuestion",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1104 encodes submitAnswer with signedAt + signature appended")
  void encodeSubmitAnswer_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_SUBMIT,
            FIX_QUESTION_ID,
            FIX_ANSWER_ID,
            null,
            BigInteger.ZERO,
            null,
            FIX_CONTENT_HASH,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "submitAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_ANSWER_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_CONTENT_HASH)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1105 encodes updateAnswer with signedAt + signature appended")
  void encodeUpdateAnswer_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_UPDATE,
            FIX_QUESTION_ID,
            FIX_ANSWER_ID,
            null,
            BigInteger.ZERO,
            null,
            FIX_CONTENT_HASH,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "updateAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_ANSWER_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_CONTENT_HASH)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1106 encodes deleteAnswer with signedAt + signature appended")
  void encodeDeleteAnswer_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_DELETE,
            FIX_QUESTION_ID,
            FIX_ANSWER_ID,
            null,
            BigInteger.ZERO,
            null,
            null,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "deleteAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_ANSWER_ID)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1107 encodes acceptAnswer with signedAt + signature appended")
  void encodeAcceptAnswer_withServerSig_matchesContractSignature() {
    byte[] sig = sampleSignature();

    String encoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ANSWER_ACCEPT,
            FIX_QUESTION_ID,
            FIX_ANSWER_ID,
            null,
            BigInteger.ZERO,
            FIX_QUESTION_HASH,
            FIX_CONTENT_HASH,
            FIX_SIGNED_AT,
            sig);

    String expected =
        FunctionEncoder.encode(
            new Function(
                "acceptAnswer",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_ANSWER_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_HASH)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_CONTENT_HASH)),
                    new Uint256(BigInteger.valueOf(FIX_SIGNED_AT)),
                    new DynamicBytes(sig)),
                Collections.emptyList()));

    assertThat(encoded).isEqualTo(expected);
  }

  @Test
  @DisplayName("E-1108 rejects QNA_ADMIN_SETTLE on server-sig overload")
  void encodeAdminSettle_withServerSig_throws() {
    byte[] sig = sampleSignature();

    assertThatThrownBy(
            () ->
                encoder.encode(
                    QnaExecutionActionType.QNA_ADMIN_SETTLE,
                    FIX_QUESTION_ID,
                    FIX_ANSWER_ID,
                    null,
                    BigInteger.ZERO,
                    FIX_QUESTION_HASH,
                    FIX_CONTENT_HASH,
                    FIX_SIGNED_AT,
                    sig))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("admin action not supported");
  }

  @Test
  @DisplayName("E-1109 rejects QNA_ADMIN_REFUND on server-sig overload")
  void encodeAdminRefund_withServerSig_throws() {
    byte[] sig = sampleSignature();

    assertThatThrownBy(
            () ->
                encoder.encode(
                    QnaExecutionActionType.QNA_ADMIN_REFUND,
                    FIX_QUESTION_ID,
                    null,
                    null,
                    BigInteger.ZERO,
                    null,
                    null,
                    FIX_SIGNED_AT,
                    sig))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("admin action not supported");
  }

  @Test
  @DisplayName("E-1110 rejects wrong-length signature on server-sig overload")
  void encodeWithServerSig_wrongLengthSignature_throws() {
    byte[] tooShort = new byte[64];

    assertThatThrownBy(
            () ->
                encoder.encode(
                    QnaExecutionActionType.QNA_QUESTION_DELETE,
                    FIX_QUESTION_ID,
                    null,
                    null,
                    BigInteger.ZERO,
                    null,
                    null,
                    FIX_SIGNED_AT,
                    tooShort))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature must be 65 bytes");
  }

  @Test
  @DisplayName("E-1111 rejects null signature on server-sig overload")
  void encodeWithServerSig_nullSignature_throws() {
    assertThatThrownBy(
            () ->
                encoder.encode(
                    QnaExecutionActionType.QNA_QUESTION_DELETE,
                    FIX_QUESTION_ID,
                    null,
                    null,
                    BigInteger.ZERO,
                    null,
                    null,
                    FIX_SIGNED_AT,
                    null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signature");
  }

  @Test
  @DisplayName("E-1112 rejects negative signedAt on server-sig overload")
  void encodeWithServerSig_negativeSignedAt_throws() {
    byte[] sig = sampleSignature();

    assertThatThrownBy(
            () ->
                encoder.encode(
                    QnaExecutionActionType.QNA_QUESTION_DELETE,
                    FIX_QUESTION_ID,
                    null,
                    null,
                    BigInteger.ZERO,
                    null,
                    null,
                    -1L,
                    sig))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("signedAt must be non-negative");
  }

  @Test
  @DisplayName("E-1113 defensively copies signatureBytes — caller mutation has no effect")
  void encodeWithServerSig_defensivelyCopiesSignature() {
    byte[] sig = sampleSignature();

    String firstEncoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            FIX_QUESTION_ID,
            null,
            null,
            BigInteger.ZERO,
            null,
            null,
            FIX_SIGNED_AT,
            sig);

    // Mutate caller's array AFTER encode returned. If the encoder retained a reference, this
    // would corrupt downstream usage; the encoder must have copied the bytes internally.
    sig[0] = (byte) 0xFF;
    sig[64] = (byte) 0xFE;

    // Encode again with a pristine signature — should be byte-identical to the first call.
    String secondEncoded =
        encoder.encode(
            QnaExecutionActionType.QNA_QUESTION_DELETE,
            FIX_QUESTION_ID,
            null,
            null,
            BigInteger.ZERO,
            null,
            null,
            FIX_SIGNED_AT,
            sampleSignature());

    assertThat(firstEncoded).isEqualTo(secondEncoded);
  }

  @Test
  @DisplayName(
      "E-1114 regression — 7-arg overload for QNA_ADMIN_SETTLE / QNA_ADMIN_REFUND unchanged")
  void encodeAdminActions_via7ArgOverload_remainsByteIdentical() {
    // QNA_ADMIN_SETTLE — adminSettle(questionId, answerId, questionHash, contentHash)
    String settleEncoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ADMIN_SETTLE,
            FIX_QUESTION_ID,
            FIX_ANSWER_ID,
            null,
            BigInteger.ZERO,
            FIX_QUESTION_HASH,
            FIX_CONTENT_HASH);

    String settleExpected =
        FunctionEncoder.encode(
            new Function(
                "adminSettle",
                List.of(
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_ANSWER_ID)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_HASH)),
                    new Bytes32(Numeric.hexStringToByteArray(FIX_CONTENT_HASH))),
                Collections.emptyList()));

    assertThat(settleEncoded).isEqualTo(settleExpected);

    // QNA_ADMIN_REFUND — adminRefund(questionId)
    String refundEncoded =
        encoder.encode(
            QnaExecutionActionType.QNA_ADMIN_REFUND,
            FIX_QUESTION_ID,
            null,
            null,
            BigInteger.ZERO,
            null,
            null);

    String refundExpected =
        FunctionEncoder.encode(
            new Function(
                "adminRefund",
                List.of(new Bytes32(Numeric.hexStringToByteArray(FIX_QUESTION_ID))),
                Collections.emptyList()));

    assertThat(refundEncoded).isEqualTo(refundExpected);
  }
}
