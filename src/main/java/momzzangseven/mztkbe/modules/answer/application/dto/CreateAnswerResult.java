package momzzangseven.mztkbe.modules.answer.application.dto;

import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionWriteView;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;

public record CreateAnswerResult(
    Long postId,
    Long answerId,
    AnswerPublicationStatus publicationStatus,
    AnswerExecutionWriteView web3) {

  public CreateAnswerResult(Long postId, Long answerId, AnswerExecutionWriteView web3) {
    this(postId, answerId, AnswerPublicationStatus.VISIBLE, web3);
  }
}
