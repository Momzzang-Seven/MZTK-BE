package momzzangseven.mztkbe.modules.comment.domain.event;

import java.util.List;

/**
 * 댓글이 하드 딜리트(영구 삭제)되었을 때 발행되는 도메인 이벤트입니다. *
 *
 * <p>이 이벤트를 통해 해당 댓글과 연관된 다른 데이터(알림, 좋아요 등)를 연쇄적으로 삭제할 수 있습니다.
 *
 * @param commentIds 하드 딜리트된 댓글들의 ID 리스트
 */
public record CommentsHardDeletedEvent(List<Long> commentIds) {}
