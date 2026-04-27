package momzzangseven.mztkbe.modules.post.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter.ImageModuleAdapter;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostLikeEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostLikeJpaRepository;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("MyPostV2 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyPostV2IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private PostLikeJpaRepository postLikeJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

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

  @MockitoBean private ImageModuleAdapter imageModuleAdapter;

  @BeforeEach
  void setUp() {
    given(imageModuleAdapter.loadImagesByPostIds(any())).willReturn(Map.of());
  }

  @Test
  @DisplayName("GET /v2/users/me/posts applies author, type, cursor, and requester like state")
  void getMyPosts_realFlow_filtersTypeAndAppliesCursor() throws Exception {
    Long requesterId = persistUser("requester").getId();
    Long otherUserId = persistUser("other").getId();

    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostEntity firstFree = persistPost(requesterId, PostType.FREE, null, "first free", base);
    PostEntity secondFree =
        persistPost(requesterId, PostType.FREE, null, "second free", base.minusMinutes(1));
    persistPost(otherUserId, PostType.FREE, null, "other free", base.plusMinutes(1));
    persistPost(requesterId, PostType.QUESTION, "question", "question", base.minusMinutes(2));
    persistLike(PostLikeTargetType.POST, firstFree.getId(), requesterId);
    persistLike(PostLikeTargetType.POST, secondFree.getId(), otherUserId);

    MvcResult firstPage =
        mockMvc
            .perform(get("/v2/users/me/posts?type=FREE&size=1").with(userPrincipal(requesterId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.posts[0].postId").value(firstFree.getId()))
            .andExpect(jsonPath("$.data.posts[0].content").value("first free"))
            .andExpect(jsonPath("$.data.posts[0].isLiked").value(true))
            .andExpect(jsonPath("$.data.posts[0].writer.nickname").value("requester"))
            .andReturn();

    String nextCursor =
        com.jayway.jsonpath.JsonPath.read(
            firstPage.getResponse().getContentAsString(), "$.data.nextCursor");

    mockMvc
        .perform(
            get("/v2/users/me/posts?type=FREE&size=1&cursor=" + nextCursor)
                .with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.data.posts[0].postId").value(secondFree.getId()))
        .andExpect(jsonPath("$.data.posts[0].content").value("second free"))
        .andExpect(jsonPath("$.data.posts[0].isLiked").value(false));
  }

  @Test
  @DisplayName("GET /v2/users/me/posts applies tag and QUESTION search filters")
  void getMyPosts_realFlow_filtersTagAndSearch() throws Exception {
    Long requesterId = persistUser("requester").getId();
    Long otherUserId = persistUser("other").getId();
    Long squatTagId = persistTag("squat");
    Long benchTagId = persistTag("bench");
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);
    PostEntity matching =
        persistPost(
            requesterId,
            PostType.QUESTION,
            "Squat Form Check",
            "question",
            base,
            PostStatus.PENDING_ACCEPT,
            1L);
    PostEntity wrongTag =
        persistPost(requesterId, PostType.QUESTION, "Squat Form", "question", base.minusMinutes(1));
    PostEntity otherUser =
        persistPost(otherUserId, PostType.QUESTION, "Squat Form", "question", base.minusMinutes(2));
    linkTag(matching.getId(), squatTagId);
    linkTag(wrongTag.getId(), benchTagId);
    linkTag(otherUser.getId(), squatTagId);
    entityManager.clear();

    mockMvc
        .perform(
            get("/v2/users/me/posts?type=QUESTION&tag=Squat&search=form")
                .with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.posts[0].postId").value(matching.getId()))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100))
        .andExpect(jsonPath("$.data.posts[0].question.isSolved").value(true))
        .andExpect(jsonPath("$.data.posts[0].status").doesNotExist());
  }

  @Test
  @DisplayName("GET /v2/users/me/posts returns empty page when tag is missing")
  void getMyPosts_missingTag_returnsEmptyPage() throws Exception {
    Long requesterId = persistUser("requester").getId();
    persistPost(
        requesterId,
        PostType.QUESTION,
        "Squat Form Check",
        "question",
        LocalDateTime.of(2026, 4, 27, 12, 0));

    mockMvc
        .perform(
            get("/v2/users/me/posts?type=QUESTION&tag=missing").with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.data.posts").isArray())
        .andExpect(jsonPath("$.data.posts").isEmpty());
  }

  @Test
  @DisplayName("GET /v2/users/me/posts ignores search for FREE posts")
  void getMyPosts_freeSearchIgnored() throws Exception {
    Long requesterId = persistUser("requester").getId();
    PostEntity free =
        persistPost(
            requesterId,
            PostType.FREE,
            null,
            "content without keyword",
            LocalDateTime.of(2026, 4, 27, 12, 0));

    mockMvc
        .perform(
            get("/v2/users/me/posts?type=FREE&search=not-present").with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.posts[0].postId").value(free.getId()));
  }

  private UserEntity persistUser(String nickname) {
    UserEntity entity =
        UserEntity.builder()
            .email(nickname + "@example.com")
            .nickname(nickname)
            .profileImageUrl("https://cdn.example.com/" + nickname + ".png")
            .role(UserRole.USER)
            .build();
    return userJpaRepository.saveAndFlush(entity);
  }

  private PostEntity persistPost(
      Long userId, PostType type, String title, String content, LocalDateTime createdAt) {
    return persistPost(userId, type, title, content, createdAt, PostStatus.OPEN, null);
  }

  private PostEntity persistPost(Long userId, PostType type, String title, String content) {
    return persistPost(userId, type, title, content, LocalDateTime.now(), PostStatus.OPEN, null);
  }

  private PostEntity persistPost(
      Long userId,
      PostType type,
      String title,
      String content,
      LocalDateTime createdAt,
      PostStatus status,
      Long acceptedAnswerId) {
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .content(content)
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .acceptedAnswerId(acceptedAnswerId)
            .status(status)
            .build();
    ReflectionTestUtils.setField(entity, "createdAt", createdAt);
    ReflectionTestUtils.setField(entity, "updatedAt", createdAt);
    PostEntity saved = postJpaRepository.saveAndFlush(entity);
    jdbcTemplate.update(
        "UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?",
        createdAt,
        createdAt,
        saved.getId());
    entityManager.clear();
    return saved;
  }

  private PostLikeEntity persistLike(PostLikeTargetType targetType, Long targetId, Long userId) {
    PostLikeEntity entity =
        PostLikeEntity.builder().targetType(targetType).targetId(targetId).userId(userId).build();
    return postLikeJpaRepository.saveAndFlush(entity);
  }

  private Long persistTag(String name) {
    jdbcTemplate.update("INSERT INTO tags (name) VALUES (?)", name);
    return jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ?", Long.class, name);
  }

  private void linkTag(Long postId, Long tagId) {
    jdbcTemplate.update("INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)", postId, tagId);
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    return authenticatedPrincipal(userId, "ROLE_USER");
  }

  private RequestPostProcessor authenticatedPrincipal(Long userId, String... authorities) {
    java.util.List<SimpleGrantedAuthority> grantedAuthorities =
        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, grantedAuthorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }
}
