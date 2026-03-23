package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.InvalidImageExtensionException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("IssuePresignedUrlCommand 단위 테스트")
class IssuePresignedUrlCommandTest {

  @Nested
  @DisplayName("userId 검증")
  class UserIdValidation {

    @Test
    @DisplayName("userId가 null이면 UserNotAuthenticatedException 발생")
    void validate_throwsException_whenUserIdIsNull() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(null, ImageReferenceType.COMMUNITY_FREE, List.of("a.jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(UserNotAuthenticatedException.class);
    }

    @Test
    @DisplayName("userId가 0 이하이면 UserNotAuthenticatedException 발생")
    void validate_throwsException_whenUserIdIsZeroOrNegative() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(0L, ImageReferenceType.COMMUNITY_FREE, List.of("a.jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(UserNotAuthenticatedException.class);
    }
  }

  @Nested
  @DisplayName("[E-3/E-4] 내부 전용 referenceType 차단")
  class InternalReferenceTypeValidation {

    @Test
    @DisplayName("내부 전용 타입 MARKET_CLASS_THUMB 요청 시 InvalidImageRefTypeException 발생")
    void validate_throwsException_whenMarketClassThumb() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS_THUMB, List.of("photo.jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageRefTypeException.class);
    }

    @Test
    @DisplayName("내부 전용 타입 MARKET_CLASS_DETAIL 요청 시 InvalidImageRefTypeException 발생")
    void validate_throwsException_whenMarketClassDetail() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.MARKET_CLASS_DETAIL, List.of("photo.jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageRefTypeException.class);
    }
  }

  @Nested
  @DisplayName("[E-9~E-13] 파일명/확장자 검증")
  class FilenameExtensionValidation {

    @Test
    @DisplayName("[E-9] 확장자 없는 파일명은 InvalidImageExtensionException 발생")
    void validate_throwsException_whenNoExtension() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("filename"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageExtensionException.class);
    }

    @Test
    @DisplayName("[E-10] 점으로 시작하는 파일명(.jpg 숨김파일 형태)은 InvalidImageExtensionException 발생")
    void validate_throwsException_whenHiddenFileLike() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of(".jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageExtensionException.class);
    }

    @Test
    @DisplayName("[E-11] 허용되지 않는 확장자(webp)는 InvalidImageExtensionException 발생")
    void validate_throwsException_whenWebpExtension() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.webp"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageExtensionException.class);
    }

    @Test
    @DisplayName("[E-13] 혼합 리스트(허용+불허)에서 불허 확장자가 있으면 예외 발생")
    void validate_throwsException_whenMixedExtensions() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("valid.jpg", "invalid.webp"));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageExtensionException.class);
    }

    @Test
    @DisplayName("[E-12] double extension(file.php.jpg)은 마지막 확장자 기준으로 허용됨 — 예외 없이 통과")
    void validate_passes_forDoubleExtensionEndingWithAllowed() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(
              1L, ImageReferenceType.COMMUNITY_FREE, List.of("file.php.jpg"));
      assertThatCode(command::validate).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("[E-14~E-16] 이미지 수 정책 검증")
  class ImageCountPolicyValidation {

    @Test
    @DisplayName("[E-14] WORKOUT에 2장 요청 시 ImageMaxCountExceedException 발생")
    void validate_throwsException_whenWorkoutExceedsLimit() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.WORKOUT, List.of("a.jpg", "b.jpg"));
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }

    @Test
    @DisplayName("[E-15] MARKET_CLASS에 6장 요청 시 ImageMaxCountExceedException 발생")
    void validate_throwsException_whenMarketClassExceedsLimit() {
      List<String> filenames = List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg", "6.jpg");
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_CLASS, filenames);
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }

    @Test
    @DisplayName("[E-16] COMMUNITY_FREE에 11장 요청 시 ImageMaxCountExceedException 발생")
    void validate_throwsException_whenCommunityExceedsLimit() {
      List<String> filenames = Collections.nCopies(11, "photo.jpg");
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, filenames);
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }
  }

  @Nested
  @DisplayName("정상 케이스 — validate() 통과")
  class HappyPath {

    @Test
    @DisplayName("[H-2] WORKOUT 1장(최대) validate() 통과")
    void validate_passes_forWorkoutSingleImage() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.WORKOUT, List.of("exercise.jpg"));
      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[H-8] MARKET_CLASS 5장(최대) validate() 통과")
    void validate_passes_forMarketClassMaxImages() {
      List<String> filenames = List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg");
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_CLASS, filenames);
      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[H-3] COMMUNITY_FREE 10장(최대) validate() 통과")
    void validate_passes_forCommunityMaxImages() {
      List<String> filenames = Collections.nCopies(10, "photo.jpg");
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, filenames);
      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"file.jpg", "file.jpeg", "file.png", "file.gif", "file.heic", "file.heif"})
    @DisplayName("[H-9] 허용된 모든 확장자는 validate() 통과")
    void validate_passes_forAllAllowedExtensions(String filename) {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.COMMUNITY_FREE, List.of(filename));
      assertThatCode(command::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MARKET_CLASS 타입은 요청 가능 타입이므로 validate() 통과")
    void validate_passes_forMarketClassType() {
      IssuePresignedUrlCommand command =
          new IssuePresignedUrlCommand(1L, ImageReferenceType.MARKET_CLASS, List.of("product.jpg"));
      assertThatCode(command::validate).doesNotThrowAnyException();
    }
  }
}
