package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpsertStoreResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveStorePort;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("UpsertStoreService 단위 테스트")
class UpsertStoreServiceTest {

  @Mock private LoadStorePort loadStorePort;

  @Mock private SaveStorePort saveStorePort;

  @InjectMocks private UpsertStoreService upsertStoreService;

  @Captor private ArgumentCaptor<TrainerStore> storeCaptor;

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
        .xProfileUrl("https://x.com/test")
        .build();
  }

  private static TrainerStore createExistingStore() {
    return TrainerStore.builder()
        .id(100L)
        .trainerId(1L)
        .storeName("Old Studio")
        .address("서울시 강남구")
        .detailAddress("1층")
        .latitude(37.4979)
        .longitude(127.0276)
        .phoneNumber("010-0000-0000")
        .build();
  }

  // ============================================
  // execute() — 신규 생성
  // ============================================

  @Nested
  @DisplayName("execute() - 신규 생성 케이스")
  class CreateCases {

    @Test
    @DisplayName("기존 스토어가 없으면 신규 생성하고 결과를 반환한다")
    void execute_createsNewStore_whenNotExists() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      UpsertStoreResult result = upsertStoreService.execute(command);

      // then
      assertThat(result.storeId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("신규 생성 시 SaveStorePort에 id가 null인 도메인 모델을 전달한다")
    void execute_passesNullIdStore_whenCreating() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(storeCaptor.capture())).willReturn(savedStore);

      // when
      upsertStoreService.execute(command);

      // then
      TrainerStore captured = storeCaptor.getValue();
      assertThat(captured.getId()).isNull();
      assertThat(captured.getTrainerId()).isEqualTo(command.trainerId());
      assertThat(captured.getStoreName()).isEqualTo(command.storeName());
    }

    @Test
    @DisplayName("신규 생성 시 모든 필드가 올바르게 전달된다")
    void execute_passesAllFields_whenCreating() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(storeCaptor.capture())).willReturn(savedStore);

      // when
      upsertStoreService.execute(command);

      // then
      TrainerStore captured = storeCaptor.getValue();
      assertThat(captured.getAddress()).isEqualTo(command.address());
      assertThat(captured.getDetailAddress()).isEqualTo(command.detailAddress());
      assertThat(captured.getLatitude()).isEqualTo(command.latitude());
      assertThat(captured.getLongitude()).isEqualTo(command.longitude());
      assertThat(captured.getPhoneNumber()).isEqualTo(command.phoneNumber());
      assertThat(captured.getHomepageUrl()).isEqualTo(command.homepageUrl());
      assertThat(captured.getInstagramUrl()).isEqualTo(command.instagramUrl());
      assertThat(captured.getXProfileUrl()).isEqualTo(command.xProfileUrl());
    }

    @Test
    @DisplayName("선택 필드(URL)가 null인 커맨드도 정상 처리한다")
    void execute_withNullOptionalFields_succeeds() {
      // given
      UpsertStoreCommand command =
          new UpsertStoreCommand(
              1L, "Store", "Address", "Detail", 37.0, 127.0, "010-1234-5678", null, null, null);
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      UpsertStoreResult result = upsertStoreService.execute(command);

      // then
      assertThat(result.storeId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("신규 생성 시 LoadStorePort와 SaveStorePort를 각 1번 호출한다")
    void execute_callsPortsOnce_whenCreating() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      upsertStoreService.execute(command);

      // then
      then(loadStorePort).should(times(1)).findByTrainerId(1L);
      then(saveStorePort).should(times(1)).save(any(TrainerStore.class));
    }
  }

  // ============================================
  // execute() — 기존 업데이트
  // ============================================

  @Nested
  @DisplayName("execute() - 기존 업데이트 케이스")
  class UpdateCases {

    @Test
    @DisplayName("기존 스토어가 있으면 기존 ID를 이관하여 업데이트한다")
    void execute_updatesExistingStore_whenExists() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore existingStore = createExistingStore();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.of(existingStore));
      given(saveStorePort.save(storeCaptor.capture())).willReturn(savedStore);

      // when
      UpsertStoreResult result = upsertStoreService.execute(command);

      // then
      assertThat(result.storeId()).isEqualTo(100L);
      TrainerStore captured = storeCaptor.getValue();
      assertThat(captured.getId()).isEqualTo(100L); // 기존 ID 이관
      assertThat(captured.getStoreName()).isEqualTo("PT Studio"); // 새 값
    }

    @Test
    @DisplayName("업데이트 시 새 커맨드의 모든 필드가 기존 값을 덮어쓴다")
    void execute_overwritesAllFields_whenUpdating() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore existingStore = createExistingStore();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.of(existingStore));
      given(saveStorePort.save(storeCaptor.capture())).willReturn(savedStore);

      // when
      upsertStoreService.execute(command);

      // then
      TrainerStore captured = storeCaptor.getValue();
      assertThat(captured.getId()).isEqualTo(existingStore.getId());
      assertThat(captured.getStoreName()).isEqualTo(command.storeName());
      assertThat(captured.getDetailAddress()).isEqualTo(command.detailAddress());
      assertThat(captured.getPhoneNumber()).isEqualTo(command.phoneNumber());
      assertThat(captured.getHomepageUrl()).isEqualTo(command.homepageUrl());
    }

    @Test
    @DisplayName("업데이트 시에도 LoadStorePort와 SaveStorePort를 각 1번 호출한다")
    void execute_callsPortsOnce_whenUpdating() {
      // given
      UpsertStoreCommand command = createValidCommand();
      TrainerStore existingStore = createExistingStore();
      TrainerStore savedStore = createSavedStore();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.of(existingStore));
      given(saveStorePort.save(any(TrainerStore.class))).willReturn(savedStore);

      // when
      upsertStoreService.execute(command);

      // then
      then(loadStorePort).should(times(1)).findByTrainerId(1L);
      then(saveStorePort).should(times(1)).save(any(TrainerStore.class));
    }
  }

  // ============================================
  // execute() — 실패 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("command.validate() 실패 시 IllegalArgumentException이 발생한다")
    void execute_throwsException_whenCommandValidationFails() {
      // given — trainerId가 null인 커맨드
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              null, "Store", "Address", "Detail", 37.0, 127.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class);

      // LoadStorePort, SaveStorePort는 호출되지 않아야 한다
      then(loadStorePort).shouldHaveNoInteractions();
      then(saveStorePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 전화번호 포맷은 command.validate()에서 실패한다")
    void execute_throwsException_whenPhoneInvalidFormat() {
      // given — 잘못된 전화번호 포맷 (이제 command.validate()에서 잡힌다)
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "Store", "Address", "Detail", 37.0, 127.0, "abc", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Phone number");

      // command.validate()에서 실패하므로 포트 호출 없음
      then(loadStorePort).shouldHaveNoInteractions();
      then(saveStorePort).should(never()).save(any(TrainerStore.class));
    }

    @Test
    @DisplayName("SaveStorePort에서 예외 발생 시 그대로 전파된다")
    void execute_propagatesException_whenSaveFails() {
      // given
      UpsertStoreCommand command = createValidCommand();
      given(loadStorePort.findByTrainerId(1L)).willReturn(Optional.empty());
      given(saveStorePort.save(any(TrainerStore.class)))
          .willThrow(new IllegalStateException("DB error"));

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(command))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("DB error");
    }

    @Test
    @DisplayName("storeName이 blank인 커맨드는 command.validate()에서 실패한다")
    void execute_throwsException_whenStoreNameBlank() {
      // given
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "   ", "Address", "Detail", 37.0, 127.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Store name");

      then(loadStorePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("detailAddress가 blank인 커맨드는 command.validate()에서 실패한다")
    void execute_throwsException_whenDetailAddressBlank() {
      // given
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "Store", "Address", "", 37.0, 127.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Detail address");
    }

    @Test
    @DisplayName("latitude가 null인 커맨드는 command.validate()에서 실패한다")
    void execute_throwsException_whenLatitudeNull() {
      // given
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "Store", "Address", "Detail", null, 127.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Latitude");
    }

    @Test
    @DisplayName("latitude가 범위(-90~90) 초과인 커맨드는 command.validate()에서 실패한다")
    void execute_throwsException_whenLatitudeOutOfRange() {
      // given
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "Store", "Address", "Detail", -91.0, 127.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Latitude");

      then(loadStorePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("longitude가 범위(-180~180) 초과인 커맨드는 command.validate()에서 실패한다")
    void execute_throwsException_whenLongitudeOutOfRange() {
      // given
      UpsertStoreCommand invalidCommand =
          new UpsertStoreCommand(
              1L, "Store", "Address", "Detail", 37.0, 181.0, "010-1234-5678", null, null, null);

      // when & then
      assertThatThrownBy(() -> upsertStoreService.execute(invalidCommand))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Longitude");

      then(loadStorePort).shouldHaveNoInteractions();
    }
  }
}
