package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

@Component
public class QnaEscrowAbiEncoder {

  public String encode(
      QnaExecutionActionType actionType,
      String questionId,
      String answerId,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHash,
      String contentHash) {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }

    Function function =
        switch (actionType) {
          case QNA_QUESTION_CREATE ->
              new Function(
                  "createQuestion",
                  List.of(
                      bytes32(questionId),
                      new Address(tokenAddress),
                      new Uint256(rewardAmountWei),
                      bytes32(questionHash)),
                  Collections.emptyList());
          case QNA_QUESTION_UPDATE ->
              new Function(
                  "updateQuestion",
                  List.of(bytes32(questionId), bytes32(questionHash)),
                  Collections.emptyList());
          case QNA_QUESTION_DELETE ->
              new Function("deleteQuestion", List.of(bytes32(questionId)), Collections.emptyList());
          case QNA_ANSWER_SUBMIT ->
              new Function(
                  "submitAnswer",
                  List.of(bytes32(questionId), bytes32(answerId), bytes32(contentHash)),
                  Collections.emptyList());
          case QNA_ANSWER_UPDATE ->
              new Function(
                  "updateAnswer",
                  List.of(bytes32(questionId), bytes32(answerId), bytes32(contentHash)),
                  Collections.emptyList());
          case QNA_ANSWER_DELETE ->
              new Function(
                  "deleteAnswer",
                  List.of(bytes32(questionId), bytes32(answerId)),
                  Collections.emptyList());
          case QNA_ANSWER_ACCEPT ->
              new Function(
                  "acceptAnswer",
                  List.of(
                      bytes32(questionId),
                      bytes32(answerId),
                      bytes32(questionHash),
                      bytes32(contentHash)),
                  Collections.emptyList());
          case QNA_ADMIN_SETTLE ->
              new Function(
                  "adminSettle",
                  List.of(
                      bytes32(questionId),
                      bytes32(answerId),
                      bytes32(questionHash),
                      bytes32(contentHash)),
                  Collections.emptyList());
          case QNA_ADMIN_REFUND ->
              new Function("adminRefund", List.of(bytes32(questionId)), Collections.emptyList());
        };

    return FunctionEncoder.encode(function);
  }

  private Type<?> bytes32(String value) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException("bytes32 value is required");
    }
    byte[] padded = Numeric.hexStringToByteArray(value);
    if (padded.length != 32) {
      throw new Web3InvalidInputException("bytes32 hex value must be 32 bytes");
    }
    return new Bytes32(padded);
  }
}
