package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GetImagesByIdsCommand validate() 단위 테스트. */
@DisplayName("GetImagesByIdsCommand validate() 단위 테스트")
class GetImagesByIdsCommandTest {

  private static final ImageReferenceType FREE = ImageReferenceType.COMMUNITY_FREE;

  private GetImagesByIdsCommand validCommand(List<Long> ids) {
    return new GetImagesByIdsCommand(1L, FREE, 100L, ids);
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[TC-CMD-001] 유효한 커맨드는 예외 없이 통과")
    void validate_validCommand_noException() {
      assertThatNoException().isThrownBy(() -> validCommand(List.of(1L, 2L, 3L)).validate());
    }

    @Test
    @DisplayName("[TC-CMD-002] MARKET_STORE(virtual) 타입도 request-facing이므로 통과")
    void validate_marketStoreType_noException() {
      GetImagesByIdsCommand command =
          new GetImagesByIdsCommand(1L, ImageReferenceType.MARKET_STORE, 100L, List.of(1L));
      assertThatNoException().isThrownBy(command::validate);
    }
  }

  @Nested
  @DisplayName("userId 검증")
  class UserIdValidation {

    @Test
    @DisplayName("[TC-CMD-003] userId=null → UserNotAuthenticatedException")
    void validate_nullUserId_throws() {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(null, FREE, 100L, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(UserNotAuthenticatedException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-CMD-004] userId<=0 → UserNotAuthenticatedException")
    void validate_nonPositiveUserId_throws(long userId) {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(userId, FREE, 100L, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(UserNotAuthenticatedException.class);
    }
  }

  @Nested
  @DisplayName("referenceType 검증")
  class ReferenceTypeValidation {

    @Test
    @DisplayName("[TC-CMD-005] referenceType=null → InvalidImageRefTypeException")
    void validate_nullReferenceType_throws() {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, null, 100L, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageRefTypeException.class);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "MARKET_CLASS_THUMB",
          "MARKET_CLASS_DETAIL",
          "MARKET_STORE_THUMB",
          "MARKET_STORE_DETAIL"
        })
    @DisplayName("[TC-CMD-006] internal-only 타입 → InvalidImageRefTypeException")
    void validate_internalOnlyType_throws(String typeName) {
      ImageReferenceType internalType = ImageReferenceType.valueOf(typeName);
      GetImagesByIdsCommand command =
          new GetImagesByIdsCommand(1L, internalType, 100L, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(InvalidImageRefTypeException.class);
    }
  }

  @Nested
  @DisplayName("referenceId 검증")
  class ReferenceIdValidation {

    @Test
    @DisplayName("[TC-CMD-007] referenceId=null → IllegalArgumentException")
    void validate_nullReferenceId_throws() {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, FREE, null, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("[TC-CMD-008] referenceId<=0 → IllegalArgumentException")
    void validate_nonPositiveReferenceId_throws(long referenceId) {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, FREE, referenceId, List.of(1L));
      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("ids 검증")
  class IdsValidation {

    @Test
    @DisplayName("[TC-CMD-009] ids=null → IllegalArgumentException")
    void validate_nullIds_throws() {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, FREE, 100L, null);
      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[TC-CMD-010] ids=빈 리스트 → IllegalArgumentException")
    void validate_emptyIds_throws() {
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, FREE, 100L, List.of());
      assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("[TC-CMD-011] ids 개수가 최대(10)를 초과하면 ImageMaxCountExceedException")
    void validate_exceedsMaxCount_throws() {
      List<Long> tooMany = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
      GetImagesByIdsCommand command = new GetImagesByIdsCommand(1L, FREE, 100L, tooMany);
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }

    @Test
    @DisplayName("[TC-CMD-012] ids 개수가 정확히 최대(10)이면 통과")
    void validate_exactMaxCount_noException() {
      List<Long> exactly10 = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
      assertThatNoException()
          .isThrownBy(() -> new GetImagesByIdsCommand(1L, FREE, 100L, exactly10).validate());
    }

    @Test
    @DisplayName("[M-13] WORKOUT ids가 최대(1)를 초과하면 ImageMaxCountExceedException")
    void validate_workoutExceedsMaxCount_throws() {
      GetImagesByIdsCommand command =
          new GetImagesByIdsCommand(1L, ImageReferenceType.WORKOUT, 100L, List.of(1L, 2L));
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }

    @Test
    @DisplayName("[M-14] MARKET_STORE ids가 최대(5)를 초과하면 ImageMaxCountExceedException")
    void validate_marketStoreExceedsMaxCount_throws() {
      GetImagesByIdsCommand command =
          new GetImagesByIdsCommand(
              1L, ImageReferenceType.MARKET_STORE, 100L, List.of(1L, 2L, 3L, 4L, 5L, 6L));
      assertThatThrownBy(command::validate).isInstanceOf(ImageMaxCountExceedException.class);
    }
  }
}
