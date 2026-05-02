package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity.AdminBoardModerationActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminBoardModerationActionJpaRepository
    extends JpaRepository<AdminBoardModerationActionEntity, Long> {

  @Query(
      "SELECT a.reasonCode AS reasonCode, COUNT(a.id) AS actionCount "
          + "FROM AdminBoardModerationActionEntity a "
          + "GROUP BY a.reasonCode")
  List<ReasonCodeCount> countByReasonCode();

  @Query(
      "SELECT a.boardType AS boardType, COUNT(a.id) AS actionCount "
          + "FROM AdminBoardModerationActionEntity a "
          + "WHERE a.boardType IS NOT NULL "
          + "GROUP BY a.boardType")
  List<BoardTypeCount> countByBoardType();

  @Query(
      "SELECT a.targetType AS targetType, COUNT(a.id) AS actionCount "
          + "FROM AdminBoardModerationActionEntity a "
          + "GROUP BY a.targetType")
  List<TargetTypeCount> countByTargetType();

  interface ReasonCodeCount {
    AdminBoardModerationReasonCode getReasonCode();

    Long getActionCount();
  }

  interface BoardTypeCount {
    AdminBoardType getBoardType();

    Long getActionCount();
  }

  interface TargetTypeCount {
    AdminBoardModerationTargetType getTargetType();

    Long getActionCount();
  }
}
