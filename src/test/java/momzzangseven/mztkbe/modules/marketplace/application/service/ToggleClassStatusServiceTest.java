package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
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

  private static final Long TRAINER_ID = 1L;
  private static final Long CLASS_ID = 10L;

  private static MarketplaceClass classWithActive(boolean active) {
    return MarketplaceClass.builder()
        .id(CLASS_ID)
        .trainerId(TRAINER_ID)
        .title("PT 60분")
        .category(ClassCategory.PT)
        .description("설명")
        .priceAmount(50000)
        .durationMinutes(60)
        .active(active)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-21] active=true 클래스 토글 → inactive 반환, 제재 체크 없음")
    void execute_ToggleActiveToInactive_SavesAndReturnsInactive() {
      // given
      MarketplaceClass activeClass = classWithActive(true);
      MarketplaceClass inactiveClass = classWithActive(false);

      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(activeClass));
      given(saveClassPort.save(any())).willReturn(inactiveClass);

      // when
      ToggleClassStatusResult result =
          toggleClassStatusService.execute(new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID));

      // then
      assertThat(result.active()).isFalse();
      // 비활성화 시 제재 체크 불필요
      verify(loadTrainerSanctionPort, never()).hasActiveSanction(any());
      verify(saveClassPort, times(1)).save(any());
    }

    @Test
    @DisplayName("active=false 클래스 토글 → active=true 반환 (제재 없을 때)")
    void execute_ToggleInactiveToActive_ReturnsActive() {
      // given
      MarketplaceClass inactiveClass = classWithActive(false);
      MarketplaceClass activeClass = classWithActive(true);

      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(inactiveClass));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(false);
      given(saveClassPort.save(any())).willReturn(activeClass);

      // when
      ToggleClassStatusResult result =
          toggleClassStatusService.execute(new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID));

      // then
      assertThat(result.active()).isTrue();
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("클래스 미존재 → ClassNotFoundException")
    void execute_ClassNotFound_ThrowsClassNotFoundException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () ->
                  toggleClassStatusService.execute(
                      new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID)))
          .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("타 트레이너 접근 → MarketplaceUnauthorizedAccessException")
    void execute_UnauthorizedTrainer_ThrowsException() {
      // given
      Long OTHER_TRAINER = 99L;
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(classWithActive(true)));

      // when & then
      assertThatThrownBy(
              () ->
                  toggleClassStatusService.execute(
                      new ToggleClassStatusCommand(OTHER_TRAINER, CLASS_ID)))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
      verify(saveClassPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-22] 제재 트레이너 재활성화 → TrainerSuspendedException")
    void execute_ReactivateSuspendedTrainer_ThrowsTrainerSuspendedException() {
      // given
      given(loadClassPort.findById(CLASS_ID)).willReturn(Optional.of(classWithActive(false)));
      given(loadTrainerSanctionPort.hasActiveSanction(TRAINER_ID)).willReturn(true);

      // when & then
      assertThatThrownBy(
              () ->
                  toggleClassStatusService.execute(
                      new ToggleClassStatusCommand(TRAINER_ID, CLASS_ID)))
          .isInstanceOf(TrainerSuspendedException.class);
      verify(saveClassPort, never()).save(any());
    }
  }
}
