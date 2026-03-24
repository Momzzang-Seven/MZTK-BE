package momzzangseven.mztkbe.modules.image.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * ImageReferenceType 단위 테스트.
 *
 * <p>리팩터링 후 도메인은 S3 경로 조립 책임을 갖지 않는다. 경로 prefix 매핑은 S3PresignedUrlAdapter (infrastructure)에서 테스트한다.
 * 도메인은 isRequestFacing() 비즈니스 분류만 담당한다.
 */
@DisplayName("ImageReferenceType 단위 테스트")
class ImageReferenceTypeTest {

  @Nested
  @DisplayName("[D-3] isRequestFacing() — 요청 가능 타입 여부")
  class IsRequestFacingTests {

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {
          "COMMUNITY_FREE",
          "COMMUNITY_QUESTION",
          "COMMUNITY_ANSWER",
          "MARKET",
          "MARKET_STORE",
          "WORKOUT"
        })
    @DisplayName("클라이언트 요청 가능 타입은 true를 반환한다")
    void isRequestFacing_returnsTrue(ImageReferenceType type) {
      assertThat(type.isRequestFacing()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {"MARKET_THUMB", "MARKET_DETAIL", "MARKET_STORE_THUMB", "MARKET_STORE_DETAIL"})
    @DisplayName(
        "내부 전용 타입(MARKET_THUMB, MARKET_DETAIL, MARKET_STORE_THUMB, MARKET_STORE_DETAIL)은 false를 반환한다")
    void isRequestFacing_returnsFalse_forInternalTypes(ImageReferenceType type) {
      assertThat(type.isRequestFacing()).isFalse();
    }
  }

  @Nested
  @DisplayName("virtual type helpers")
  class VirtualTypeTests {

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {"MARKET", "MARKET_STORE"})
    @DisplayName("virtual request-facing types return true from isVirtual()")
    void isVirtual_returnsTrue(ImageReferenceType type) {
      assertThat(type.isVirtual()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"MARKET", "MARKET_STORE"})
    @DisplayName("non-virtual types return false from isVirtual()")
    void isVirtual_returnsFalse(ImageReferenceType type) {
      assertThat(type.isVirtual()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
        value = ImageReferenceType.class,
        names = {"MARKET", "MARKET_STORE"})
    @DisplayName("expand returns concrete stored subtypes for virtual types")
    void expand_returnsConcreteTypes(ImageReferenceType type) {
      assertThat(type.expand())
          .isEqualTo(
              type == ImageReferenceType.MARKET
                  ? java.util.List.of(
                      ImageReferenceType.MARKET_THUMB, ImageReferenceType.MARKET_DETAIL)
                  : java.util.List.of(
                      ImageReferenceType.MARKET_STORE_THUMB,
                      ImageReferenceType.MARKET_STORE_DETAIL));
    }
  }
}
