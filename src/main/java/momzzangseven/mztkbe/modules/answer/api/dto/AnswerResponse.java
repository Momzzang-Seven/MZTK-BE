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
    List<String> imageUrls,
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
        answer.imageUrls() == null ? List.of() : answer.imageUrls(),
        answer.createdAt(),
        answer.updatedAt());
  }
}
