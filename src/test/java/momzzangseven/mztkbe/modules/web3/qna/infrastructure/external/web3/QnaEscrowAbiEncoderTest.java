package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

@DisplayName("QnaEscrowAbiEncoder unit test")
class QnaEscrowAbiEncoderTest {

  private final QnaEscrowAbiEncoder encoder = new QnaEscrowAbiEncoder();

  @Test
  @DisplayName("encodes createQuestion with question hash payload")
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
  @DisplayName("encodes acceptAnswer with questionHash and contentHash")
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
  @DisplayName("encodes submitAnswer with answer content hash")
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
  @DisplayName("encodes updateQuestion with new question hash")
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
  @DisplayName("encodes deleteQuestion")
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
  @DisplayName("encodes updateAnswer with new content hash")
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
  @DisplayName("encodes deleteAnswer")
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
}
