package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.GetAnswerRootCommentsCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetCommentsCursorResult;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesCursorQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsCursorQuery;

public interface GetCommentCursorUseCase {
  GetCommentsCursorResult getRootCommentsByCursor(GetRootCommentsCursorQuery query);

  GetCommentsCursorResult getAnswerRootCommentsByCursor(GetAnswerRootCommentsCursorQuery query);

  GetCommentsCursorResult getRepliesByCursor(GetRepliesCursorQuery query);
}
