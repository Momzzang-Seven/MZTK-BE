package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
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
@DisplayName("GetImagesByReferenceService unit test")
class GetImagesByReferenceServiceTest {

  @Mock private LoadImagePort loadImagePort;

  @InjectMocks private GetImagesByReferenceService service;

  @Test
  @DisplayName("returns all image states and expands reference type before loading")
  void executeReturnsAllImageStates() {
    GetImagesByReferenceCommand command =
        new GetImagesByReferenceCommand(ImageReferenceType.MARKET, 10L);

    when(loadImagePort.findImagesByReference(ImageReferenceType.MARKET.expand(), 10L))
        .thenReturn(
            List.of(
                image(1L, ImageReferenceType.MARKET_THUMB, ImageStatus.COMPLETED, "a.webp"),
                image(2L, ImageReferenceType.MARKET_DETAIL, ImageStatus.PENDING, null)));

    GetImagesByReferenceResult result = service.execute(command);

    assertThat(result.items())
        .containsExactly(
            new GetImagesByReferenceResult.ImageItem(1L, ImageStatus.COMPLETED, "a.webp"),
            new GetImagesByReferenceResult.ImageItem(2L, ImageStatus.PENDING, null));
    verify(loadImagePort).findImagesByReference(ImageReferenceType.MARKET.expand(), 10L);
  }

  private Image image(Long id, ImageReferenceType type, ImageStatus status, String finalObjectKey) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(type)
        .referenceId(10L)
        .status(status)
        .tmpObjectKey("tmp/" + id)
        .finalObjectKey(finalObjectKey)
        .imgOrder(id.intValue())
        .createdAt(Instant.parse("2026-03-24T00:00:00Z"))
        .updatedAt(Instant.parse("2026-03-24T00:00:00Z"))
        .build();
  }
}
