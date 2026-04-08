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
    long likeCount,
    boolean liked,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AnswerResult from(
      Answer answer,
      String nickname,
      String profileImageUrl,
      long likeCount,
      boolean liked,
      List<String> imageUrls) {
    return new AnswerResult(
        answer.getId(),
        answer.getUserId(),
        nickname,
        profileImageUrl,
        answer.getContent(),
        answer.getIsAccepted(),
        likeCount,
        liked,
        imageUrls == null ? List.of() : imageUrls,
        answer.getCreatedAt(),
        answer.getUpdatedAt());
  }
}
