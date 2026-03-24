package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** GetImagesByReferenceResult 및 ImageItem 팩토리 단위 테스트. */
@DisplayName("GetImagesByReferenceResult / ImageItem 단위 테스트")
class GetImagesByReferenceResultTest {

  @Nested
  @DisplayName("ImageItem.from() 팩토리 메서드")
  class ImageItemFromFactoryTests {

    @Test
    @DisplayName("[TC-GET-010] COMPLETED 이미지: imageId, status=COMPLETED, finalObjectKey 올바르게 매핑")
    void from_completedImage_mapsAllFields() {
      Image image =
          Image.builder()
              .id(42L)
              .userId(1L)
              .referenceType(ImageReferenceType.COMMUNITY_FREE)
              .referenceId(1L)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/42.jpg")
              .finalObjectKey("final/42.webp")
              .imgOrder(1)
              .build();

      ImageItem item = ImageItem.from(image);

      assertThat(item.imageId()).isEqualTo(42L);
      assertThat(item.status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(item.finalObjectKey()).isEqualTo("final/42.webp");
    }

    @Test
    @DisplayName("[TC-GET-011] PENDING 이미지: imageId, status=PENDING, finalObjectKey=null로 매핑")
    void from_pendingImage_finalObjectKeyIsNull() {
      Image image =
          Image.builder()
              .id(7L)
              .userId(1L)
              .referenceType(ImageReferenceType.COMMUNITY_FREE)
              .referenceId(1L)
              .status(ImageStatus.PENDING)
              .tmpObjectKey("tmp/7.jpg")
              .finalObjectKey(null)
              .imgOrder(1)
              .build();

      ImageItem item = ImageItem.from(image);

      assertThat(item.imageId()).isEqualTo(7L);
      assertThat(item.status()).isEqualTo(ImageStatus.PENDING);
      assertThat(item.finalObjectKey()).isNull();
    }

    @Test
    @DisplayName("[GET-ITEM-3] FAILED 이미지: status=FAILED, finalObjectKey=null로 매핑")
    void from_failedImage_finalObjectKeyIsNull() {
      Image image =
          Image.builder()
              .id(8L)
              .userId(1L)
              .referenceType(ImageReferenceType.COMMUNITY_FREE)
              .referenceId(1L)
              .status(ImageStatus.FAILED)
              .tmpObjectKey("tmp/8.jpg")
              .finalObjectKey(null)
              .imgOrder(1)
              .build();

      ImageItem item = ImageItem.from(image);

      assertThat(item.imageId()).isEqualTo(8L);
      assertThat(item.status()).isEqualTo(ImageStatus.FAILED);
      assertThat(item.finalObjectKey()).isNull();
    }
  }

  @Nested
  @DisplayName("GetImagesByReferenceResult.of() 팩토리 메서드")
  class ResultOfFactoryTests {

    @Test
    @DisplayName("[RESULT-1] of(items) 반환값의 items 리스트가 동일하게 보존됨")
    void of_returnsResultWithSameItems() {
      Image image =
          Image.builder()
              .id(1L)
              .userId(1L)
              .referenceType(ImageReferenceType.COMMUNITY_FREE)
              .referenceId(1L)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey("final/1.webp")
              .imgOrder(1)
              .build();

      var items = java.util.List.of(ImageItem.from(image));
      GetImagesByReferenceResult result = GetImagesByReferenceResult.of(items);

      assertThat(result.items()).isSameAs(items);
    }
  }
}
