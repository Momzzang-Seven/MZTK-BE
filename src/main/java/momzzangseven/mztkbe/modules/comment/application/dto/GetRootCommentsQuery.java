package momzzangseven.mztkbe.modules.comment.application.dto;

import org.springframework.data.domain.Pageable;

public record GetRootCommentsQuery(Long postId, Long requesterUserId, Pageable pageable) {

  public GetRootCommentsQuery(Long postId, Pageable pageable) {
    this(postId, null, pageable);
  }
}
