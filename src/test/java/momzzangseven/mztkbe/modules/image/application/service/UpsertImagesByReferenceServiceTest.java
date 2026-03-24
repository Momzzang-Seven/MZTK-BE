package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UpsertImagesByReferenceService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpsertImagesByReferenceService 단위 테스트")
class UpsertImagesByReferenceServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @Mock private DeleteImagePort deleteImagePort;
  @Mock private DeleteS3ObjectPort deleteS3ObjectPort;
  @Mock private UpdateImagePort updateImagePort;

  @InjectMocks private UpsertImagesByReferenceService service;

  private static final Long USER_ID = 10L;
  private static final Long REF_ID = 5L;
  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  private Image completedImage(long id, Long userId, Long refId, String finalKey) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(FREE)
        .referenceId(refId)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(finalKey)
        .imgOrder((int) id)
        .build();
  }

  private Image pendingImage(long id, Long userId, Long refId) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(FREE)
        .referenceId(refId)
        .status(ImageStatus.PENDING)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder((int) id)
        .build();
  }

  private Image failedImage(long id, Long userId, Long refId) {
    return Image.builder()
        .id(id)
        .userId(userId)
        .referenceType(FREE)
        .referenceId(refId)
        .status(ImageStatus.FAILED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder((int) id)
        .build();
  }

  // ======================== Phase 1: 삭제 Phase ========================

  @Nested
  @DisplayName("Phase 1 — 삭제 Phase")
  class DeletePhaseTests {

    @Test
    @DisplayName("[TC-UPD-DEL-001] 기존 3장 중 1장 제거: COMPLETED 이미지 S3 삭제 + unlink")
    void execute_removesOneCompleted_deletesS3AndUnlinks() {
      List<Image> existing =
          List.of(
              completedImage(1L, USER_ID, REF_ID, "a/1.webp"),
              completedImage(2L, USER_ID, REF_ID, "a/2.webp"),
              completedImage(3L, USER_ID, REF_ID, "a/3.webp"));
      List<Image> retained =
          List.of(
              completedImage(1L, USER_ID, REF_ID, "a/1.webp"),
              completedImage(3L, USER_ID, REF_ID, "a/3.webp"));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L, 3L))).willReturn(retained);
      given(updateImagePort.updateAll(any())).willReturn(retained);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L, 3L)));

      verify(deleteS3ObjectPort).deleteObject("a/2.webp");
      verify(deleteImagePort).unlinkImagesByIdIn(List.of(2L));
      verify(deleteS3ObjectPort, org.mockito.Mockito.never()).deleteObject("a/1.webp");
      verify(deleteS3ObjectPort, org.mockito.Mockito.never()).deleteObject("a/3.webp");
    }

    @Test
    @DisplayName("[TC-UPD-DEL-002] 삭제 대상이 PENDING 상태이면 S3 삭제 없이 unlink만")
    void execute_removedPending_skipsS3DeleteUnlinksOnly() {
      List<Image> existing =
          List.of(
              pendingImage(1L, USER_ID, REF_ID), completedImage(2L, USER_ID, REF_ID, "a/2.webp"));
      List<Image> retained = List.of(completedImage(2L, USER_ID, REF_ID, "a/2.webp"));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);
      given(loadImagePort.findImagesByIdInForUpdate(List.of(2L))).willReturn(retained);
      given(updateImagePort.updateAll(any())).willReturn(retained);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(2L)));

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).unlinkImagesByIdIn(List.of(1L));
    }

    @Test
    @DisplayName("[TC-UPD-DEL-003] 삭제 대상이 FAILED 상태이면 S3 삭제 없이 unlink만")
    void execute_removedFailed_skipsS3DeleteUnlinksOnly() {
      List<Image> existing =
          List.of(
              failedImage(1L, USER_ID, REF_ID), completedImage(2L, USER_ID, REF_ID, "a/2.webp"));
      List<Image> retained = List.of(completedImage(2L, USER_ID, REF_ID, "a/2.webp"));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);
      given(loadImagePort.findImagesByIdInForUpdate(List.of(2L))).willReturn(retained);
      given(updateImagePort.updateAll(any())).willReturn(retained);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(2L)));

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).unlinkImagesByIdIn(List.of(1L));
    }

    @Test
    @DisplayName("[TC-UPD-DEL-004] COMPLETED 이미지의 finalObjectKey=null이면 S3 삭제 스킵 후 unlink")
    void execute_completedWithNullFinalKey_skipsS3DeleteUnlinks() {
      List<Image> existing = List.of(completedImage(1L, USER_ID, REF_ID, null));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of()));

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort).unlinkImagesByIdIn(List.of(1L));
    }

    @Test
    @DisplayName("[TC-UPD-DEL-005] 기존 이미지가 없는 경우 unlink 및 Phase 2,3 호출 없음")
    void execute_noExistingImages_skipsAllPorts() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of()));

      verify(deleteImagePort, never()).unlinkImagesByIdIn(any());
      verify(loadImagePort, never()).findImagesByIdInForUpdate(any());
    }

    @Test
    @DisplayName("[TC-UPD-DEL-006] 전체 기존 이미지 제거(imageIds=[]): 모두 S3 삭제 + unlink, Phase 2,3 건너뜀")
    void execute_removeAllImages_deletesAllS3AndUnlinks() {
      List<Image> existing =
          List.of(
              completedImage(1L, USER_ID, REF_ID, "a.webp"),
              completedImage(2L, USER_ID, REF_ID, "b.webp"));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of()));

      verify(deleteS3ObjectPort).deleteObject("a.webp");
      verify(deleteS3ObjectPort).deleteObject("b.webp");

      ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
      verify(deleteImagePort).unlinkImagesByIdIn(idsCaptor.capture());
      assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(1L, 2L);

      verify(loadImagePort, never()).findImagesByIdInForUpdate(any());
      verify(updateImagePort, never()).updateAll(any());
    }
  }

  // ======================== Phase 2: 검증 Phase ========================

  @Nested
  @DisplayName("Phase 2 — 검증 Phase")
  class ValidatePhaseTests {

    @Test
    @DisplayName("[TC-UPD-VALID-001] 요청자 소유 이미지, referenceId=null(신규) 통과")
    void execute_ownedImagesWithNullRef_passes() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> finalImages =
          List.of(pendingImage(1L, USER_ID, null), pendingImage(2L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L, 2L))).willReturn(finalImages);
      given(updateImagePort.updateAll(any())).willReturn(finalImages);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L, 2L)));

      verify(updateImagePort).updateAll(any());
    }

    @Test
    @DisplayName("[TC-UPD-VALID-002] 이미 이 엔티티에 연결된 이미지(referenceId=command.referenceId)는 통과")
    void execute_imageAlreadyLinkedToSameRef_passes() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> finalImages = List.of(completedImage(1L, USER_ID, REF_ID, "k.webp"));
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L))).willReturn(finalImages);
      given(updateImagePort.updateAll(any())).willReturn(finalImages);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L)));

      verify(updateImagePort).updateAll(any());
    }

    @Test
    @DisplayName("[TC-UPD-VALID-003] 다른 사용자 소유 이미지 포함 시 ImageNotBelongsToUserException")
    void execute_imageOwnedByOtherUser_throws() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> finalImages =
          List.of(
              completedImage(1L, USER_ID, null, "k1.webp"),
              completedImage(2L, 99L, null, "k2.webp")); // 다른 사용자 소유
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L, 2L))).willReturn(finalImages);

      assertThatThrownBy(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L, 2L))))
          .isInstanceOf(ImageNotBelongsToUserException.class);
    }

    @Test
    @DisplayName("[TC-UPD-VALID-004] 다른 엔티티에 이미 연결된 이미지 포함 시 InvalidImageRefTypeException")
    void execute_imageLinkedToDifferentRef_throws() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> finalImages =
          List.of(completedImage(1L, USER_ID, 999L, "k.webp")); // referenceId=999
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L))).willReturn(finalImages);

      assertThatThrownBy(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L))))
          .isInstanceOf(InvalidImageRefTypeException.class);
    }

    @Test
    @DisplayName("[TC-UPD-VALID-005] ImageCountPolicy(10장) 초과 시 ImageMaxCountExceedException")
    void execute_exceedsMaxCount_throws() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Long> elevenIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
      List<Image> elevenImages =
          elevenIds.stream().map(id -> pendingImage(id, USER_ID, null)).toList();
      given(loadImagePort.findImagesByIdInForUpdate(elevenIds)).willReturn(elevenImages);

      assertThatThrownBy(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, elevenIds)))
          .isInstanceOf(ImageMaxCountExceedException.class);
    }

    @Test
    @DisplayName("[TC-UPD-VALID-006] ImageCountPolicy 정확히 10장은 통과")
    void execute_exactlyMaxCount_passes() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Long> tenIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
      List<Image> tenImages = tenIds.stream().map(id -> pendingImage(id, USER_ID, null)).toList();
      given(loadImagePort.findImagesByIdInForUpdate(tenIds)).willReturn(tenImages);
      given(updateImagePort.updateAll(any())).willReturn(tenImages);

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, tenIds));

      verify(updateImagePort).updateAll(any());
    }

    @Test
    @DisplayName(
        "[TC-UPD-VALID-008] 다른 referenceType으로 발급된 미연결 이미지 포함 시 InvalidImageRefTypeException")
    void execute_pendingImageIssuedForDifferentType_throws() {
      // USER_PROFILE 용도로 발급된 미연결 이미지를 COMMUNITY_FREE 게시글에 연결 시도
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      Image wrongTypeImage =
          Image.builder()
              .id(1L)
              .userId(USER_ID)
              .referenceType(ImageReferenceType.USER_PROFILE) // 다른 용도로 발급된 이미지
              .referenceId(null)
              .status(ImageStatus.PENDING)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey(null)
              .imgOrder(1)
              .build();
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L)))
          .willReturn(List.of(wrongTypeImage));

      assertThatThrownBy(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L))))
          .isInstanceOf(InvalidImageRefTypeException.class);
    }

    @Test
    @DisplayName(
        "[TC-UPD-VALID-009] unlink된 이미지(COMPLETED, referenceId=null)는 동일 userId·referenceType이면 재사용 허용")
    void execute_unlinkedCompletedImage_sameUserAndType_canBeRelinked() {
      // unlink 시 referenceType·status는 보존되고 referenceId만 null로 변환된다.
      // 동일 userId + 동일 referenceType 계열이면 재연결 가능해야 한다.
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      Image unlinkedImage =
          Image.builder()
              .id(1L)
              .userId(USER_ID)
              .referenceType(ImageReferenceType.COMMUNITY_FREE)
              .referenceId(null) // unlink 시 null로 변환, status는 COMPLETED 유지
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey("imgs/1.webp")
              .imgOrder(1)
              .build();
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L)))
          .willReturn(List.of(unlinkedImage));
      given(updateImagePort.updateAll(any())).willAnswer(inv -> inv.getArgument(0));

      // 예외 없이 정상 실행되어야 한다
      assertThatCode(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("[TC-UPD-VALID-007] imageIds에 존재하지 않는 id 포함 시 ImageNotFoundException")
    void execute_unknownImageId_throws() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      // DB에서 id=1만 반환, id=9999 없음
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L, 9999L)))
          .willReturn(List.of(pendingImage(1L, USER_ID, null)));

      assertThatThrownBy(
              () ->
                  service.execute(
                      new UpsertImagesByReferenceCommand(
                          USER_ID, REF_ID, FREE, List.of(1L, 9999L))))
          .isInstanceOf(ImageNotFoundException.class);
    }
  }

  // ======================== Phase 3: 순서 업데이트 Phase ========================

  @Nested
  @DisplayName("Phase 3 — 순서 업데이트 Phase")
  class OrderUpdatePhaseTests {

    @Test
    @DisplayName("[TC-UPD-ORDER-001] 3장 이미지 순서 재배치: imageIds=[3,1,2] → imgOrder=1,2,3")
    void execute_reordersImages_correctImgOrder() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Long> imageIds = List.of(3L, 1L, 2L);
      List<Image> dbImages =
          List.of(
              pendingImage(3L, USER_ID, null),
              pendingImage(1L, USER_ID, null),
              pendingImage(2L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      List<Image> updated = captor.getValue();

      assertThat(updated).hasSize(3);
      assertThat(updated.get(0).getId()).isEqualTo(3L);
      assertThat(updated.get(0).getImgOrder()).isEqualTo(1);
      assertThat(updated.get(1).getId()).isEqualTo(1L);
      assertThat(updated.get(1).getImgOrder()).isEqualTo(2);
      assertThat(updated.get(2).getId()).isEqualTo(2L);
      assertThat(updated.get(2).getImgOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("[TC-UPD-ORDER-002] 신규 이미지(referenceId=null) 추가 + 기존 이미지 순서 유지")
    void execute_addsNewImageWithCorrectOrder() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Long> imageIds = List.of(1L, 10L);
      List<Image> dbImages =
          List.of(completedImage(1L, USER_ID, REF_ID, "k1.webp"), pendingImage(10L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      List<Image> updated = captor.getValue();

      assertThat(updated.get(0).getId()).isEqualTo(1L);
      assertThat(updated.get(0).getImgOrder()).isEqualTo(1);
      assertThat(updated.get(1).getId()).isEqualTo(10L);
      assertThat(updated.get(1).getImgOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("[TC-UPD-ORDER-003] 1장만 남기는 경우 imgOrder=1")
    void execute_singleImage_imgOrderIsOne() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> dbImages = List.of(completedImage(1L, USER_ID, REF_ID, "k.webp"));
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L))).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L)));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      assertThat(captor.getValue().get(0).getImgOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("[TC-UPD-ORDER-004] DB 반환 순서와 무관하게 요청(imageIds) 순서 기준으로 imgOrder 배정")
    void execute_usesRequestOrderNotDbOrder() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Long> imageIds = List.of(1L, 2L, 3L);
      // DB는 [3, 1, 2] 순서로 반환
      List<Image> dbImages =
          List.of(
              pendingImage(3L, USER_ID, null),
              pendingImage(1L, USER_ID, null),
              pendingImage(2L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      List<Image> updated = captor.getValue();

      assertThat(updated.get(0).getId()).isEqualTo(1L);
      assertThat(updated.get(0).getImgOrder()).isEqualTo(1);
      assertThat(updated.get(1).getId()).isEqualTo(2L);
      assertThat(updated.get(1).getImgOrder()).isEqualTo(2);
      assertThat(updated.get(2).getId()).isEqualTo(3L);
      assertThat(updated.get(2).getImgOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("[TC-UPD-ORDER-005] updateAll() 호출 시 imageIds 크기와 동일한 이미지 리스트 전달")
    void execute_updateAllReceivesCorrectCount() {
      List<Long> imageIds = List.of(1L, 2L, 3L);
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> dbImages =
          List.of(
              pendingImage(1L, USER_ID, null),
              pendingImage(2L, USER_ID, null),
              pendingImage(3L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    @DisplayName("[TC-UPD-ORDER-*] 업데이트된 이미지에 referenceType/referenceId가 올바르게 설정됨")
    void execute_setsReferenceOnUpdatedImages() {
      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      List<Image> dbImages = List.of(pendingImage(1L, USER_ID, null));
      given(loadImagePort.findImagesByIdInForUpdate(List.of(1L))).willReturn(dbImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, List.of(1L)));

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      Image updated = captor.getValue().get(0);
      assertThat(updated.getReferenceType()).isEqualTo(FREE);
      assertThat(updated.getReferenceId()).isEqualTo(REF_ID);
    }
  }

  // ======================== 복합 케이스 ========================

  @Nested
  @DisplayName("복합 케이스")
  class ComplexCases {

    @Test
    @DisplayName("[TC-UPD-COMPL-001] 기존 4장 중 1장 제거, 2장 추가")
    void execute_removeOneAddTwo_correctFlow() {
      List<Image> existing =
          List.of(
              completedImage(1L, USER_ID, REF_ID, "a/1.webp"),
              completedImage(2L, USER_ID, REF_ID, "a/2.webp"),
              completedImage(3L, USER_ID, REF_ID, "a/3.webp"),
              completedImage(4L, USER_ID, REF_ID, "a/4.webp"));
      List<Long> imageIds = List.of(1L, 2L, 5L, 4L, 6L);
      List<Image> finalImages =
          List.of(
              completedImage(1L, USER_ID, REF_ID, "a/1.webp"),
              completedImage(2L, USER_ID, REF_ID, "a/2.webp"),
              pendingImage(5L, USER_ID, null),
              completedImage(4L, USER_ID, REF_ID, "a/4.webp"),
              pendingImage(6L, USER_ID, null));

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(existing);
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(finalImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      // id=3 제거: S3 삭제 후 unlink
      verify(deleteS3ObjectPort).deleteObject("a/3.webp");
      verify(deleteImagePort).unlinkImagesByIdIn(List.of(3L));

      // updateAll로 [1,2,5,4,6] 5개가 전달되어야 함
      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      assertThat(captor.getValue()).extracting(Image::getId).containsExactly(1L, 2L, 5L, 4L, 6L);
    }

    @Test
    @DisplayName("[TC-UPD-COMPLICATE-002] 기존 이미지 없음, 신규 이미지만 추가")
    void execute_noExisting_addsNewImages() {
      List<Long> imageIds = List.of(1L, 2L, 5L, 4L, 6L);
      List<Image> newImages = imageIds.stream().map(id -> pendingImage(id, USER_ID, null)).toList();

      given(loadImagePort.findImagesByReferenceForUpdate(FREE.expand(), REF_ID))
          .willReturn(List.of());
      given(loadImagePort.findImagesByIdInForUpdate(imageIds)).willReturn(newImages);
      given(updateImagePort.updateAll(any())).willReturn(List.of());

      service.execute(new UpsertImagesByReferenceCommand(USER_ID, REF_ID, FREE, imageIds));

      verify(deleteS3ObjectPort, never()).deleteObject(any());
      verify(deleteImagePort, never()).unlinkImagesByIdIn(any());

      ArgumentCaptor<List<Image>> captor = ArgumentCaptor.forClass(List.class);
      verify(updateImagePort).updateAll(captor.capture());
      assertThat(captor.getValue()).extracting(Image::getId).containsExactly(1L, 2L, 5L, 4L, 6L);
    }
  }
}
