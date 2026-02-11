package momzzangseven.mztkbe.modules.comment.application.dto;

public record CreateCommentCommand(Long postId, Long writerId, Long parentId, String content) {}
