package momzzangseven.mztkbe.modules.admin.board.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardCommentsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.api.dto.GetAdminBoardPostsRequestDTO;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Admin board request DTO 단위 테스트")
class GetAdminBoardRequestsDTOTest {

  @Test
  @DisplayName("게시글 request 는 blank search 와 기본 page/size/sort 를 정규화한다")
  void postRequest_toCommand_normalizesDefaults() {
    GetAdminBoardPostsCommand command =
        new GetAdminBoardPostsRequestDTO("   ", 10L, 20L, null, null, null, null, null, null, null)
            .toCommand(9L);

    assertThat(command.search()).isNull();
    assertThat(command.postId()).isEqualTo(10L);
    assertThat(command.userId()).isEqualTo(20L);
    assertThat(command.page()).isZero();
    assertThat(command.size()).isEqualTo(20);
    assertThat(command.sortKey()).isEqualTo(AdminBoardPostSortKey.CREATED_AT);
  }

  @Test
  @DisplayName("게시글 request 는 양수가 아닌 postId 를 거부한다")
  void postRequest_toCommand_nonPositivePostIdThrows() {
    assertThatThrownBy(
            () ->
                new GetAdminBoardPostsRequestDTO(
                        null, 0L, null, null, null, null, null, 0, 20, null)
                    .toCommand(9L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("postId must be positive");
  }

  @Test
  @DisplayName("게시글 command 는 max size 초과를 거부한다")
  void postCommand_sizeOverMaxThrows() {
    assertThatThrownBy(
            () ->
                new GetAdminBoardPostsCommand(
                    9L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    101,
                    AdminBoardPostSortKey.CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be less than or equal to 100");
  }

  @Test
  @DisplayName("댓글 request 는 blank search 와 기본 page/size/sort 를 정규화한다")
  void commentRequest_toCommand_normalizesDefaults() {
    GetAdminBoardCommentsCommand command =
        new GetAdminBoardCommentsRequestDTO(
                "   ", 31L, 7L, AdminBoardCommentTargetType.POST, null, null, null)
            .toCommand(9L);

    assertThat(command.search()).isNull();
    assertThat(command.commentId()).isEqualTo(31L);
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.targetType()).isEqualTo(AdminBoardCommentTargetType.POST);
    assertThat(command.page()).isZero();
    assertThat(command.size()).isEqualTo(20);
    assertThat(command.sortKey()).isEqualTo(AdminBoardCommentSortKey.CREATED_AT);
  }

  @Test
  @DisplayName("댓글 request 는 whitelist 밖 sort 값을 거부한다")
  void commentRequest_toCommand_unsupportedSortThrows() {
    assertThatThrownBy(
            () ->
                new GetAdminBoardCommentsRequestDTO(null, null, null, null, 0, 20, "postId")
                    .toCommand(9L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported sort value");
  }

  @Test
  @DisplayName("댓글 command 는 max size 초과를 거부한다")
  void commentCommand_sizeOverMaxThrows() {
    assertThatThrownBy(
            () ->
                new GetAdminBoardCommentsCommand(
                    9L, null, null, null, null, 0, 101, AdminBoardCommentSortKey.CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("size must be less than or equal to 100");
  }
}
