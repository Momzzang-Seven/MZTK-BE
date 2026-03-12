package momzzangseven.mztkbe.modules.answer.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository.AnswerJpaRepository;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("AnswerController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  protected AnswerJpaRepository answerJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  protected PostJpaRepository postJpaRepository;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.application.port.in
          .MarkTransactionSucceededUseCase
      txMarkTransactionSucceededUseCase;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionReceiptWorker
      txTransactionReceiptWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .TransactionIssuerWorker
      txTransactionIssuerWorker;

  @MockBean
  private momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.worker
          .SignedRecoveryWorker
      txSignedRecoveryWorker;

  @Test
  @DisplayName("답변 생성/조회/수정/삭제가 실제 DB에 반영된다")
  void createGetUpdateDeleteAnswer_realFlow_reflectsInH2() throws Exception {
    PostEntity savedPost =
        postJpaRepository.save(
            PostEntity.builder()
                .userId(501L)
                .type(PostType.QUESTION)
                .title("답변 테스트 질문")
                .content("어떻게 테스트하나요?")
                .imageUrls(List.of())
                .reward(100L)
                .isSolved(false)
                .build());
    Long postId = savedPost.getId();

    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/" + postId + "/answers")
                    .with(userPrincipal(502L))
                    .contentType(APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "content",
                                "이렇게 통합 테스트합니다.",
                                "imageUrls",
                                List.of("https://example.com/answer-1.png")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();

    Long answerId = extractLong(createResult, "/data/answerId");
    AnswerEntity savedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
    assertThat(savedAnswer.getPostId()).isEqualTo(postId);
    assertThat(savedAnswer.getUserId()).isEqualTo(502L);
    assertThat(savedAnswer.getContent()).isEqualTo("이렇게 통합 테스트합니다.");
    assertThat(savedAnswer.getImageUrls()).containsExactly("https://example.com/answer-1.png");
    assertThat(savedAnswer.getIsAccepted()).isFalse();

    mockMvc
        .perform(get("/posts/" + postId + "/answers").with(userPrincipal(501L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].answerId").value(answerId))
        .andExpect(jsonPath("$.data[0].userId").value(502L))
        .andExpect(jsonPath("$.data[0].content").value("이렇게 통합 테스트합니다."))
        .andExpect(jsonPath("$.data[0].imageUrls[0]").value("https://example.com/answer-1.png"));

    mockMvc
        .perform(
            put("/posts/" + postId + "/answers/" + answerId)
                .with(userPrincipal(502L))
                .contentType(APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "content",
                            "수정된 답변입니다.",
                            "imageUrls",
                            List.of(
                                "https://example.com/answer-2.png",
                                "https://example.com/answer-3.png")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    AnswerEntity updatedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
    assertThat(updatedAnswer.getContent()).isEqualTo("수정된 답변입니다.");
    assertThat(updatedAnswer.getImageUrls())
        .containsExactly("https://example.com/answer-2.png", "https://example.com/answer-3.png");

    mockMvc
        .perform(delete("/posts/" + postId + "/answers/" + answerId).with(userPrincipal(502L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    assertThat(answerJpaRepository.findById(answerId)).isEmpty();
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
