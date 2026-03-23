package momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageModuleAdapter unit test")
class ImageModuleAdapterTest {

  @Mock private UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  @Mock private GetImagesByReferenceUseCase getImagesByReferenceUseCase;

  @InjectMocks private ImageModuleAdapter imageModuleAdapter;

  @Test
  @DisplayName("updateImages maps FREE post type to COMMUNITY_FREE reference")
  void updateImagesMapsFreeReferenceType() {
    imageModuleAdapter.updateImages(1L, 10L, PostType.FREE, List.of(1L, 2L));

    ArgumentCaptor<UpsertImagesByReferenceCommand> captor =
        ArgumentCaptor.forClass(UpsertImagesByReferenceCommand.class);
    verify(upsertImagesByReferenceUseCase).execute(captor.capture());

    UpsertImagesByReferenceCommand command = captor.getValue();
    assertThat(command.userId()).isEqualTo(1L);
    assertThat(command.referenceId()).isEqualTo(10L);
    assertThat(command.referenceType()).isEqualTo(ImageReferenceType.COMMUNITY_FREE);
    assertThat(command.imageIds()).containsExactly(1L, 2L);
  }

  @Test
  @DisplayName("updateImages maps QUESTION post type to COMMUNITY_QUESTION reference")
  void updateImagesMapsQuestionReferenceType() {
    imageModuleAdapter.updateImages(2L, 11L, PostType.QUESTION, List.of(3L));

    ArgumentCaptor<UpsertImagesByReferenceCommand> captor =
        ArgumentCaptor.forClass(UpsertImagesByReferenceCommand.class);
    verify(upsertImagesByReferenceUseCase).execute(captor.capture());

    assertThat(captor.getValue().referenceType()).isEqualTo(ImageReferenceType.COMMUNITY_QUESTION);
  }

  @Test
  @DisplayName("loadImages maps image module result items to post image slots")
  void loadImagesMapsResultItems() {
    when(getImagesByReferenceUseCase.execute(
            new momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand(
                ImageReferenceType.COMMUNITY_FREE, 12L)))
        .thenReturn(
            GetImagesByReferenceResult.of(
                List.of(
                    new GetImagesByReferenceResult.ImageItem(1L, "a.webp"),
                    new GetImagesByReferenceResult.ImageItem(2L, "b.webp"))));

    PostImageResult result = imageModuleAdapter.loadImages(PostType.FREE, 12L);

    assertThat(result.slots())
        .containsExactly(
            new PostImageResult.PostImageSlot(1L, "a.webp"),
            new PostImageResult.PostImageSlot(2L, "b.webp"));
  }
}
