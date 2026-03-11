package momzzangseven.mztkbe.modules.image.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AllowedImageExtension 단위 테스트")
class AllowedImageExtensionTest {

  @Nested
  @DisplayName("[D-1] isAllowed() — 허용되지 않는 입력")
  class NotAllowedCases {

    @ParameterizedTest
    @NullSource
    @DisplayName("null 입력은 false를 반환한다")
    void isAllowed_returnsFalse_forNull(String filename) {
      assertThat(AllowedImageExtension.isAllowed(filename)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ".", ".jpg", "filename", "file.webp", "archive.tar.gz"})
    @DisplayName("확장자 없음/숨김파일/미허용 확장자는 false를 반환한다")
    void isAllowed_returnsFalse_forInvalidInputs(String filename) {
      assertThat(AllowedImageExtension.isAllowed(filename)).isFalse();
    }

    @Test
    @DisplayName("점 하나만 있는 파일명은 false를 반환한다")
    void isAllowed_returnsFalse_forDotOnly() {
      assertThat(AllowedImageExtension.isAllowed(".")).isFalse();
    }

    @Test
    @DisplayName("숨김파일 형태(.jpg) — lastIndexOf('.')==0이므로 false를 반환한다")
    void isAllowed_returnsFalse_forHiddenFileLike() {
      assertThat(AllowedImageExtension.isAllowed(".jpg")).isFalse();
    }

    @Test
    @DisplayName("허용되지 않는 확장자 webp는 false를 반환한다")
    void isAllowed_returnsFalse_forWebp() {
      assertThat(AllowedImageExtension.isAllowed("file.webp")).isFalse();
    }

    @Test
    @DisplayName("허용되지 않는 복합 확장자 tar.gz는 false를 반환한다")
    void isAllowed_returnsFalse_forTarGz() {
      assertThat(AllowedImageExtension.isAllowed("archive.tar.gz")).isFalse();
    }
  }

  @Nested
  @DisplayName("[D-1] isAllowed() — 허용된 입력")
  class AllowedCases {

    @ParameterizedTest
    @ValueSource(strings = {"file.jpg", "file.jpeg", "file.png", "file.gif", "file.heic", "file.heif"})
    @DisplayName("허용된 소문자 확장자는 true를 반환한다")
    void isAllowed_returnsTrue_forAllowedLowercaseExtensions(String filename) {
      assertThat(AllowedImageExtension.isAllowed(filename)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"file.JPG", "file.JPEG", "file.PNG", "FILE.JPG"})
    @DisplayName("대문자 확장자도 대소문자 무관하게 true를 반환한다")
    void isAllowed_returnsTrue_forUppercaseExtensions(String filename) {
      assertThat(AllowedImageExtension.isAllowed(filename)).isTrue();
    }

    @Test
    @DisplayName("double extension 파일은 마지막 확장자 기준으로 판단 — file.php.jpg → true")
    void isAllowed_returnsTrue_forDoubleExtensionEndingWithAllowed() {
      assertThat(AllowedImageExtension.isAllowed("file.php.jpg")).isTrue();
    }
  }
}
