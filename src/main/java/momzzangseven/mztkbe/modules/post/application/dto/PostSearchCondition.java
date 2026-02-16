package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public record PostSearchCondition(
    PostType type, String tagName, String search, int page, int size) {
  public static PostSearchCondition of(
      PostType type, String tag, String search, int page, int size) {
    return new PostSearchCondition(type, tag, search, page, size);
  }
}
