package momzzangseven.mztkbe.modules.post.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("PostLikeV2 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostLikeV2IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserJpaRepository userJpaRepository;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private PostLikeJpaRepository postLikeJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

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
  @DisplayName("GET /v2/users/me/liked-posts는 실제 service/repository를 통해 타입 필터와 cursor를 적용한다")
  void getMyLikedPosts_realFlow_filtersTypeAndAppliesCursor() throws Exception {
    Long requesterId = persistUser("requester").getId();
    Long firstWriterId = persistUser("first-writer").getId();
    Long secondWriterId = persistUser("second-writer").getId();
    Long questionWriterId = persistUser("question-writer").getId();

    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);
    PostEntity firstFree =
        persistPost(firstWriterId, PostType.FREE, null, "first liked free content");
    PostEntity secondFree =
        persistPost(secondWriterId, PostType.FREE, null, "second liked free content");
    PostEntity question =
        persistPost(questionWriterId, PostType.QUESTION, "liked question", "question content");

    persistLike(PostLikeTargetType.POST, secondFree.getId(), requesterId, base.minusMinutes(1));
    persistLike(PostLikeTargetType.POST, firstFree.getId(), requesterId, base);
    persistLike(PostLikeTargetType.POST, question.getId(), requesterId, base.minusMinutes(2));

    PostEntity otherUserFree =
        persistPost(firstWriterId, PostType.FREE, null, "other user liked free content");
    persistLike(PostLikeTargetType.POST, otherUserFree.getId(), 999L, base.minusMinutes(3));
    persistLike(
        PostLikeTargetType.ANSWER, otherUserFree.getId(), requesterId, base.minusMinutes(4));
    persistLike(PostLikeTargetType.POST, firstFree.getId(), 999L, base.minusMinutes(5));

    MvcResult firstPage =
        mockMvc
            .perform(
                get("/v2/users/me/liked-posts?type=FREE&size=1").with(userPrincipal(requesterId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.hasNext").value(true))
            .andExpect(jsonPath("$.data.posts[0].postId").value(firstFree.getId()))
            .andExpect(jsonPath("$.data.posts[0].content").value("first liked free content"))
            .andExpect(jsonPath("$.data.posts[0].likeCount").value(2))
            .andExpect(jsonPath("$.data.posts[0].isLiked").value(true))
            .andExpect(jsonPath("$.data.posts[0].writer.nickname").value("first-writer"))
            .andReturn();

    String nextCursor = nextCursor(firstPage);

    mockMvc
        .perform(
            get("/v2/users/me/liked-posts?type=FREE&size=1&cursor=" + nextCursor)
                .with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.data.posts[0].postId").value(secondFree.getId()))
        .andExpect(jsonPath("$.data.posts[0].content").value("second liked free content"))
        .andExpect(jsonPath("$.data.posts[0].isLiked").value(true));

    mockMvc
        .perform(
            get("/v2/users/me/liked-posts?type=QUESTION&size=10").with(userPrincipal(requesterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.hasNext").value(false))
        .andExpect(jsonPath("$.data.posts[0].postId").value(question.getId()))
        .andExpect(jsonPath("$.data.posts[0].type").value("QUESTION"))
        .andExpect(jsonPath("$.data.posts[0].question.reward").value(100));
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

  private PostEntity persistPost(Long userId, PostType type, String title, String content) {
    PostEntity entity =
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .content(content)
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .status(PostStatus.OPEN)
            .build();
    return postJpaRepository.saveAndFlush(entity);
  }

  private PostLikeEntity persistLike(
      PostLikeTargetType targetType, Long targetId, Long userId, LocalDateTime createdAt) {
    PostLikeEntity entity =
        PostLikeEntity.builder().targetType(targetType).targetId(targetId).userId(userId).build();
    PostLikeEntity saved = postLikeJpaRepository.saveAndFlush(entity);
    jdbcTemplate.update(
        "UPDATE post_like SET created_at = ? WHERE id = ?", createdAt, saved.getId());
    return saved;
  }

  private String nextCursor(MvcResult result) throws Exception {
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.path("data").path("nextCursor").asText();
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
