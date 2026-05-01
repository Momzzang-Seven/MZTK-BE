package momzzangseven.mztkbe.modules.post.application.dto;

import java.util.List;

public record GetMyLikedPostsCursorResult(
    List<PostListResult> posts, boolean hasNext, String nextCursor) {

  public GetMyLikedPostsCursorResult {
    posts = posts == null ? List.of() : List.copyOf(posts);
  }
}
