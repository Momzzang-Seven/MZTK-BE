package momzzangseven.mztkbe.modules.comment.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
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
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.SignedRecoveryWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionIssuerWorker;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker.TransactionReceiptWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@DisplayName("Answer comment lifecycle integration test")
@SpringBootTest
@AutoConfigureMockMvc
class AnswerCommentLifecycleIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Autowired private PostJpaRepository postJpaRepository;

  @Autowired private AnswerJpaRepository answerJpaRepository;

  @Autowired private CommentJpaRepository commentJpaRepository;

  @MockitoBean private MarkTransactionSucceededUseCase txMarkTransactionSucceededUseCase;

  @MockitoBean private TransactionReceiptWorker txTransactionReceiptWorker;

  @MockitoBean private TransactionIssuerWorker txTransactionIssuerWorker;

  @MockitoBean private SignedRecoveryWorker txSignedRecoveryWorker;

  @MockitoBean private UpdateAnswerImagesPort updateAnswerImagesPort;

  @MockitoBean private LoadAnswerImagesPort loadAnswerImagesPort;

  @MockitoBean private LoadAnswerLikePort loadAnswerLikePort;

  @MockitoBean private AnswerLifecycleExecutionPort answerLifecycleExecutionPort;

  @MockitoBean private GrantXpUseCase grantXpUseCase;

  @BeforeEach
  void setUp() {
    given(loadAnswerImagesPort.loadImagesByAnswerIds(anyCollection())).willReturn(Map.of());
    given(loadAnswerLikePort.countLikeByAnswerIds(anyCollection())).willReturn(Map.of());
    given(
            loadAnswerLikePort.loadLikedAnswerIds(
                anyCollection(), org.mockito.ArgumentMatchers.nullable(Long.class)))
        .willReturn(java.util.Set.of());
    given(
            answerLifecycleExecutionPort.prepareAnswerCreate(
                anyLong(),
                anyLong(),
                anyLong(),
                anyLong(),
                anyString(),
                anyLong(),
                anyString(),
                org.mockito.ArgumentMatchers.anyInt()))
        .willReturn(Optional.empty());
    given(
            answerLifecycleExecutionPort.prepareAnswerDelete(
                anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyLong(), anyInt()))
        .willReturn(Optional.empty());
    given(grantXpUseCase.execute(any()))
        .willReturn(GrantXpResult.granted(1, -1, 1, LocalDate.of(2026, 5, 7)));
  }

  @AfterEach
  void cleanUp() {
    commentJpaRepository.deleteAllInBatch();
    answerJpaRepository.deleteAllInBatch();
    postJpaRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("answer comment count is visible in answer list and cleaned up after answer delete")
  void answerCommentLifecycle_createCountAndCleanupAfterAnswerDelete() throws Exception {
    PostEntity question =
        postJpaRepository.saveAndFlush(
            PostEntity.builder()
                .userId(901L)
                .type(PostType.QUESTION)
                .title("answer comment lifecycle question")
                .content("question body")
                .reward(100L)
                .status(PostStatus.OPEN)
                .build());

    Long answerId = createAnswer(question.getId(), 902L, "lifecycle answer");
    Long commentId = createAnswerComment(answerId, 903L, "lifecycle answer comment");

    CommentEntity createdComment = commentJpaRepository.findById(commentId).orElseThrow();
    assertThat(createdComment.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(createdComment.getPostId()).isEqualTo(question.getId());
    assertThat(createdComment.getAnswerId()).isEqualTo(answerId);
    assertThat(createdComment.isDeleted()).isFalse();

    mockMvc
        .perform(get("/questions/" + question.getId() + "/answers").with(userPrincipal(901L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].answerId").value(answerId))
        .andExpect(jsonPath("$.data[0].commentCount").value(1));

    mockMvc
        .perform(
            delete("/questions/" + question.getId() + "/answers/" + answerId)
                .with(userPrincipal(902L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    assertThat(answerJpaRepository.findById(answerId)).isEmpty();

    CommentEntity cleanedComment = waitUntilCommentSoftDeleted(commentId);
    assertThat(cleanedComment.getTargetType()).isEqualTo(CommentTargetType.ANSWER);
    assertThat(cleanedComment.getPostId()).isEqualTo(question.getId());
    assertThat(cleanedComment.getAnswerId()).isEqualTo(answerId);
    assertThat(
            commentJpaRepository.countByTargetTypeAndAnswerId(CommentTargetType.ANSWER, answerId))
        .isZero();
  }

  private Long createAnswer(Long postId, Long userId, String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/questions/" + postId + "/answers")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", content, "imageIds", List.of()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();
    return extractLong(result, "/data/answerId");
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
            .andReturn();
    return extractLong(result, "/data/commentId");
  }

  private CommentEntity waitUntilCommentSoftDeleted(Long commentId) throws InterruptedException {
    CommentEntity comment = commentJpaRepository.findById(commentId).orElseThrow();
    for (int attempt = 0; attempt < 20 && !comment.isDeleted(); attempt++) {
      Thread.sleep(50L);
      comment = commentJpaRepository.findById(commentId).orElseThrow();
    }
    assertThat(comment.isDeleted()).isTrue();
    return comment;
  }

  private Long extractLong(MvcResult result, String pointer) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at(pointer).asLong();
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    List<SimpleGrantedAuthority> authorities =
        Arrays.stream(new String[] {"ROLE_USER"}).map(SimpleGrantedAuthority::new).toList();
    UsernamePasswordAuthenticationToken token =
        new UsernamePasswordAuthenticationToken(userId, null, authorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
    return objectMapper.writeValueAsString(value);
  }
}
