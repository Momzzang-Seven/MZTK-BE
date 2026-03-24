package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetImagesByReferencesService unit test")
class GetImagesByReferencesServiceTest {

  @Mock private LoadImagePort loadImagePort;

  @InjectMocks private GetImagesByReferencesService service;

  @Test
  @DisplayName("groups images by reference id and preserves per-reference order")
  void executeGroupsImagesByReferenceId() {
    GetImagesByReferencesCommand command =
        new GetImagesByReferencesCommand(ImageReferenceType.COMMUNITY_ANSWER, List.of(10L, 11L));

    when(loadImagePort.findImagesByReferenceIds(
            ImageReferenceType.COMMUNITY_ANSWER.expand(), List.of(10L, 11L)))
        .thenReturn(
            List.of(
                image(100L, 10L, 1, "answers/first.webp"),
                pendingImage(101L, 10L, 2),
                image(102L, 11L, 1, "answers/third.webp")));

    GetImagesByReferencesResult result = service.execute(command);

    Map<Long, List<GetImagesByReferencesResult.ImageItem>> expected = new LinkedHashMap<>();
    expected.put(
        10L,
        List.of(
            new GetImagesByReferencesResult.ImageItem(
                100L, ImageStatus.COMPLETED, "answers/first.webp"),
            new GetImagesByReferencesResult.ImageItem(101L, ImageStatus.PENDING, null)));
    expected.put(
        11L,
        List.of(
            new GetImagesByReferencesResult.ImageItem(
                102L, ImageStatus.COMPLETED, "answers/third.webp")));

    assertThat(result.itemsByReferenceId()).isEqualTo(expected);
    verify(loadImagePort)
        .findImagesByReferenceIds(ImageReferenceType.COMMUNITY_ANSWER.expand(), List.of(10L, 11L));
  }

  @Test
  @DisplayName("returns empty result without loading when referenceIds are empty")
  void executeReturnsEmptyResultWhenReferenceIdsEmpty() {
    GetImagesByReferencesResult result =
        service.execute(
            new GetImagesByReferencesCommand(ImageReferenceType.COMMUNITY_ANSWER, List.of()));

    assertThat(result.itemsByReferenceId()).isEmpty();
    verifyNoInteractions(loadImagePort);
  }

  private Image image(Long id, Long referenceId, int imgOrder, String finalObjectKey) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(ImageReferenceType.COMMUNITY_ANSWER)
        .referenceId(referenceId)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id)
        .finalObjectKey(finalObjectKey)
        .imgOrder(imgOrder)
        .createdAt(Instant.parse("2026-03-24T00:00:00Z"))
        .updatedAt(Instant.parse("2026-03-24T00:00:00Z"))
        .build();
  }

  private Image pendingImage(Long id, Long referenceId, int imgOrder) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(ImageReferenceType.COMMUNITY_ANSWER)
        .referenceId(referenceId)
        .status(ImageStatus.PENDING)
        .tmpObjectKey("tmp/" + id)
        .finalObjectKey(null)
        .imgOrder(imgOrder)
        .createdAt(Instant.parse("2026-03-24T00:00:00Z"))
        .updatedAt(Instant.parse("2026-03-24T00:00:00Z"))
        .build();
  }
}
