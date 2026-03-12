package momzzangseven.mztkbe.modules.answer.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerResult;

public record AnswerResponse(
    Long answerId,
    Long userId,
    String content,
    boolean isAccepted,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResponse from(AnswerResult answer) {
    return new AnswerResponse(
        answer.answerId(),
        answer.userId(),
        answer.content(),
        answer.accepted(),
        answer.imageUrls() == null ? List.of() : List.copyOf(answer.imageUrls()),
        answer.createdAt(),
        answer.updatedAt());
  }
}
