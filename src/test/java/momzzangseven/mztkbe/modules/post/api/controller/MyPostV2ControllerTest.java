package momzzangseven.mztkbe.modules.post.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.pagination.InvalidCursorException;
import momzzangseven.mztkbe.global.error.post.InvalidMyPostsQueryException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;
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

@DisplayName("MyPostV2Controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
class MyPostV2ControllerTest {

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
  @DisplayName("GET /v2/users/me/posts returns FREE authored post list")
  void getMyPosts_free_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostListResult post =
        new PostListResult(
            2L,
            PostType.FREE,
            null,
            "written free content",
            4L,
            3L,
            false,
            1L,
            "nickname",
            null,
            0L,
            false,
            List.of("routine"),
            List.of(),
            now,
            now);
    given(getMyPostsCursorUseCase.execute(any()))
        .willReturn(new GetMyPostsCursorResult(List.of(post), true, "next"));

    mockMvc
        .perform(get("/v2/users/me/posts?type=FREE&size=1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.posts[0].postId").value(2))
        .andExpect(jsonPath("$.data.posts[0].type").value("FREE"))
        .andExpect(jsonPath("$.data.posts[0].isLiked").value(false));
  }

  @Test
  @DisplayName("GET /v2/users/me/posts accepts QUESTION tag and search filters")
  void getMyPosts_questionWithFilters_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostListResult post =
        new PostListResult(
            3L,
            PostType.QUESTION,
            "question",
            "written question content",
            5L,
            4L,
            true,
            1L,
            "nickname",
            null,
            100L,
            true,
            List.of("squat"),
            List.of(),
            now,
            now);
    given(getMyPostsCursorUseCase.execute(any()))
        .willReturn(new GetMyPostsCursorResult(List.of(post), false, null));

    mockMvc
        .perform(
            get("/v2/users/me/posts?type=QUESTION&tag=Squat&search=Form&size=10")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.data.posts[0].postId").value(3))
        .andExpect(jsonPath("$.data.posts[0].type").value("QUESTION"))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100))
        .andExpect(jsonPath("$.data.posts[0].question.isSolved").value(true));

    ArgumentCaptor<GetMyPostsCursorCommand> captor =
        ArgumentCaptor.forClass(GetMyPostsCursorCommand.class);
    verify(getMyPostsCursorUseCase).execute(captor.capture());
    assertThat(captor.getValue().requesterId()).isEqualTo(1L);
    assertThat(captor.getValue().type()).isEqualTo(PostType.QUESTION);
    assertThat(captor.getValue().tag()).isEqualTo("Squat");
    assertThat(captor.getValue().search()).isEqualTo("Form");
    assertThat(captor.getValue().effectiveSearch()).isEqualTo("form");
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 400 for missing type")
  void getMyPosts_missingType_returns400() throws Exception {
    given(getMyPostsCursorUseCase.execute(any()))
        .willThrow(new InvalidMyPostsQueryException("type is required."));

    mockMvc
        .perform(get("/v2/users/me/posts").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 400 for invalid type")
  void getMyPosts_invalidType_returns400() throws Exception {
    mockMvc
        .perform(get("/v2/users/me/posts?type=UNKNOWN").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));

    verifyNoInteractions(getMyPostsCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 400 for invalid size")
  void getMyPosts_invalidSize_returns400() throws Exception {
    given(getMyPostsCursorUseCase.execute(any()))
        .willThrow(new InvalidCursorException("size must be between 1 and 50"));

    mockMvc
        .perform(get("/v2/users/me/posts?type=FREE&size=0").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 400 for non-numeric size")
  void getMyPosts_nonNumericSize_returns400() throws Exception {
    mockMvc
        .perform(get("/v2/users/me/posts?type=FREE&size=abc").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));

    verifyNoInteractions(getMyPostsCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 400 for malformed cursor")
  void getMyPosts_malformedCursor_returns400() throws Exception {
    given(getMyPostsCursorUseCase.execute(any()))
        .willThrow(new InvalidCursorException("Invalid cursor"));

    mockMvc
        .perform(get("/v2/users/me/posts?type=FREE&cursor=%%%").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns 401 when unauthenticated")
  void getMyPosts_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/v2/users/me/posts?type=FREE")).andExpect(status().isUnauthorized());

    verifyNoInteractions(getMyPostsCursorUseCase);
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
