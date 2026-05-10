package momzzangseven.mztkbe.modules.admin.board.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSortKey;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardCommentsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminBoardCommentsService 단위 테스트")
class GetAdminBoardCommentsServiceTest {

  @Mock private LoadAdminBoardCommentsPort loadAdminBoardCommentsPort;
  @Mock private LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @InjectMocks private GetAdminBoardCommentsService service;

  @Test
  @DisplayName("전역 댓글 검색 결과와 작성자 닉네임을 조합한다")
  void execute_combinesWriterNicknames() {
    GetAdminBoardCommentsCommand command =
        new GetAdminBoardCommentsCommand(
            9L,
            "hello",
            31L,
            7L,
            AdminBoardCommentTargetType.ANSWER,
            0,
            20,
            AdminBoardCommentSortKey.CREATED_AT);
    given(
            loadAdminBoardCommentsPort.load(
                new LoadAdminBoardCommentsPort.AdminBoardCommentQuery(
                    "hello",
                    31L,
                    7L,
                    AdminBoardCommentTargetType.ANSWER,
                    0,
                    20,
                    AdminBoardCommentSortKey.CREATED_AT)))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new LoadAdminBoardCommentsPort.AdminBoardCommentView(
                        31L,
                        21L,
                        41L,
                        AdminBoardCommentTargetType.ANSWER,
                        7L,
                        "comment",
                        true,
                        LocalDateTime.parse("2025-01-02T00:00:00"),
                        LocalDateTime.parse("2025-01-03T00:00:00")))));
    given(loadAdminBoardWriterNicknamesPort.load(List.of(7L))).willReturn(Map.of(7L, "writer"));

    var result = service.execute(command);

    assertThat(result.getContent().get(0).commentId()).isEqualTo(31L);
    assertThat(result.getContent().get(0).postId()).isEqualTo(21L);
    assertThat(result.getContent().get(0).answerId()).isEqualTo(41L);
    assertThat(result.getContent().get(0).targetType())
        .isEqualTo(AdminBoardCommentTargetType.ANSWER);
    assertThat(result.getContent().get(0).userId()).isEqualTo(7L);
    assertThat(result.getContent().get(0).nickname()).isEqualTo("writer");
    assertThat(result.getContent().get(0).isDeleted()).isTrue();
  }
}
