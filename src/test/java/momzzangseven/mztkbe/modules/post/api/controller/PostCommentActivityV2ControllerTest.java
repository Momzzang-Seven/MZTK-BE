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
import momzzangseven.mztkbe.global.error.post.InvalidCommentedPostsQueryException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.in.CheckAdminAccountStatusUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreateQuestionPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetMyCommentedPostsCursorUseCase;
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

@DisplayName("PostCommentActivityV2Controller contract test")
@SpringBootTest
@AutoConfigureMockMvc
class PostCommentActivityV2ControllerTest {

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
  @MockitoBean private GetMyCommentedPostsCursorUseCase getMyCommentedPostsCursorUseCase;
  @MockitoBean private AcceptAnswerUseCase acceptAnswerUseCase;
  @MockitoBean private LikePostUseCase likePostUseCase;

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns cursor post list")
  void getMyCommentedPostsV2_success() throws Exception {
    LocalDateTime now = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostListResult postResult =
        new PostListResult(
            5L,
            PostType.QUESTION,
            "question",
            "content",
            1L,
            2L,
            false,
            3L,
            "writer",
            null,
            100L,
            false,
            List.of("spring"),
            List.of(),
            now,
            now);
    given(getMyCommentedPostsCursorUseCase.execute(any(GetMyCommentedPostsCursorCommand.class)))
        .willReturn(new GetMyCommentedPostsCursorResult(List.of(postResult), true, "next"));

    mockMvc
        .perform(
            get("/v2/users/me/commented-posts")
                .param("type", "QUESTION")
                .param("search", " FoRm ")
                .param("size", "1")
                .with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(true))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.posts[0].postId").value(5))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100));

    ArgumentCaptor<GetMyCommentedPostsCursorCommand> commandCaptor =
        ArgumentCaptor.forClass(GetMyCommentedPostsCursorCommand.class);
    verify(getMyCommentedPostsCursorUseCase).execute(commandCaptor.capture());
    assertThat(commandCaptor.getValue().search()).isEqualTo(" FoRm ");
    assertThat(commandCaptor.getValue().effectiveSearch()).isEqualTo("form");
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns 400 for missing type")
  void getMyCommentedPostsV2_missingType_returns400() throws Exception {
    given(getMyCommentedPostsCursorUseCase.execute(any(GetMyCommentedPostsCursorCommand.class)))
        .willThrow(new InvalidCommentedPostsQueryException("type is required."));

    mockMvc
        .perform(get("/v2/users/me/commented-posts").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns 400 for invalid type")
  void getMyCommentedPostsV2_invalidType_returns400() throws Exception {
    mockMvc
        .perform(get("/v2/users/me/commented-posts?type=UNKNOWN").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));

    verifyNoInteractions(getMyCommentedPostsCursorUseCase);
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns 400 for invalid size")
  void getMyCommentedPostsV2_invalidSize_returns400() throws Exception {
    given(getMyCommentedPostsCursorUseCase.execute(any(GetMyCommentedPostsCursorCommand.class)))
        .willThrow(new InvalidCursorException("size must be between 1 and 50"));

    mockMvc
        .perform(get("/v2/users/me/commented-posts?type=FREE&size=0").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns 400 for malformed cursor")
  void getMyCommentedPostsV2_malformedCursor_returns400() throws Exception {
    given(getMyCommentedPostsCursorUseCase.execute(any(GetMyCommentedPostsCursorCommand.class)))
        .willAnswer(
            invocation -> {
              GetMyCommentedPostsCursorCommand command = invocation.getArgument(0);
              command.pageRequest();
              return new GetMyCommentedPostsCursorResult(List.of(), false, null);
            });

    mockMvc
        .perform(get("/v2/users/me/commented-posts?type=FREE&cursor=%%%").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts returns 401 when unauthenticated")
  void getMyCommentedPostsV2_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(get("/v2/users/me/commented-posts?type=FREE"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(getMyCommentedPostsCursorUseCase);
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
