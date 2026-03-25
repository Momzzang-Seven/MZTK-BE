package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.config.ImagePostOrphanCleanupProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImagePostOrphanCleanupService 단위 테스트")
class ImagePostOrphanCleanupServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @Mock private DeleteImagePort deleteImagePort;
  @Mock private ImagePostOrphanCleanupProperties props;

  @InjectMocks private ImagePostOrphanCleanupService cleanupService;

  @BeforeEach
  void setUp() {
    given(props.getBatchSize()).willReturn(100);
  }

  @Test
  @DisplayName("고아 post-linked 이미지가 없으면 0을 반환하고 unlink를 호출하지 않는다")
  void runBatch_noOrphanImages_returnsZero() {
    given(loadImagePort.findOrphanPostImages(100)).willReturn(List.of());

    int result = cleanupService.runBatch();

    assertThat(result).isZero();
    verify(deleteImagePort, never()).unlinkImagesByIdIn(any());
  }

  @Test
  @DisplayName("고아 post-linked 이미지를 일괄 unlink 한다")
  void runBatch_unlinksOrphanImages() {
    given(loadImagePort.findOrphanPostImages(100)).willReturn(List.of(image(10L), image(11L)));

    int result = cleanupService.runBatch();

    assertThat(result).isEqualTo(2);
    verify(deleteImagePort).unlinkImagesByIdIn(List.of(10L, 11L));
  }

  private Image image(long id) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(ImageReferenceType.COMMUNITY_FREE)
        .referenceId(31L)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey("final/" + id + ".webp")
        .imgOrder(1)
        .build();
  }
}
