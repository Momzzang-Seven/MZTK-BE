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
import org.junit.jupiter.api.Nested;
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

  @Nested
  @DisplayName("Success cases")
  class SuccessCases {

    @Test
    @DisplayName("CRUD flow persists and updates answers through HTTP")
    void createGetUpdateAndDeleteAnswer_realFlow_works() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
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
          .andExpect(status().isOk());

      mockMvc
          .perform(
              put("/questions/" + postId + "/answers/" + answerId)
                  .with(userPrincipal(502L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("imageUrls", List.of("https://example.com/answer-2.png")))))
          .andExpect(status().isOk());

      AnswerEntity updatedAnswer = answerJpaRepository.findById(answerId).orElseThrow();
      assertThat(updatedAnswer.getContent()).isEqualTo("updated answer");
      assertThat(updatedAnswer.getImageUrls()).containsExactly("https://example.com/answer-2.png");

      mockMvc
          .perform(
              delete("/questions/" + postId + "/answers/" + answerId).with(userPrincipal(502L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("SUCCESS"));

      assertThat(answerJpaRepository.findById(answerId)).isEmpty();
    }

    @Test
    @DisplayName("GET answers returns accepted answers first")
    void getAnswers_returnsAcceptedAnswerFirst() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
      Long postId = savedPost.getId();

      answerJpaRepository.save(buildAnswerEntity(postId, 502L, "regular", false, List.of()));
      answerJpaRepository.save(buildAnswerEntity(postId, 503L, "accepted", true, List.of()));

      mockMvc
          .perform(get("/questions/" + postId + "/answers").with(userPrincipal(501L)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].content").value("accepted"))
          .andExpect(jsonPath("$.data[0].isAccepted").value(true));
    }

    @Test
    @DisplayName("Deleting a question post removes orphan answers through the event handler")
    void deletingQuestionPost_removesAnswersThroughEventHandler() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
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
  }

  @Nested
  @DisplayName("Failure cases")
  class FailureCases {

    @Test
    @DisplayName("POST answer returns 401 when unauthenticated")
    void createAnswer_returns401_whenUnauthenticated() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);

      mockMvc
          .perform(
              post("/questions/" + savedPost.getId() + "/answers")
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "answer content"))))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST answer returns 400 when the request body has blank content")
    void createAnswer_returns400_whenBlankContent() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);

      mockMvc
          .perform(
              post("/questions/" + savedPost.getId() + "/answers")
                  .with(userPrincipal(502L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", " "))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("POST answer returns 400 when the question is already solved")
    void createAnswer_returns400_whenPostIsSolved() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, true);

      mockMvc
          .perform(
              post("/questions/" + savedPost.getId() + "/answers")
                  .with(userPrincipal(502L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "answer content"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("POST answer returns 400 when the requester answers his or her own post")
    void createAnswer_returns400_whenAnswerOwnPost() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);

      mockMvc
          .perform(
              post("/questions/" + savedPost.getId() + "/answers")
                  .with(userPrincipal(501L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "answer content"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("POST answer returns 400 when the target post is not a question")
    void createAnswer_returns400_whenPostIsNotQuestion() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.FREE, false);

      mockMvc
          .perform(
              post("/questions/" + savedPost.getId() + "/answers")
                  .with(userPrincipal(502L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "answer content"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("GET answers returns 404 when the post does not exist")
    void getAnswers_returns404_whenPostNotFound() throws Exception {
      mockMvc
          .perform(get("/questions/999999/answers").with(userPrincipal(501L)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value("FAIL"))
          .andExpect(jsonPath("$.code").value("POST_001"));
    }

    @Test
    @DisplayName("PUT answer returns 403 when the requester is not the owner")
    void updateAnswer_returns403_whenNotOwner() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
      Long answerId = createAnswer(savedPost.getId(), 502L, "answer content", List.of());

      mockMvc
          .perform(
              put("/questions/" + savedPost.getId() + "/answers/" + answerId)
                  .with(userPrincipal(503L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "updated"))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("DELETE answer returns 403 when the requester is not the owner")
    void deleteAnswer_returns403_whenNotOwner() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
      Long answerId = createAnswer(savedPost.getId(), 502L, "answer content", List.of());

      mockMvc
          .perform(
              delete("/questions/" + savedPost.getId() + "/answers/" + answerId)
                  .with(userPrincipal(503L)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("PUT answer returns 400 when the answer is accepted")
    void updateAnswer_returns400_whenAnswerIsAccepted() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
      AnswerEntity answer =
          answerJpaRepository.save(
              buildAnswerEntity(savedPost.getId(), 502L, "accepted answer", true, List.of()));

      mockMvc
          .perform(
              put("/questions/" + savedPost.getId() + "/answers/" + answer.getId())
                  .with(userPrincipal(502L))
                  .contentType(APPLICATION_JSON)
                  .content(json(Map.of("content", "updated"))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }

    @Test
    @DisplayName("DELETE answer returns 400 when the answer is accepted")
    void deleteAnswer_returns400_whenAnswerIsAccepted() throws Exception {
      PostEntity savedPost = savePost(501L, PostType.QUESTION, false);
      AnswerEntity answer =
          answerJpaRepository.save(
              buildAnswerEntity(savedPost.getId(), 502L, "accepted answer", true, List.of()));

      mockMvc
          .perform(
              delete("/questions/" + savedPost.getId() + "/answers/" + answer.getId())
                  .with(userPrincipal(502L)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.status").value("FAIL"));
    }
  }

  private PostEntity savePost(Long userId, PostType type, boolean isSolved) {
    return postJpaRepository.save(
        PostEntity.builder()
            .userId(userId)
            .type(type)
            .title(type == PostType.QUESTION ? "question title" : null)
            .content("post content")
            .imageUrls(List.of())
            .reward(type == PostType.QUESTION ? 100L : 0L)
            .isSolved(isSolved)
            .build());
  }

  private AnswerEntity buildAnswerEntity(
      Long postId, Long userId, String content, boolean isAccepted, List<String> imageUrls) {
    return AnswerEntity.builder()
        .postId(postId)
        .userId(userId)
        .content(content)
        .isAccepted(isAccepted)
        .imageUrls(imageUrls)
        .build();
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
