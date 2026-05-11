package momzzangseven.mztkbe.modules.admin.board.api.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSearchResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardCommentUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.BanAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.UnblockAdminBoardPostUseCase;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
  @MockitoBean private GetAdminBoardCommentsUseCase getAdminBoardCommentsUseCase;
  @MockitoBean private GetAdminBoardPostCommentsUseCase getAdminBoardPostCommentsUseCase;
  @MockitoBean private BanAdminBoardPostUseCase banAdminBoardPostUseCase;
  @MockitoBean private UnblockAdminBoardPostUseCase unblockAdminBoardPostUseCase;
  @MockitoBean private BanAdminBoardCommentUseCase banAdminBoardCommentUseCase;

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
                        AdminBoardPostType.FREE,
                        AdminBoardPostStatus.OPEN,
                        AdminBoardPostPublicationStatus.VISIBLE,
                        AdminBoardPostModerationStatus.NORMAL,
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
        .andExpect(jsonPath("$.data.content[0].publicationStatus").value("VISIBLE"))
        .andExpect(jsonPath("$.data.content[0].moderationStatus").value("NORMAL"))
        .andExpect(jsonPath("$.data.content[0].writerNickname").value("writer"))
        .andExpect(jsonPath("$.data.content[0].commentCount").value(3));
  }

  @Test
  @DisplayName("GET /admin/boards/posts 검색과 상태 필터를 command 로 전달한다")
  void getPosts_withSearchFilters_passesCommand() throws Exception {
    given(
            getAdminBoardPostsUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminBoardPostsCommand.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/admin/boards/posts")
                .param("search", "  content  ")
                .param("postId", "21")
                .param("userId", "7")
                .param("status", "OPEN")
                .param("type", "QUESTION")
                .param("publicationStatus", "FAILED")
                .param("moderationStatus", "BLOCKED")
                .param("page", "2")
                .param("size", "30")
                .param("sort", "type")
                .with(adminPrincipal(9L)))
        .andExpect(status().isOk());

    ArgumentCaptor<GetAdminBoardPostsCommand> captor =
        ArgumentCaptor.forClass(GetAdminBoardPostsCommand.class);
    org.mockito.Mockito.verify(getAdminBoardPostsUseCase).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().search()).isEqualTo("content");
    org.assertj.core.api.Assertions.assertThat(captor.getValue().postId()).isEqualTo(21L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().status())
        .isEqualTo(AdminBoardPostStatus.OPEN);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().type())
        .isEqualTo(AdminBoardPostType.QUESTION);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().publicationStatus())
        .isEqualTo(AdminBoardPostPublicationStatus.FAILED);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().moderationStatus())
        .isEqualTo(AdminBoardPostModerationStatus.BLOCKED);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(30);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sortKey().name())
        .isEqualTo("TYPE");
  }

  @Test
  @DisplayName("GET /admin/boards/posts whitelist 밖 sort 값이면 400")
  void getPosts_invalidSort_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").param("sort", "title").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/posts postId 가 양수가 아니면 400")
  void getPosts_nonPositivePostId_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").param("postId", "0").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/posts userId 가 양수가 아니면 400")
  void getPosts_nonPositiveUserId_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").param("userId", "0").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/posts size 가 100을 초과하면 400")
  void getPosts_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/posts").param("size", "101").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/comments ADMIN 이면 전역 댓글 검색 페이지 응답을 반환한다")
  void getAllComments_admin_returns200() throws Exception {
    given(
            getAdminBoardCommentsUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminBoardCommentsCommand.class)))
        .willReturn(
            new PageImpl<>(
                List.of(
                    new AdminBoardCommentSearchResult(
                        31L,
                        21L,
                        41L,
                        30L,
                        AdminBoardCommentTargetType.ANSWER,
                        7L,
                        "writer",
                        "comment",
                        true,
                        LocalDateTime.parse("2025-01-02T10:00:00"),
                        LocalDateTime.parse("2025-01-03T10:00:00")))));

    mockMvc
        .perform(get("/admin/boards/comments").with(adminPrincipal(9L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(31))
        .andExpect(jsonPath("$.data.content[0].postId").value(21))
        .andExpect(jsonPath("$.data.content[0].answerId").value(41))
        .andExpect(jsonPath("$.data.content[0].parentId").value(30))
        .andExpect(jsonPath("$.data.content[0].targetType").value("ANSWER"))
        .andExpect(jsonPath("$.data.content[0].userId").value(7))
        .andExpect(jsonPath("$.data.content[0].nickname").value("writer"))
        .andExpect(jsonPath("$.data.content[0].isDeleted").value(true));
  }

  @Test
  @DisplayName("GET /admin/boards/comments 검색 필터와 페이지 조건을 command 로 전달한다")
  void getAllComments_withSearchFilters_passesCommand() throws Exception {
    given(
            getAdminBoardCommentsUseCase.execute(
                org.mockito.ArgumentMatchers.any(GetAdminBoardCommentsCommand.class)))
        .willReturn(new PageImpl<>(List.of()));

    mockMvc
        .perform(
            get("/admin/boards/comments")
                .param("search", "  comment  ")
                .param("commentId", "31")
                .param("userId", "7")
                .param("targetType", "ANSWER")
                .param("page", "2")
                .param("size", "30")
                .param("sort", "commentId")
                .with(adminPrincipal(9L)))
        .andExpect(status().isOk());

    ArgumentCaptor<GetAdminBoardCommentsCommand> captor =
        ArgumentCaptor.forClass(GetAdminBoardCommentsCommand.class);
    org.mockito.Mockito.verify(getAdminBoardCommentsUseCase).execute(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().search()).isEqualTo("comment");
    org.assertj.core.api.Assertions.assertThat(captor.getValue().commentId()).isEqualTo(31L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().targetType())
        .isEqualTo(AdminBoardCommentTargetType.ANSWER);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(30);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sortKey().name())
        .isEqualTo("COMMENT_ID");
  }

  @Test
  @DisplayName("GET /admin/boards/comments whitelist 밖 sort 값이면 400")
  void getAllComments_invalidSort_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").param("sort", "postId").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/comments commentId 가 양수가 아니면 400")
  void getAllComments_nonPositiveCommentId_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").param("commentId", "0").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/comments userId 가 양수가 아니면 400")
  void getAllComments_nonPositiveUserId_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").param("userId", "0").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/comments size 가 100을 초과하면 400")
  void getAllComments_sizeOverMax_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").param("size", "101").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/comments targetType 이 enum 밖이면 400")
  void getAllComments_invalidTargetType_returns400() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").param("targetType", "BAD").with(adminPrincipal(9L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /admin/boards/posts enum filter 값이 잘못되면 400")
  void getPosts_invalidEnumFilter_returns400() throws Exception {
    mockMvc
        .perform(
            get("/admin/boards/posts").param("publicationStatus", "BAD").with(adminPrincipal(9L)))
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

  @Test
  @DisplayName("GET /admin/boards/comments USER 권한이면 403")
  void getAllComments_userForbidden_returns403() throws Exception {
    mockMvc
        .perform(get("/admin/boards/comments").with(userPrincipal(1L)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/boards/comments/{commentId}/ban 응답은 post 상태 필드를 제외한다")
  void banComment_admin_returns200() throws Exception {
    given(
            banAdminBoardCommentUseCase.execute(
                org.mockito.ArgumentMatchers.any(BanAdminBoardCommentCommand.class)))
        .willReturn(
            new AdminBoardModerationResult(
                31L,
                AdminBoardModerationTargetType.COMMENT,
                AdminBoardModerationReasonCode.SPAM,
                true));

    mockMvc
        .perform(
            post("/admin/boards/comments/{commentId}/ban", 31L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"SPAM\",\"reasonDetail\":\"ad\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetId").value(31))
        .andExpect(jsonPath("$.data.targetType").value("COMMENT"))
        .andExpect(jsonPath("$.data.reasonCode").value("SPAM"))
        .andExpect(jsonPath("$.data.moderated").value(true))
        .andExpect(jsonPath("$.data.publicationStatus").doesNotExist())
        .andExpect(jsonPath("$.data.moderationStatus").doesNotExist())
        .andExpect(jsonPath("$.data.publiclyVisible").doesNotExist());
  }

  @Test
  @DisplayName("POST /admin/boards/comments/{commentId}/ban reasonCode 가 enum 밖이면 400")
  void banComment_invalidReasonCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/comments/{commentId}/ban", 31L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"BAD\",\"reasonDetail\":\"ad\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /admin/boards/comments/{commentId}/ban reasonCode 가 null 이면 400")
  void banComment_nullReasonCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/comments/{commentId}/ban", 31L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":null,\"reasonDetail\":\"ad\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /admin/boards/comments/{commentId}/ban reasonDetail 이 500자를 초과하면 400")
  void banComment_tooLongReasonDetail_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/comments/{commentId}/ban", 31L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"SPAM\",\"reasonDetail\":\"" + "a".repeat(501) + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/ban 응답은 post 상태 필드를 포함한다")
  void banPost_admin_returns200WithStatuses() throws Exception {
    given(
            banAdminBoardPostUseCase.execute(
                org.mockito.ArgumentMatchers.any(BanAdminBoardPostCommand.class)))
        .willReturn(
            new AdminBoardModerationResult(
                21L,
                AdminBoardModerationTargetType.POST,
                AdminBoardModerationReasonCode.POLICY_VIOLATION,
                true,
                AdminBoardPostPublicationStatus.VISIBLE,
                AdminBoardPostModerationStatus.BLOCKED));

    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/ban", 21L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"POLICY_VIOLATION\",\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetId").value(21))
        .andExpect(jsonPath("$.data.targetType").value("POST"))
        .andExpect(jsonPath("$.data.reasonCode").value("POLICY_VIOLATION"))
        .andExpect(jsonPath("$.data.moderated").value(true))
        .andExpect(jsonPath("$.data.publicationStatus").value("VISIBLE"))
        .andExpect(jsonPath("$.data.moderationStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.data.publiclyVisible").value(false));
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/unblock 응답은 post 상태 필드와 공개 여부 true 를 포함한다")
  void unblockPost_visibleNormal_returns200WithPubliclyVisibleTrue() throws Exception {
    given(
            unblockAdminBoardPostUseCase.execute(
                org.mockito.ArgumentMatchers.any(UnblockAdminBoardPostCommand.class)))
        .willReturn(
            new AdminBoardModerationResult(
                21L,
                AdminBoardModerationTargetType.POST,
                AdminBoardModerationReasonCode.POLICY_VIOLATION,
                true,
                AdminBoardPostPublicationStatus.VISIBLE,
                AdminBoardPostModerationStatus.NORMAL));

    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/unblock", 21L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"POLICY_VIOLATION\",\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetId").value(21))
        .andExpect(jsonPath("$.data.targetType").value("POST"))
        .andExpect(jsonPath("$.data.reasonCode").value("POLICY_VIOLATION"))
        .andExpect(jsonPath("$.data.moderated").value(true))
        .andExpect(jsonPath("$.data.publicationStatus").value("VISIBLE"))
        .andExpect(jsonPath("$.data.moderationStatus").value("NORMAL"))
        .andExpect(jsonPath("$.data.publiclyVisible").value(true));
  }

  @Test
  @DisplayName(
      "POST /admin/boards/posts/{postId}/unblock FAILED 응답은 post 상태 필드와 공개 여부 false 를 포함한다")
  void unblockPost_failedNormal_returns200WithPubliclyVisibleFalse() throws Exception {
    given(
            unblockAdminBoardPostUseCase.execute(
                org.mockito.ArgumentMatchers.any(UnblockAdminBoardPostCommand.class)))
        .willReturn(
            new AdminBoardModerationResult(
                21L,
                AdminBoardModerationTargetType.POST,
                AdminBoardModerationReasonCode.POLICY_VIOLATION,
                true,
                AdminBoardPostPublicationStatus.FAILED,
                AdminBoardPostModerationStatus.NORMAL));

    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/unblock", 21L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"POLICY_VIOLATION\",\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetId").value(21))
        .andExpect(jsonPath("$.data.targetType").value("POST"))
        .andExpect(jsonPath("$.data.reasonCode").value("POLICY_VIOLATION"))
        .andExpect(jsonPath("$.data.moderated").value(true))
        .andExpect(jsonPath("$.data.publicationStatus").value("FAILED"))
        .andExpect(jsonPath("$.data.moderationStatus").value("NORMAL"))
        .andExpect(jsonPath("$.data.publiclyVisible").value(false));
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/ban reasonCode 가 null 이면 400")
  void banPost_nullReasonCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/ban", 21L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":null,\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/unblock reasonCode 가 null 이면 400")
  void unblockPost_nullReasonCode_returns400() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/unblock", 21L)
                .with(adminPrincipal(9L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":null,\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/ban USER 권한이면 403")
  void banPost_userForbidden_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/ban", 21L)
                .with(userPrincipal(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"POLICY_VIOLATION\",\"reasonDetail\":\"policy\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /admin/boards/posts/{postId}/unblock USER 권한이면 403")
  void unblockPost_userForbidden_returns403() throws Exception {
    mockMvc
        .perform(
            post("/admin/boards/posts/{postId}/unblock", 21L)
                .with(userPrincipal(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"POLICY_VIOLATION\",\"reasonDetail\":\"policy\"}"))
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
