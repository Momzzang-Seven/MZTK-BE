package momzzangseven.mztkbe.modules.level.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMyLevelServiceTest {

  @Mock private UserProgressPort userProgressPort;
  @Mock private LevelPolicyResolver levelPolicyResolver;

  @InjectMocks private GetMyLevelService service;

  @Test
  void execute_shouldThrowWhenUserIdNull() {
    assertThatThrownBy(() -> service.execute(null))
        .isInstanceOf(UserNotAuthenticatedException.class);
  }

  @Test
  void execute_shouldBuildValidatedResult() {
    UserProgress progress =
        UserProgress.builder()
            .userId(1L)
            .level(3)
            .availableXp(250)
            .lifetimeXp(1000)
            .createdAt(LocalDateTime.of(2026, 2, 1, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 2, 28, 0, 0))
            .build();

    when(userProgressPort.loadUserProgress(1L)).thenReturn(Optional.of(progress));
    when(levelPolicyResolver.resolveNextLevelInfo(eq(3), any(LocalDateTime.class)))
        .thenReturn(new LevelPolicyResolver.NextLevelPolicyInfo(300, 15));

    GetMyLevelResult result = service.execute(1L);

    assertThat(result.level()).isEqualTo(3);
    assertThat(result.availableXp()).isEqualTo(250);
    assertThat(result.requiredXpForNext()).isEqualTo(300);
    assertThat(result.rewardMztkForNext()).isEqualTo(15);
    verify(userProgressPort).loadUserProgress(1L);
  }

  @Test
  void execute_shouldReturnInitialSnapshotWithoutPersistingWhenProgressMissing() {
    when(userProgressPort.loadUserProgress(1L)).thenReturn(Optional.empty());
    when(levelPolicyResolver.resolveNextLevelInfo(eq(1), any(LocalDateTime.class)))
        .thenReturn(new LevelPolicyResolver.NextLevelPolicyInfo(100, 10));

    GetMyLevelResult result = service.execute(1L);

    assertThat(result.level()).isEqualTo(1);
    assertThat(result.availableXp()).isEqualTo(0);
    assertThat(result.requiredXpForNext()).isEqualTo(100);
    assertThat(result.rewardMztkForNext()).isEqualTo(10);
    verify(userProgressPort).loadUserProgress(1L);
  }
}
