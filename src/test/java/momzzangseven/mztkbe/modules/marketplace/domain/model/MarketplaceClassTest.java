package momzzangseven.mztkbe.modules.marketplace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCategoryException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidDurationException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidFeatureException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidPriceException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTagException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTitleException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidTrainerIdException;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MarketplaceClass 도메인 단위 테스트")
class MarketplaceClassTest {

  // ========================================================
  // Helpers
  // ========================================================

  private static MarketplaceClass validClass() {
    return MarketplaceClass.create(
        1L,
        "PT 30분 기초체력",
        ClassCategory.PT,
        "기초 체력 향상을 위한 PT 클래스",
        30000,
        60,
        List.of("다이어트"),
        List.of("1:1 맞춤"),
        null);
  }

  // ========================================================
  // 성공 케이스
  // ========================================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-1] 유효한 입력으로 생성하면 active=true 인스턴스 반환")
    void create_ValidInput_ReturnsActiveClass() {
      MarketplaceClass result = validClass();

      assertThat(result.isActive()).isTrue();
      assertThat(result.getId()).isNull();
      assertThat(result.getTags()).containsExactly("다이어트");
      assertThat(result.getFeatures()).containsExactly("1:1 맞춤");
    }

    @Test
    @DisplayName("[M-7] toggleStatus() — active 클래스를 토글하면 inactive 반환, 원본 불변")
    void toggleStatus_ActiveClass_ReturnsInactiveClass() {
      MarketplaceClass active = validClass();
      MarketplaceClass toggled = active.toggleStatus();

      assertThat(toggled.isActive()).isFalse();
      assertThat(active.isActive()).isTrue(); // 원본 불변
    }

    @Test
    @DisplayName("[M-7b] toggleStatus() — inactive 클래스를 토글하면 active 반환")
    void toggleStatus_InactiveClass_ReturnsActiveClass() {
      MarketplaceClass inactive = validClass().toBuilder().active(false).build();
      MarketplaceClass toggled = inactive.toggleStatus();

      assertThat(toggled.isActive()).isTrue();
    }

    @Test
    @DisplayName("[M-8] isOwnedBy — 동일 trainerId 이면 true 반환")
    void isOwnedBy_MatchingId_ReturnsTrue() {
      MarketplaceClass mc = validClass();

      assertThat(mc.isOwnedBy(1L)).isTrue();
      assertThat(mc.isOwnedBy(99L)).isFalse();
    }

    @Test
    @DisplayName("isOwnedBy — null trainerId이면 false 반환 (NPE 없음)")
    void isOwnedBy_NullTrainerId_ReturnsFalse() {
      MarketplaceClass mc = validClass();
      assertThat(mc.isOwnedBy(null)).isFalse();
    }

    @Test
    @DisplayName("update() — id/trainerId 보존, 새 인스턴스 반환, 원본 불변")
    void update_PreservesIdentityAndReturnsNewInstance() {
      MarketplaceClass withId = validClass().toBuilder().id(10L).build();

      MarketplaceClass updated =
          withId.update(
              "변경된 제목",
              ClassCategory.YOGA,
              "변경된 설명",
              80000,
              90,
              List.of("요가"),
              List.of("스트레칭"),
              null);

      assertThat(updated.getId()).isEqualTo(10L);
      assertThat(updated.getTrainerId()).isEqualTo(1L);
      assertThat(updated.getTitle()).isEqualTo("변경된 제목");
      assertThat(updated.isActive()).isTrue();
      // 원본 불변
      assertThat(withId.getTitle()).isEqualTo("PT 30분 기초체력");
    }

    @Test
    @DisplayName("duration 경계값 1분으로 생성 성공")
    void create_MinDuration_Success() {
      MarketplaceClass mc =
          MarketplaceClass.create(1L, "제목", ClassCategory.PT, "설명", 10000, 1, null, null, null);
      assertThat(mc.getDurationMinutes()).isEqualTo(1);
    }

