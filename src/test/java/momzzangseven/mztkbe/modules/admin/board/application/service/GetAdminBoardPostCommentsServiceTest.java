package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminBoardPostCommentsService 단위 테스트")
class GetAdminBoardPostCommentsServiceTest {

  @Mock private LoadAdminBoardPostCommentsPort loadAdminBoardPostCommentsPort;
  @Mock private LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @InjectMocks private GetAdminBoardPostCommentsService service;

  @Test
  @DisplayName("댓글과 작성자 닉네임을 조합한다")
  void execute_combinesWriterNicknames() {
    GetAdminBoardPostCommentsCommand command = new GetAdminBoardPostCommentsCommand(9L, 21L, 0, 20);
    given(loadAdminBoardPostCommentsPort.load(command))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new LoadAdminBoardPostCommentsPort.AdminBoardCommentView(
                        31L,
                        21L,
                        7L,
                        "comment",
                        null,
                        false,
                        LocalDateTime.parse("2025-01-02T00:00:00")))));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(7L))).willReturn(Map.of(7L, "writer"));

    var result = service.execute(command);

    assertThat(result.getContent().get(0).commentId()).isEqualTo(31L);
    assertThat(result.getContent().get(0).writerNickname()).isEqualTo("writer");
    assertThat(result.getContent().get(0).isDeleted()).isFalse();
  }
}
