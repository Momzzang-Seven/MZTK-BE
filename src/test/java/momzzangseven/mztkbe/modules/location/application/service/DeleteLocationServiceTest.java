package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationAlreadyDeletedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.global.error.location.MissingLocationInfoException;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.out.DeleteLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import momzzangseven.mztkbe.modules.location.domain.vo.GpsCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteLocationServiceTest {

  @Mock private LoadLocationPort loadLocationPort;
  @Mock private DeleteLocationPort deleteLocationPort;

  @InjectMocks private DeleteLocationService service;

  @Test
  void execute_shouldThrowWhenUserIdIsMissing() {
    DeleteLocationCommand command = DeleteLocationCommand.of(null, 10L);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId is required");

    verifyNoInteractions(loadLocationPort, deleteLocationPort);
  }

  @Test
  void execute_shouldThrowWhenLocationIdIsMissing() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, null);

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(MissingLocationInfoException.class)
        .hasMessage("locationId is required");

    verifyNoInteractions(loadLocationPort, deleteLocationPort);
  }

  @Test
  void execute_shouldThrowWhenLocationNotFound() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 10L);
    when(loadLocationPort.findByLocationId(10L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(LocationNotFoundException.class);
    verify(deleteLocationPort, never()).deleteById(10L);
  }

  @Test
  void execute_shouldThrowWhenNotOwner() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 10L);
    when(loadLocationPort.findByLocationId(10L)).thenReturn(Optional.of(location(2L, null)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(UserNotAuthenticatedException.class)
        .hasMessageContaining("You can only delete your own locations");
    verify(deleteLocationPort, never()).deleteById(10L);
  }

  @Test
  void execute_shouldThrowWhenOwnerMissing() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 10L);
    when(loadLocationPort.findByLocationId(10L)).thenReturn(Optional.of(location(null, null)));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(UserNotAuthenticatedException.class);
    verify(deleteLocationPort, never()).deleteById(10L);
  }

  @Test
  void execute_shouldThrowWhenAlreadyDeleted() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 10L);
    when(loadLocationPort.findByLocationId(10L))
        .thenReturn(Optional.of(location(1L, Instant.parse("2026-02-28T00:00:00Z"))));

    assertThatThrownBy(() -> service.execute(command))
        .isInstanceOf(LocationAlreadyDeletedException.class);
  }

  @Test
  void execute_shouldDeleteAndReturnResult() {
    DeleteLocationCommand command = DeleteLocationCommand.of(1L, 10L);
    when(loadLocationPort.findByLocationId(10L)).thenReturn(Optional.of(location(1L, null)));

    DeleteLocationResult result = service.execute(command);

    verify(deleteLocationPort).deleteById(10L);
    assertThat(result.locationId()).isEqualTo(10L);
    assertThat(result.locationName()).isEqualTo("Gym");
    assertThat(result.deletedAt()).isNotNull();
  }

  private Location location(Long userId, Instant deletedAt) {
    return Location.builder()
        .id(10L)
        .userId(userId)
        .locationName("Gym")
        .postalCode("04524")
        .address("Seoul")
        .detailAddress("2F")
        .coordinate(new GpsCoordinate(37.5, 126.9))
        .registeredAt(Instant.now())
        .deletedAt(deletedAt)
        .build();
  }
}