    @Test
    @DisplayName("duration 경계값 1440분으로 생성 성공")
    void create_MaxDuration_Success() {
      MarketplaceClass mc =
          MarketplaceClass.create(1L, "제목", ClassCategory.PT, "설명", 10000, 1440, null, null, null);
      assertThat(mc.getDurationMinutes()).isEqualTo(1440);
    }
  }

  // ========================================================
  // trainerId 검증
  // ========================================================

  @Nested
  @DisplayName("trainerId 검증")
  class TrainerIdValidation {

    @Test
    @DisplayName("[M-2] null trainerId → MarketplaceInvalidTrainerIdException")
    void create_NullTrainerId_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      null, "제목", ClassCategory.PT, "설명", 10000, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidTrainerIdException.class);
    }

    @Test
    @DisplayName("trainerId=0 → MarketplaceInvalidTrainerIdException")
    void create_ZeroTrainerId_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      0L, "제목", ClassCategory.PT, "설명", 10000, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidTrainerIdException.class);
    }
  }

  // ========================================================
  // 제목 검증
  // ========================================================

  @Nested
  @DisplayName("제목 검증")
  class TitleValidation {

    @Test
    @DisplayName("[M-3] 제목 101자 → MarketplaceInvalidTitleException")
    void create_TitleExceedsMaxLength_ThrowsException() {
      String longTitle = "a".repeat(101);
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, longTitle, ClassCategory.PT, "설명", 10000, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidTitleException.class);
    }

    @Test
    @DisplayName("빈 제목 → MarketplaceInvalidTitleException")
    void create_BlankTitle_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "  ", ClassCategory.PT, "설명", 10000, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidTitleException.class);
    }

    @Test
    @DisplayName("정확히 100자 제목 → 예외 없음 (경계값)")
    void create_TitleExactMaxLength_NoException() {
      String exactTitle = "a".repeat(100);
      assertThat(
              MarketplaceClass.create(
                  1L, exactTitle, ClassCategory.PT, "설명", 10000, 60, null, null, null))
          .isNotNull();
    }
  }

  // ========================================================
  // 카테고리 검증
  // ========================================================

  @Nested
  @DisplayName("카테고리 검증")
  class CategoryValidation {

    @Test
    @DisplayName("[M-4] null 카테고리 → MarketplaceInvalidCategoryException")
    void create_NullCategory_ThrowsException() {
      assertThatThrownBy(
              () -> MarketplaceClass.create(1L, "제목", null, "설명", 10000, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidCategoryException.class);
    }
  }

  // ========================================================
  // 가격 및 기간 검증
  // ========================================================

  @Nested
  @DisplayName("가격 및 기간 검증")
  class PriceAndDurationValidation {

    @Test
    @DisplayName("priceAmount=0 → MarketplaceInvalidPriceException")
    void create_ZeroPrice_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 0, 60, null, null, null))
          .isInstanceOf(MarketplaceInvalidPriceException.class);
    }

    @Test
    @DisplayName("durationMinutes=0 → MarketplaceInvalidDurationException")
    void create_ZeroDuration_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 0, null, null, null))
          .isInstanceOf(MarketplaceInvalidDurationException.class);
    }

    @Test
    @DisplayName("durationMinutes=1441 → MarketplaceInvalidDurationException")
    void create_DurationExceedsMax_ThrowsException() {
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 1441, null, null, null))
          .isInstanceOf(MarketplaceInvalidDurationException.class);
    }
  }

  // ========================================================
  // 태그 검증
  // ========================================================

  @Nested
  @DisplayName("태그 검증")
  class TagValidation {

    @Test
    @DisplayName("[M-5] 태그 4개 → MarketplaceInvalidTagException (최대 3개)")
    void create_TagsExceedMaxCount_ThrowsException() {
      List<String> tags = List.of("a", "b", "c", "d");
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, tags, null, null))
          .isInstanceOf(MarketplaceInvalidTagException.class);
    }

    @Test
    @DisplayName("[M-6] 개별 태그 31자 → MarketplaceInvalidTagException (최대 30자)")
    void create_SingleTagExceedsMaxLength_ThrowsException() {
      List<String> tags = List.of("a".repeat(31));
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, tags, null, null))
          .isInstanceOf(MarketplaceInvalidTagException.class);
    }

    @Test
    @DisplayName("null 태그 리스트 → 예외 없이 빈 리스트로 처리")
    void create_NullTags_TreatedAsEmptyList() {
      MarketplaceClass mc =
          MarketplaceClass.create(1L, "제목", ClassCategory.PT, "설명", 10000, 60, null, null, null);
      assertThat(mc.getTags()).isEmpty();
    }

    @Test
    @DisplayName("태그 항목이 blank이면 MarketplaceInvalidTagException 발생")
    void create_BlankTagItem_ThrowsException() {
      List<String> tags = List.of("정상태그", "");
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, tags, null, null))
          .isInstanceOf(MarketplaceInvalidTagException.class);
    }
  }

  // ========================================================
  // 피처 검증 (코드리뷰에서 수정·추가된 케이스)
  // ========================================================

  @Nested
  @DisplayName("feature 검증")
  class FeatureValidation {

    @Test
    @DisplayName("feature 항목이 null이면 MarketplaceInvalidFeatureException 발생")
    void create_NullFeatureItem_ThrowsException() {
      List<String> features = new ArrayList<>();
      features.add(null);
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, null, features, null))
          .isInstanceOf(MarketplaceInvalidFeatureException.class);
    }

    @Test
    @DisplayName("feature 항목이 blank이면 MarketplaceInvalidFeatureException 발생")
    void create_BlankFeatureItem_ThrowsException() {
      List<String> features = List.of("정상피처", "");
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, null, features, null))
          .isInstanceOf(MarketplaceInvalidFeatureException.class);
    }

    @Test
    @DisplayName("feature 11개 → MarketplaceInvalidFeatureException (최대 10개)")
    void create_FeaturesExceedMax_ThrowsException() {
      List<String> features = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k");
      assertThatThrownBy(
              () ->
                  MarketplaceClass.create(
                      1L, "제목", ClassCategory.PT, "설명", 10000, 60, null, features, null))
          .isInstanceOf(MarketplaceInvalidFeatureException.class);
    }
  }
}
