package momzzangseven.mztkbe.modules.user.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.user.application.dto.GetMyProfileResult;
import momzzangseven.mztkbe.modules.user.domain.vo.WorkoutCompletedMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetMyProfileResponseDTO 단위 테스트")
class GetMyProfileResponseDTOTest {

  @Test
  @DisplayName("[M-5] GetMyProfileResult로부터 DTO가 completedWorkoutMethod 포함 모든 필드를 올바르게 매핑한다")
  void from_validResult_mapsAllFieldsIncludingCompletedWorkoutMethod() {
    // given
    GetMyProfileResult result =
        new GetMyProfileResult(
            "홍길동",
            "hong@example.com",
            "KAKAO",
            "0xABC",
            5,
            300,
            500,
            true,
            true,
            WorkoutCompletedMethod.WORKOUT_RECORD,
            3);

    // when
    GetMyProfileResponseDTO dto = GetMyProfileResponseDTO.from(result);

    // then
    assertThat(dto.nickname()).isEqualTo("홍길동");
    assertThat(dto.provider()).isEqualTo("KAKAO");
    assertThat(dto.walletAddress()).isEqualTo("0xABC");
    assertThat(dto.level()).isEqualTo(5);
    assertThat(dto.hasCompletedWorkoutToday()).isTrue();
    assertThat(dto.completedWorkoutMethod()).isEqualTo(WorkoutCompletedMethod.WORKOUT_RECORD);
    assertThat(dto.weeklyAttendanceCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("[M-5b] walletAddress가 null인 경우에도 NPE 없이 null을 반환한다")
  void from_nullWalletAddress_mapsNullSafely() {
    // given
    GetMyProfileResult result =
        new GetMyProfileResult(
            "홍길동",
            "hong@example.com",
            "LOCAL",
            null,
            1,
            0,
            300,
            false,
            false,
            WorkoutCompletedMethod.UNKNOWN,
            0);

    // when
    GetMyProfileResponseDTO dto = GetMyProfileResponseDTO.from(result);

    // then
    assertThat(dto.walletAddress()).isNull();
  }
}
