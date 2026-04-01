package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.event.TrainerStoreUpsertedEvent;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreCommandHandler 단위 테스트")
class StoreCommandHandlerTest {

  @Mock private SaveStorePort saveStorePort;

  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private StoreCommandHandler storeCommandHandler;

  @Captor private ArgumentCaptor<TrainerStore> storeCaptor;

  @Captor private ArgumentCaptor<TrainerStoreUpsertedEvent> eventCaptor;

  // ============================================
  // Test Fixtures
  // ============================================

  private static UpsertStoreCommand createValidCommand() {
    return new UpsertStoreCommand(
        1L,
        "PT Studio",
        "서울시 강남구",
        "2층",
        37.4979,
        127.0276,
        "010-1234-5678",
        "https://example.com",
        "https://instagram.com/test",
        "https://x.com/test");
  }

  private static TrainerStore createSavedStore() {
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
        .xUrl("https://x.com/test")
        .build();
  }

  // ============================================
  // execute() — 성공 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("유효한 커맨드로 스토어를 upsert하고 결과를 반환한다")
    void execute_upsertStoreAndReturnsResult() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      UpsertStoreResult result = storeCommandHandler.execute(command);

      // then
      assertThat(result.storeId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("SaveStorePort에 올바른 도메인 모델을 전달한다")
    void execute_passesCorrectDomainModelToSavePort() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(saveStorePort.save(storeCaptor.capture())).willReturn(savedStore);

      // when
      storeCommandHandler.execute(command);

      // then
      TrainerStore captured = storeCaptor.getValue();
      assertThat(captured.getTrainerId()).isEqualTo(command.trainerId());
      assertThat(captured.getStoreName()).isEqualTo(command.storeName());
      assertThat(captured.getAddress()).isEqualTo(command.address());
      assertThat(captured.getDetailAddress()).isEqualTo(command.detailAddress());
      assertThat(captured.getLatitude()).isEqualTo(command.latitude());
      assertThat(captured.getLongitude()).isEqualTo(command.longitude());
    }

    @Test
    @DisplayName("도메인 이벤트(TrainerStoreUpsertedEvent)를 발행한다")
    void execute_publishesDomainEvent() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      storeCommandHandler.execute(command);

      // then
      then(eventPublisher).should(times(1)).publishEvent(eventCaptor.capture());
      TrainerStoreUpsertedEvent event = eventCaptor.getValue();
      assertThat(event.storeId()).isEqualTo(100L);
      assertThat(event.trainerId()).isEqualTo(1L);
      assertThat(event.eventId()).isNotNull();
      assertThat(event.occurredAt()).isNotNull();
    }
  }

  // ============================================
  // execute() — 실패 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("도메인 검증 실패 시 IllegalArgumentException이 발생한다")
    void execute_throwsException_whenDomainValidationFails() {
      // given — trainerId가 null인 커맨드 (도메인 검증에서 걸림)
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              null, "Store", "Address", null, 37.0, 127.0, null, null, null, null);

      // when & then
      assertThatThrownBy(() -> storeCommandHandler.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class);

      // SaveStorePort는 호출되지 않아야 한다
      then(saveStorePort).shouldHaveNoInteractions();
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("SaveStorePort에서 예외 발생 시 이벤트를 발행하지 않는다")
    void execute_doesNotPublishEvent_whenSaveFails() {
      // given
      UpsertStoreCommand command = createValidCommand();
      given(saveStorePort.save(any(TrainerStore.class)))
          .willThrow(new IllegalStateException("DB error"));

      // when & then
      assertThatThrownBy(() -> storeCommandHandler.execute(command))
          .isInstanceOf(IllegalStateException.class);

      then(eventPublisher).shouldHaveNoInteractions();
    }
  }
}
