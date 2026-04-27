package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.util.UriComponentsBuilder;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /v2/posts cursor 검색 테스트")
class PostV2CursorE2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  private String accessToken;

  @BeforeEach
  void setUp() {
    accessToken = signupAndLogin("e2e-v2-cursor-user").accessToken();
  }

  private Long createQuestionPost(String title, List<String> tags) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "title",
                    title,
                    "content",
                    "cursor search e2e content",
                    "reward",
                    10L,
                    "imageIds",
                    List.of(),
                    "tags",
                    tags),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper.readTree(res.getBody()).at("/data/postId").asLong();
  }

  private JsonNode fetchPosts(String query) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            baseUrl() + "/v2/posts" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody());
  }

  private JsonNode fetchPosts(URI uri) throws Exception {
    ResponseEntity<String> res =
        restTemplate.exchange(
            uri, HttpMethod.GET, new HttpEntity<>(bearerJsonHeaders(accessToken)), String.class);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(res.getBody());
  }

  @Test
  @DisplayName("[E-1] 태그 검색 cursor는 search 대소문자가 달라도 다음 페이지를 조회한다")
  void getPostsV2_withTagAndDifferentSearchCase_returnsNextPage() throws Exception {
    Long olderPostId = createQuestionPost("FORM cursor older", List.of("Java"));
    Long newerPostId = createQuestionPost("FORM cursor newer", List.of("Java"));

    JsonNode firstPage = fetchPosts("?type=QUESTION&tag=java&search=FORM&size=1");

    assertThat(firstPage.at("/data/posts/0/postId").asLong()).isEqualTo(newerPostId);
    assertThat(firstPage.at("/data/hasNext").asBoolean()).isTrue();
    String nextCursor = firstPage.at("/data/nextCursor").asText();
    assertThat(nextCursor).isNotBlank();

    JsonNode secondPage =
        fetchPosts("?type=QUESTION&tag=java&search=form&size=1&cursor=" + nextCursor);

    assertThat(secondPage.at("/data/posts/0/postId").asLong()).isEqualTo(olderPostId);
  }

  @Test
  @DisplayName("[E-2] 태그 없는 검색 cursor는 search 대소문자가 달라도 다음 페이지를 조회한다")
  void getPostsV2_withoutTagAndDifferentSearchCase_returnsNextPage() throws Exception {
    Long olderPostId = createQuestionPost("MIXEDCASE cursor older", List.of());
    Long newerPostId = createQuestionPost("MIXEDCASE cursor newer", List.of());

    JsonNode firstPage = fetchPosts("?type=QUESTION&search=MIXEDCASE&size=1");

    assertThat(firstPage.at("/data/posts/0/postId").asLong()).isEqualTo(newerPostId);
    assertThat(firstPage.at("/data/hasNext").asBoolean()).isTrue();
    String nextCursor = firstPage.at("/data/nextCursor").asText();
    assertThat(nextCursor).isNotBlank();

    JsonNode secondPage = fetchPosts("?type=QUESTION&search=mixedcase&size=1&cursor=" + nextCursor);

    assertThat(secondPage.at("/data/posts/0/postId").asLong()).isEqualTo(olderPostId);
  }

  @Test
  @DisplayName("[E-3] 태그 없는 검색은 wildcard 문자를 literal로 처리한다")
  void getPostsV2_withoutTag_searchTreatsWildcardsLiterally() throws Exception {
    createQuestionPost("100ab form decoy", List.of());
    Long literalMatchPostId = createQuestionPost("100%_ form match", List.of());

    JsonNode response =
        fetchPosts(
            UriComponentsBuilder.fromHttpUrl(baseUrl() + "/v2/posts")
                .queryParam("type", "QUESTION")
                .queryParam("search", "100%_")
                .build()
                .encode()
                .toUri());

    assertThat(response.at("/data/posts").size()).isEqualTo(1);
    assertThat(response.at("/data/posts/0/postId").asLong()).isEqualTo(literalMatchPostId);
    assertThat(response.at("/data/posts/0/title").asText()).isEqualTo("100%_ form match");
  }

  @Test
  @DisplayName("[E-4] 태그 검색은 wildcard 문자를 literal로 처리한다")
  void getPostsV2_withTag_searchTreatsWildcardsLiterally() throws Exception {
    createQuestionPost("100ab form tagged decoy", List.of("java"));
    Long literalMatchPostId = createQuestionPost("100%_ form tagged match", List.of("java"));
    createQuestionPost("100%_ form other tag", List.of("spring"));

    JsonNode response =
        fetchPosts(
            UriComponentsBuilder.fromHttpUrl(baseUrl() + "/v2/posts")
                .queryParam("type", "QUESTION")
                .queryParam("tag", "java")
                .queryParam("search", "100%_")
                .build()
                .encode()
                .toUri());

    assertThat(response.at("/data/posts").size()).isEqualTo(1);
    assertThat(response.at("/data/posts/0/postId").asLong()).isEqualTo(literalMatchPostId);
    assertThat(response.at("/data/posts/0/title").asText()).isEqualTo("100%_ form tagged match");
  }
}
