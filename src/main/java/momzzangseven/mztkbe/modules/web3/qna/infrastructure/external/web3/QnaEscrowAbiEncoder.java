package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
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

  /**
   * Server-sig overload for the 7 user-facing QnaEscrow actions. Appends {@code signedAt (uint256)}
   * and {@code signature (bytes)} to the calldata per the on-chain contract signature.
   *
   * <p>Admin actions ({@link QnaExecutionActionType#QNA_ADMIN_SETTLE} / {@link
   * QnaExecutionActionType#QNA_ADMIN_REFUND}) are rejected here — they must go through the 7-arg
   * overload because the contract does not require a server signature for them.
   */
  public String encode(
      QnaExecutionActionType actionType,
      String questionId,
      String answerId,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHash,
      String contentHash,
      long signedAt,
      byte[] signatureBytes) {
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (signatureBytes == null) {
      throw new Web3InvalidInputException("signature is required");
    }
    if (signatureBytes.length != 65) {
      throw new Web3InvalidInputException("signature must be 65 bytes");
    }
    if (signedAt < 0) {
      throw new Web3InvalidInputException("signedAt must be non-negative");
    }

    // Defensive copy: prevent caller mutation from leaking into the encoded calldata.
    byte[] safe = signatureBytes.clone();
    Uint256 signedAtParam = new Uint256(BigInteger.valueOf(signedAt));
    DynamicBytes signatureParam = new DynamicBytes(safe);

    Function function =
        switch (actionType) {
          case QNA_QUESTION_CREATE ->
              new Function(
                  "createQuestion",
                  List.of(
                      bytes32(questionId),
                      new Address(tokenAddress),
                      new Uint256(rewardAmountWei),
                      bytes32(questionHash),
                      signedAtParam,
                      signatureParam),
                  Collections.emptyList());
          case QNA_QUESTION_UPDATE ->
              new Function(
                  "updateQuestion",
                  List.of(
                      bytes32(questionId), bytes32(questionHash), signedAtParam, signatureParam),
                  Collections.emptyList());
          case QNA_QUESTION_DELETE ->
              new Function(
                  "deleteQuestion",
                  List.of(bytes32(questionId), signedAtParam, signatureParam),
                  Collections.emptyList());
          case QNA_ANSWER_SUBMIT ->
              new Function(
                  "submitAnswer",
                  List.of(
                      bytes32(questionId),
                      bytes32(answerId),
                      bytes32(contentHash),
                      signedAtParam,
                      signatureParam),
                  Collections.emptyList());
          case QNA_ANSWER_UPDATE ->
              new Function(
                  "updateAnswer",
                  List.of(
                      bytes32(questionId),
                      bytes32(answerId),
                      bytes32(contentHash),
                      signedAtParam,
                      signatureParam),
                  Collections.emptyList());
          case QNA_ANSWER_DELETE ->
              new Function(
                  "deleteAnswer",
                  List.of(bytes32(questionId), bytes32(answerId), signedAtParam, signatureParam),
                  Collections.emptyList());
          case QNA_ANSWER_ACCEPT ->
              new Function(
                  "acceptAnswer",
                  List.of(
                      bytes32(questionId),
                      bytes32(answerId),
                      bytes32(questionHash),
                      bytes32(contentHash),
                      signedAtParam,
                      signatureParam),
                  Collections.emptyList());
          case QNA_ADMIN_SETTLE, QNA_ADMIN_REFUND ->
              throw new IllegalStateException(
                  "admin action not supported by server-sig encoder overload");
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
