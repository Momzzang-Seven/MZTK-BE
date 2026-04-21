package momzzangseven.mztkbe.modules.answer.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.response.ImageItemResponse;
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
    List<ImageItemResponse> images,
    AnswerWeb3ExecutionResponse web3Execution,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResponse from(AnswerResult answer) {
    List<ImageItemResponse> images =
        answer.images().stream()
            .map(slot -> new ImageItemResponse(slot.imageId(), slot.imageUrl()))
            .toList();

    return new AnswerResponse(
        answer.answerId(),
        answer.userId(),
        answer.nickname(),
        answer.profileImageUrl(),
        answer.content(),
        answer.accepted(),
        answer.likeCount(),
        answer.liked(),
        images,
        AnswerWeb3ExecutionResponse.from(answer.web3Execution()),
        answer.createdAt(),
        answer.updatedAt());
  }
}
