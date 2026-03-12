package momzzangseven.mztkbe.modules.image.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.image.InvalidObjectKeyBuildException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("ImageReferenceType 단위 테스트")
class ImageReferenceTypeTest {

  @Nested
  @DisplayName("[D-2] buildTmpObjectKey() — S3 경로 매핑 검증")
  class BuildTmpObjectKeyTests {

    @Test
    @DisplayName("COMMUNITY_FREE → public/community/free/tmp/{uuid}.jpg")
    void buildTmpObjectKey_communityFree() {
      String key = ImageReferenceType.COMMUNITY_FREE.buildTmpObjectKey("uuid", "jpg");
      assertThat(key).isEqualTo("public/community/free/tmp/uuid.jpg");
    }

    @Test
    @DisplayName("COMMUNITY_QUESTION → public/community/question/tmp/{uuid}.jpg")
    void buildTmpObjectKey_communityQuestion() {
      String key = ImageReferenceType.COMMUNITY_QUESTION.buildTmpObjectKey("uuid", "jpg");
      assertThat(key).isEqualTo("public/community/question/tmp/uuid.jpg");
    }

    @Test
    @DisplayName("COMMUNITY_ANSWER → public/community/answer/tmp/{uuid}.jpg")
    void buildTmpObjectKey_communityAnswer() {
      String key = ImageReferenceType.COMMUNITY_ANSWER.buildTmpObjectKey("uuid", "jpg");
      assertThat(key).isEqualTo("public/community/answer/tmp/uuid.jpg");
    }

    @Test
    @DisplayName("MARKET_THUMB → public/market/thumb/tmp/{uuid}.jpg")
    void buildTmpObjectKey_marketThumb() {
      String key = ImageReferenceType.MARKET_THUMB.buildTmpObjectKey("uuid", "jpg");
      assertThat(key).isEqualTo("public/market/thumb/tmp/uuid.jpg");
    }

    @Test
    @DisplayName("MARKET_DETAIL → public/market/detail/tmp/{uuid}.png")
    void buildTmpObjectKey_marketDetail() {
      String key = ImageReferenceType.MARKET_DETAIL.buildTmpObjectKey("uuid", "png");
      assertThat(key).isEqualTo("public/market/detail/tmp/uuid.png");
    }

    @Test
    @DisplayName("WORKOUT → private/workout/{uuid}.jpg (tmp/ 서브폴더 없음)")
    void buildTmpObjectKey_workout() {
      String key = ImageReferenceType.WORKOUT.buildTmpObjectKey("uuid", "jpg");
      assertThat(key).isEqualTo("private/workout/uuid.jpg");
      assertThat(key).doesNotContain("tmp/");
    }

    @Test
    @DisplayName("MARKET(virtual type)에서 buildTmpObjectKey 호출 시 InvalidObjectKeyBuildException 발생")
    void buildTmpObjectKey_market_throwsException() {
      assertThatThrownBy(() -> ImageReferenceType.MARKET.buildTmpObjectKey("uuid", "jpg"))
          .isInstanceOf(InvalidObjectKeyBuildException.class);
    }
  }

  @Nested
  @DisplayName("[D-3] isRequestFacing() — 요청 가능 타입 여부")
  class IsRequestFacingTests {

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {"COMMUNITY_FREE", "COMMUNITY_QUESTION", "COMMUNITY_ANSWER", "MARKET", "WORKOUT"})
    @DisplayName("클라이언트 요청 가능 타입은 true를 반환한다")
    void isRequestFacing_returnsTrue(ImageReferenceType type) {
      assertThat(type.isRequestFacing()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {"MARKET_THUMB", "MARKET_DETAIL"})
    @DisplayName("내부 전용 타입(MARKET_THUMB, MARKET_DETAIL)은 false를 반환한다")
    void isRequestFacing_returnsFalse_forInternalTypes(ImageReferenceType type) {
      assertThat(type.isRequestFacing()).isFalse();
    }
  }
}
