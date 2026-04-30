package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyCommentedPostsV2Request(
    PostType type, String search, String cursor, Integer size) {

  public GetMyCommentedPostsCursorCommand toCommand(Long requesterId) {
    return new GetMyCommentedPostsCursorCommand(requesterId, type, search, cursor, size);
  }
}
