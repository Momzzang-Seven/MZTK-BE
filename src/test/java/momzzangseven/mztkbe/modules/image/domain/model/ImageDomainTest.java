package momzzangseven.mztkbe.modules.image.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.global.error.image.ImageStatusInvalidException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Image 도메인 모델 상태 전이 단위 테스트. */
@DisplayName("Image 도메인 상태 전이 테스트")
class ImageDomainTest {

  private Image pendingImage() {
    return Image.createPending(1L, ImageReferenceType.COMMUNITY_FREE, "tmp.jpg", 1);
  }

  // ========== Happy Day ==========

  @Nested
  @DisplayName("성공 케이스 — PENDING에서의 정상 전이")
  class SuccessCases {

    @Test
    @DisplayName("[H-1] PENDING → complete() → COMPLETED, finalObjectKey 설정, errorReason=null")
    void complete_fromPending_succeeds() {
      Image result = pendingImage().complete("final.webp");

      assertThat(result.getStatus()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(result.getFinalObjectKey()).isEqualTo("final.webp");
      assertThat(result.getErrorReason()).isNull();
    }

    @Test
    @DisplayName("[H-2] PENDING → fail(errorReason) → FAILED, finalObjectKey=null, errorReason 저장")
    void fail_fromPending_savesErrorReason() {
      Image result = pendingImage().fail("OOM error");

      assertThat(result.getStatus()).isEqualTo(ImageStatus.FAILED);
      assertThat(result.getFinalObjectKey()).isNull();
      assertThat(result.getErrorReason()).isEqualTo("OOM error");
    }

    @Test
    @DisplayName("[H-3] PENDING → fail(null) → FAILED, errorReason=null (errorReason은 선택)")
    void fail_fromPending_withNullErrorReason_succeeds() {
      Image result = pendingImage().fail(null);

      assertThat(result.getStatus()).isEqualTo(ImageStatus.FAILED);
      assertThat(result.getErrorReason()).isNull();
    }
  }

  // ========== Edge Cases — 잘못된 상태 전이 ==========

  @Nested
  @DisplayName("실패 케이스 — COMPLETED 상태에서의 잘못된 전이")
  class CompletedStateFailureCases {

    @Test
    @DisplayName("[E-1] COMPLETED 상태에서 complete() 재호출 → ImageStatusInvalidException")
    void complete_fromCompleted_throws() {
      Image completed = pendingImage().complete("final.webp");

      assertThatThrownBy(() -> completed.complete("another.webp"))
          .isInstanceOf(ImageStatusInvalidException.class);
    }

    @Test
    @DisplayName("[E-2] COMPLETED 상태에서 fail() 호출 → ImageStatusInvalidException")
    void fail_fromCompleted_throws() {
      Image completed = pendingImage().complete("final.webp");

      assertThatThrownBy(() -> completed.fail("reason"))
          .isInstanceOf(ImageStatusInvalidException.class);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — FAILED 상태에서의 잘못된 전이")
  class FailedStateFailureCases {

    @Test
    @DisplayName("[E-3] FAILED 상태에서 fail() 재호출 → ImageStatusInvalidException")
    void fail_fromFailed_throws() {
      Image failed = pendingImage().fail("original error");

      assertThatThrownBy(() -> failed.fail("retry reason"))
          .isInstanceOf(ImageStatusInvalidException.class);
    }

    @Test
    @DisplayName("[E-4] FAILED 상태에서 complete() 호출 → ImageStatusInvalidException")
    void complete_fromFailed_throws() {
      Image failed = pendingImage().fail("original error");

      assertThatThrownBy(() -> failed.complete("final.webp"))
          .isInstanceOf(ImageStatusInvalidException.class);
    }
  }

  // ========== 불변성 검증 ==========

  @Nested
  @DisplayName("불변성 — 상태 전이 후 원본 객체는 변하지 않는다")
  class ImmutabilityTests {

    @Test
    @DisplayName("[Immut-1] complete() 호출 후 원본의 status는 여전히 PENDING")
    void complete_doesNotMutateOriginal() {
      Image original = pendingImage();
      original.complete("final.webp");

      assertThat(original.getStatus()).isEqualTo(ImageStatus.PENDING);
      assertThat(original.getFinalObjectKey()).isNull();
    }

    @Test
    @DisplayName("[Immut-2] fail() 호출 후 원본의 status는 여전히 PENDING")
    void fail_doesNotMutateOriginal() {
      Image original = pendingImage();
      original.fail("some error");

      assertThat(original.getStatus()).isEqualTo(ImageStatus.PENDING);
      assertThat(original.getErrorReason()).isNull();
    }

    @Test
    @DisplayName("[Immut-3] complete()가 반환한 새 인스턴스는 원본과 다른 객체이다")
    void complete_returnsNewInstance() {
      Image original = pendingImage();
      Image completed = original.complete("final.webp");

      assertThat(completed).isNotSameAs(original);
    }

    @Test
    @DisplayName("[Immut-4] fail()이 반환한 새 인스턴스는 원본과 다른 객체이다")
    void fail_returnsNewInstance() {
      Image original = pendingImage();
      Image failed = original.fail("error");

      assertThat(failed).isNotSameAs(original);
    }
  }

  // ========== createPending 팩토리 검증 ==========

  @Nested
  @DisplayName("createPending() 팩토리 메서드")
  class CreatePendingTests {

    @Test
    @DisplayName("[Factory-1] createPending() 반환값은 status=PENDING, errorReason=null 포함 초기값 검증")
    void createPending_hasCorrectInitialState() {
      Image image =
          Image.createPending(42L, ImageReferenceType.COMMUNITY_FREE, "public/tmp/uuid.jpg", 1);

      assertThat(image.getStatus()).isEqualTo(ImageStatus.PENDING);
      assertThat(image.getFinalObjectKey()).isNull();
      assertThat(image.getErrorReason()).isNull();
      assertThat(image.getReferenceId()).isNull();
      assertThat(image.getUserId()).isEqualTo(42L);
      assertThat(image.getTmpObjectKey()).isEqualTo("public/tmp/uuid.jpg");
      assertThat(image.getImgOrder()).isEqualTo(1);
      assertThat(image.getReferenceType()).isEqualTo(ImageReferenceType.COMMUNITY_FREE);
    }
  }
}
