package momzzangseven.mztkbe.modules.comment.api.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsCursorQuery;

public record GetCommentsCursorRequest(String cursor, Integer size) {

  private static final int ROOT_DEFAULT_SIZE = 20;
  private static final int REPLIES_DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public GetRootCommentsCursorQuery toRootQuery(Long postId) {
    return new GetRootCommentsCursorQuery(
        postId,
        CursorPageRequest.of(
            cursor, size, ROOT_DEFAULT_SIZE, MAX_SIZE, CursorScope.rootComments(postId)));
  }

  public GetRepliesCursorQuery toRepliesQuery(Long parentId) {
    return new GetRepliesCursorQuery(
        parentId,
        CursorPageRequest.of(
            cursor, size, REPLIES_DEFAULT_SIZE, MAX_SIZE, CursorScope.replies(parentId)));
  }
}
