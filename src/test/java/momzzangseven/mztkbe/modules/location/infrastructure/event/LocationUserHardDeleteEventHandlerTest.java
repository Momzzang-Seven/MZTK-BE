package momzzangseven.mztkbe.modules.location.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.location.application.service.LocationHardDeleteService;
import momzzangseven.mztkbe.modules.user.domain.event.UsersHardDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationUserHardDeleteEventHandlerTest {

  @Mock private LocationHardDeleteService locationHardDeleteService;

  @InjectMocks private LocationUserHardDeleteEventHandler handler;

  @Test
  void handleUsersHardDeleted_ignoresNullOrEmptyUserIds() {
    handler.handleUsersHardDeleted(new UsersHardDeletedEvent(null));
    handler.handleUsersHardDeleted(new UsersHardDeletedEvent(List.of()));

    verify(locationHardDeleteService, never()).deleteByUserIds(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void handleUsersHardDeleted_deletesByUserIds() {
    when(locationHardDeleteService.deleteByUserIds(List.of(1L, 2L))).thenReturn(3);

    handler.handleUsersHardDeleted(new UsersHardDeletedEvent(List.of(1L, 2L)));

    verify(locationHardDeleteService).deleteByUserIds(List.of(1L, 2L));
  }

  @Test
  void handleUsersHardDeleted_rethrowsWhenDeletionFails() {
    when(locationHardDeleteService.deleteByUserIds(List.of(1L)))
        .thenThrow(new RuntimeException("db fail"));

    assertThatThrownBy(() -> handler.handleUsersHardDeleted(new UsersHardDeletedEvent(List.of(1L))))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("db fail");
  }
}

