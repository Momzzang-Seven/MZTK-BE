package momzzangseven.mztkbe.modules.comment.application.dto;

import org.springframework.data.domain.Pageable;

public record GetRepliesQuery(Long parentId, Long requesterUserId, Pageable pageable) {

  public GetRepliesQuery(Long parentId, Pageable pageable) {
    this(parentId, null, pageable);
  }
}
