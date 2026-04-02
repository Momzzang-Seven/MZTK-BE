package momzzangseven.mztkbe.modules.post.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostLikeResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.LikePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("PostController contract test")
@SpringBootTest
@AutoConfigureMockMvc
class PostControllerTest {

  @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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

  @MockitoBean private CreatePostUseCase createPostUseCase;
  @MockitoBean private GetPostUseCase getPostUseCase;
  @MockitoBean private UpdatePostUseCase updatePostUseCase;
  @MockitoBean private DeletePostUseCase deletePostUseCase;
  @MockitoBean private SearchPostsUseCase searchPostsUseCase;
  @MockitoBean private AcceptAnswerUseCase acceptAnswerUseCase;
  @MockitoBean private LikePostUseCase likePostUseCase;

  @Test
  @DisplayName("POST /posts/free succeeds")
  void createFreePost_success() throws Exception {
    given(createPostUseCase.execute(any(CreatePostCommand.class)))
        .willReturn(new CreatePostResult(100L, true, 20L, "ok"));

    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "content",
                            "content",
                            "imageIds",
                            List.of(1L, 2L),
                            "tags",
                            List.of("health")))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(100));

    verify(createPostUseCase).execute(any(CreatePostCommand.class));
  }

  @Test
  @DisplayName("POST /posts/free returns 401 when unauthenticated")
  void createFreePost_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/posts/free").contentType(APPLICATION_JSON).content(json(Map.of("content", "c"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("GET /posts/{postId} succeeds")
  void getPost_success() throws Exception {
    given(getPostUseCase.getPost(1L, 1L))
        .willReturn(
            new PostDetailResult(
                1L,
                PostType.FREE,
                "title",
                "content",
                3L,
                true,
                1L,
                "nickname",
                null,
                List.of(),
                0L,
                false,
                List.of("tag"),
                LocalDateTime.now(),
                LocalDateTime.now()));

    mockMvc
        .perform(get("/posts/1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1))
        .andExpect(jsonPath("$.data.type").value("FREE"))
        .andExpect(jsonPath("$.data.likeCount").value(3))
        .andExpect(jsonPath("$.data.isLiked").value(true))
        .andExpect(jsonPath("$.data.imageUrls").isArray());
  }

  @Test
  @DisplayName("GET /posts returns post list")
  void getPosts_success() throws Exception {
    PostListResult postResult =
        new PostListResult(
            2L,
            PostType.FREE,
            "list",
            "content",
            4L,
            true,
            1L,
            "nickname",
            null,
            0L,
            false,
            List.of("health"),
            LocalDateTime.now(),
            LocalDateTime.now());
    given(searchPostsUseCase.searchPosts(any(PostSearchCondition.class), any(Long.class)))
        .willReturn(List.of(postResult));

    mockMvc
        .perform(get("/posts?type=FREE&tag=health&search=list").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].postId").value(2))
        .andExpect(jsonPath("$.data[0].type").value("FREE"))
        .andExpect(jsonPath("$.data[0].likeCount").value(4))
        .andExpect(jsonPath("$.data[0].isLiked").value(true));
  }

  @Test
  @DisplayName("POST /posts/question succeeds")
  void createQuestionPost_success() throws Exception {
    given(createPostUseCase.execute(any(CreatePostCommand.class)))
        .willReturn(new CreatePostResult(200L, true, 50L, "ok"));

    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "question", "content", "body", "reward", 50))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(200));

    verify(createPostUseCase).execute(any(CreatePostCommand.class));
  }

  @Test
  @DisplayName("GET /posts/{postId} includes question info")
  void getPost_questionType_includesQuestionInfo() throws Exception {
    given(getPostUseCase.getPost(5L, 1L))
        .willReturn(
            new PostDetailResult(
                5L,
                PostType.QUESTION,
                "question",
                "body",
                0L,
                false,
                1L,
                "nickname",
                null,
                List.of(),
                50L,
                false,
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now()));

    mockMvc
        .perform(get("/posts/5").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.type").value("QUESTION"))
        .andExpect(jsonPath("$.data.question.reward").value(50))
        .andExpect(jsonPath("$.data.question.isSolved").value(false));
  }

  @Test
  @DisplayName("PATCH /posts/{postId} succeeds")
  void updatePost_success() throws Exception {
    mockMvc
        .perform(
            patch("/posts/1")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "updated"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1));
  }

  @Test
  @DisplayName("DELETE /posts/{postId} succeeds")
  void deletePost_success() throws Exception {
    mockMvc
        .perform(delete("/posts/1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1));
  }

  @Test
  @DisplayName("POST /posts/{postId}/answers/{answerId}/accept succeeds")
  void acceptAnswer_success() throws Exception {
    given(acceptAnswerUseCase.execute(any()))
        .willReturn(new AcceptAnswerResult(1L, 2L, PostStatus.RESOLVED));

    mockMvc
        .perform(post("/posts/1/answers/2/accept").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1))
        .andExpect(jsonPath("$.data.acceptedAnswerId").value(2))
        .andExpect(jsonPath("$.data.status").value("RESOLVED"));

    verify(acceptAnswerUseCase).execute(any());
  }

  @Test
  @DisplayName("POST /posts/{postId}/likes succeeds")
  void likePost_success() throws Exception {
    given(likePostUseCase.like(any()))
        .willReturn(new PostLikeResult(PostLikeTargetType.POST, 1L, true, 5L));

    mockMvc
        .perform(post("/posts/1/likes").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetType").value("POST"))
        .andExpect(jsonPath("$.data.targetId").value(1))
        .andExpect(jsonPath("$.data.liked").value(true))
        .andExpect(jsonPath("$.data.likeCount").value(5));
  }

  @Test
  @DisplayName("DELETE /questions/{postId}/answers/{answerId}/likes succeeds")
  void unlikeAnswer_success() throws Exception {
    given(likePostUseCase.unlike(any()))
        .willReturn(new PostLikeResult(PostLikeTargetType.ANSWER, 2L, false, 1L));

    mockMvc
        .perform(delete("/questions/1/answers/2/likes").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.targetType").value("ANSWER"))
        .andExpect(jsonPath("$.data.targetId").value(2))
        .andExpect(jsonPath("$.data.liked").value(false))
        .andExpect(jsonPath("$.data.likeCount").value(1));
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private RequestPostProcessor nullPrincipalWithRoles(String... authorities) {
    List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(null, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
