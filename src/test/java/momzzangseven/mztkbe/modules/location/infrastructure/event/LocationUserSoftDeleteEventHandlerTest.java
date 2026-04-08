package momzzangseven.mztkbe.modules.location.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.event.UserSoftDeletedEvent;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.SaveLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationUserSoftDeleteEventHandlerTest {

  @Mock private LoadLocationPort loadLocationPort;
  @Mock private SaveLocationPort saveLocationPort;

  @InjectMocks private LocationUserSoftDeleteEventHandler handler;

  @Test
  void handleUserSoftDeleted_ignoresNullUserId() {
    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(null));

    verifyNoInteractions(loadLocationPort, saveLocationPort);
  }

  @Test
  void handleUserSoftDeleted_returnsWhenNoLocations() {
    when(loadLocationPort.findByUserId(1L)).thenReturn(List.of());

    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(1L));

    verify(loadLocationPort).findByUserId(1L);
    verify(saveLocationPort, never()).save(org.mockito.ArgumentMatchers.any(Location.class));
  }

  @Test
  void handleUserSoftDeleted_marksAndSavesAllLocations() {
    Location first = location(10L, 1L, false);
    Location second = location(11L, 1L, false);
    when(loadLocationPort.findByUserId(1L)).thenReturn(List.of(first, second));
    when(saveLocationPort.save(org.mockito.ArgumentMatchers.any(Location.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    handler.handleUserSoftDeleted(new UserSoftDeletedEvent(1L));

    verify(loadLocationPort).findByUserId(1L);
    ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
    verify(saveLocationPort, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues()).allMatch(Location::isDeleted);
  }

  private Location location(Long id, Long userId, boolean deleted) {
    return Location.builder()
        .id(id)
        .userId(userId)
        .locationName("home")
        .postalCode("12345")
        .address("address")
        .detailAddress("detail")
        .registeredAt(Instant.now())
        .deletedAt(deleted ? Instant.now() : null)
        .build();
  }
}
