package momzzangseven.mztkbe.modules.post.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;

public record GetMyLikedPostsV2Response(
    List<PostListResponse> posts, boolean hasNext, String nextCursor) {

  public static GetMyLikedPostsV2Response from(GetMyLikedPostsCursorResult result) {
    return new GetMyLikedPostsV2Response(
        result.posts().stream().map(PostListResponse::from).toList(),
        result.hasNext(),
        result.nextCursor());
  }
}
