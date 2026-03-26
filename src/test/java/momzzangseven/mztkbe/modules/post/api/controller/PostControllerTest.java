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
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostDetailResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.in.AcceptAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("PostController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class PostControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockitoBean private CreatePostUseCase createPostUseCase;
  @MockitoBean private GetPostUseCase getPostUseCase;
  @MockitoBean private UpdatePostUseCase updatePostUseCase;
  @MockitoBean private DeletePostUseCase deletePostUseCase;
  @MockitoBean private SearchPostsUseCase searchPostsUseCase;
  @MockitoBean private AcceptAnswerUseCase acceptAnswerUseCase;

  @Test
  @DisplayName("POST /posts/free 성공")
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
                            "내용",
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
  @DisplayName("POST /posts/free 인증 없으면 401")
  void createFreePost_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/posts/free").contentType(APPLICATION_JSON).content(json(Map.of("content", "c"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free 내용 누락이면 400")
  void createFreePost_missingContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("tags", List.of("health")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free 내용 공백이면 400")
  void createFreePost_blankContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free tags에 공백이 있으면 400")
  void createFreePost_blankTag_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "내용", "tags", List.of("   ")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free imageIds가 유효하면 201")
  void createFreePost_validImageIds_returns201() throws Exception {
    given(createPostUseCase.execute(any(CreatePostCommand.class)))
        .willReturn(new CreatePostResult(100L, false, 0L, "게시글 작성 완료"));

    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "content",
                            "내용",
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
  @DisplayName("POST /posts/free imageIds에 음수가 있으면 400")
  void createFreePost_negativeImageId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "내용", "imageIds", List.of(-1L)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free principal이 null이면 401 (AUTH_006)")
  void createFreePost_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "내용"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("GET /posts/{postId} 성공")
  void getPost_success() throws Exception {
    given(getPostUseCase.getPost(1L))
        .willReturn(
            new PostDetailResult(
                1L,
                PostType.FREE,
                "제목",
                "본문",
                1L,
                "닉네임",
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
        .andExpect(jsonPath("$.data.imageUrls").isArray());
  }

  @Test
  @DisplayName("GET /posts/{postId} 인증 없으면 401")
  void getPost_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/posts/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /posts 목록 조회 성공")
  void getPosts_success() throws Exception {
    PostListResult postResult =
        new PostListResult(
            2L,
            PostType.FREE,
            "목록",
            "본문",
            1L,
            "닉네임",
            null,
            0L,
            false,
            List.of("health"),
            LocalDateTime.now(),
            LocalDateTime.now());
    given(searchPostsUseCase.searchPosts(any(PostSearchCondition.class)))
        .willReturn(List.of(postResult));

    mockMvc
        .perform(get("/posts?type=FREE&tag=health&search=목록").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].postId").value(2))
        .andExpect(jsonPath("$.data[0].type").value("FREE"));
  }

  @Test
  @DisplayName("GET /posts type enum 값이 잘못되면 400")
  void getPosts_invalidTypeEnum_returns400() throws Exception {
    mockMvc
        .perform(get("/posts?type=INVALID").with(userPrincipal(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("VALIDATION_001"));
  }

  @Test
  @DisplayName("GET /posts 인증 없으면 401")
  void getPosts_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/posts?type=FREE")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /posts/question 성공")
  void createQuestionPost_success() throws Exception {
    given(createPostUseCase.execute(any(CreatePostCommand.class)))
        .willReturn(new CreatePostResult(200L, true, 50L, "ok"));

    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "title", "질문 제목",
                            "content", "질문 내용",
                            "reward", 50))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(200));

    verify(createPostUseCase).execute(any(CreatePostCommand.class));
  }

  @Test
  @DisplayName("POST /posts/question 인증 없으면 401")
  void createQuestionPost_unauthenticated_returns401() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "t", "content", "c", "reward", 10))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question 제목 누락이면 400")
  void createQuestionPost_missingTitle_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "내용", "reward", 10))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question 내용 누락이면 400")
  void createQuestionPost_missingContent_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "제목", "reward", 10))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question reward 누락이면 400")
  void createQuestionPost_missingReward_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "제목", "content", "내용"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question reward가 0 이하이면 400")
  void createQuestionPost_nonPositiveReward_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "제목", "content", "내용", "reward", 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question tags에 공백이 있으면 400")
  void createQuestionPost_blankTag_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "title",
                            "질문 제목",
                            "content",
                            "질문 내용",
                            "reward",
                            10,
                            "tags",
                            List.of("   ")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/question null principal이면 401 (AUTH_006)")
  void createQuestionPost_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(nullUserPrincipal())
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "제목", "content", "내용", "reward", 10))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("GET /posts/{postId} QUESTION 타입이면 question 필드 포함")
  void getPost_questionType_includesQuestionInfo() throws Exception {
    given(getPostUseCase.getPost(5L))
        .willReturn(
            new PostDetailResult(
                5L,
                PostType.QUESTION,
                "질문 제목",
                "질문 내용",
                1L,
                "닉네임",
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
  @DisplayName("GET /posts?type=QUESTION 질문 게시글 목록 조회 성공")
  void getPosts_questionType_success() throws Exception {
    PostListResult questionResult =
        new PostListResult(
            3L,
            PostType.QUESTION,
            "질문 제목",
            "질문 내용",
            1L,
            "닉네임",
            null,
            30L,
            false,
            List.of(),
            LocalDateTime.now(),
            LocalDateTime.now());
    given(searchPostsUseCase.searchPosts(any(PostSearchCondition.class)))
        .willReturn(List.of(questionResult));

    mockMvc
        .perform(get("/posts?type=QUESTION").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].type").value("QUESTION"))
        .andExpect(jsonPath("$.data[0].question.reward").value(30));
  }

  @Test
  @DisplayName("PATCH /posts/{postId} 성공")
  void updatePost_success() throws Exception {
    mockMvc
        .perform(
            patch("/posts/1")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "수정 제목"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1));
  }

  @Test
  @DisplayName("PATCH /posts/{postId} imageIds에 음수가 있으면 400")
  void updatePost_negativeImageId_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/posts/1")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("imageIds", List.of(-1L)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(updatePostUseCase);
  }

  @Test
  @DisplayName("PATCH /posts/{postId} tags에 공백이 있으면 400")
  void updatePost_blankTag_returns400() throws Exception {
    mockMvc
        .perform(
            patch("/posts/1")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("tags", List.of("   ")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(updatePostUseCase);
  }

  @Test
  @DisplayName("PATCH /posts/{postId} principal이 null이면 401 (AUTH_006)")
  void updatePost_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(
            patch("/posts/1").with(nullUserPrincipal()).contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));
  }

  @Test
  @DisplayName("DELETE /posts/{postId} 성공")
  void deletePost_success() throws Exception {
    mockMvc
        .perform(delete("/posts/1").with(userPrincipal(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(1));
  }

  @Test
  @DisplayName("DELETE /posts/{postId} 인증 없으면 401")
  void deletePost_unauthenticated_returns401() throws Exception {
    mockMvc.perform(delete("/posts/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("DELETE /posts/{postId} principal이 null이면 401 (AUTH_006)")
  void deletePost_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(delete("/posts/1").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("AUTH_006"));
  }

  @Test
  @DisplayName("PATCH /posts/{postId} 인증 없으면 401")
  void updatePost_unauthenticated_returns401() throws Exception {
    mockMvc.perform(patch("/posts/1")).andExpect(status().isUnauthorized());
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

  private org.springframework.test.web.servlet.request.RequestPostProcessor userPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor stepUpPrincipal(
      Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullUserPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullAdminPrincipal() {
    return nullPrincipalWithRoles("ROLE_ADMIN");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullStepUpPrincipal() {
    return nullPrincipalWithRoles("ROLE_USER", "ROLE_STEP_UP");
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor nullPrincipalWithRoles(
      String... authorities) {
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            null, null, grantedAuthorities);
    org.springframework.security.core.context.SecurityContext context =
        org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedPrincipal(
      Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<org.springframework.security.core.authority.SimpleGrantedAuthority>
        grantedAuthorities =
            java.util.Arrays.stream(authorities)
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken token =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            userId, null, grantedAuthorities);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.authentication(token);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
