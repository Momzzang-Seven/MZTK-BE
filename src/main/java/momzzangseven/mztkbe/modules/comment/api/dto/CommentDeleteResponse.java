package momzzangseven.mztkbe.modules.comment.api.dto;

/** API response for comment delete endpoints. */
public record CommentDeleteResponse(Long commentId) {

  /** Creates a delete response for the deleted comment id. */
  public static CommentDeleteResponse from(Long commentId) {
    return new CommentDeleteResponse(commentId);
  }
}
