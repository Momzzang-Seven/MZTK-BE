package momzzangseven.mztkbe.modules.answer.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public record AnswerResponse(
    Long answerId,
    Long userId,
    String content,
    boolean isAccepted,
    List<String> imageUrls,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  /** Domain 객체를 Response DTO로 변환하는 정적 팩토리 메서드 */
  public static AnswerResponse from(Answer answer) {
    return new AnswerResponse(
        answer.getId(),
        answer.getUserId(),
        answer.getContent(),
        answer.getIsAccepted(),
        answer.getImageUrls() != null ? answer.getImageUrls() : List.of(),
        answer.getCreatedAt(),
        answer.getUpdatedAt());
  }
}
