package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /v2/users/me/liked-posts 테스트")
class LikedPostsV2E2ETest extends E2ETestBase {

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Test
  @DisplayName("[E-1] 좋아요한 게시글은 게시판 타입별로 cursor 페이지 조회된다")
  void getMyLikedPostsV2_filtersByTypeAndPaginatesWithCursor() throws Exception {
    TestUser writer = signupAndLogin("liked-post-writer");
    TestUser liker = signupAndLogin("liked-post-liker");

    Long olderFreePostId = createFreePost(writer.accessToken(), "older liked free post");
    Long newerFreePostId = createFreePost(writer.accessToken(), "newer liked free post");
    Long questionPostId =
        createQuestionPost(writer.accessToken(), "liked question post", "question content", 100L);

    likePost(olderFreePostId, liker.accessToken());
    likePost(newerFreePostId, liker.accessToken());
    likePost(questionPostId, liker.accessToken());
    TestUser answerer = signupAndLogin("liked-post-answerer");
    Long answerId = createAnswer(questionPostId, answerer.accessToken(), "answer like target");
    likeAnswer(questionPostId, answerId, liker.accessToken());

    JsonNode firstFreePage = fetchLikedPosts(liker.accessToken(), "?type=FREE&size=1");

    assertThat(firstFreePage.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(firstFreePage.at("/data/posts").size()).isEqualTo(1);
    assertThat(firstFreePage.at("/data/posts/0/postId").asLong()).isEqualTo(newerFreePostId);
    assertThat(firstFreePage.at("/data/posts/0/type").asText()).isEqualTo("FREE");
    assertThat(firstFreePage.at("/data/posts/0/content").asText())
        .isEqualTo("newer liked free post");
    assertThat(firstFreePage.at("/data/posts/0/isLiked").asBoolean()).isTrue();
    assertThat(firstFreePage.at("/data/posts/0/likeCount").asLong()).isEqualTo(1L);
    assertThat(firstFreePage.at("/data/posts/0/writer/nickname").asText())
        .isEqualTo("liked-post-writer");
    assertThat(firstFreePage.at("/data/hasNext").asBoolean()).isTrue();

    String nextCursor = firstFreePage.at("/data/nextCursor").asText();
    assertThat(nextCursor).isNotBlank();

    JsonNode secondFreePage =
        fetchLikedPosts(liker.accessToken(), "?type=FREE&size=1&cursor=" + nextCursor);

    assertThat(secondFreePage.at("/data/posts").size()).isEqualTo(1);
    assertThat(secondFreePage.at("/data/posts/0/postId").asLong()).isEqualTo(olderFreePostId);
    assertThat(secondFreePage.at("/data/posts/0/type").asText()).isEqualTo("FREE");
    assertThat(secondFreePage.at("/data/posts/0/isLiked").asBoolean()).isTrue();
    assertThat(secondFreePage.at("/data/hasNext").asBoolean()).isFalse();
    assertThat(secondFreePage.at("/data/nextCursor").isNull()).isTrue();

    JsonNode questionPage = fetchLikedPosts(liker.accessToken(), "?type=QUESTION&size=10");

    assertThat(questionPage.at("/data/posts").size()).isEqualTo(1);
    assertThat(questionPage.at("/data/posts/0/postId").asLong()).isEqualTo(questionPostId);
    assertThat(questionPage.at("/data/posts/0/type").asText()).isEqualTo("QUESTION");
    assertThat(questionPage.at("/data/posts/0/question/reward").asLong()).isEqualTo(100L);
    assertThat(questionPage.at("/data/posts/0/isLiked").asBoolean()).isTrue();
    assertThat(questionPage.at("/data/hasNext").asBoolean()).isFalse();
  }

  private Long createFreePost(String accessToken, String content) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/free",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return parse(response).at("/data/postId").asLong();
  }

  private Long createQuestionPost(String accessToken, String title, String content, long reward)
      throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("title", title, "content", content, "reward", reward, "imageIds", List.of()),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return parse(response).at("/data/postId").asLong();
  }

  private Long createAnswer(Long postId, String accessToken, String content) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("content", content, "imageIds", List.of()), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return parse(response).at("/data/answerId").asLong();
  }

  private void likePost(Long postId, String accessToken) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/likes",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(parse(response).at("/status").asText()).isEqualTo("SUCCESS");
  }

  private void likeAnswer(Long postId, Long answerId, String accessToken) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/questions/" + postId + "/answers/" + answerId + "/likes",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(parse(response).at("/status").asText()).isEqualTo("SUCCESS");
  }

  private JsonNode fetchLikedPosts(String accessToken, String query) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/v2/users/me/liked-posts" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return parse(response);
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }
}
