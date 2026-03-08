package momzzangseven.mztkbe.modules.location.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import momzzangseven.mztkbe.modules.location.application.port.out.DeleteLocationPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocationHardDeleteServiceTest {

  @Mock private DeleteLocationPort deleteLocationPort;

  @InjectMocks private LocationHardDeleteService service;

  @Test
  void deleteByUserIds_shouldReturnZeroWhenInputEmpty() {
    int deleted = service.deleteByUserIds(List.of());

    assertThat(deleted).isZero();
    verify(deleteLocationPort, never()).deleteByUserIds(List.of());
  }

  @Test
  void deleteByUserIds_shouldDelegateWhenInputPresent() {
    when(deleteLocationPort.deleteByUserIds(List.of(1L, 2L))).thenReturn(3);

    int deleted = service.deleteByUserIds(List.of(1L, 2L));

    assertThat(deleted).isEqualTo(3);
    verify(deleteLocationPort).deleteByUserIds(List.of(1L, 2L));
  }
}
