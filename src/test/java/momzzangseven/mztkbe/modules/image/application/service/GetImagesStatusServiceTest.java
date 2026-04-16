package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.LookupStatus;
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
@DisplayName("GetImagesStatusService unit test")
class GetImagesStatusServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @InjectMocks private GetImagesStatusService service;

  private Image image(long id, long userId, ImageStatus status) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(ImageReferenceType.COMMUNITY_FREE)
        .referenceId(null)
        .status(status)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .build();
  }

  @Test
  @DisplayName("returns mapped statuses for owned images")
  void execute_returnsMappedStatuses() {
    given(loadImagePort.findImagesByIdIn(List.of(1L, 2L, 3L)))
        .willReturn(
            List.of(
                image(1L, 10L, ImageStatus.PENDING),
                image(2L, 10L, ImageStatus.COMPLETED),
                image(3L, 10L, ImageStatus.FAILED)));

    GetImagesStatusResult result =
        service.execute(new GetImagesStatusCommand(10L, List.of(1L, 2L, 3L)));

    assertThat(result.images())
        .extracting(item -> item.status())
        .containsExactly(LookupStatus.PENDING, LookupStatus.COMPLETED, LookupStatus.FAILED);
  }

  @Test
  @DisplayName("missing image ids are returned as NOT_FOUND")
  void execute_missingImage_returnsNotFound() {
    given(loadImagePort.findImagesByIdIn(List.of(1L, 999L)))
        .willReturn(List.of(image(1L, 10L, ImageStatus.COMPLETED)));

    GetImagesStatusResult result =
        service.execute(new GetImagesStatusCommand(10L, List.of(1L, 999L)));

    assertThat(result.images())
        .extracting(item -> item.status())
        .containsExactly(LookupStatus.COMPLETED, LookupStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("foreign images are masked as NOT_FOUND")
  void execute_foreignImage_returnsNotFound() {
    given(loadImagePort.findImagesByIdIn(List.of(7L)))
        .willReturn(List.of(image(7L, 99L, ImageStatus.COMPLETED)));

    GetImagesStatusResult result = service.execute(new GetImagesStatusCommand(10L, List.of(7L)));

    assertThat(result.images().get(0).status()).isEqualTo(LookupStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("duplicates preserve order and cardinality")
  void execute_duplicatesPreserveOrder() {
    given(loadImagePort.findImagesByIdIn(List.of(1L, 2L, 2L)))
        .willReturn(
            List.of(image(1L, 10L, ImageStatus.PENDING), image(2L, 10L, ImageStatus.COMPLETED)));

    GetImagesStatusResult result =
        service.execute(new GetImagesStatusCommand(10L, List.of(1L, 2L, 2L)));

    assertThat(result.images()).extracting(item -> item.imageId()).containsExactly(1L, 2L, 2L);
    assertThat(result.images())
        .extracting(item -> item.status())
        .containsExactly(LookupStatus.PENDING, LookupStatus.COMPLETED, LookupStatus.COMPLETED);
  }
}
