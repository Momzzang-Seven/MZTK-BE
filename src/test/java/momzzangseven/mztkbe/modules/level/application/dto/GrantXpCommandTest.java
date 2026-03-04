package momzzangseven.mztkbe.modules.level.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import momzzangseven.mztkbe.modules.level.domain.vo.XpType;
import org.junit.jupiter.api.Test;

class GrantXpCommandTest {

  @Test
  void constructor_shouldThrowWhenRequiredFieldMissing() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 28, 9, 0);

    assertThatThrownBy(
            () -> GrantXpCommand.of(null, XpType.CHECK_IN, occurredAt, "checkin:1", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class);
    assertThatThrownBy(() -> GrantXpCommand.of(1L, null, occurredAt, "checkin:1", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class);
    assertThatThrownBy(() -> GrantXpCommand.of(1L, XpType.CHECK_IN, null, "checkin:1", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_shouldThrowWhenIdempotencyKeyIsNull() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 28, 9, 0);

    assertThatThrownBy(() -> GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, null, "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void constructor_shouldValidateIdempotencyKeyBoundaries() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 28, 9, 0);
    String tooLong = "checkin:" + "a".repeat(300);

    assertThatThrownBy(() -> GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, " ", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class);
    assertThatThrownBy(() -> GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, tooLong, "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class)
        .hasMessageContaining("<= 200");
    assertThatThrownBy(
            () -> GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "checkin:bad key", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class)
        .hasMessageContaining("invalid characters");
    assertThatThrownBy(() -> GrantXpCommand.of(1L, XpType.CHECK_IN, occurredAt, "workout:1", "src"))
        .isInstanceOf(LevelUpCommandInvalidException.class)
        .hasMessageContaining("prefix");
  }

  @Test
  void of_shouldCreateCommandWhenValid() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 28, 9, 0);

    GrantXpCommand command =
        GrantXpCommand.of(
            1L,
            XpType.WORKOUT,
            occurredAt,
            "workout:location-verify:1:10:20260228",
            "location-verification:10");

    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.xpType()).isEqualTo(XpType.WORKOUT);
    assertThat(command.idempotencyKey()).startsWith("workout:");
  }

  @Test
  void of_shouldCreateCommandForAllXpTypes() {
    LocalDateTime occurredAt = LocalDateTime.of(2026, 2, 28, 9, 0);

    GrantXpCommand streak =
        GrantXpCommand.of(1L, XpType.STREAK_7D, occurredAt, "streak7:1:20260228", "streak");
    assertThat(streak.xpType()).isEqualTo(XpType.STREAK_7D);

    GrantXpCommand post =
        GrantXpCommand.of(1L, XpType.POST, occurredAt, "post:123", "post-create:123");
    assertThat(post.xpType()).isEqualTo(XpType.POST);

    GrantXpCommand comment =
        GrantXpCommand.of(1L, XpType.COMMENT, occurredAt, "comment:456", "comment-create:456");
    assertThat(comment.xpType()).isEqualTo(XpType.COMMENT);
  }
}
