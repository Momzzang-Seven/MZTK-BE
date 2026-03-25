package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetImagesByReferencesResult 단위 테스트")
class GetImagesByReferencesResultTest {

  @Test
  @DisplayName("null image list는 빈 리스트로 정규화하고 referenceId 순서를 유지한다")
  void of_normalizesNullListsAndKeepsKeyOrder() {
    Map<Long, List<ImageItem>> itemsByReferenceId = new LinkedHashMap<>();
    itemsByReferenceId.put(31L, null);
    itemsByReferenceId.put(32L, List.of(new ImageItem(100L, ImageStatus.COMPLETED, "a.webp")));

    GetImagesByReferencesResult result = GetImagesByReferencesResult.of(itemsByReferenceId);

    assertThat(result.itemsByReferenceId().keySet()).containsExactly(31L, 32L);
    assertThat(result.itemsByReferenceId().get(31L)).isEmpty();
    assertThat(result.itemsByReferenceId().get(32L))
        .containsExactly(new ImageItem(100L, ImageStatus.COMPLETED, "a.webp"));
  }
}
