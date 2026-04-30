package momzzangseven.mztkbe.modules.post.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidLikedPostsQueryException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("PostLikeV2Controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
class PostLikeV2ControllerTest {

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
  @DisplayName("GET /v2/users/me/liked-posts returns FREE liked post list")
  void getMyLikedPosts_free_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostListResult post =
        new PostListResult(
            2L,
            PostType.FREE,
            null,
            "liked free content",
            4L,
            3L,
            true,
            1L,
            "nickname",
            null,
            0L,
            false,
            List.of("routine"),
            List.of(),
            now,
            now);
    given(getMyLikedPostsCursorUseCase.execute(any()))
        .willReturn(new GetMyLikedPostsCursorResult(List.of(post), true, "next"));

    mockMvc
        .perform(get("/v2/users/me/liked-posts?type=FREE&size=1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.posts[0].postId").value(2))
        .andExpect(jsonPath("$.data.posts[0].type").value("FREE"))
        .andExpect(jsonPath("$.data.posts[0].isLiked").value(true));
  }

  @Test
  @DisplayName("GET /v2/users/me/liked-posts returns QUESTION liked post list")
  void getMyLikedPosts_question_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostListResult post =
        new PostListResult(
            3L,
            PostType.QUESTION,
            "question",
            "liked question content",
            5L,
            4L,
            true,
            1L,
            "nickname",
            null,
            100L,
            false,
            List.of("squat"),
            List.of(),
            now,
            now);
    given(getMyLikedPostsCursorUseCase.execute(any()))
        .willReturn(new GetMyLikedPostsCursorResult(List.of(post), false, null));

    mockMvc
        .perform(
            get("/v2/users/me/liked-posts?type=QUESTION&search=Form&size=10")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.data.posts[0].postId").value(3))
        .andExpect(jsonPath("$.data.posts[0].type").value("QUESTION"))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100));

    ArgumentCaptor<GetMyLikedPostsCursorCommand> captor =
        ArgumentCaptor.forClass(GetMyLikedPostsCursorCommand.class);
    org.mockito.Mockito.verify(getMyLikedPostsCursorUseCase).execute(captor.capture());
    assertThat(captor.getValue().search()).isEqualTo("Form");
    assertThat(captor.getValue().effectiveSearch()).isEqualTo("form");
  }

  @Test
  @DisplayName("GET /v2/users/me/liked-posts returns 400 for missing type")
  void getMyLikedPosts_missingType_returns400() throws Exception {
    given(getMyLikedPostsCursorUseCase.execute(any()))
        .willThrow(new InvalidLikedPostsQueryException("type is required."));

    mockMvc
        .perform(get("/v2/users/me/liked-posts").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/liked-posts returns 400 for malformed cursor")
  void getMyLikedPosts_malformedCursor_returns400() throws Exception {
    given(getMyLikedPostsCursorUseCase.execute(any()))
        .willThrow(new InvalidCursorException("Invalid cursor"));

    mockMvc
        .perform(get("/v2/users/me/liked-posts?type=FREE&cursor=%%%").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/liked-posts returns 401 when unauthenticated")
  void getMyLikedPosts_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/v2/users/me/liked-posts?type=FREE")).andExpect(status().isUnauthorized());

    verifyNoInteractions(getMyLikedPostsCursorUseCase);
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
