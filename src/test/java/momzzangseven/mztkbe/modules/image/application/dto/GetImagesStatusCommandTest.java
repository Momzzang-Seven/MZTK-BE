package momzzangseven.mztkbe.modules.image.application.dto;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetImagesStatusCommand validate() unit test")
class GetImagesStatusCommandTest {

  @Test
  @DisplayName("duplicates are allowed when all ids are valid")
  void validate_allowsDuplicates() {
    GetImagesStatusCommand command = new GetImagesStatusCommand(1L, List.of(1L, 2L, 2L));

    assertThatNoException().isThrownBy(command::validate);
  }

  @Test
  @DisplayName("null userId throws UserNotAuthenticatedException")
  void validate_nullUserId_throws() {
    GetImagesStatusCommand command = new GetImagesStatusCommand(null, List.of(1L));

    assertThatThrownBy(command::validate).isInstanceOf(UserNotAuthenticatedException.class);
  }

  @Test
  @DisplayName("empty ids throws IllegalArgumentException")
  void validate_emptyIds_throws() {
    GetImagesStatusCommand command = new GetImagesStatusCommand(1L, List.of());

    assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("non-positive ids throw IllegalArgumentException")
  void validate_nonPositiveIds_throws() {
    GetImagesStatusCommand command = new GetImagesStatusCommand(1L, List.of(0L));

    assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("more than 10 ids throws IllegalArgumentException")
  void validate_exceedsMax_throws() {
    GetImagesStatusCommand command =
        new GetImagesStatusCommand(1L, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

    assertThatThrownBy(command::validate).isInstanceOf(IllegalArgumentException.class);
  }
}
