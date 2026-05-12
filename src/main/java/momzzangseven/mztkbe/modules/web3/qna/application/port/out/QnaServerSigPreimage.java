package momzzangseven.mztkbe.modules.web3.qna.application.port.out;

import java.math.BigInteger;

/**
 * QnA Escrow 7개 user action 에 대한 EIP-712 server-sig preimage 의 sealed root.
 *
 * <p>각 nested record subtype 은 on-chain contract 의 typehash 와 1:1 매핑된다. {@code msg.sender} 가 되는 필드는
 * subtype 별 Javadoc 에 명시한다. hex 필드는 모두 {@code 0x}-prefix 32-byte 가정이며, 검증은 어댑터에서 수행한다.
 */
public sealed interface QnaServerSigPreimage {

  /**
   * Pairs with contract typehash {@code _CREATE_QUESTION_TYPEHASH}.
   *
   * <p>msg.sender = {@code creator}.
   */
  record CreateQuestionPreimage(
      String creator,
      String questionIdHex,
      String tokenAddress,
      BigInteger rewardAmountWei,
      String questionHashHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _UPDATE_QUESTION_TYPEHASH}.
   *
   * <p>msg.sender = {@code asker}.
   */
  record UpdateQuestionPreimage(String asker, String questionIdHex, String newQuestionHashHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _DELETE_QUESTION_TYPEHASH}.
   *
   * <p>msg.sender = {@code asker}.
   */
  record DeleteQuestionPreimage(String asker, String questionIdHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _SUBMIT_ANSWER_TYPEHASH}.
   *
   * <p>msg.sender = {@code responder}.
   */
  record SubmitAnswerPreimage(
      String responder, String questionIdHex, String answerIdHex, String contentHashHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _UPDATE_ANSWER_TYPEHASH}.
   *
   * <p>msg.sender = {@code responder}.
   */
  record UpdateAnswerPreimage(
      String responder, String questionIdHex, String answerIdHex, String newContentHashHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _DELETE_ANSWER_TYPEHASH}.
   *
   * <p>msg.sender = {@code responder}.
   */
  record DeleteAnswerPreimage(String responder, String questionIdHex, String answerIdHex)
      implements QnaServerSigPreimage {}

  /**
   * Pairs with contract typehash {@code _ACCEPT_ANSWER_TYPEHASH}.
   *
   * <p>msg.sender = {@code asker}.
   */
  record AcceptAnswerPreimage(
      String asker,
      String questionIdHex,
      String answerIdHex,
      String questionHashHex,
      String contentHashHex)
      implements QnaServerSigPreimage {}
}
