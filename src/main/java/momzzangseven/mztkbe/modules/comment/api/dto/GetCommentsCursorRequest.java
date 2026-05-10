package momzzangseven.mztkbe.modules.comment.api.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.global.pagination.CursorScope;
import momzzangseven.mztkbe.modules.comment.application.dto.GetAnswerRootCommentsCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsCursorQuery;

public record GetCommentsCursorRequest(String cursor, Integer size) {

  private static final int ROOT_DEFAULT_SIZE = 20;
  private static final int REPLIES_DEFAULT_SIZE = 10;
  private static final int MAX_SIZE = 50;

  public GetRootCommentsCursorQuery toRootQuery(Long postId, Long requesterUserId) {
    return new GetRootCommentsCursorQuery(
        postId,
        requesterUserId,
        CursorPageRequest.of(
            cursor, size, ROOT_DEFAULT_SIZE, MAX_SIZE, CursorScope.rootComments(postId)));
  }

  public GetRootCommentsCursorQuery toRootQuery(Long postId) {
    return toRootQuery(postId, null);
  }

  public GetAnswerRootCommentsCursorQuery toAnswerRootQuery(Long answerId, Long requesterUserId) {
    return new GetAnswerRootCommentsCursorQuery(
        answerId,
        requesterUserId,
        CursorPageRequest.of(
            cursor, size, ROOT_DEFAULT_SIZE, MAX_SIZE, CursorScope.answerRootComments(answerId)));
  }

  public GetRepliesCursorQuery toRepliesQuery(Long parentId, Long requesterUserId) {
    return new GetRepliesCursorQuery(
        parentId,
        requesterUserId,
        CursorPageRequest.of(
            cursor, size, REPLIES_DEFAULT_SIZE, MAX_SIZE, CursorScope.replies(parentId)));
  }

  public GetRepliesCursorQuery toRepliesQuery(Long parentId) {
    return toRepliesQuery(parentId, null);
  }
}
