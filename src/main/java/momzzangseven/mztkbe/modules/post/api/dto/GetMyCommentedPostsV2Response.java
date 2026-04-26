package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;

public record GetMyCommentedPostsV2Response(
    List<PostListResponse> posts, boolean hasNext, String nextCursor) {

  public static GetMyCommentedPostsV2Response from(GetMyCommentedPostsCursorResult result) {
    return new GetMyCommentedPostsV2Response(
        result.posts().stream().map(PostListResponse::from).toList(),
        result.hasNext(),
        result.nextCursor());
  }
}
