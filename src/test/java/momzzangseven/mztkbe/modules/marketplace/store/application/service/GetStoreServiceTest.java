package momzzangseven.mztkbe.modules.marketplace.store.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.StoreNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.GetStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.store.application.dto.GetStoreResult;
import momzzangseven.mztkbe.modules.marketplace.store.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.store.domain.model.TrainerStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetStoreService 단위 테스트")
class GetStoreServiceTest {

  @Mock private LoadStorePort loadStorePort;

  @InjectMocks private GetStoreService getStoreService;

  // ============================================
  // Test Fixtures
  // ============================================

  private static TrainerStore createExistingStore() {
    return TrainerStore.builder()
        .id(100L)
        .trainerId(1L)
        .storeName("PT Studio")
        .address("서울시 강남구")
        .detailAddress("2층")
        .latitude(37.4979)
        .longitude(127.0276)
        .phoneNumber("010-1234-5678")
        .homepageUrl("https://example.com")
        .instagramUrl("https://instagram.com/test")
        .xProfileUrl("https://x.com/test")
        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
        .updatedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
        .build();
  }

  // ============================================
  // execute() — 성공 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("존재하는 스토어를 조회하면 결과를 반환한다")
    void execute_returnsResult_whenStoreExists() {
      // given
      Long trainerId = 1L;
      GetStoreCommand command = new GetStoreCommand(trainerId);
      TrainerStore existingStore = createExistingStore();
      given(loadStorePort.findByTrainerId(trainerId)).willReturn(Optional.of(existingStore));

      // when
      GetStoreResult result = getStoreService.execute(command);

      // then
      assertThat(result.storeId()).isEqualTo(100L);
      assertThat(result.storeName()).isEqualTo("PT Studio");
      assertThat(result.address()).isEqualTo("서울시 강남구");
      assertThat(result.detailAddress()).isEqualTo("2층");
      assertThat(result.latitude()).isEqualTo(37.4979);
      assertThat(result.longitude()).isEqualTo(127.0276);
      assertThat(result.phoneNumber()).isEqualTo("010-1234-5678");
      assertThat(result.homepageUrl()).isEqualTo("https://example.com");
      assertThat(result.instagramUrl()).isEqualTo("https://instagram.com/test");
      assertThat(result.xProfileUrl()).isEqualTo("https://x.com/test");
    }

    @Test
    @DisplayName("LoadStorePort를 정확히 1번 호출한다")
    void execute_callsLoadStorePortOnce() {
      // given
      Long trainerId = 1L;
      GetStoreCommand command = new GetStoreCommand(trainerId);
      given(loadStorePort.findByTrainerId(trainerId))
          .willReturn(Optional.of(createExistingStore()));

      // when
      getStoreService.execute(command);

      // then
      then(loadStorePort).should(times(1)).findByTrainerId(trainerId);
    }
  }

  // ============================================
  // execute() — 실패 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("스토어가 없으면 StoreNotFoundException이 발생한다")
    void execute_throwsStoreNotFoundException_whenNotFound() {
      // given
      Long trainerId = 999L;
      GetStoreCommand command = new GetStoreCommand(trainerId);
      given(loadStorePort.findByTrainerId(trainerId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> getStoreService.execute(command))
          .isInstanceOf(StoreNotFoundException.class);
    }
  }
}
