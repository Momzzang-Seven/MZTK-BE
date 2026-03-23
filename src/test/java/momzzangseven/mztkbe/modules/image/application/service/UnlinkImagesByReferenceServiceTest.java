package momzzangseven.mztkbe.modules.image.application.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.image.application.dto.UnlinkImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UnlinkImagesByReferenceService 단위 테스트. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnlinkImagesByReferenceService 단위 테스트")
class UnlinkImagesByReferenceServiceTest {

  @Mock private DeleteImagePort deleteImagePort;
  @InjectMocks private UnlinkImagesByReferenceService service;

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[TC-UNLINK-001] COMMUNITY_FREE 게시글의 이미지 unlink 성공")
    void execute_communityFree_callsUnlinkPort() {
      service.execute(new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, 1L));

      verify(deleteImagePort).unlinkImagesByReference(ImageReferenceType.COMMUNITY_FREE, 1L);
    }

    @Test
    @DisplayName("[TC-UNLINK-002] COMMUNITY_QUESTION 게시글의 이미지 unlink 성공")
    void execute_communityQuestion_callsUnlinkPort() {
      service.execute(
          new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_QUESTION, 42L));

      verify(deleteImagePort).unlinkImagesByReference(ImageReferenceType.COMMUNITY_QUESTION, 42L);
    }

    @Test
    @DisplayName("[TC-UNLINK-003] 연결된 이미지가 없어도 예외 없이 정상 완료")
    void execute_noImages_completesWithoutException() {
      assertThatNoException()
          .isThrownBy(
              () ->
                  service.execute(
                      new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, 99L)));

      verify(deleteImagePort).unlinkImagesByReference(ImageReferenceType.COMMUNITY_FREE, 99L);
    }
  }

  @Nested
  @DisplayName("실패 케이스 — validate()")
  class FailureCases {

    @Test
    @DisplayName("[TC-UNLINK-004] referenceType=null → IllegalArgumentException, port 호출 없음")
    void execute_nullReferenceType_throwsAndSkipsPort() {
      assertThatThrownBy(() -> service.execute(new UnlinkImagesByReferenceCommand(null, 1L)))
          .isInstanceOf(IllegalArgumentException.class);

      verify(deleteImagePort, never()).unlinkImagesByReference(any(), any());
    }

    @Test
    @DisplayName("[TC-UNLINK-005] referenceId=null → IllegalArgumentException")
    void execute_nullReferenceId_throws() {
      assertThatThrownBy(
              () ->
                  service.execute(
                      new UnlinkImagesByReferenceCommand(ImageReferenceType.COMMUNITY_FREE, null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-UNLINK-006] referenceId=0 또는 음수 → IllegalArgumentException")
    void execute_nonPositiveReferenceId_throws(long referenceId) {
      assertThatThrownBy(
              () ->
                  service.execute(
                      new UnlinkImagesByReferenceCommand(
                          ImageReferenceType.COMMUNITY_FREE, referenceId)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
