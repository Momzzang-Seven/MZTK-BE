package momzzangseven.mztkbe.modules.answer.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerExecutionResumeView;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;

public record AnswerResult(
    Long answerId,
    Long userId,
    String nickname,
    String profileImageUrl,
    String content,
    boolean accepted,
    long likeCount,
    long commentCount,
    boolean liked,
    List<AnswerImageResult.AnswerImageSlot> images,
    AnswerExecutionResumeView web3Execution,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public AnswerResult {
    images = images == null ? List.of() : images;
  }

  public AnswerResult(
      Long answerId,
      Long userId,
      String nickname,
      String profileImageUrl,
      String content,
      boolean accepted,
      long likeCount,
      boolean liked,
      List<AnswerImageResult.AnswerImageSlot> images,
      AnswerExecutionResumeView web3Execution,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        answerId,
        userId,
        nickname,
        profileImageUrl,
        content,
        accepted,
        likeCount,
        0L,
        liked,
        images,
        web3Execution,
        createdAt,
        updatedAt);
  }

  public static AnswerResult from(
      Answer answer,
      String nickname,
      String profileImageUrl,
      long likeCount,
      boolean liked,
      List<AnswerImageResult.AnswerImageSlot> images,
      AnswerExecutionResumeView web3Execution) {
    return from(answer, nickname, profileImageUrl, likeCount, 0L, liked, images, web3Execution);
  }

  public static AnswerResult from(
      Answer answer,
      String nickname,
      String profileImageUrl,
      long likeCount,
      long commentCount,
      boolean liked,
      List<AnswerImageResult.AnswerImageSlot> images,
      AnswerExecutionResumeView web3Execution) {
    return new AnswerResult(
        answer.getId(),
        answer.getUserId(),
        nickname,
        profileImageUrl,
        answer.getContent(),
        answer.getIsAccepted(),
        likeCount,
        commentCount,
        liked,
        images,
        web3Execution,
        answer.getCreatedAt(),
        answer.getUpdatedAt());
  }
}
