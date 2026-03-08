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
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostCommand;
import momzzangseven.mztkbe.modules.post.application.dto.CreatePostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.port.in.CreatePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.SearchPostsUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.UpdatePostUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

@DisplayName("PostController 컨트롤러 계약 테스트 (MockMvc + H2)")
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class PostControllerTest {

  @org.springframework.beans.factory.annotation.Autowired
  protected org.springframework.test.web.servlet.MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @org.springframework.boot.test.mock.mockito.MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @MockBean private CreatePostUseCase createPostUseCase;
  @MockBean private GetPostUseCase getPostUseCase;
  @MockBean private UpdatePostUseCase updatePostUseCase;
  @MockBean private DeletePostUseCase deletePostUseCase;
  @MockBean private SearchPostsUseCase searchPostsUseCase;

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
                            "imageUrls",
                            List.of("https://example.com/1.png"),
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
  @DisplayName("POST /posts/free imageUrls URL 형식이 잘못되면 400")
  void createFreePost_invalidImageUrl_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(1L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "내용", "imageUrls", List.of("invalid-url")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"));

    verifyNoInteractions(createPostUseCase);
  }

  @Test
  @DisplayName("POST /posts/free principal이 null이면 401")
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
            new PostResult(
                1L,
                PostType.FREE,
                "제목",
                "본문",
                1L,
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
        .andExpect(jsonPath("$.data.type").value("FREE"));
  }

  @Test
  @DisplayName("GET /posts/{postId} 인증 없으면 401")
  void getPost_unauthenticated_returns401() throws Exception {
    mockMvc.perform(get("/posts/1")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /posts 목록 조회 성공")
  void getPosts_success() throws Exception {
    Post post =
        Post.builder()
            .id(2L)
            .userId(1L)
            .type(PostType.FREE)
            .title("목록")
            .content("본문")
            .imageUrls(List.of())
            .reward(0L)
            .isSolved(false)
            .tags(List.of("health"))
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    given(searchPostsUseCase.searchPosts(any(PostSearchCondition.class))).willReturn(List.of(post));

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
    mockMvc.perform(get("/posts")).andExpect(status().isUnauthorized());
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
  @DisplayName("PATCH /posts/{postId} 인증 principal이 null이면 401")
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
  @DisplayName("DELETE /posts/{postId} principal이 null이면 401")
  void deletePost_nullPrincipal_returns401() throws Exception {
    mockMvc
        .perform(delete("/posts/1").with(nullUserPrincipal()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH /posts/{postId} 인증 없으면 401")
  void updatePost_unauthenticated_returns401() throws Exception {
    mockMvc.perform(patch("/posts/1")).andExpect(status().isUnauthorized());
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
