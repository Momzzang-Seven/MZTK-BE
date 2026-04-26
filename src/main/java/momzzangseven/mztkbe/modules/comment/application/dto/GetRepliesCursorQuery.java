package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;

public record GetRepliesCursorQuery(Long parentId, CursorPageRequest pageRequest) {}
