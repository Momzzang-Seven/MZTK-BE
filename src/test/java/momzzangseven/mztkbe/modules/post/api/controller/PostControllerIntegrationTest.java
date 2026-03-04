package momzzangseven.mztkbe.modules.post.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

@DisplayName("PostController 실경로 통합 테스트 (MockMvc + H2)")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerIntegrationTest {

  @org.springframework.beans.factory.annotation.Autowired protected MockMvc mockMvc;

  @org.springframework.beans.factory.annotation.Autowired
  protected com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
  @DisplayName("POST /posts/free → DB 저장, GET /posts/{id}로 조회 가능")
  void createAndGet_realFlow_persistsAndLoadsFromH2() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/free")
                    .with(userPrincipal(101L))
                    .contentType(APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "title",
                                "실경로 제목",
                                "content",
                                "실경로 본문",
                                "imageUrls",
                                List.of("https://example.com/real-1.png")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();

    Long postId = extractPostId(createResult);
    PostEntity saved = postJpaRepository.findById(postId).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(101L);
    assertThat(saved.getTitle()).isEqualTo("실경로 제목");
    assertThat(saved.getContent()).isEqualTo("실경로 본문");

    mockMvc
        .perform(get("/posts/" + postId).with(userPrincipal(101L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(postId))
        .andExpect(jsonPath("$.data.title").value("실경로 제목"));
  }

  @Test
  @DisplayName("PATCH/DELETE /posts/{id} → DB 수정/삭제 반영")
  void updateAndDelete_realFlow_updatesAndDeletesInH2() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/free")
                    .with(userPrincipal(202L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("title", "초기 제목", "content", "초기 본문"))))
            .andExpect(status().isCreated())
            .andReturn();
    Long postId = extractPostId(createResult);

    mockMvc
        .perform(
            patch("/posts/" + postId)
                .with(userPrincipal(202L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("title", "수정 제목", "content", "수정 본문"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(postId));

    PostEntity updated = postJpaRepository.findById(postId).orElseThrow();
    assertThat(updated.getTitle()).isEqualTo("수정 제목");
    assertThat(updated.getContent()).isEqualTo("수정 본문");

    mockMvc
        .perform(delete("/posts/" + postId).with(userPrincipal(202L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(postId));

    assertThat(postJpaRepository.findById(postId)).isEmpty();
  }

  private Long extractPostId(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at("/data/postId").asLong();
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
