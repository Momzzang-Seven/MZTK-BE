package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetImageByTmpObjectKeyServiceTest {

  @Mock private LoadImagePort loadImagePort;

  private GetImageByTmpObjectKeyService service;

  @BeforeEach
  void setUp() {
    service = new GetImageByTmpObjectKeyService(loadImagePort);
  }

  @Test
  void resolvesReadObjectKeyFromFinalObjectKey() {
    Image image =
        Image.builder()
            .id(1L)
            .userId(10L)
            .referenceType(ImageReferenceType.WORKOUT)
            .status(ImageStatus.COMPLETED)
            .tmpObjectKey("private/workout/tmp.jpg")
            .finalObjectKey("private/workout/final.jpg")
            .build();
    when(loadImagePort.findByTmpObjectKey("private/workout/tmp.jpg"))
        .thenReturn(Optional.of(image));

    var result = service.execute("private/workout/tmp.jpg");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().readObjectKey()).isEqualTo("private/workout/final.jpg");
  }

  @Test
  void returnsEmptyWhenImageDoesNotExist() {
    when(loadImagePort.findByTmpObjectKey("missing")).thenReturn(Optional.empty());

    assertThat(service.execute("missing")).isEmpty();
  }

  @Test
  void resolvesReadObjectKeyWhenLockedForUpdate() {
    Image image =
        Image.builder()
            .id(2L)
            .userId(11L)
            .referenceType(ImageReferenceType.WORKOUT)
            .status(ImageStatus.COMPLETED)
            .tmpObjectKey("private/workout/locked.jpg")
            .finalObjectKey("private/workout/locked-final.jpg")
            .build();
    when(loadImagePort.findByTmpObjectKeyForUpdate("private/workout/locked.jpg"))
        .thenReturn(Optional.of(image));

    var result = service.executeForUpdate("private/workout/locked.jpg");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().readObjectKey()).isEqualTo("private/workout/locked-final.jpg");
  }
}
