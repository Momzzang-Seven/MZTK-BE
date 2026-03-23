package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** GetImagesByReferenceService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetImagesByReferenceService 단위 테스트")
class GetImagesByReferenceServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @InjectMocks private GetImagesByReferenceService service;

  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  private Image completedImage(long id, int order, String finalKey) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(FREE)
        .referenceId(1L)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(finalKey)
        .imgOrder(order)
        .build();
  }

  private Image pendingImage(long id, int order) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(FREE)
        .referenceId(1L)
        .status(ImageStatus.PENDING)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(order)
        .build();
  }

  private Image failedImage(long id, int order) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(FREE)
        .referenceId(1L)
        .status(ImageStatus.FAILED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(order)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[TC-GET-001] COMPLETED 이미지만 있는 경우 status=COMPLETED, finalObjectKey 포함하여 반환")
    void execute_onlyCompleted_returnsItemsWithFinalKey() {
      given(loadImagePort.findImagesByReference(FREE, 1L))
          .willReturn(
              List.of(
                  completedImage(10, 1, "imgs/10.webp"), completedImage(11, 2, "imgs/11.webp")));

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(FREE, 1L));

      assertThat(result.items()).hasSize(2);
      ImageItem item0 = result.items().get(0);
      assertThat(item0.imageId()).isEqualTo(10L);
      assertThat(item0.status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(item0.finalObjectKey()).isEqualTo("imgs/10.webp");

      ImageItem item1 = result.items().get(1);
      assertThat(item1.imageId()).isEqualTo(11L);
      assertThat(item1.status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(item1.finalObjectKey()).isEqualTo("imgs/11.webp");
    }

    @Test
    @DisplayName("[TC-GET-002] PENDING/FAILED 이미지도 items에 포함되며 finalObjectKey는 null")
    void execute_mixedStatus_allIncludedWithNullKeyForNonCompleted() {
      given(loadImagePort.findImagesByReference(FREE, 1L))
          .willReturn(
              List.of(
                  pendingImage(10, 1), failedImage(11, 2), completedImage(12, 3, "imgs/12.webp")));

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(FREE, 1L));

      assertThat(result.items()).hasSize(3);
      assertThat(result.items().get(0).imageId()).isEqualTo(10L);
      assertThat(result.items().get(0).status()).isEqualTo(ImageStatus.PENDING);
      assertThat(result.items().get(0).finalObjectKey()).isNull();

      assertThat(result.items().get(1).imageId()).isEqualTo(11L);
      assertThat(result.items().get(1).status()).isEqualTo(ImageStatus.FAILED);
      assertThat(result.items().get(1).finalObjectKey()).isNull();

      assertThat(result.items().get(2).imageId()).isEqualTo(12L);
      assertThat(result.items().get(2).status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(result.items().get(2).finalObjectKey()).isEqualTo("imgs/12.webp");
    }

    @Test
    @DisplayName("[TC-GET-003] 모든 이미지가 PENDING/FAILED인 경우에도 items에 포함 (finalObjectKey=null)")
    void execute_allPendingOrFailed_allIncludedWithNullKey() {
      given(loadImagePort.findImagesByReference(FREE, 1L))
          .willReturn(List.of(pendingImage(10, 1), failedImage(11, 2)));

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(FREE, 1L));

      assertThat(result.items()).hasSize(2);
      assertThat(result.items().get(0).imageId()).isEqualTo(10L);
      assertThat(result.items().get(0).status()).isEqualTo(ImageStatus.PENDING);
      assertThat(result.items().get(0).finalObjectKey()).isNull();

      assertThat(result.items().get(1).imageId()).isEqualTo(11L);
      assertThat(result.items().get(1).status()).isEqualTo(ImageStatus.FAILED);
      assertThat(result.items().get(1).finalObjectKey()).isNull();
    }

    @Test
    @DisplayName("[TC-GET-004] referenceId에 연결된 이미지가 없으면 빈 items 반환")
    void execute_noImages_returnsEmptyItems() {
      given(loadImagePort.findImagesByReference(FREE, 1L)).willReturn(List.of());

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(FREE, 1L));

      assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("[TC-GET-005] MARKET_STORE referenceType으로 조회 가능 (신규 타입 지원 확인)")
    void execute_marketStoreType_returnsItems() {
      given(loadImagePort.findImagesByReference(ImageReferenceType.MARKET_STORE, 5L))
          .willReturn(
              List.of(
                  Image.builder()
                      .id(20L)
                      .userId(1L)
                      .referenceType(ImageReferenceType.MARKET_STORE)
                      .referenceId(5L)
                      .status(ImageStatus.COMPLETED)
                      .tmpObjectKey("tmp/20.jpg")
                      .finalObjectKey("store/5/thumb.webp")
                      .imgOrder(1)
                      .build()));

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(ImageReferenceType.MARKET_STORE, 5L));

      assertThat(result.items()).hasSize(1);
      assertThat(result.items().get(0).imageId()).isEqualTo(20L);
      assertThat(result.items().get(0).status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(result.items().get(0).finalObjectKey()).isEqualTo("store/5/thumb.webp");
    }

    @Test
    @DisplayName("[TC-GET-006] img_order 순서대로 정렬되어 반환 (로드 순서 그대로 유지)")
    void execute_returnsItemsInPortOrder() {
      given(loadImagePort.findImagesByReference(FREE, 1L))
          .willReturn(
              List.of(
                  completedImage(3, 1, "k3.webp"),
                  completedImage(1, 2, "k1.webp"),
                  completedImage(2, 3, "k2.webp")));

      GetImagesByReferenceResult result =
          service.execute(new GetImagesByReferenceCommand(FREE, 1L));

      assertThat(result.items()).extracting(ImageItem::imageId).containsExactly(3L, 1L, 2L);
    }
  }

  @Nested
  @DisplayName("엣지 케이스 — validate()")
  class EdgeCases {

    @Test
    @DisplayName("[TC-GET-007] referenceType=null → IllegalArgumentException, LoadImagePort 호출 없음")
    void execute_nullReferenceType_throwsAndSkipsPort() {
      assertThatThrownBy(() -> service.execute(new GetImagesByReferenceCommand(null, 1L)))
          .isInstanceOf(IllegalArgumentException.class);

      verify(loadImagePort, never()).findImagesByReference(null, null);
    }

    @Test
    @DisplayName("[TC-GET-008] referenceId=null → IllegalArgumentException")
    void execute_nullReferenceId_throws() {
      assertThatThrownBy(() -> service.execute(new GetImagesByReferenceCommand(FREE, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-GET-009] referenceId=0 또는 음수 → IllegalArgumentException")
    void execute_nonPositiveReferenceId_throws(long referenceId) {
      assertThatThrownBy(() -> service.execute(new GetImagesByReferenceCommand(FREE, referenceId)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
