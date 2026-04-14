package momzzangseven.mztkbe.modules.post.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.RecoverQuestionPostEscrowCommand;
import momzzangseven.mztkbe.modules.post.application.port.in.RecoverQuestionPostEscrowUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrecheckQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerAcceptCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareQuestionUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * QuestionLifecycleExecutionAdapter 가 실제로 와이어링된 상태에서 POST /posts/question 의 QnA escrow 흐름을 검증합니다.
 *
 * <p>web3.reward-token.enabled=true + web3.eip7702.enabled=true 로 활성화하고,
 * QuestionEscrowExecutionUseCase 를 MockitoBean 으로 대체해 실제 블록체인 호출을 차단합니다.
 */
@TestPropertySource(properties = {"web3.reward-token.enabled=true", "web3.eip7702.enabled=true"})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("PostController + QnA Escrow 어댑터 통합 테스트 (reward-token=true)")
class PostControllerQnaEscrowIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private AnswerJpaRepository answerJpaRepository;

  @MockitoBean private QuestionEscrowExecutionUseCase questionEscrowExecutionUseCase;
  @MockitoBean private RecoverQuestionPostEscrowUseCase recoverQuestionPostEscrowUseCase;
  @MockitoBean private GrantXpUseCase grantXpUseCase;
  @MockitoBean private UpdatePostImagesPort updatePostImagesPort;
  @MockitoBean private LoadPostImagesPort loadPostImagesPort;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      markTransactionSucceededUseCase;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      transactionReceiptWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      transactionIssuerWorker;

  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      signedRecoveryWorker;

  @BeforeEach
  void setUp() {
    BDDMockito.given(grantXpUseCase.execute(any()))
        .willReturn(GrantXpResult.granted(20, 10, 1, LocalDate.of(2026, 3, 12)));
    BDDMockito.given(loadPostImagesPort.loadImages(any(), any()))
        .willReturn(PostImageResult.empty());
    BDDMockito.given(questionEscrowExecutionUseCase.prepareQuestionCreate(any()))
        .willReturn(questionIntent("intent-1", "QNA_QUESTION_CREATE"));
    BDDMockito.given(questionEscrowExecutionUseCase.prepareQuestionUpdate(any()))
        .willReturn(questionIntent("intent-2", "QNA_QUESTION_UPDATE"));
    BDDMockito.given(questionEscrowExecutionUseCase.prepareQuestionDelete(any()))
        .willReturn(questionIntent("intent-3", "QNA_QUESTION_DELETE"));
    BDDMockito.given(questionEscrowExecutionUseCase.prepareAnswerAccept(any()))
        .willReturn(questionIntent("intent-4", "QNA_ANSWER_ACCEPT"));
    BDDMockito.given(
            recoverQuestionPostEscrowUseCase.recoverQuestionCreate(
                any(RecoverQuestionPostEscrowCommand.class)))
        .willReturn(
            new momzzangseven.mztkbe.modules.post.application.dto.PostMutationResult(101L, null));
  }

  @Test
  @DisplayName(
      "POST /posts/question — precheckQuestionCreate 와 prepareQuestionCreate 가 실제 어댑터 경로로 호출됨")
  void createQuestionPost_invokesEscrowAdapterPath() throws Exception {
    mockMvc
        .perform(
            post("/posts/question")
                .with(userPrincipal(101L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "title",
                            "escrow 테스트 질문",
                            "content",
                            "escrow 질문 본문",
                            "reward",
                            50,
                            "tags",
                            List.of()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(questionEscrowExecutionUseCase)
        .precheckQuestionCreate(any(PrecheckQuestionCreateCommand.class));
    verify(questionEscrowExecutionUseCase)
        .prepareQuestionCreate(any(PrepareQuestionCreateCommand.class));
  }

  @Test
  @DisplayName("PATCH /posts/{id} — prepareQuestionUpdate 가 실제 어댑터 경로로 호출됨")
  void updateQuestionPost_invokesEscrowAdapterPath() throws Exception {
    Long postId = createQuestionPost(202L);

    mockMvc
        .perform(
            patch("/posts/" + postId)
                .with(userPrincipal(202L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "수정 제목", "content", "수정 본문"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionUpdate(any(PrepareQuestionUpdateCommand.class));
  }

  @Test
  @DisplayName("DELETE /posts/{id} — prepareQuestionDelete 가 실제 어댑터 경로로 호출됨")
  void deleteQuestionPost_invokesEscrowAdapterPath() throws Exception {
    Long postId = createQuestionPost(303L);

    mockMvc
        .perform(delete("/posts/" + postId).with(userPrincipal(303L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(questionEscrowExecutionUseCase)
        .prepareQuestionDelete(any(PrepareQuestionDeleteCommand.class));
  }

  @Test
  @DisplayName("POST /posts/{id}/web3/recover-create — zombie question 복구 엔드포인트가 노출됨")
  void recoverQuestionPost_endpointIsExposed() throws Exception {
    Long postId = createQuestionPost(303L);

    mockMvc
        .perform(post("/posts/" + postId + "/web3/recover-create").with(userPrincipal(303L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(101));

    verify(recoverQuestionPostEscrowUseCase)
        .recoverQuestionCreate(any(RecoverQuestionPostEscrowCommand.class));
  }

  @Test
  @DisplayName(
      "POST /questions/{postId}/answers/{answerId}/accept — prepareAnswerAccept 가 실제 어댑터 경로로 호출됨")
  void acceptAnswer_invokesEscrowAdapterPath() throws Exception {
    PostEntity post =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(404L)
                .type(PostType.QUESTION)
                .title("답변 채택 테스트")
                .content("채택 질문 본문")
                .reward(50L)
                .status(PostStatus.OPEN)
                .build());
    momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity answer =
        answerJpaRepository.save(
            momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity
                .builder()
                .postId(post.getId())
                .userId(405L)
                .content("채택될 답변")
                .isAccepted(false)
                .build());

    mockMvc
        .perform(
            post("/posts/" + post.getId() + "/answers/" + answer.getId() + "/accept")
                .with(userPrincipal(404L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.acceptedAnswerId").value(answer.getId()))
        .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

    PostEntity updatedPost = postJpaRepository.findById(post.getId()).orElseThrow();
    assertThat(updatedPost.getAcceptedAnswerId()).isEqualTo(answer.getId());
    assertThat(updatedPost.getStatus().name()).isEqualTo("PENDING_ACCEPT");
    assertThat(updatedPost.getStatus()).isEqualTo(PostStatus.PENDING_ACCEPT);

    assertThat(answerJpaRepository.findById(answer.getId()).orElseThrow().getIsAccepted())
        .isFalse();

    verify(questionEscrowExecutionUseCase)
        .prepareAnswerAccept(any(PrepareAnswerAcceptCommand.class));
  }

  @Test
  @DisplayName("GET /posts/{id} shows question.isSolved=true while status is pending accept")
  void getQuestionDetail_marksPendingAcceptAsSolved() throws Exception {
    PostEntity post =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(504L)
                .type(PostType.QUESTION)
                .title("답변 채택 조회 테스트")
                .content("채택 질문 본문")
                .reward(50L)
                .status(PostStatus.OPEN)
                .build());
    momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity answer =
        answerJpaRepository.save(
            momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity
                .builder()
                .postId(post.getId())
                .userId(505L)
                .content("채택될 답변")
                .isAccepted(false)
                .build());

    mockMvc
        .perform(
            post("/posts/" + post.getId() + "/answers/" + answer.getId() + "/accept")
                .with(userPrincipal(504L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("PENDING_ACCEPT"));

    mockMvc
        .perform(get("/posts/" + post.getId()).with(userPrincipal(504L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.type").value("QUESTION"))
        .andExpect(jsonPath("$.data.question.isSolved").value(true));
  }

  // ── 헬퍼 ──────────────────────────────────────────────────────────────────

  private Long createQuestionPost(Long userId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/posts/question")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "title", "헬퍼 질문", "content", "헬퍼 본문", "reward", 10, "tags",
                                List.of()))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.path("data").path("postId").asLong();
  }

  private RequestPostProcessor userPrincipal(Long userId) {
    var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    var token = new UsernamePasswordAuthenticationToken(userId, null, authorities);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(token);
    return org.springframework.security.test.web.servlet.request
        .SecurityMockMvcRequestPostProcessors.securityContext(context);
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private QnaExecutionIntentResult questionIntent(String intentId, String actionType) {
    return new QnaExecutionIntentResult(
        new QnaExecutionIntentResult.Resource("QUESTION", "1", "PENDING_EXECUTION"),
        actionType,
        new QnaExecutionIntentResult.ExecutionIntent(
            intentId, "AWAITING_SIGNATURE", LocalDateTime.of(2026, 4, 14, 10, 0)),
        new QnaExecutionIntentResult.Execution("EIP7702", 2),
        null,
        false);
  }
}
