package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** GetImagesByIdsService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetImagesByIdsService 단위 테스트")
class GetImagesByIdsServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @InjectMocks private GetImagesByIdsService service;

  private static final long USER_ID = 1L;
  private static final long REF_ID = 100L;
  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  private Image completedImage(long id, int order) {
    return Image.builder()
        .id(id)
        .userId(USER_ID)
        .referenceType(FREE)
        .referenceId(REF_ID)
        .status(ImageStatus.COMPLETED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey("public/community/free/" + id + ".webp")
        .imgOrder(order)
        .build();
  }

  private Image pendingImage(long id, int order) {
    return Image.builder()
        .id(id)
        .userId(USER_ID)
        .referenceType(FREE)
        .referenceId(REF_ID)
        .status(ImageStatus.PENDING)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(order)
        .build();
  }

  private Image failedImage(long id, int order) {
    return Image.builder()
        .id(id)
        .userId(USER_ID)
        .referenceType(FREE)
        .referenceId(REF_ID)
        .status(ImageStatus.FAILED)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(null)
        .imgOrder(order)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[TC-SVC-001] COMPLETED 이미지 조회 - finalObjectKey 포함 반환")
    void execute_completedImages_returnsFinalKey() {
      given(loadImagePort.findImagesByIdIn(List.of(1L, 2L)))
          .willReturn(List.of(completedImage(1, 1), completedImage(2, 2)));

      GetImagesByIdsResult result =
          service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L, 2L)));

      assertThat(result.images()).hasSize(2);
      assertThat(result.images().get(0).imageId()).isEqualTo(1L);
      assertThat(result.images().get(0).status()).isEqualTo(ImageStatus.COMPLETED);
      assertThat(result.images().get(0).finalObjectKey()).isNotNull();
    }

    @Test
    @DisplayName("[TC-SVC-002] PENDING/FAILED 이미지는 finalObjectKey=null로 반환")
    void execute_pendingAndFailed_nullFinalKey() {
      given(loadImagePort.findImagesByIdIn(List.of(1L, 2L, 3L)))
          .willReturn(List.of(pendingImage(1, 1), failedImage(2, 2), completedImage(3, 3)));

      GetImagesByIdsResult result =
          service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L, 2L, 3L)));

      assertThat(result.images()).hasSize(3);
      assertThat(result.images().get(0).finalObjectKey()).isNull();
      assertThat(result.images().get(1).finalObjectKey()).isNull();
      assertThat(result.images().get(2).finalObjectKey()).isNotNull();
    }

    @Test
    @DisplayName("[TC-SVC-003] soft-miss: DB에 없는 ID는 응답에서 조용히 제외")
    void execute_nonExistentId_silentlyExcluded() {
      // id=3은 DB에 없어서 loadImagePort가 2개만 반환
      given(loadImagePort.findImagesByIdIn(List.of(1L, 2L, 3L)))
          .willReturn(List.of(completedImage(1, 1), completedImage(2, 2)));

      GetImagesByIdsResult result =
          service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L, 2L, 3L)));

      assertThat(result.images()).hasSize(2);
      assertThat(result.images()).extracting(ImageItem::imageId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("[TC-SVC-004] 빈 결과 반환 (모든 ID가 DB에 없는 경우)")
    void execute_allNonExistent_returnsEmpty() {
      given(loadImagePort.findImagesByIdIn(List.of(99L))).willReturn(List.of());

      GetImagesByIdsResult result =
          service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(99L)));

      assertThat(result.images()).isEmpty();
    }

    @Test
    @DisplayName("[TC-SVC-005] MARKET_STORE virtual type: expand() 된 concrete type 이미지 소유권 통과")
    void execute_marketStoreVirtualType_ownershipPassesWithConcreteType() {
      Image thumbImage =
          Image.builder()
              .id(10L)
              .userId(USER_ID)
              .referenceType(ImageReferenceType.MARKET_STORE_THUMB)
              .referenceId(REF_ID)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/10.jpg")
              .finalObjectKey("store/thumb.webp")
              .imgOrder(1)
              .build();

      given(loadImagePort.findImagesByIdIn(List.of(10L))).willReturn(List.of(thumbImage));

      GetImagesByIdsResult result =
          service.execute(
              new GetImagesByIdsCommand(
                  USER_ID, ImageReferenceType.MARKET_STORE, REF_ID, List.of(10L)));

      assertThat(result.images()).hasSize(1);
      assertThat(result.images().get(0).imageId()).isEqualTo(10L);
    }
  }

  @Nested
  @DisplayName("소유권 검증 실패 — 전체 요청 거부 (403)")
  class OwnershipFailure {

    @Test
    @DisplayName("[TC-SVC-006] userId 불일치 → ImageNotBelongsToUserException")
    void execute_wrongUserId_throws() {
      Image otherUserImage =
          Image.builder()
              .id(1L)
              .userId(999L) // 다른 사용자
              .referenceType(FREE)
              .referenceId(REF_ID)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey("key.webp")
              .imgOrder(1)
              .build();

      given(loadImagePort.findImagesByIdIn(List.of(1L))).willReturn(List.of(otherUserImage));

      assertThatThrownBy(
              () -> service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L))))
          .isInstanceOf(ImageNotBelongsToUserException.class);
    }

    @Test
    @DisplayName("[TC-SVC-007] referenceType 불일치 → ImageNotBelongsToUserException")
    void execute_wrongReferenceType_throws() {
      Image differentTypeImage =
          Image.builder()
              .id(1L)
              .userId(USER_ID)
              .referenceType(ImageReferenceType.COMMUNITY_QUESTION) // 요청과 다른 타입
              .referenceId(REF_ID)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey("key.webp")
              .imgOrder(1)
              .build();

      given(loadImagePort.findImagesByIdIn(List.of(1L))).willReturn(List.of(differentTypeImage));

      assertThatThrownBy(
              () -> service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L))))
          .isInstanceOf(ImageNotBelongsToUserException.class);
    }

    @Test
    @DisplayName("[TC-SVC-008] referenceId 불일치 → ImageNotBelongsToUserException")
    void execute_wrongReferenceId_throws() {
      Image differentRefImage =
          Image.builder()
              .id(1L)
              .userId(USER_ID)
              .referenceType(FREE)
              .referenceId(999L) // 다른 referenceId
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/1.jpg")
              .finalObjectKey("key.webp")
              .imgOrder(1)
              .build();

      given(loadImagePort.findImagesByIdIn(List.of(1L))).willReturn(List.of(differentRefImage));

      assertThatThrownBy(
              () -> service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L))))
          .isInstanceOf(ImageNotBelongsToUserException.class);
    }

    @Test
    @DisplayName("[TC-SVC-009] 첫 번째 이미지는 통과, 두 번째 이미지 소유권 불일치 → 전체 거부")
    void execute_secondImageOwnershipFail_rejectsAll() {
      Image validImage = completedImage(1, 1);
      Image invalidImage =
          Image.builder()
              .id(2L)
              .userId(999L) // 다른 사용자
              .referenceType(FREE)
              .referenceId(REF_ID)
              .status(ImageStatus.COMPLETED)
              .tmpObjectKey("tmp/2.jpg")
              .finalObjectKey("key2.webp")
              .imgOrder(2)
              .build();

      given(loadImagePort.findImagesByIdIn(List.of(1L, 2L)))
          .willReturn(List.of(validImage, invalidImage));

      assertThatThrownBy(
              () ->
                  service.execute(
                      new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, List.of(1L, 2L))))
          .isInstanceOf(ImageNotBelongsToUserException.class);
    }
  }

  @Nested
  @DisplayName("엣지 케이스 — validate() 실패 시 LoadImagePort 미호출")
  class ValidateEdgeCases {

    @Test
    @DisplayName("[TC-SVC-010] ids=null → IllegalArgumentException, LoadImagePort 호출 없음")
    void execute_nullIds_throwsAndSkipsPort() {
      assertThatThrownBy(
              () -> service.execute(new GetImagesByIdsCommand(USER_ID, FREE, REF_ID, null)))
          .isInstanceOf(IllegalArgumentException.class);

      verify(loadImagePort, never()).findImagesByIdIn(null);
    }
  }
}
