package momzzangseven.mztkbe.modules.comment.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("CommentController integration test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  protected CommentJpaRepository commentJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected PostJpaRepository postJpaRepository;

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

  @MockitoBean private GrantXpUseCase grantXpUseCase;

  @BeforeEach
  void setUp() {
    org.mockito.BDDMockito.given(grantXpUseCase.execute(any()))
        .willReturn(GrantXpResult.granted(1, -1, 1, LocalDate.of(2026, 3, 29)));
  }

  @Test
  @DisplayName("create, query, and delete comment are reflected in H2")
  void createGetDeleteComment_realFlow_reflectsInH2() throws Exception {
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(401L)
                .type(PostType.FREE)
                .title("comment test title")
                .content("comment test body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());
    Long postId = savedPost.getId();

    MvcResult createCommentResult =
        mockMvc
            .perform(
                post("/posts/" + postId + "/comments")
                    .with(userPrincipal(401L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "first comment"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").value("first comment"))
            .andReturn();
    Long commentId = extractLong(createCommentResult, "/data/commentId");

    CommentEntity saved = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(saved.getPostId()).isEqualTo(postId);
    assertThat(saved.getWriterId()).isEqualTo(401L);
    assertThat(saved.getContent()).isEqualTo("first comment");
    assertThat(saved.isDeleted()).isFalse();

    mockMvc
        .perform(get("/posts/" + postId + "/comments").with(userPrincipal(401L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(commentId))
        .andExpect(jsonPath("$.data.content[0].content").value("first comment"));

    mockMvc
        .perform(delete("/comments/" + commentId).with(userPrincipal(401L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    CommentEntity deleted = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(deleted.isDeleted()).isTrue();

    mockMvc
        .perform(get("/posts/" + postId + "/comments").with(userPrincipal(401L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(commentId))
        .andExpect(jsonPath("$.data.content[0].isDeleted").value(true))
        .andExpect(jsonPath("$.data.content[0].content").value("삭제된 댓글입니다."));
  }

  @Test
  @DisplayName("comment content longer than 1000 chars is persisted")
  void createComment_longContent_persistsInH2() throws Exception {
    String longContent = "a".repeat(5000);

    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(401L)
                .type(PostType.FREE)
                .title("long content post")
                .content("body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());

    MvcResult createCommentResult =
        mockMvc
            .perform(
                post("/posts/" + savedPost.getId() + "/comments")
                    .with(userPrincipal(401L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", longContent))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").value(longContent))
            .andReturn();

    Long commentId = extractLong(createCommentResult, "/data/commentId");
    CommentEntity saved = commentJpaRepository.findById(commentId).orElseThrow();

    assertThat(saved.getContent()).hasSize(5000);
    assertThat(saved.getContent()).isEqualTo(longContent);
  }

  @Test
  @DisplayName("missing post returns 404 when fetching root comments")
  void getRootComments_missingPost_returns404() throws Exception {
    mockMvc
        .perform(get("/posts/999999/comments").with(userPrincipal(401L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("POST_001"));
  }

  @Test
  @DisplayName("missing post returns 404 when fetching replies")
  void getReplies_missingParentPost_returns404() throws Exception {
    CommentEntity orphanParent =
        commentJpaRepository.save(
            CommentEntity.builder()
                .postId(999999L)
                .writerId(401L)
                .content("orphan parent")
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

    mockMvc
        .perform(get("/comments/" + orphanParent.getId() + "/replies").with(userPrincipal(401L)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("POST_001"));
  }

  private Long extractLong(MvcResult result, String pointer) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at(pointer).asLong();
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.Objects.requireNonNull(userId, "userId");
    java.util.List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
