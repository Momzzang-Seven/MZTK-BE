package momzzangseven.mztkbe.modules.post.application.dto;

import lombok.Getter;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

@Getter
public class PostSearchCondition {
  private final PostType type;
  private final String tagName;
  private final String search;
  private final int page;
  private final int size;

  public PostSearchCondition(PostType type, String tagName, String search, int page, int size) {
    this.type = type;
    this.tagName = tagName;
    this.search = search;
    this.page = page;
    this.size = size;
  }

  public static PostSearchCondition of(
      PostType type, String tag, String search, int page, int size) {
    return new PostSearchCondition(type, tag, search, page, size);
  }
}
