package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.ApplyAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReleaseAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReserveAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
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
@DisplayName("ManageAnswerUpdateImagesService")
class ManageAnswerUpdateImagesServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @Mock private UpdateImagePort updateImagePort;

  @InjectMocks private ManageAnswerUpdateImagesService service;

  @Test
  @DisplayName("reservePendingImages reserves only unlinked answer images for update state")
  void reservePendingImages_reservesUnlinkedAnswerImages() {
    Image image = image(1L, 10L, ImageReferenceType.COMMUNITY_ANSWER, null, null);
    when(loadImagePort.findImagesByIdInForUpdate(List.of(1L))).thenReturn(List.of(image));

    service.reservePendingImages(
        new ReserveAnswerUpdateImagesCommand(100L, 10L, 200L, List.of(1L)));

    ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
    verify(updateImagePort).updateAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getReferenceType())
        .isEqualTo(ImageReferenceType.COMMUNITY_ANSWER_UPDATE);
    assertThat(captor.getValue().get(0).getReferenceId()).isEqualTo(100L);
  }

  @Test
  @DisplayName("applyPendingImages detaches removed images and applies requested order")
  void applyPendingImages_detachesRemovedAndAppliesRequestedOrder() {
    Image current = image(1L, 10L, ImageReferenceType.COMMUNITY_ANSWER, 200L, 1);
    Image requested = image(2L, 10L, ImageReferenceType.COMMUNITY_ANSWER_UPDATE, 100L, null);
    when(loadImagePort.findImagesByIdInForUpdate(List.of(2L))).thenReturn(List.of(requested));
    when(loadImagePort.findImagesByReferenceForUpdate(
            ImageReferenceType.COMMUNITY_ANSWER.expand(), 200L))
        .thenReturn(List.of(current));

    service.applyPendingImages(new ApplyAnswerUpdateImagesCommand(100L, 10L, 200L, List.of(2L)));

    ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
    verify(updateImagePort).updateAll(captor.capture());
    assertThat(captor.getValue()).hasSize(2);
    assertThat(captor.getValue().get(0).getId()).isEqualTo(1L);
    assertThat(captor.getValue().get(0).getReferenceId()).isNull();
    assertThat(captor.getValue().get(1).getId()).isEqualTo(2L);
    assertThat(captor.getValue().get(1).getReferenceType())
        .isEqualTo(ImageReferenceType.COMMUNITY_ANSWER);
    assertThat(captor.getValue().get(1).getReferenceId()).isEqualTo(200L);
    assertThat(captor.getValue().get(1).getImgOrder()).isEqualTo(1);
  }

  @Test
  @DisplayName("releasePendingImages restores update-reserved images to answer pool")
  void releasePendingImages_restoresUpdateReservedImages() {
    Image pending = image(3L, 10L, ImageReferenceType.COMMUNITY_ANSWER_UPDATE, 100L, null);
    when(loadImagePort.findImagesByReferenceIds(
            ImageReferenceType.COMMUNITY_ANSWER_UPDATE.expand(), List.of(100L)))
        .thenReturn(List.of(pending));

    service.releasePendingImages(new ReleaseAnswerUpdateImagesCommand(List.of(100L)));

    ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
    verify(updateImagePort).updateAll(captor.capture());
    assertThat(captor.getValue().get(0).getReferenceType())
        .isEqualTo(ImageReferenceType.COMMUNITY_ANSWER);
    assertThat(captor.getValue().get(0).getReferenceId()).isNull();
  }

  @Test
  @DisplayName("empty reserve request returns without loading images")
  void reservePendingImages_emptyRequestReturns() {
    service.reservePendingImages(new ReserveAnswerUpdateImagesCommand(100L, 10L, 200L, List.of()));

    verify(loadImagePort, never()).findImagesByIdInForUpdate(any());
    verify(updateImagePort, never()).updateAll(any());
  }

  @Test
  @DisplayName("duplicate image ids are rejected")
  void duplicateImageIdsRejected() {
    assertThatThrownBy(
            () ->
                service.reservePendingImages(
                    new ReserveAnswerUpdateImagesCommand(100L, 10L, 200L, List.of(1L, 1L))))
        .isInstanceOf(InvalidImageRefTypeException.class);
  }

  @Test
  @DisplayName("images owned by another user are rejected")
  void imageOwnedByAnotherUserRejected() {
    when(loadImagePort.findImagesByIdInForUpdate(List.of(1L)))
        .thenReturn(List.of(image(1L, 99L, ImageReferenceType.COMMUNITY_ANSWER, null, null)));

    assertThatThrownBy(
            () ->
                service.reservePendingImages(
                    new ReserveAnswerUpdateImagesCommand(100L, 10L, 200L, List.of(1L))))
        .isInstanceOf(ImageNotBelongsToUserException.class);
  }

  private Image image(
      Long id,
      Long userId,
      ImageReferenceType referenceType,
      Long referenceId,
      Integer imageOrder) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(referenceType)
        .referenceId(referenceId)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id)
        .finalObjectKey("final/" + id)
        .imgOrder(imageOrder)
        .build();
  }
}
