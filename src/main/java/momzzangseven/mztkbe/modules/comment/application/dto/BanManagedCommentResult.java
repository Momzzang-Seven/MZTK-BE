package momzzangseven.mztkbe.modules.comment.application.dto;

/** Result for admin-managed comment soft delete. */
public record BanManagedCommentResult(Long commentId, Long postId, boolean moderated) {}
