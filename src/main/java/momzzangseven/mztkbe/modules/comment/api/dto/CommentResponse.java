package momzzangseven.mztkbe.modules.comment.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;

public record CommentResponse(
    Long commentId,
    String content,
    Long writerId,
    WriterInfo writer,
    Long parentId,
    long replyCount,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public record WriterInfo(Long userId, String nickname, String profileImage) {}

  public static CommentResponse from(CommentResult result) {
    WriterInfo writer =
        result.isDeleted()
            ? null
            : new WriterInfo(
                result.writerId(), result.writerNickname(), result.writerProfileImageUrl());

    return new CommentResponse(
        result.id(),
        result.isDeleted() ? "삭제된 댓글입니다." : result.content(),
        result.isDeleted() ? null : result.writerId(),
        writer,
        result.parentId(),
        result.replyCount(),
        result.isDeleted(),
        result.createdAt(),
        result.updatedAt());
  }
}
