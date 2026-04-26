package momzzangseven.mztkbe.integration.e2e.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] GET /v2/users/me/commented-posts 테스트")
class CommentedPostsV2E2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Test
  @DisplayName("[E-1] 댓글 단 게시글은 타입별로 cursor 페이지 조회되고 상세 조회로 이동 가능하다")
  void getMyCommentedPostsV2_filtersByTypePaginatesDeduplicatesAndAllowsDetail() throws Exception {
    TestUser writer = signupAndLogin("commented-post-writer");
    TestUser commenter = signupAndLogin("commented-post-commenter");

    Long olderFreePostId = createFreePost(writer.accessToken(), "older commented free post");
    Long newerFreePostId = createFreePost(writer.accessToken(), "newer commented free post");
    Long questionPostId =
        createQuestionPost(
            writer.accessToken(), "commented question post", "question content", 100L);
    LocalDateTime base = LocalDateTime.of(2026, 4, 26, 12, 0);

    Long newerFreeOlderCommentId =
        createComment(commenter.accessToken(), newerFreePostId, "older duplicate comment");
    setCommentState(newerFreeOlderCommentId, base.minusMinutes(5), false);
    Long olderFreeCommentId =
        createComment(commenter.accessToken(), olderFreePostId, "older free comment");
    setCommentState(olderFreeCommentId, base.minusMinutes(2), false);
    Long questionCommentId =
        createComment(commenter.accessToken(), questionPostId, "question comment");
    setCommentState(questionCommentId, base.minusMinutes(3), false);
    Long newerFreeLatestCommentId =
        createComment(commenter.accessToken(), newerFreePostId, "latest free comment");
    setCommentState(newerFreeLatestCommentId, base.minusMinutes(1), false);
    TestUser otherCommenter = signupAndLogin("commented-post-other");
    Long otherWriterCommentId =
        createComment(otherCommenter.accessToken(), olderFreePostId, "other writer comment");
    setCommentState(otherWriterCommentId, base.plusMinutes(1), false);
    Long deletedNewerCommentId =
        createComment(commenter.accessToken(), olderFreePostId, "deleted newer comment");
    setCommentState(deletedNewerCommentId, base.plusMinutes(2), true);

    JsonNode firstFreePage = fetchCommentedPosts(commenter.accessToken(), "?type=FREE&size=1");

    assertThat(firstFreePage.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(firstFreePage.at("/data/posts").size()).isEqualTo(1);
    assertThat(firstFreePage.at("/data/posts/0/postId").asLong()).isEqualTo(newerFreePostId);
    assertThat(firstFreePage.at("/data/posts/0/type").asText()).isEqualTo("FREE");
    assertThat(firstFreePage.at("/data/posts/0/content").asText())
        .isEqualTo("newer commented free post");
    assertThat(firstFreePage.at("/data/posts/0/isLiked").asBoolean()).isFalse();
    assertThat(firstFreePage.at("/data/posts/0/writer/nickname").asText())
        .isEqualTo("commented-post-writer");
    assertThat(firstFreePage.at("/data/hasNext").asBoolean()).isTrue();

    Long listedPostId = firstFreePage.at("/data/posts/0/postId").asLong();
    JsonNode detail = fetchPostDetail(commenter.accessToken(), listedPostId);
    assertThat(detail.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(detail.at("/data/postId").asLong()).isEqualTo(listedPostId);

    String nextCursor = firstFreePage.at("/data/nextCursor").asText();
    assertThat(nextCursor).isNotBlank();

    JsonNode secondFreePage =
        fetchCommentedPosts(commenter.accessToken(), "?type=FREE&size=1&cursor=" + nextCursor);

    assertThat(secondFreePage.at("/data/posts").size()).isEqualTo(1);
    assertThat(secondFreePage.at("/data/posts/0/postId").asLong()).isEqualTo(olderFreePostId);
    assertThat(secondFreePage.at("/data/posts/0/type").asText()).isEqualTo("FREE");
    assertThat(secondFreePage.at("/data/hasNext").asBoolean()).isFalse();
    assertThat(secondFreePage.at("/data/nextCursor").isNull()).isTrue();

    JsonNode questionPage = fetchCommentedPosts(commenter.accessToken(), "?type=QUESTION&size=10");

    assertThat(questionPage.at("/data/posts").size()).isEqualTo(1);
    assertThat(questionPage.at("/data/posts/0/postId").asLong()).isEqualTo(questionPostId);
    assertThat(questionPage.at("/data/posts/0/type").asText()).isEqualTo("QUESTION");
    assertThat(questionPage.at("/data/posts/0/title").asText())
        .isEqualTo("commented question post");
    assertThat(questionPage.at("/data/posts/0/question/reward").asLong()).isEqualTo(100L);
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

  private Long createComment(String accessToken, Long postId, String content) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId + "/comments",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("content", content), bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(parse(response).at("/status").asText()).isEqualTo("SUCCESS");
    return parse(response).at("/data/commentId").asLong();
  }

  private JsonNode fetchCommentedPosts(String accessToken, String query) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/v2/users/me/commented-posts" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return parse(response);
  }

  private JsonNode fetchPostDetail(String accessToken, Long postId) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/" + postId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return parse(response);
  }

  private void setCommentState(Long commentId, LocalDateTime createdAt, boolean deleted) {
    int updated =
        jdbcTemplate.update(
            "UPDATE comments SET created_at = ?, updated_at = ?, is_deleted = ? WHERE id = ?",
            createdAt,
            createdAt,
            deleted,
            commentId);
    assertThat(updated).isEqualTo(1);
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }
}
