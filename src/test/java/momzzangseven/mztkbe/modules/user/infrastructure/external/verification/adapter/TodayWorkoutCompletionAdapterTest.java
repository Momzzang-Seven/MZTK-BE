package momzzangseven.mztkbe.modules.user.infrastructure.external.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.modules.user.application.dto.WorkoutCompletionInfo;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;
import momzzangseven.mztkbe.modules.verification.application.dto.TodayWorkoutCompletionResult;
import momzzangseven.mztkbe.modules.verification.application.port.in.GetTodayWorkoutCompletionUseCase;
import momzzangseven.mztkbe.modules.verification.domain.vo.CompletedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TodayWorkoutCompletionAdapter 단위 테스트")
class TodayWorkoutCompletionAdapterTest {

  @Mock private GetTodayWorkoutCompletionUseCase getTodayWorkoutCompletionUseCase;

  @InjectMocks private TodayWorkoutCompletionAdapter adapter;

  @Nested
  @DisplayName("CompletedMethod 변환 케이스")
  class MethodMappingCases {

    @Test
    @DisplayName(
        "[M-12] completedMethod=WORKOUT_PHOTO → WorkoutCompletedMethod.WORKOUT_PHOTO로 변환된다")
    void loadCompletion_workoutPhoto_mapsToWorkoutPhoto() {
      // given
      given(getTodayWorkoutCompletionUseCase.execute(1L))
          .willReturn(
              TodayWorkoutCompletionResult.builder()
                  .todayCompleted(true)
                  .completedMethod(CompletedMethod.WORKOUT_PHOTO)
                  .build());

      // when
      WorkoutCompletionInfo info = adapter.loadCompletion(1L);

      // then
      assertThat(info.hasCompletedWorkoutToday()).isTrue();
      assertThat(info.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.WORKOUT_PHOTO);
    }

    @Test
    @DisplayName(
        "[M-13] completedMethod=WORKOUT_RECORD → WorkoutCompletedMethod.WORKOUT_RECORD로 변환된다")
    void loadCompletion_workoutRecord_mapsToWorkoutRecord() {
      // given
      given(getTodayWorkoutCompletionUseCase.execute(1L))
          .willReturn(
              TodayWorkoutCompletionResult.builder()
                  .todayCompleted(true)
                  .completedMethod(CompletedMethod.WORKOUT_RECORD)
                  .build());

      // when
      WorkoutCompletionInfo info = adapter.loadCompletion(1L);

      // then
      assertThat(info.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.WORKOUT_RECORD);
    }

    @Test
    @DisplayName("[M-14] completedMethod=LOCATION → WorkoutCompletedMethod.LOCATION으로 변환된다")
    void loadCompletion_location_mapsToLocation() {
      // given
      given(getTodayWorkoutCompletionUseCase.execute(1L))
          .willReturn(
              TodayWorkoutCompletionResult.builder()
                  .todayCompleted(true)
                  .completedMethod(CompletedMethod.LOCATION)
                  .build());

      // when
      WorkoutCompletionInfo info = adapter.loadCompletion(1L);

      // then
      assertThat(info.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.LOCATION);
    }

    @Test
    @DisplayName("[M-15] completedMethod=null → WorkoutCompletedMethod.UNKNOWN으로 폴백된다 (NPE 없음)")
    void loadCompletion_nullMethod_fallsBackToUnknownWithoutNpe() {
      // given
      given(getTodayWorkoutCompletionUseCase.execute(1L))
          .willReturn(
              TodayWorkoutCompletionResult.builder()
                  .todayCompleted(false)
                  .completedMethod(null)
                  .build());

      // when
      WorkoutCompletionInfo info = adapter.loadCompletion(1L);

      // then
      assertThat(info.hasCompletedWorkoutToday()).isFalse();
      assertThat(info.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.UNKNOWN);
    }

    @Test
    @DisplayName("[M-16] completedMethod=UNKNOWN → WorkoutCompletedMethod.UNKNOWN으로 변환된다")
    void loadCompletion_unknown_mapsToUnknown() {
      // given
      given(getTodayWorkoutCompletionUseCase.execute(1L))
          .willReturn(
              TodayWorkoutCompletionResult.builder()
                  .todayCompleted(false)
                  .completedMethod(CompletedMethod.UNKNOWN)
                  .build());

      // when
      WorkoutCompletionInfo info = adapter.loadCompletion(1L);

      // then
      assertThat(info.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.UNKNOWN);
    }
  }
}
