package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyPostsV2Request(
    PostType type, String tag, String search, String cursor, Integer size) {

  public GetMyPostsCursorCommand toCommand(Long requesterId) {
    return new GetMyPostsCursorCommand(requesterId, type, tag, search, cursor, size);
  }
}
