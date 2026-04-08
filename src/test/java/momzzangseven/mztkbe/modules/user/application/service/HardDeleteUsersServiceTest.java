package momzzangseven.mztkbe.modules.user.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.user.application.port.out.DeleteUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HardDeleteUsersService 단위 테스트")
class HardDeleteUsersServiceTest {

  @Mock private DeleteUserPort deleteUserPort;

  private HardDeleteUsersService service;

  @BeforeEach
  void setUp() {
    service = new HardDeleteUsersService(deleteUserPort);
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-51] userId 목록을 DeleteUserPort에 위임")
    void hardDeleteUsers_validIds_delegatesToPort() {
      // given
      List<Long> userIds = List.of(1L, 2L, 3L);

      // when
      service.hardDeleteUsers(userIds);

      // then
      verify(deleteUserPort).deleteAllByIdInBatch(userIds);
    }
  }

  @Nested
  @DisplayName("엣지 케이스")
  class EdgeCases {

    @Test
    @DisplayName("[M-52] 빈 목록 → deleteAllByIdInBatch 호출 안 함")
    void hardDeleteUsers_emptyList_doesNothing() {
      // when
      service.hardDeleteUsers(Collections.emptyList());

      // then
      verify(deleteUserPort, never()).deleteAllByIdInBatch(any());
    }

    @Test
    @DisplayName("[M-53] null 목록 → deleteAllByIdInBatch 호출 안 함")
    void hardDeleteUsers_nullList_doesNothing() {
      // when
      service.hardDeleteUsers(null);

      // then
      verify(deleteUserPort, never()).deleteAllByIdInBatch(any());
    }
  }
}
