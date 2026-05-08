package momzzangseven.mztkbe.modules.comment.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import momzzangseven.mztkbe.modules.comment.domain.model.CommentTargetType;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.entity.CommentEntity;
import momzzangseven.mztkbe.modules.comment.infrastructure.persistence.repository.CommentJpaRepository;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
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

  @org.springframework.beans.factory.annotation.Autowired
  protected UserJpaRepository userJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected AnswerJpaRepository answerJpaRepository;

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
            .andExpect(jsonPath("$.message").value("Comment created successfully"))
            .andExpect(jsonPath("$.data.content").value("first comment"))
            .andExpect(jsonPath("$.data.writer").doesNotExist())
            .andExpect(jsonPath("$.data.replyCount").doesNotExist())
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
  @DisplayName("create answer comment persists root post id and answer id")
  void createAnswerComment_persistsRootPostIdAndAnswerId() throws Exception {
    PostEntity question =
        postJpaRepository.saveAndFlush(
            PostEntity.builder()
                .userId(401L)
                .type(PostType.QUESTION)
                .title("answer comment question")
                .content("question body")
                .reward(100L)
                .status(PostStatus.OPEN)
                .build());
    AnswerEntity answer =
        answerJpaRepository.saveAndFlush(
            AnswerEntity.builder()
                .postId(question.getId())
                .userId(402L)
                .content("answer body")
                .isAccepted(false)
                .build());

    MvcResult createCommentResult =
        mockMvc
            .perform(
                post("/v2/answers/" + answer.getId() + "/comments")
                    .with(userPrincipal(403L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "answer comment"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.data.content").value("answer comment"))
            .andReturn();

    Long commentId = extractLong(createCommentResult, "/data/commentId");
    CommentEntity saved = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(saved.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(saved.getPostId()).isEqualTo(question.getId());
    assertThat(saved.getAnswerId()).isEqualTo(answer.getId());
    assertThat(saved.getWriterId()).isEqualTo(403L);
  }

  @Test
  @DisplayName("v2 post comment create persists POST target row")
  void createPostCommentV2_persistsPostTargetRow() throws Exception {
    PostEntity savedPost =
        postJpaRepository.saveAndFlush(
            PostEntity.builder()
                .userId(401L)
                .type(PostType.FREE)
                .title("v2 post comment")
                .content("post body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());

    MvcResult createCommentResult =
        mockMvc
            .perform(
                post("/v2/posts/" + savedPost.getId() + "/comments")
                    .with(userPrincipal(403L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "v2 post comment"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andExpect(jsonPath("$.data.content").value("v2 post comment"))
            .andReturn();

    Long commentId = extractLong(createCommentResult, "/data/commentId");
    CommentEntity saved = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(saved.getTargetType()).isEqualTo(CommentTargetType.POST);
    assertThat(saved.getPostId()).isEqualTo(savedPost.getId());
    assertThat(saved.getAnswerId()).isNull();
    assertThat(saved.getWriterId()).isEqualTo(403L);
    assertThat(saved.isDeleted()).isFalse();
  }

  @Test
  @DisplayName("answer reply parent from different answer returns target mismatch code")
  void createAnswerComment_parentAnswerMismatch_returnsTargetMismatch() throws Exception {
    PostEntity question = saveQuestionPost(401L, "answer parent mismatch question");
    AnswerEntity targetAnswer = saveAnswer(question.getId(), 402L, false);
    AnswerEntity otherAnswer = saveAnswer(question.getId(), 404L, false);
    Long otherAnswerCommentId =
        createAnswerComment(otherAnswer.getId(), 403L, "other answer parent");

    mockMvc
        .perform(
            post("/v2/answers/" + targetAnswer.getId() + "/comments")
                .with(userPrincipal(405L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "wrong parent", "parentId", otherAnswerCommentId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("COMMENT_009"))
        .andExpect(jsonPath("$.message").value("Comment does not belong to the specified target"));
  }

  @Test
  @DisplayName("common comment API updates and deletes answer comments with comment policy")
  void commonCommentApi_updateDeleteAnswerComment_realFlow() throws Exception {
    PostEntity question = saveQuestionPost(401L, "common answer comment policy");
    AnswerEntity answer = saveAnswer(question.getId(), 402L, false);
    Long commentId = createAnswerComment(answer.getId(), 403L, "before common update");

    mockMvc
        .perform(
            put("/comments/" + commentId)
                .with(userPrincipal(403L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "after common update"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content").value("after common update"));

    CommentEntity updated = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(updated.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(updated.getPostId()).isEqualTo(question.getId());
    assertThat(updated.getAnswerId()).isEqualTo(answer.getId());
    assertThat(updated.getContent()).isEqualTo("after common update");

    mockMvc
        .perform(delete("/comments/" + commentId).with(userPrincipal(403L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    CommentEntity deleted = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(deleted.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("v2 answer comment API updates and deletes answer comments with same policy")
  void answerCommentV2Api_updateDeleteAnswerComment_realFlow() throws Exception {
    PostEntity question = saveQuestionPost(401L, "v2 answer comment policy");
    AnswerEntity answer = saveAnswer(question.getId(), 402L, true);
    Long commentId = createAnswerComment(answer.getId(), 403L, "before v2 update");

    mockMvc
        .perform(
            put("/v2/answers/" + answer.getId() + "/comments/" + commentId)
                .with(userPrincipal(403L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "after v2 update"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content").value("after v2 update"));

    CommentEntity updated = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(updated.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(updated.getPostId()).isEqualTo(question.getId());
    assertThat(updated.getAnswerId()).isEqualTo(answer.getId());
    assertThat(updated.getContent()).isEqualTo("after v2 update");

    mockMvc
        .perform(
            delete("/v2/answers/" + answer.getId() + "/comments/" + commentId)
                .with(userPrincipal(403L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    CommentEntity deleted = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(deleted.isDeleted()).isTrue();
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

  @Test
  @DisplayName("root comment query includes writer details, replyCount, and Page last metadata")
  void getRootComments_includesWriterReplyCountAndPageLast() throws Exception {
    UserEntity writer = saveUser("root-writer", "root-profile.webp");
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(writer.getId())
                .type(PostType.FREE)
                .title("comment response contract")
                .content("body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());

    Long firstRootId = createComment(savedPost.getId(), writer.getId(), "first root", null);
    createComment(savedPost.getId(), writer.getId(), "second root", null);
    createComment(savedPost.getId(), writer.getId(), "reply-1", firstRootId);
    createComment(savedPost.getId(), writer.getId(), "reply-2", firstRootId);

    mockMvc
        .perform(
            get("/posts/" + savedPost.getId() + "/comments?page=0&size=1")
                .with(userPrincipal(writer.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(firstRootId))
        .andExpect(jsonPath("$.data.content[0].writer.userId").value(writer.getId()))
        .andExpect(jsonPath("$.data.content[0].writer.nickname").value("root-writer"))
        .andExpect(jsonPath("$.data.content[0].writer.profileImage").value("root-profile.webp"))
        .andExpect(jsonPath("$.data.content[0].replyCount").value(2))
        .andExpect(jsonPath("$.data.last").value(false));
  }

  @Test
  @DisplayName("reply query includes writer details")
  void getReplies_includesWriterDetails() throws Exception {
    UserEntity rootWriter = saveUser("root-owner", "root.webp");
    UserEntity replyWriter = saveUser("reply-owner", "reply.webp");
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(rootWriter.getId())
                .type(PostType.FREE)
                .title("reply response contract")
                .content("body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());

    Long rootId = createComment(savedPost.getId(), rootWriter.getId(), "root", null);
    Long replyId = createComment(savedPost.getId(), replyWriter.getId(), "reply", rootId);
    CommentEntity reply = commentJpaRepository.findById(replyId).orElseThrow();
    commentJpaRepository.save(
        CommentEntity.builder()
            .postId(savedPost.getId())
            .writerId(replyWriter.getId())
            .content("legacy nested reply")
            .parent(reply)
            .isDeleted(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build());

    mockMvc
        .perform(get("/comments/" + rootId + "/replies").with(userPrincipal(rootWriter.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.content[0].commentId").value(replyId))
        .andExpect(jsonPath("$.data.content[0].writer.userId").value(replyWriter.getId()))
        .andExpect(jsonPath("$.data.content[0].writer.nickname").value("reply-owner"))
        .andExpect(jsonPath("$.data.content[0].writer.profileImage").value("reply.webp"))
        .andExpect(jsonPath("$.data.content[0].replyCount").value(0))
        .andExpect(jsonPath("$.data.last").value(true));
  }

  @Test
  @DisplayName("creating a reply to a reply returns 400")
  void createComment_nestedReply_returns400() throws Exception {
    UserEntity writer = saveUser("nested-writer", "nested.webp");
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(writer.getId())
                .type(PostType.FREE)
                .title("nested reply policy")
                .content("body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());
    Long rootId = createComment(savedPost.getId(), writer.getId(), "root", null);
    Long replyId = createComment(savedPost.getId(), writer.getId(), "reply", rootId);

    mockMvc
        .perform(
            post("/posts/" + savedPost.getId() + "/comments")
                .with(userPrincipal(writer.getId()))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "nested reply", "parentId", replyId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("COMMENT_008"));
  }

  @Test
  @DisplayName("fetching replies of a reply returns 400")
  void getReplies_nestedReplyParent_returns400() throws Exception {
    UserEntity writer = saveUser("nested-fetch-writer", "nested-fetch.webp");
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(writer.getId())
                .type(PostType.FREE)
                .title("nested reply fetch policy")
                .content("body")
                .reward(0L)
                .status(PostStatus.OPEN)
                .build());
    Long rootId = createComment(savedPost.getId(), writer.getId(), "root", null);
    Long replyId = createComment(savedPost.getId(), writer.getId(), "reply", rootId);

    mockMvc
        .perform(get("/comments/" + replyId + "/replies").with(userPrincipal(writer.getId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("COMMENT_008"));
  }

  private Long extractLong(MvcResult result, String pointer) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at(pointer).asLong();
  }

  private Long createComment(Long targetPostId, Long userId, String content, Long parentId)
      throws Exception {
    Map<String, Object> body =
        parentId == null
            ? Map.of("content", content)
            : Map.of("content", content, "parentId", parentId);
    MvcResult result =
        mockMvc
            .perform(
                post("/posts/" + targetPostId + "/comments")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(json(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();
    return extractLong(result, "/data/commentId");
  }

  private Long createAnswerComment(Long answerId, Long userId, String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/v2/answers/" + answerId + "/comments")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", content))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andReturn();
    return extractLong(result, "/data/commentId");
  }

  private PostEntity saveQuestionPost(Long writerId, String title) {
    return postJpaRepository.saveAndFlush(
        PostEntity.builder()
            .userId(writerId)
            .type(PostType.QUESTION)
            .title(title)
            .content("question body")
            .reward(100L)
            .status(PostStatus.OPEN)
            .build());
  }

  private AnswerEntity saveAnswer(Long postId, Long writerId, boolean accepted) {
    return answerJpaRepository.saveAndFlush(
        AnswerEntity.builder()
            .postId(postId)
            .userId(writerId)
            .content("answer body")
            .isAccepted(accepted)
            .build());
  }

  private UserEntity saveUser(String nickname, String profileImageUrl) {
    Instant now = Instant.now();
    return userJpaRepository.save(
        UserEntity.builder()
            .email(UUID.randomUUID() + "@example.com")
            .nickname(nickname)
            .profileImageUrl(profileImageUrl)
            .role(UserRole.USER)
            .createdAt(now)
            .updatedAt(now)
            .build());
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
