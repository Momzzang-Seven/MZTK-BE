package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record GetMyCommentedPostsCursorResult(
    List<PostListResult> posts, boolean hasNext, String nextCursor) {

  public GetMyCommentedPostsCursorResult {
    posts = posts == null ? List.of() : List.copyOf(posts);
  }
}
