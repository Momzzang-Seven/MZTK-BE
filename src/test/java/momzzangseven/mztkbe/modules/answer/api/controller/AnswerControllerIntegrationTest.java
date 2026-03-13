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
import java.util.LinkedHashMap;
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

@DisplayName("AnswerControllerIntegrationTest")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnswerControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired private MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @org.springframework.beans.factory.annotation.Autowired
  private AnswerJpaRepository answerJpaRepository;

  @org.springframework.beans.factory.annotation.Autowired
  private PostJpaRepository postJpaRepository;

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
  void createGetUpdateAndDeleteAnswer_realFlow_works() throws Exception {
    PostEntity savedPost = saveQuestionPost(501L, false);
    Long postId = savedPost.getId();

    MvcResult createResult =
        mockMvc
            .perform(
                post("/questions/" + postId + "/answers")
                    .with(userPrincipal(502L))
                    .contentType(APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "content",
                                "integration answer",
                                "imageUrls",
                                List.of("https://example.com/answer-1.png")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();

    Long answerId = extractLong(createResult, "/data/answerId");
    AnswerEntity savedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
    assertThat(savedAnswer.getPostId()).isEqualTo(postId);
    assertThat(savedAnswer.getUserId()).isEqualTo(502L);
    assertThat(savedAnswer.getContent()).isEqualTo("integration answer");
    assertThat(savedAnswer.getImageUrls()).containsExactly("https://example.com/answer-1.png");
    assertThat(savedAnswer.getIsAccepted()).isFalse();

    mockMvc
        .perform(get("/questions/" + postId + "/answers").with(userPrincipal(501L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data[0].answerId").value(answerId))
        .andExpect(jsonPath("$.data[0].userId").value(502L))
        .andExpect(jsonPath("$.data[0].content").value("integration answer"))
        .andExpect(jsonPath("$.data[0].imageUrls[0]").value("https://example.com/answer-1.png"));

    mockMvc
        .perform(
            put("/questions/" + postId + "/answers/" + answerId)
                .with(userPrincipal(502L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "updated answer"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    AnswerEntity updatedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
    assertThat(updatedAnswer.getContent()).isEqualTo("updated answer");
    assertThat(updatedAnswer.getImageUrls()).containsExactly("https://example.com/answer-1.png");

    mockMvc
        .perform(
            put("/questions/" + postId + "/answers/" + answerId)
                .with(userPrincipal(502L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("imageUrls", List.of("https://example.com/answer-2.png")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    AnswerEntity imageOnlyUpdatedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
    assertThat(imageOnlyUpdatedAnswer.getContent()).isEqualTo("updated answer");
    assertThat(imageOnlyUpdatedAnswer.getImageUrls())
        .containsExactly("https://example.com/answer-2.png");

    mockMvc
        .perform(delete("/questions/" + postId + "/answers/" + answerId).with(userPrincipal(502L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    assertThat(answerJpaRepository.findById(answerId)).isEmpty();
  }

  @Test
  void deletingQuestionPost_removesAnswersThroughEventHandler() throws Exception {
    PostEntity savedPost = saveQuestionPost(501L, false);
    Long postId = savedPost.getId();
    Long firstAnswerId = createAnswer(postId, 502L, "first", List.of());
    Long secondAnswerId =
        createAnswer(postId, 503L, "second", List.of("https://example.com/2.png"));

    mockMvc
        .perform(delete("/posts/" + postId).with(userPrincipal(501L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    assertThat(postJpaRepository.findById(postId)).isEmpty();
    assertThat(answerJpaRepository.findById(firstAnswerId)).isEmpty();
    assertThat(answerJpaRepository.findById(secondAnswerId)).isEmpty();
  }

  private PostEntity saveQuestionPost(Long userId, boolean isSolved) {
    return postJpaRepository.save(
        PostEntity.builder()
            .userId(userId)
            .type(PostType.QUESTION)
            .title("question title")
            .content("question content")
            .imageUrls(List.of())
            .reward(100L)
            .isSolved(isSolved)
            .build());
  }

  private Long createAnswer(Long postId, Long userId, String content, List<String> imageUrls)
      throws Exception {
    LinkedHashMap<String, Object> request = new LinkedHashMap<>();
    request.put("content", content);
    request.put("imageUrls", imageUrls);

    MvcResult createResult =
        mockMvc
            .perform(
                post("/questions/" + postId + "/answers")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(json(request)))
            .andExpect(status().isCreated())
            .andReturn();

    return extractLong(createResult, "/data/answerId");
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
    List<SimpleGrantedAuthority> grantedAuthorities =
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
