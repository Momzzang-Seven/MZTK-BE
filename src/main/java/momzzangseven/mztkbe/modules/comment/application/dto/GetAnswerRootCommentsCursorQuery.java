package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;

public record GetAnswerRootCommentsCursorQuery(
    Long answerId, Long requesterUserId, CursorPageRequest pageRequest) {}
