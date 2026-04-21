package momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ValidatePostAttachableImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.ValidatePostAttachableImagesUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.external.image.config.PostImageStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageModuleAdapter unit test")
class ImageModuleAdapterTest {

  private static final String URL_PREFIX = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/";

  @Mock private UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  @Mock private GetImagesByReferenceUseCase getImagesByReferenceUseCase;
  @Mock private GetImagesByReferencesUseCase getImagesByReferencesUseCase;
  @Mock private ValidatePostAttachableImagesUseCase validatePostAttachableImagesUseCase;
  @Mock private PostImageStorageProperties postImageStorageProperties;

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
  @DisplayName("validateAttachableImages maps post type and post id")
  void validateAttachableImagesMapsReferenceType() {
    imageModuleAdapter.validateAttachableImages(3L, 12L, PostType.FREE, List.of(7L, 8L));

    ArgumentCaptor<ValidatePostAttachableImagesCommand> captor =
        ArgumentCaptor.forClass(ValidatePostAttachableImagesCommand.class);
    verify(validatePostAttachableImagesUseCase).execute(captor.capture());

    assertThat(captor.getValue().userId()).isEqualTo(3L);
    assertThat(captor.getValue().referenceId()).isEqualTo(12L);
    assertThat(captor.getValue().referenceType()).isEqualTo(ImageReferenceType.COMMUNITY_FREE);
    assertThat(captor.getValue().imageIds()).containsExactly(7L, 8L);
  }

  @Test
  @DisplayName("loadImages maps final object keys to public image urls")
  void loadImagesMapsResultItems() {
    when(postImageStorageProperties.getUrlPrefix()).thenReturn(URL_PREFIX);
    when(getImagesByReferenceUseCase.execute(
            new momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand(
                ImageReferenceType.COMMUNITY_FREE, 12L)))
        .thenReturn(
            GetImagesByReferenceResult.of(
                List.of(
                    new ImageItem(1L, ImageStatus.COMPLETED, "a.webp"),
                    new ImageItem(2L, ImageStatus.COMPLETED, "b.webp"))));

    PostImageResult result = imageModuleAdapter.loadImages(PostType.FREE, 12L);

    assertThat(result.slots())
        .containsExactly(
            new PostImageResult.PostImageSlot(1L, URL_PREFIX + "a.webp"),
            new PostImageResult.PostImageSlot(2L, URL_PREFIX + "b.webp"));
  }

  @Nested
  @DisplayName("loadImagesByPostIds")
  class LoadImagesByPostIds {

