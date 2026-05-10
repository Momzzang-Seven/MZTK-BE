package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.adapter.AdminBoardModerationActionPersistenceAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("AdminBoardModerationActionJpaRepository DataJpaTest")
class AdminBoardModerationActionJpaRepositoryTest {

  @Autowired private AdminBoardModerationActionJpaRepository repository;

  @Test
  @DisplayName("같은 target_type과 target_id에 대해 여러 moderation action row를 저장할 수 있다")
  void save_allowsMultipleActionsForSameTarget() {
    AdminBoardModerationActionPersistenceAdapter adapter =
        new AdminBoardModerationActionPersistenceAdapter(repository);

    AdminBoardModerationAction first =
        action(AdminBoardModerationReasonCode.SPAM, "first moderation action");
    AdminBoardModerationAction second =
        action(AdminBoardModerationReasonCode.POLICY_VIOLATION, "second moderation action");

    AdminBoardModerationAction savedFirst = adapter.save(first);
    AdminBoardModerationAction savedSecond = adapter.save(second);

    assertThat(savedFirst.getId()).isNotNull();
    assertThat(savedSecond.getId()).isNotNull().isNotEqualTo(savedFirst.getId());
    assertThat(repository.findAll())
        .extracting(
            entity ->
                entity.getTargetType().name()
                    + ":"
                    + entity.getTargetId()
                    + ":"
                    + entity.getReasonCode().name())
        .containsExactlyInAnyOrder("COMMENT:31:SPAM", "COMMENT:31:POLICY_VIOLATION");
  }

  private AdminBoardModerationAction action(
      AdminBoardModerationReasonCode reasonCode, String reasonDetail) {
    return AdminBoardModerationAction.create(
        9L,
        AdminBoardModerationTargetType.COMMENT,
        31L,
        21L,
        AdminBoardType.FREE,
        reasonCode,
        reasonDetail,
        AdminBoardModerationTargetFlowType.STANDARD,
        AdminBoardModerationExecutionMode.SOFT_DELETE,
        LocalDateTime.parse("2026-05-04T00:00:00"));
  }
}
