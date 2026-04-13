package momzzangseven.mztkbe.modules.answer.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerLikePort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerCreateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerDeleteCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.PrepareAnswerUpdateCommand;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.qna.application.port.in.AnswerEscrowExecutionUseCase;
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
 * AnswerLifecycleExecutionAdapter 가 실제로 와이어링된 상태에서 답변 CRUD 의 QnA escrow 흐름을 검증합니다.
 *
 * <p>web3.reward-token.enabled=true + web3.eip7702.enabled=true 로 활성화하고,
 * AnswerEscrowExecutionUseCase 를 MockitoBean 으로 대체해 실제 블록체인 호출을 차단합니다.
 */
@TestPropertySource(properties = {"web3.reward-token.enabled=true", "web3.eip7702.enabled=true"})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("AnswerController + QnA Escrow 어댑터 통합 테스트 (reward-token=true)")
class AnswerControllerQnaEscrowIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PostJpaRepository postJpaRepository;
  @Autowired private AnswerJpaRepository answerJpaRepository;

  @MockitoBean private AnswerEscrowExecutionUseCase answerEscrowExecutionUseCase;
  @MockitoBean private UpdateAnswerImagesPort updateAnswerImagesPort;
  @MockitoBean private LoadAnswerImagesPort loadAnswerImagesPort;
  @MockitoBean private LoadAnswerLikePort loadAnswerLikePort;

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

  // QuestionEscrowExecutionUseCase 도 필요: AnswerLifecycleExecutionAdapter 가 활성화되면
  // QuestionEscrowExecutionUseCase 빈도 필요(QuestionEscrowExecutionService 가 의존)
  @MockitoBean
  private momzzangseven.mztkbe.modules.web3.qna.application.port.in.QuestionEscrowExecutionUseCase
      questionEscrowExecutionUseCase;

  private Long questionPostId;
  private Long questionWriterUserId;

  @BeforeEach
  void setUp() {
    BDDMockito.given(loadAnswerImagesPort.loadImagesByAnswerIds(any())).willReturn(Map.of());
    BDDMockito.given(loadAnswerLikePort.countLikeByAnswerIds(any())).willReturn(Map.of());
    BDDMockito.given(loadAnswerLikePort.loadLikedAnswerIds(any(), any()))
        .willReturn(java.util.Set.of());
    BDDMockito.given(answerEscrowExecutionUseCase.prepareAnswerCreate(any()))
        .willReturn(new QnaExecutionIntentResult("intent-create", "EIP7702", 2, null, false));
    BDDMockito.given(answerEscrowExecutionUseCase.prepareAnswerUpdate(any()))
        .willReturn(new QnaExecutionIntentResult("intent-update", "EIP7702", 2, null, false));
    BDDMockito.given(answerEscrowExecutionUseCase.prepareAnswerDelete(any()))
        .willReturn(new QnaExecutionIntentResult("intent-delete", "EIP7702", 2, null, false));

    questionWriterUserId = 501L;
    PostEntity post =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(questionWriterUserId)
                .type(PostType.QUESTION)
                .title("escrow 통합 테스트 질문")
                .content("질문 본문")
                .reward(50L)
                .status(PostStatus.OPEN)
                .build());
    questionPostId = post.getId();
  }

  @Test
  @DisplayName("POST /questions/{postId}/answers — prepareAnswerCreate 가 실제 어댑터 경로로 호출됨")
  void createAnswer_invokesEscrowAdapterPath() throws Exception {
    mockMvc
        .perform(
            post("/questions/" + questionPostId + "/answers")
                .with(userPrincipal(502L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "escrow 테스트 답변", "imageIds", List.of()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(answerEscrowExecutionUseCase).prepareAnswerCreate(any(PrepareAnswerCreateCommand.class));
  }

  @Test
  @DisplayName("PUT /questions/{postId}/answers/{answerId} — prepareAnswerUpdate 가 실제 어댑터 경로로 호출됨")
  void updateAnswer_invokesEscrowAdapterPath() throws Exception {
    Long answerId = createAnswer(502L, "원본 답변");

    mockMvc
        .perform(
            put("/questions/" + questionPostId + "/answers/" + answerId)
                .with(userPrincipal(502L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "수정된 답변"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(answerEscrowExecutionUseCase).prepareAnswerUpdate(any(PrepareAnswerUpdateCommand.class));
  }

  @Test
  @DisplayName(
      "DELETE /questions/{postId}/answers/{answerId} — prepareAnswerDelete 가 실제 어댑터 경로로 호출됨")
  void deleteAnswer_invokesEscrowAdapterPath() throws Exception {
    Long answerId = createAnswer(503L, "삭제될 답변");

    mockMvc
        .perform(
            delete("/questions/" + questionPostId + "/answers/" + answerId)
                .with(userPrincipal(503L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    verify(answerEscrowExecutionUseCase).prepareAnswerDelete(any(PrepareAnswerDeleteCommand.class));
  }

  // ── 헬퍼 ──────────────────────────────────────────────────────────────────

  private Long createAnswer(Long userId, String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/questions/" + questionPostId + "/answers")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", content, "imageIds", List.of()))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.path("data").path("answerId").asLong();
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
}
