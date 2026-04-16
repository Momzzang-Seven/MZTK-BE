package momzzangseven.mztkbe.modules.marketplace.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceInvalidCategoryException;
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
      // when
      MarketplaceClass result = validClass();

      // then
      assertThat(result.isActive()).isTrue();
      assertThat(result.getId()).isNull();
      assertThat(result.getTags()).containsExactly("다이어트");
      assertThat(result.getFeatures()).containsExactly("1:1 맞춤");
    }

    @Test
    @DisplayName("[M-7] toggleStatus() — active 클래스를 토글하면 inactive 반환, 원본 불변")
    void toggleStatus_ActiveClass_ReturnsInactiveClass() {
      // given
      MarketplaceClass active = validClass();

      // when
      MarketplaceClass toggled = active.toggleStatus();

      // then
      assertThat(toggled.isActive()).isFalse();
      assertThat(active.isActive()).isTrue(); // 원본 불변
    }

    @Test
    @DisplayName("[M-8] isOwnedBy — 동일 trainerId 이면 true 반환")
    void isOwnedBy_MatchingId_ReturnsTrue() {
      // given
      MarketplaceClass mc = validClass();

      // then
      assertThat(mc.isOwnedBy(1L)).isTrue();
      assertThat(mc.isOwnedBy(99L)).isFalse();
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
  }
}
