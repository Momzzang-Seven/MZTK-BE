package momzzangseven.mztkbe.modules.marketplace.store.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.store.application.dto.UpsertStoreCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("UpsertStoreRequestDTO 단위 테스트")
class UpsertStoreRequestDTOTest {

  // ============================================
  // Test Fixtures
  // ============================================

  private static UpsertStoreRequestDTO createValidDTO() {
    return new UpsertStoreRequestDTO(
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

  // ============================================
  // toCommand() — blank → null 정규화
  // ============================================

  @Nested
  @DisplayName("toCommand() - blank→null 정규화")
  class BlankToNullNormalization {

    @Test
    @DisplayName("필수 필드는 그대로 전달된다")
    void toCommand_requiredFields_passedAsIs() {
      // given
      UpsertStoreRequestDTO dto = createValidDTO();

      // when
      UpsertStoreCommand command = dto.toCommand(1L);

      // then
      assertThat(command.trainerId()).isEqualTo(1L);
      assertThat(command.storeName()).isEqualTo("PT Studio");
      assertThat(command.address()).isEqualTo("서울시 강남구");
      assertThat(command.detailAddress()).isEqualTo("2층");
      assertThat(command.latitude()).isEqualTo(37.4979);
      assertThat(command.longitude()).isEqualTo(127.0276);
      assertThat(command.phoneNumber()).isEqualTo("010-1234-5678");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("homepageUrl이 blank이면 null로 정규화된다")
    void toCommand_blankHomepageUrl_normalizedToNull(String homepageUrl) {
      // given
      UpsertStoreRequestDTO dto =
          new UpsertStoreRequestDTO(
              "Store", "Address", "Detail", 37.0, 127.0, "010-1234-5678", homepageUrl, null, null);

      // when
      UpsertStoreCommand command = dto.toCommand(1L);

      // then
      assertThat(command.homepageUrl()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("instagramUrl이 blank이면 null로 정규화된다")
    void toCommand_blankInstagramUrl_normalizedToNull(String instagramUrl) {
      // given
      UpsertStoreRequestDTO dto =
          new UpsertStoreRequestDTO(
              "Store", "Address", "Detail", 37.0, 127.0, "010-1234-5678", null, instagramUrl, null);

      // when
      UpsertStoreCommand command = dto.toCommand(1L);

      // then
      assertThat(command.instagramUrl()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("xProfileUrl이 blank이면 null로 정규화된다")
    void toCommand_blankXProfileUrl_normalizedToNull(String xProfileUrl) {
      // given
      UpsertStoreRequestDTO dto =
          new UpsertStoreRequestDTO(
              "Store", "Address", "Detail", 37.0, 127.0, "010-1234-5678", null, null, xProfileUrl);

      // when
      UpsertStoreCommand command = dto.toCommand(1L);

      // then
      assertThat(command.xProfileUrl()).isNull();
    }

    @Test
    @DisplayName("유효한 URL 값은 그대로 전달된다")
    void toCommand_validUrls_passedAsIs() {
      // given
      UpsertStoreRequestDTO dto = createValidDTO();

      // when
      UpsertStoreCommand command = dto.toCommand(1L);

      // then
      assertThat(command.homepageUrl()).isEqualTo("https://example.com");
      assertThat(command.instagramUrl()).isEqualTo("https://instagram.com/test");
      assertThat(command.xProfileUrl()).isEqualTo("https://x.com/test");
    }
  }
}
