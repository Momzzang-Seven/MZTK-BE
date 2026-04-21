package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToggleClassStatusService 단위 테스트")
class ToggleClassStatusServiceTest {

  @Mock private LoadClassPort loadClassPort;
  @Mock private SaveClassPort saveClassPort;
  @Mock private LoadTrainerSanctionPort loadTrainerSanctionPort;

  @InjectMocks private ToggleClassStatusService toggleClassStatusService;

  // ========================================================
  // Helpers
  // ========================================================

  private static final Long TRAINER_ID = 1L;
  private static final Long CLASS_ID = 10L;

  private static MarketplaceClass activeClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("PT 클래스")
        .category(ClassCategory.PT)
        .description("설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(true)
        .build();
  }

  private static MarketplaceClass inactiveClass() {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("PT 클래스")
        .category(ClassCategory.PT)
        .description("설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(false)
        .build();
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-21] 활성 클래스 비활성화 → active=false 반환, 제재 체크 없음")
    void execute_ToggleActiveToInactive_SavesAndReturnsInactive() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(activeClass()));
      given(saveClassPort.save(any())).willReturn(inactiveClass());

      // when
      ToggleClassStatusResult result =
          toggleClassStatusService.execute(new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID));

      // then
      assertThat(result.active()).isFalse();
      // 비활성화 시 제재 체크를 하지 않아야 한다 (불필요한 외부 호출 방지)
      verify(loadTrainerSanctionPort, never()).hasActiveSanction(any());
    }
  }

  // ========================================================
  // 실패 케이스
  // ========================================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-22] 제재 중 재활성화 시도 → TrainerSuspendedException")
    void execute_ReactivateSuspendedTrainer_ThrowsTrainerSuspendedException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(inactiveClass()));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);

      // when & then
      assertThatThrownBy(
              () ->
                  toggleClassStatusService.execute(
                      new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID)))
          .isInstanceOf(TrainerSuspendedException.class);
      // 제재 확인 후 save는 호출되지 않아야 한다
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("정상 트레이너 비활성 → 활성화 성공 (제재 해제 상태)")
    void execute_ReactivateNonSuspendedTrainer_Succeeds() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(inactiveClass()));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(saveClassPort.save(any())).willReturn(activeClass());

      // when
      ToggleClassStatusResult result =
          toggleClassStatusService.execute(new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID));

      // then
      assertThat(result.active()).isTrue();
    }
  }
}
