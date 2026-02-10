package momzzangseven.mztkbe.modules.post.application.dto;

public record CreatePostResult(Long postId, boolean isXpGranted, Long grantedXp, String message) {}
