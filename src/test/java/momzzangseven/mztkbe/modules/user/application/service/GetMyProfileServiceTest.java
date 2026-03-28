package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.dto.AttendanceSummary;
import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.application.dto.UserLevelInfo;
import momzzangseven.mztkbe.modules.user.application.dto.WorkoutCompletionInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadAttendanceSummaryPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadTodayWorkoutCompletionPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLevelPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetMyProfileService 단위 테스트")
class GetMyProfileServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private LoadUserLevelPort loadUserLevelPort;
  @Mock private LoadAttendanceSummaryPort loadAttendanceSummaryPort;
  @Mock private LoadTodayWorkoutCompletionPort loadTodayWorkoutCompletionPort;
  @Mock private LoadUserWalletPort loadUserWalletPort;

  @InjectMocks private GetMyProfileService service;

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-1] 정상 유저 조회 시 모든 포트 결과를 합쳐 반환한다")
    void execute_validUser_returnsAggregatedResult() {
      // given
      User user =
          User.builder()
              .id(1L)
              .nickname("테스터")
              .email("test@example.com")
              .authProvider(AuthProvider.LOCAL)
              .role(UserRole.USER)
              .status(UserStatus.ACTIVE)
              .build();
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(user));
      given(loadUserLevelPort.loadLevelInfo(1L)).willReturn(new UserLevelInfo(3, 120, 200));
      given(loadAttendanceSummaryPort.loadSummary(1L)).willReturn(new AttendanceSummary(true, 5));
      given(loadTodayWorkoutCompletionPort.loadCompletion(1L))
          .willReturn(new WorkoutCompletionInfo(true, WorkoutCompletedMethod.WORKOUT_PHOTO));
      given(loadUserWalletPort.loadActiveWalletAddress(1L)).willReturn(Optional.empty());

      // when
      GetMyProfileResult result = service.execute(1L);

      // then
      assertThat(result.nickname()).isEqualTo("테스터");
      assertThat(result.level()).isEqualTo(3);
      assertThat(result.currentXp()).isEqualTo(120);
      assertThat(result.requiredXpForNextLevel()).isEqualTo(200);
      assertThat(result.hasAttendedToday()).isTrue();
      assertThat(result.weeklyAttendanceCount()).isEqualTo(5);
      assertThat(result.hasCompletedWorkoutToday()).isTrue();
      assertThat(result.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.WORKOUT_PHOTO);
      verify(loadUserPort, times(1)).loadUserById(1L);
      verify(loadUserLevelPort, times(1)).loadLevelInfo(1L);
      verify(loadAttendanceSummaryPort, times(1)).loadSummary(1L);
      verify(loadTodayWorkoutCompletionPort, times(1)).loadCompletion(1L);
    }

    @Test
    @DisplayName("[M-3] walletAddress가 null인 유저도 NPE 없이 정상 반환된다")
    void execute_userWithNoWallet_returnsNullWalletAddressSafely() {
      // given
      User user =
          User.builder()
              .id(2L)
              .nickname("지갑없음")
              .email("nowallet@example.com")
              .authProvider(AuthProvider.LOCAL)
              .role(UserRole.USER)
              .status(UserStatus.ACTIVE)
              .build();
      given(loadUserPort.loadUserById(2L)).willReturn(Optional.of(user));
      given(loadUserLevelPort.loadLevelInfo(2L)).willReturn(new UserLevelInfo(1, 0, 100));
      given(loadAttendanceSummaryPort.loadSummary(2L)).willReturn(new AttendanceSummary(false, 0));
      given(loadTodayWorkoutCompletionPort.loadCompletion(2L))
          .willReturn(new WorkoutCompletionInfo(false, WorkoutCompletedMethod.UNKNOWN));
      given(loadUserWalletPort.loadActiveWalletAddress(2L)).willReturn(Optional.empty());

      // when
      GetMyProfileResult result = service.execute(2L);

      // then
      assertThat(result.walletAddress()).isNull();
      assertThat(result.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.UNKNOWN);
    }

    @Test
    @DisplayName("[M-4] 오늘 출석하지 않은 유저의 hasAttendedToday가 false이다")
    void execute_userNotAttendedToday_returnsCorrectAttendanceAndWorkoutFields() {
      // given
      User user =
          User.builder()
              .id(3L)
              .nickname("미출석")
              .email("absent@example.com")
              .authProvider(AuthProvider.LOCAL)
              .role(UserRole.USER)
              .status(UserStatus.ACTIVE)
              .build();
      given(loadUserPort.loadUserById(3L)).willReturn(Optional.of(user));
      given(loadUserLevelPort.loadLevelInfo(3L)).willReturn(new UserLevelInfo(2, 50, 150));
      given(loadAttendanceSummaryPort.loadSummary(3L)).willReturn(new AttendanceSummary(false, 2));
      given(loadTodayWorkoutCompletionPort.loadCompletion(3L))
          .willReturn(new WorkoutCompletionInfo(false, WorkoutCompletedMethod.UNKNOWN));
      given(loadUserWalletPort.loadActiveWalletAddress(3L)).willReturn(Optional.empty());

      // when
      GetMyProfileResult result = service.execute(3L);

      // then
      assertThat(result.hasAttendedToday()).isFalse();
      assertThat(result.hasCompletedWorkoutToday()).isFalse();
      assertThat(result.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.UNKNOWN);
      assertThat(result.weeklyAttendanceCount()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-2] 존재하지 않는 userId로 조회 시 UserNotFoundException이 발생한다")
    void execute_userNotFound_throwsUserNotFoundException() {
      // given
      given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

      // when / then
      assertThatThrownBy(() -> service.execute(999L)).isInstanceOf(UserNotFoundException.class);
      verify(loadUserLevelPort, never()).loadLevelInfo(999L);
      verify(loadAttendanceSummaryPort, never()).loadSummary(999L);
      verify(loadTodayWorkoutCompletionPort, never()).loadCompletion(999L);
    }
  }
}
