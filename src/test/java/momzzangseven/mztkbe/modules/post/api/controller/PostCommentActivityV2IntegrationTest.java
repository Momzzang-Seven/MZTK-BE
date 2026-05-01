package momzzangseven.mztkbe.modules.post.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter.ImageModuleAdapter;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("PostCommentActivityV2 MockMvc + H2 integration test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostCommentActivityV2IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private CommentJpaRepository commentJpaRepository;

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
  @MockitoBean private ImageModuleAdapter imageModuleAdapter;

  @BeforeEach
  void setUp() {
    given(grantXpUseCase.execute(any()))
        .willReturn(GrantXpResult.granted(20, 10, 1, LocalDate.of(2026, 4, 26)));
    given(imageModuleAdapter.loadImagesByPostIds(any())).willReturn(Map.of());
    given(imageModuleAdapter.loadImages(any(), any())).willReturn(PostImageResult.empty());
  }

  @Test
  @DisplayName(
      "GET /v2/users/me/commented-posts lists FREE and QUESTION posts and detail is reachable")
  void getMyCommentedPosts_listsByTypeAndDetailIsReachable() throws Exception {
    UserEntity requester = saveUser("requester@example.com", "requester");
    UserEntity writer = saveUser("writer@example.com", "writer");
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity latestFree = savePost(writer.getId(), PostType.FREE, null, "latest free");
    PostEntity secondFree = savePost(writer.getId(), PostType.FREE, null, "second free");
    PostEntity question =
        savePost(writer.getId(), PostType.QUESTION, "question title", "question content");

    saveComment(latestFree.getId(), requester.getId(), "older duplicate", base.minusMinutes(10));
    saveComment(secondFree.getId(), requester.getId(), "second free", base.minusMinutes(2));
    saveComment(question.getId(), requester.getId(), "question", base.minusMinutes(3));
    saveComment(latestFree.getId(), requester.getId(), "latest free", base.minusMinutes(1));
    saveComment(latestFree.getId(), 9999L, "other writer", base.plusMinutes(1));
    saveComment(secondFree.getId(), requester.getId(), "deleted newer", base.plusMinutes(2), true);

    MvcResult firstPage =
        mockMvc
            .perform(
                get("/v2/users/me/commented-posts")
                    .param("type", "FREE")
                    .param("size", "1")
                    .with(userPrincipal(requester.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.posts[0].postId").value(latestFree.getId()))
            .andReturn();

    JsonNode firstBody = objectMapper.readTree(firstPage.getResponse().getContentAsString());
    Long listedPostId = firstBody.at("/data/posts/0/postId").asLong();
    String nextCursor = firstBody.at("/data/nextCursor").asText();
    assertThat(nextCursor).isNotBlank();

    mockMvc
        .perform(get("/posts/" + listedPostId).with(userPrincipal(requester.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.postId").value(listedPostId));

    mockMvc
        .perform(
            get("/v2/users/me/commented-posts")
                .param("type", "FREE")
                .param("size", "1")
                .param("cursor", nextCursor)
                .with(userPrincipal(requester.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.posts[0].postId").value(secondFree.getId()));

    mockMvc
        .perform(
            get("/v2/users/me/commented-posts")
                .param("type", "QUESTION")
                .with(userPrincipal(requester.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.posts[0].postId").value(question.getId()))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100));
  }

  @Test
  @DisplayName("GET /v2/users/me/commented-posts searches title inside my commented post refs")
  void getMyCommentedPosts_searchesTitleInsideMyCommentedRefs() throws Exception {
    UserEntity requester = saveUser("search-requester@example.com", "search-requester");
    UserEntity writer = saveUser("search-writer@example.com", "search-writer");
    UserEntity otherCommenter = saveUser("search-other@example.com", "search-other");
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity matching =
        savePost(writer.getId(), PostType.QUESTION, "100%_ Form", "matching content");
    PostEntity wildcardDecoy =
        savePost(writer.getId(), PostType.QUESTION, "100ab Form", "wildcard decoy");
    PostEntity notCommented =
        savePost(writer.getId(), PostType.QUESTION, "100%_ Form - not commented", "not mine");
    PostEntity otherUserCommented =
        savePost(writer.getId(), PostType.QUESTION, "100%_ Form - other user", "other user");

    saveComment(matching.getId(), requester.getId(), "matching", base.minusMinutes(1));
    saveComment(wildcardDecoy.getId(), requester.getId(), "wildcard decoy", base.minusMinutes(2));
    saveComment(
        otherUserCommented.getId(), otherCommenter.getId(), "other user", base.minusMinutes(3));

    mockMvc
        .perform(
            get("/v2/users/me/commented-posts")
                .param("type", "QUESTION")
                .param("search", "100%_")
                .with(userPrincipal(requester.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.posts.length()").value(1))
        .andExpect(jsonPath("$.data.posts[0].postId").value(matching.getId()))
        .andExpect(jsonPath("$.data.posts[0].title").value("100%_ Form"));

    assertThat(notCommented.getId()).isNotEqualTo(matching.getId());
  }

  private UserEntity saveUser(String email, String nickname) {
    return userJpaRepository.save(
        UserEntity.builder()
            .email(email)
            .nickname(nickname)
            .profileImageUrl(null)
            .role(UserRole.USER)
            .build());
  }

  private PostEntity savePost(Long userId, PostType type, String title, String content) {
    return postJpaRepository.saveAndFlush(
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .content(content)
            .reward(PostType.QUESTION.equals(type) ? 100L : 0L)
            .status(PostStatus.OPEN)
            .build());
  }

  private CommentEntity saveComment(
      Long postId, Long writerId, String content, LocalDateTime createdAt) {
    return saveComment(postId, writerId, content, createdAt, false);
  }

  private CommentEntity saveComment(
      Long postId, Long writerId, String content, LocalDateTime createdAt, boolean deleted) {
    return commentJpaRepository.saveAndFlush(
        CommentEntity.builder()
            .postId(postId)
            .writerId(writerId)
            .content(content)
            .isDeleted(deleted)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());
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
