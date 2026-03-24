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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.level.application.dto.GrantXpResult;
import momzzangseven.mztkbe.modules.level.application.port.in.GrantXpUseCase;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.junit.jupiter.api.BeforeEach;
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

  @MockBean private GrantXpUseCase grantXpUseCase;

  @MockBean
  private momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort
      updatePostImagesPort;

  @MockBean
  private momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort
      loadPostImagesPort;

  @BeforeEach
  void setUp() {
    org.mockito.BDDMockito.given(grantXpUseCase.execute(org.mockito.ArgumentMatchers.any()))
        .willReturn(GrantXpResult.granted(20, 10, 1, LocalDate.of(2026, 3, 12)));
    org.mockito.BDDMockito.given(
            loadPostImagesPort.loadImages(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .willReturn(PostImageResult.empty());
  }

  @Test
  @DisplayName("POST /posts/free → DB 저장, GET /posts/{id}로 조회 가능")
  void createAndGet_realFlow_persistsAndLoadsFromH2() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/free")
                    .with(userPrincipal(101L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "실경로 본문", "imageIds", List.of(1L)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andReturn();

    Long postId = extractPostId(createResult);
    PostEntity saved = postJpaRepository.findById(postId).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(101L);
    assertThat(saved.getTitle()).isNull();
    assertThat(saved.getContent()).isEqualTo("실경로 본문");
    org.mockito.Mockito.verify(updatePostImagesPort)
        .updateImages(101L, postId, PostType.FREE, List.of(1L));

    mockMvc
        .perform(get("/posts/" + postId).with(userPrincipal(101L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(postId));
  }

  @Test
  @DisplayName("GET /posts/{id} 상세 조회는 imageUrls를 응답에 포함")
  void getPost_detailIncludesImageUrls() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/free")
                    .with(userPrincipal(111L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "이미지 포함 상세"))))
            .andExpect(status().isCreated())
            .andReturn();

    Long postId = extractPostId(createResult);
    org.mockito.BDDMockito.given(loadPostImagesPort.loadImages(PostType.FREE, postId))
        .willReturn(
            new PostImageResult(
                List.of(
                    new PostImageResult.PostImageSlot(1L, "https://cdn.example.com/images/a.webp"),
                    new PostImageResult.PostImageSlot(2L, "https://cdn.example.com/images/b.webp"))));

    mockMvc
        .perform(get("/posts/" + postId).with(userPrincipal(111L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.imageUrls[0]").value("https://cdn.example.com/images/a.webp"))
        .andExpect(jsonPath("$.data.imageUrls[1]").value("https://cdn.example.com/images/b.webp"));
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
                    .content(json(Map.of("content", "초기 본문"))))
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
    org.mockito.Mockito.verifyNoInteractions(updatePostImagesPort);

    mockMvc
        .perform(delete("/posts/" + postId).with(userPrincipal(202L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.postId").value(postId));

    assertThat(postJpaRepository.findById(postId)).isEmpty();
  }

  @Test
  @DisplayName("POST /posts/free duplicate imageIds는 400")
  void createFreePost_duplicateImageIds_returns400() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(101L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "중복 이미지", "imageIds", List.of(1L, 1L)))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("FAIL"))
        .andExpect(jsonPath("$.code").value("POST_003"));
  }

  @Test
  @DisplayName("POST /posts/free empty imageIds는 생성되지만 image sync는 호출하지 않음")
  void createFreePost_emptyImageIds_skipsImageSync() throws Exception {
    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(105L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "빈 이미지", "imageIds", List.of()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    org.mockito.Mockito.verifyNoInteractions(updatePostImagesPort);
  }

  @Test
  @DisplayName("PATCH /posts/{id} empty imageIds는 image sync 제거 요청으로 전달")
  void updatePost_emptyImageIds_callsImageSync() throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/free")
                    .with(userPrincipal(206L))
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of("content", "초기 본문"))))
            .andExpect(status().isCreated())
            .andReturn();
    Long postId = extractPostId(createResult);

    org.mockito.Mockito.clearInvocations(updatePostImagesPort);

    mockMvc
        .perform(
            patch("/posts/" + postId)
                .with(userPrincipal(206L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "수정 본문", "imageIds", List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));

    org.mockito.Mockito.verify(updatePostImagesPort)
        .updateImages(206L, postId, PostType.FREE, List.of());
  }

  private Long extractPostId(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.at("/data/postId").asLong();
  }

  @Test
  @DisplayName(
      "GET /posts with question type, tag, and title search returns only matching question post")
  void searchQuestionPostsByTypeTagAndTitle_returnsFilteredPosts() throws Exception {
    Long expectedPostId =
        createQuestionPost(301L, "Spring boot tag search", "match content", 50L, List.of("java"));
    createQuestionPost(302L, "JPA query tuning", "other title", 50L, List.of("java"));

    mockMvc
        .perform(
            post("/posts/free")
                .with(userPrincipal(303L))
                .contentType(APPLICATION_JSON)
                .content(json(Map.of("content", "free content", "tags", List.of("java")))))
        .andExpect(status().isCreated());

    createQuestionPost(
        304L, "Spring boot different tag", "different tag content", 50L, List.of("spring"));

    mockMvc
        .perform(get("/posts?type=QUESTION&tag=java&search=Spring").with(userPrincipal(301L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].postId").value(expectedPostId))
        .andExpect(jsonPath("$.data[0].type").value("QUESTION"))
        .andExpect(jsonPath("$.data[0].title").value("Spring boot tag search"))
        .andExpect(jsonPath("$.data[0].tags[0]").value("java"))
        .andExpect(jsonPath("$.data[0].question.reward").value(50))
        .andExpect(jsonPath("$.data[0].question.isSolved").value(false));
  }

  private Long createQuestionPost(
      Long userId, String title, String content, Long reward, List<String> tags) throws Exception {
    MvcResult createResult =
        mockMvc
            .perform(
                post("/posts/question")
                    .with(userPrincipal(userId))
                    .contentType(APPLICATION_JSON)
                    .content(
                        json(
                            Map.of(
                                "title", title,
                                "content", content,
                                "reward", reward,
                                "tags", tags))))
            .andExpect(status().isCreated())
            .andReturn();

    return extractPostId(createResult);
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
