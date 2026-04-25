package momzzangseven.mztkbe.modules.comment.application.dto;

import java.util.List;

public record GetCommentsCursorResult(
    List<CommentResult> comments, boolean hasNext, String nextCursor) {

  public GetCommentsCursorResult {
    comments = comments == null ? List.of() : List.copyOf(comments);
  }
}
