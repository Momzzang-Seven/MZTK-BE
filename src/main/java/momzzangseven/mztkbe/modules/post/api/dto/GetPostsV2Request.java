package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record GetPostsV2Request(
    PostType type, String tag, String search, String cursor, Integer size) {

  public PostCursorSearchCondition toCommand() {
    return PostCursorSearchCondition.of(type, tag, search, cursor, size);
  }
}
