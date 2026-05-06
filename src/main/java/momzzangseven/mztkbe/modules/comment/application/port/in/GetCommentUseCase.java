package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.GetAnswerRootCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRepliesQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.GetRootCommentsQuery;
import org.springframework.data.domain.Page;

public interface GetCommentUseCase {
  Page<CommentResult> getRootComments(GetRootCommentsQuery query);

  Page<CommentResult> getAnswerRootComments(GetAnswerRootCommentsQuery query);

  Page<CommentResult> getReplies(GetRepliesQuery query);
}
