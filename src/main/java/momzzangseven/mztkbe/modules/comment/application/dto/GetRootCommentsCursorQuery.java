package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;

public record GetRootCommentsCursorQuery(Long postId, CursorPageRequest pageRequest) {}
