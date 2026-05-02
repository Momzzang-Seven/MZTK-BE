package momzzangseven.mztkbe.modules.admin.board.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostsUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("AdminBoardController 컨트롤러 계약 테스트")
@SpringBootTest
@AutoConfigureMockMvc
class AdminBoardControllerTest {

  @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private GetAdminBoardPostsUseCase getAdminBoardPostsUseCase;
  @MockitoBean private GetAdminBoardPostCommentsUseCase getAdminBoardPostCommentsUseCase;

  @Test
  @DisplayName("GET /admin/boards/posts ADMIN 이면 페이지 응답을 반환한다")
  void getPosts_admin_returns200() throws Exception {
    given(
            getAdminBoardPostsUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminBoardPostsCommand.class)))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new AdminBoardPostResult(
                        21L,
                        PostType.FREE,
                        PostStatus.OPEN,
                        "title",
                        "content",
                        7L,
                        "writer",
                        LocalDateTime.parse("2025-01-01T10:00:00"),
                        3L))));

    mockMvc
        .perform(get("/admin/boards/posts").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].postId").value(21))
        .andExpect(jsonPath("$.data.content[0].type").value("FREE"))
        .andExpect(jsonPath("$.data.content[0].writerNickname").value("writer"))
        .andExpect(jsonPath("$.data.content[0].commentCount").value(3));
  }

  @Test
  @DisplayName("GET /admin/boards/posts whitelist 밖 sort 값이면 400")
  void getPosts_invalidSort_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").param("sort", "title").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/posts/{postId}/comments 기본 page/size 로 조회한다")
  void getComments_defaultPage_returns200() throws Exception {
    given(
            getAdminBoardPostCommentsUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminBoardPostCommentsCommand.class)))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new AdminBoardCommentResult(
                        31L,
                        21L,
                        7L,
                        "writer",
                        "comment",
                        null,
                        false,
                        LocalDateTime.parse("2025-01-02T10:00:00")))));

    mockMvc
        .perform(get("/admin/boards/posts/{postId}/comments", 21L).with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(31))
        .andExpect(jsonPath("$.data.content[0].writerNickname").value("writer"))
        .andExpect(jsonPath("$.data.content[0].isDeleted").value(false));

    ArgumentCaptor<GetAdminBoardPostCommentsCommand> captor =
        ArgumentCaptor.forClass(GetAdminBoardPostCommentsCommand.class);
    org.mockito.Mockito.verify(getAdminBoardPostCommentsUseCase).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isZero();
    org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(20);
  }

  @Test
  @DisplayName("GET /admin/boards/posts USER 권한이면 403")
  void getPosts_userForbidden_returns403() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  private RequestPostProcessor adminPrincipal(Long userId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN_GENERATED"))));
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
  }
}
