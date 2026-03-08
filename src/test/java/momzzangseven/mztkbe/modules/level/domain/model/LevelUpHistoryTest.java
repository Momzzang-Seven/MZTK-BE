package momzzangseven.mztkbe.modules.level.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.level.LevelUpCommandInvalidException;
import org.junit.jupiter.api.Test;

class LevelUpHistoryTest {

  @Test
  void initial_shouldThrowWhenLevelRangeIsInvalid() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, 10L, 3, 3, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenToLevelIsLowerThanFromLevel() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, 10L, 3, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void createPending_setsCreatedAtAndValidates() {
    LevelUpHistory history = LevelUpHistory.createPending(1L, 10L, 1, 2, 100, 10);

    assertThat(history.getCreatedAt()).isNotNull();
    assertThat(history.getUserId()).isEqualTo(1L);
    assertThat(history.getLevelPolicyId()).isEqualTo(10L);
  }

  @Test
  void assertRewardTransactionLink_shouldAllowWhenRewardIsZero() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            null, 1L, 10L, 1, 2, 100, 0, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatCode(() -> history.assertRewardTransactionLink(null)).doesNotThrowAnyException();
  }

  @Test
  void assertRewardTransactionLink_shouldThrowWhenRewardPositiveAndHistoryIdInvalid() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            null, 1L, 10L, 1, 2, 100, 50, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatThrownBy(() -> history.assertRewardTransactionLink(1L))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void assertRewardTransactionLink_shouldThrowWhenHistoryIdZero() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            0L, 1L, 10L, 1, 2, 100, 50, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatThrownBy(() -> history.assertRewardTransactionLink(1L))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void assertRewardTransactionLink_shouldThrowWhenReferenceNull() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            99L, 1L, 10L, 1, 2, 100, 50, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatThrownBy(() -> history.assertRewardTransactionLink(null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void assertRewardTransactionLink_shouldThrowWhenReferenceMismatch() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            99L, 1L, 10L, 1, 2, 100, 50, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatThrownBy(() -> history.assertRewardTransactionLink(100L))
        .isInstanceOf(LevelUpCommandInvalidException.class)
        .hasMessageContaining("must match");
  }

  @Test
  void assertRewardTransactionLink_shouldPassWhenReferenceMatches() {
    LevelUpHistory history =
        LevelUpHistory.reconstitute(
            99L, 1L, 10L, 1, 2, 100, 50, LocalDateTime.of(2026, 2, 26, 10, 0));

    assertThatCode(() -> history.assertRewardTransactionLink(99L)).doesNotThrowAnyException();
  }

  @Test
  void initial_shouldThrowWhenUserIdInvalid() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    0L, 10L, 1, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenUserIdNull() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    null, 10L, 1, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenLevelPolicyIdInvalid() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(1L, 0L, 1, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenLevelPolicyIdNull() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, null, 1, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenFromLevelNonPositive() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, 10L, 0, 2, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenToLevelNonPositive() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, 10L, 1, 0, 100, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenSpentXpNegative() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(1L, 10L, 1, 2, -1, 10, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenRewardMztkNegative() {
    assertThatThrownBy(
            () ->
                LevelUpHistory.initial(
                    1L, 10L, 1, 2, 100, -1, LocalDateTime.of(2026, 2, 26, 10, 0)))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }

  @Test
  void initial_shouldThrowWhenCreatedAtNull() {
    assertThatThrownBy(() -> LevelUpHistory.initial(1L, 10L, 1, 2, 100, 10, null))
        .isInstanceOf(LevelUpCommandInvalidException.class);
  }
}
