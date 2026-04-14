package momzzangseven.mztkbe.modules.user.infrastructure.external.level.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import momzzangseven.mztkbe.modules.level.application.dto.GetMyLevelResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GetMyLevelUseCase;
import momzzangseven.mztkbe.modules.user.application.dto.UserLevelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserLevelAdapter 단위 테스트")
class UserLevelAdapterTest {

  @Mock private GetMyLevelUseCase getMyLevelUseCase;

  @InjectMocks private UserLevelAdapter adapter;

  @Test
  @DisplayName(
      "[M-11] UserLevelAdapter가 GetMyLevelResult를 UserLevelInfo로 변환한다 (availableXp → currentXp 매핑 확인)")
  void loadLevelInfo_mapsAvailableXpToCurrentXpCorrectly() {
    // given
    given(getMyLevelUseCase.execute(1L))
        .willReturn(
            GetMyLevelResult.builder()
                .level(4)
                .availableXp(80)
                .requiredXpForNext(150)
                .rewardMztkForNext(100)
                .build());

    // when
    UserLevelInfo info = adapter.loadLevelInfo(1L);

    // then
    assertThat(info.level()).isEqualTo(4);
    assertThat(info.currentXp()).isEqualTo(80);
    assertThat(info.requiredXpForNextLevel()).isEqualTo(150);
  }
}
