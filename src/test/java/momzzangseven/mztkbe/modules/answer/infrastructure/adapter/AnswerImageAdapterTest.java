package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult.AnswerImageSlot;
import momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerImageStorageProperties;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerImageAdapter unit test")
class AnswerImageAdapterTest {

  @Mock private UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  @Mock private GetImagesByReferenceUseCase getImagesByReferenceUseCase;
  @Mock private AnswerImageStorageProperties answerImageStorageProperties;

  @InjectMocks private AnswerImageAdapter answerImageAdapter;

  @Test
  @DisplayName("updateImages maps answer image sync to COMMUNITY_ANSWER")
  void updateImagesMapsCommunityAnswerReference() {
    answerImageAdapter.updateImages(7L, 31L, List.of(100L, 101L));

    ArgumentCaptor<UpsertImagesByReferenceCommand> captor =
        ArgumentCaptor.forClass(UpsertImagesByReferenceCommand.class);
    verify(upsertImagesByReferenceUseCase).execute(captor.capture());

    UpsertImagesByReferenceCommand command = captor.getValue();
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.referenceId()).isEqualTo(31L);
    assertThat(command.referenceType()).isEqualTo(ImageReferenceType.COMMUNITY_ANSWER);
    assertThat(command.imageIds()).containsExactly(100L, 101L);
  }

  @Test
  @DisplayName(
      "loadImagesByAnswerIds maps final object keys to public urls and preserves null slots")
  void loadImagesByAnswerIdsBuildsPublicUrls() {
    when(answerImageStorageProperties.getUrlPrefix())
        .thenReturn("https://test-bucket.s3.ap-northeast-2.amazonaws.com");
    when(getImagesByReferenceUseCase.execute(
            new GetImagesByReferenceCommand(ImageReferenceType.COMMUNITY_ANSWER, 31L)))
        .thenReturn(
            GetImagesByReferenceResult.of(
                List.of(
                    new GetImagesByReferenceResult.ImageItem(
                        100L, ImageStatus.COMPLETED, "/answers/first.webp"),
                    new GetImagesByReferenceResult.ImageItem(101L, ImageStatus.PENDING, null))));

    Map<Long, AnswerImageResult> result = answerImageAdapter.loadImagesByAnswerIds(List.of(31L));

    assertThat(result)
        .containsEntry(
            31L,
            new AnswerImageResult(
                List.of(
                    new AnswerImageSlot(
                        100L,
                        "https://test-bucket.s3.ap-northeast-2.amazonaws.com/answers/first.webp"),
                    new AnswerImageSlot(101L, null))));
  }

  @Test
  @DisplayName("loadImagesByAnswerIds returns empty map for empty ids")
  void loadImagesByAnswerIdsReturnsEmptyMapForEmptyInput() {
    assertThat(answerImageAdapter.loadImagesByAnswerIds(List.of())).isEmpty();
    verifyNoInteractions(getImagesByReferenceUseCase);
  }
}
