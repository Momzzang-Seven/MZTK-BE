package momzzangseven.mztkbe.modules.post.application.dto;

public record MarkQuestionPostSolvedCommand(Long postId) {
  public void validate() {
    if (postId == null || postId <= 0) {
      throw new IllegalArgumentException("postId must be positive.");
    }
  }
}
