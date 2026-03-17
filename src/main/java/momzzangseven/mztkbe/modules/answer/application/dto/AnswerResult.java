package momzzangseven.mztkbe.modules.answer.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public record AnswerResult(
    Long answerId,
    Long userId,
    String nickname,
    String profileImageUrl,
    String content,
    boolean accepted,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResult from(Answer answer, String nickname, String profileImageUrl) {
    return new AnswerResult(
        answer.getId(),
        answer.getUserId(),
        nickname,
        profileImageUrl,
        answer.getContent(),
        answer.getIsAccepted(),
        List.copyOf(answer.getImageUrls()),
        answer.getCreatedAt(),
        answer.getUpdatedAt());
  }
}
