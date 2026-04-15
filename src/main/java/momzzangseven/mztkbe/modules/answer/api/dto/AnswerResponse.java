package momzzangseven.mztkbe.modules.answer.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;

public record AnswerResponse(
    Long answerId,
    Long userId,
    String nickname,
    String profileImageUrl,
    String content,
    boolean isAccepted,
    long likeCount,
    boolean isLiked,
    List<String> imageUrls,
    AnswerWeb3ExecutionResponse web3Execution,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResponse from(AnswerResult answer) {
    return new AnswerResponse(
        answer.answerId(),
        answer.userId(),
        answer.nickname(),
        answer.profileImageUrl(),
        answer.content(),
        answer.accepted(),
        answer.likeCount(),
        answer.liked(),
        answer.imageUrls() == null ? List.of() : answer.imageUrls(),
        AnswerWeb3ExecutionResponse.from(answer.web3Execution()),
        answer.createdAt(),
        answer.updatedAt());
  }
}
