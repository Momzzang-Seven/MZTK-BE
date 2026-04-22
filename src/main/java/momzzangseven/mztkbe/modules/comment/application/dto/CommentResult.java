package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentWriterPort.WriterSummary;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

public record CommentResult(
    Long id,
    String content,
    Long writerId,
    String writerNickname,
    String writerProfileImageUrl,
    Long parentId,
    long replyCount,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static CommentResult from(Comment comment, WriterSummary writer, long replyCount) {
    return new CommentResult(
        comment.getId(),
        comment.getContent(),
        comment.getWriterId(),
        writer != null ? writer.nickname() : null,
        writer != null ? writer.profileImageUrl() : null,
        comment.getParentId(),
        replyCount,
        comment.isDeleted(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
