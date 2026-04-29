package momzzangseven.mztkbe.modules.post.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreateQuestionPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyLikedPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.LikePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.RecoverQuestionPostEscrowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsCursorUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.service.GetPostService;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("PostV2Controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
class PostV2ControllerTest {

  @Autowired private MockMvc mockMvc;

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

  @MockitoBean private JwtTokenProvider jwtTokenProvider;
  @MockitoBean private CheckAccountStatusUseCase checkAccountStatusUseCase;
  @MockitoBean private CheckAdminAccountStatusUseCase checkAdminAccountStatusUseCase;
  @MockitoBean private CreatePostUseCase createPostUseCase;
  @MockitoBean private CreateQuestionPostUseCase createQuestionPostUseCase;
  @MockitoBean private GetPostService getPostService;
  @MockitoBean private UpdatePostUseCase updatePostUseCase;
  @MockitoBean private DeletePostUseCase deletePostUseCase;
  @MockitoBean private RecoverQuestionPostEscrowUseCase recoverQuestionPostEscrowUseCase;
  @MockitoBean private SearchPostsUseCase searchPostsUseCase;
  @MockitoBean private SearchPostsCursorUseCase searchPostsCursorUseCase;
  @MockitoBean private GetMyLikedPostsCursorUseCase getMyLikedPostsCursorUseCase;
  @MockitoBean private GetMyPostsCursorUseCase getMyPostsCursorUseCase;
  @MockitoBean private AcceptAnswerUseCase acceptAnswerUseCase;
  @MockitoBean private LikePostUseCase likePostUseCase;

  @Test
  @DisplayName("GET /v2/posts returns cursor post list")
  void getPostsV2_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 24, 12, 0);
    PostListResult postResult =
        new PostListResult(
            2L,
            PostType.FREE,
            "list",
            "content",
            4L,
            3L,
            true,
            1L,
            "nickname",
            null,
            0L,
            false,
            List.of("health"),
            List.of(),
            now,
            now);
    given(searchPostsCursorUseCase.searchPostsByCursor(any(), any(Long.class)))
        .willReturn(new SearchPostsCursorResult(List.of(postResult), true, "next"));

    mockMvc
        .perform(get("/v2/posts?type=FREE&tag=health&search=list&size=1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.posts[0].postId").value(2))
        .andExpect(jsonPath("$.data.posts[0].commentCount").value(3));
  }

  @Test
  @DisplayName("GET /v2/posts returns 400 for malformed cursor")
  void getPostsV2_malformedCursor_returns400() throws Exception {
    mockMvc
        .perform(get("/v2/posts?cursor=%%%").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(searchPostsCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/posts returns 401 when unauthenticated")
  void getPostsV2_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/v2/posts")).andExpect(status().isUnauthorized());

    verifyNoInteractions(searchPostsCursorUseCase);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    List<SimpleGrantedAuthority> grantedAuthorities =
        List.of(new SimpleGrantedAuthority("ROLE_USER"));
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }
}
