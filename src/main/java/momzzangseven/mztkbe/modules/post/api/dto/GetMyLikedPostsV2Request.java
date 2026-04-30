package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetMyLikedPostsV2Request(PostType type, String search, String cursor, Integer size) {

  public GetMyLikedPostsCursorCommand toCommand(Long requesterId) {
    return new GetMyLikedPostsCursorCommand(requesterId, type, search, cursor, size);
  }
}