    @Test
    @DisplayName("[M-9] returns empty map for null input without calling downstream")
    void returnsEmptyMapForNullInput() {
      Map<Long, PostImageResult> result = imageModuleAdapter.loadImagesByPostIds(null);

      assertThat(result).isEmpty();
      verify(getImagesByReferencesUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("[M-10] returns empty map for empty input without calling downstream")
    void returnsEmptyMapForEmptyInput() {
      Map<Long, PostImageResult> result = imageModuleAdapter.loadImagesByPostIds(Map.of());

      assertThat(result).isEmpty();
      verify(getImagesByReferencesUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("[M-11] skips types with empty id list and forwards only non-empty ones")
    void skipsTypesWithEmptyIdList() {
      given(getImagesByReferencesUseCase.execute(any()))
          .willReturn(GetImagesByReferencesResult.of(Map.of()));

      Map<PostType, List<Long>> input =
          Map.of(PostType.FREE, List.of(), PostType.QUESTION, List.of(3L));

      imageModuleAdapter.loadImagesByPostIds(input);

      ArgumentCaptor<GetImagesByReferencesCommand> captor =
          ArgumentCaptor.forClass(GetImagesByReferencesCommand.class);
      verify(getImagesByReferencesUseCase, times(1)).execute(captor.capture());
      assertThat(captor.getValue().referenceType())
          .isEqualTo(ImageReferenceType.COMMUNITY_QUESTION);
      assertThat(captor.getValue().referenceIds()).containsExactly(3L);
    }

    @Test
    @DisplayName("[M-12] deduplicates postIds while preserving first-seen order")
    void deduplicatesPostIds() {
      given(getImagesByReferencesUseCase.execute(any()))
          .willReturn(GetImagesByReferencesResult.of(Map.of()));

      imageModuleAdapter.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L, 1L, 2L)));

      ArgumentCaptor<GetImagesByReferencesCommand> captor =
          ArgumentCaptor.forClass(GetImagesByReferencesCommand.class);
      verify(getImagesByReferencesUseCase).execute(captor.capture());
      assertThat(captor.getValue().referenceIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("[M-13] merges results across multiple PostTypes")
    void mergesResultsAcrossTypes() {
      given(postImageStorageProperties.getUrlPrefix()).willReturn(URL_PREFIX);

      given(
              getImagesByReferencesUseCase.execute(
                  new GetImagesByReferencesCommand(
                      ImageReferenceType.COMMUNITY_FREE, List.of(1L, 2L))))
          .willReturn(
              GetImagesByReferencesResult.of(
                  Map.of(1L, List.of(new ImageItem(10L, ImageStatus.COMPLETED, "k1")))));
      given(
              getImagesByReferencesUseCase.execute(
                  new GetImagesByReferencesCommand(
                      ImageReferenceType.COMMUNITY_QUESTION, List.of(3L))))
          .willReturn(
              GetImagesByReferencesResult.of(
                  Map.of(3L, List.of(new ImageItem(30L, ImageStatus.COMPLETED, "k3")))));

      Map<Long, PostImageResult> merged =
          imageModuleAdapter.loadImagesByPostIds(
              Map.of(PostType.FREE, List.of(1L, 2L), PostType.QUESTION, List.of(3L)));

      assertThat(merged).containsOnlyKeys(1L, 3L);
      assertThat(merged.get(1L).slots())
          .containsExactly(new PostImageResult.PostImageSlot(10L, URL_PREFIX + "k1"));
      assertThat(merged.get(3L).slots())
          .containsExactly(new PostImageResult.PostImageSlot(30L, URL_PREFIX + "k3"));
    }

    @Test
    @DisplayName("[M-14] maps finalObjectKey via buildImageUrl")
    void mapsFinalObjectKeyViaBuildImageUrl() {
      given(postImageStorageProperties.getUrlPrefix()).willReturn(URL_PREFIX);
      given(getImagesByReferencesUseCase.execute(any()))
          .willReturn(
              GetImagesByReferencesResult.of(
                  Map.of(
                      1L,
                      List.of(
                          new ImageItem(10L, ImageStatus.COMPLETED, "posts/free/1/main.webp")))));

      Map<Long, PostImageResult> merged =
          imageModuleAdapter.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L)));

      assertThat(merged.get(1L).slots())
          .containsExactly(
              new PostImageResult.PostImageSlot(10L, URL_PREFIX + "posts/free/1/main.webp"));
    }

    @Test
    @DisplayName("[M-15] blank finalObjectKey yields null URL")
    void blankFinalObjectKeyYieldsNullUrl() {
      given(getImagesByReferencesUseCase.execute(any()))
          .willReturn(
              GetImagesByReferencesResult.of(
                  Map.of(1L, List.of(new ImageItem(10L, ImageStatus.PENDING, "")))));

      Map<Long, PostImageResult> merged =
          imageModuleAdapter.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L)));

      assertThat(merged.get(1L).slots())
          .containsExactly(new PostImageResult.PostImageSlot(10L, null));
    }

    @Test
    @DisplayName("[M-16] empty slot list for postId with no images")
    void emptySlotListForPostIdWithNoImages() {
      given(getImagesByReferencesUseCase.execute(any()))
          .willReturn(GetImagesByReferencesResult.of(Map.of(1L, List.of())));

      Map<Long, PostImageResult> merged =
          imageModuleAdapter.loadImagesByPostIds(Map.of(PostType.FREE, List.of(1L)));

      assertThat(merged).containsKey(1L);
      assertThat(merged.get(1L).slots()).isEmpty();
    }
  }
}
