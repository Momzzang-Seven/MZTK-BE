package momzzangseven.mztkbe.modules.comment.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;

public record GetCommentsResponse(
    List<CommentResponse> comments, boolean hasNext, String nextCursor) {

  public static GetCommentsResponse from(GetCommentsCursorResult result) {
    return new GetCommentsResponse(
        result.comments().stream().map(CommentResponse::from).toList(),
        result.hasNext(),
        result.nextCursor());
  }
}
