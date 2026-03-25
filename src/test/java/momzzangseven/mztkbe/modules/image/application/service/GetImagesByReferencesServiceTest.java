package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("GetImagesByReferencesService 단위 테스트")
class GetImagesByReferencesServiceTest {

  @Mock private LoadImagePort loadImagePort;
  @InjectMocks private GetImagesByReferencesService service;

  private static final ImageReferenceType ANSWER = ImageReferenceType.COMMUNITY_ANSWER;

  @Test
  @DisplayName("여러 referenceId의 이미지를 한 번에 그룹핑해서 반환한다")
  void execute_groupsImagesByReferenceId() {
    given(loadImagePort.findImagesByReferenceIds(ANSWER.expand(), List.of(31L, 32L)))
        .willReturn(
            List.of(
                image(100L, 31L, ImageStatus.COMPLETED, "a/100.webp", 1),
                image(101L, 31L, ImageStatus.PENDING, null, 2),
                image(200L, 32L, ImageStatus.FAILED, null, 1)));

    GetImagesByReferencesResult result =
        service.execute(new GetImagesByReferencesCommand(ANSWER, List.of(31L, 31L, 32L)));

    verify(loadImagePort).findImagesByReferenceIds(ANSWER.expand(), List.of(31L, 32L));
    assertThat(result.itemsByReferenceId().keySet()).containsExactly(31L, 32L);
    assertThat(result.itemsByReferenceId().get(31L))
        .containsExactly(
            new ImageItem(100L, ImageStatus.COMPLETED, "a/100.webp"),
            new ImageItem(101L, ImageStatus.PENDING, null));
    assertThat(result.itemsByReferenceId().get(32L))
        .containsExactly(new ImageItem(200L, ImageStatus.FAILED, null));
  }

  @Test
  @DisplayName("이미지가 없는 referenceId도 빈 리스트로 유지한다")
  void execute_keepsEmptyReferenceSlots() {
    given(loadImagePort.findImagesByReferenceIds(ANSWER.expand(), List.of(31L, 32L)))
        .willReturn(List.of(image(100L, 31L, ImageStatus.COMPLETED, "a/100.webp", 1)));

    GetImagesByReferencesResult result =
        service.execute(new GetImagesByReferencesCommand(ANSWER, List.of(31L, 32L)));

    assertThat(result.itemsByReferenceId().get(31L))
        .containsExactly(new ImageItem(100L, ImageStatus.COMPLETED, "a/100.webp"));
    assertThat(result.itemsByReferenceId().get(32L)).isEmpty();
  }

  @Test
  @DisplayName("referenceIds가 비어 있으면 포트를 호출하지 않고 빈 결과를 반환한다")
  void execute_emptyReferenceIds_returnsEmptyResult() {
    GetImagesByReferencesResult result =
        service.execute(new GetImagesByReferencesCommand(ANSWER, List.of()));

    assertThat(result.itemsByReferenceId()).isEmpty();
    verify(loadImagePort, never()).findImagesByReferenceIds(ANSWER.expand(), List.of());
  }

  @Nested
  @DisplayName("validate() 위임")
  class ValidationCases {

    @Test
    @DisplayName("referenceType=null 이면 IllegalArgumentException")
    void execute_nullReferenceType_throws() {
      assertThatThrownBy(() -> service.execute(new GetImagesByReferencesCommand(null, List.of(1L))))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("referenceIds 안에 null 이 있으면 IllegalArgumentException")
    void execute_nullReferenceId_throws() {
      assertThatThrownBy(
              () ->
                  service.execute(
                      new GetImagesByReferencesCommand(ANSWER, Arrays.asList(1L, null))))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private Image image(
      long id, long referenceId, ImageStatus status, String finalObjectKey, int imgOrder) {
    return Image.builder()
        .id(id)
        .userId(1L)
        .referenceType(ANSWER)
        .referenceId(referenceId)
        .status(status)
        .tmpObjectKey("tmp/" + id + ".jpg")
        .finalObjectKey(finalObjectKey)
        .imgOrder(imgOrder)
        .build();
  }
}
