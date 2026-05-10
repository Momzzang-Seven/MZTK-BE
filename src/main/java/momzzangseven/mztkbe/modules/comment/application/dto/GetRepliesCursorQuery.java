package momzzangseven.mztkbe.modules.comment.application.dto;

import momzzangseven.mztkbe.global.pagination.CursorPageRequest;

public record GetRepliesCursorQuery(
    Long parentId, Long requesterUserId, CursorPageRequest pageRequest) {

  public GetRepliesCursorQuery(Long parentId, CursorPageRequest pageRequest) {
    this(parentId, null, pageRequest);
  }
}
