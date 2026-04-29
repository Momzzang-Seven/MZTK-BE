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
@DisplayName("[E2E] GET /v2/users/me/posts 테스트")
class MyPostsV2E2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Test
  @DisplayName("[E-1] 내 질문글 검색 결과는 cursor로 3페이지까지 이어서 조회된다")
  void getMyPostsV2_withSearchAndCursor_returnsThreePages() throws Exception {
    TestUser author = signupAndLogin("my-posts-author");
    TestUser other = signupAndLogin("my-posts-other");
    LocalDateTime base = LocalDateTime.of(2026, 4, 27, 12, 0);

    Long olderPostId =
        createQuestionPost(
            author.accessToken(), "FLOW-MYPOSTS form older", "older content", List.of("flowtag"));
    Long middlePostId =
        createQuestionPost(
            author.accessToken(), "FLOW-MYPOSTS form middle", "middle content", List.of("flowtag"));
    Long newerPostId =
        createQuestionPost(
            author.accessToken(), "FLOW-MYPOSTS form newer", "newer content", List.of("flowtag"));
    Long wrongSearchPostId =
        createQuestionPost(
            author.accessToken(), "unmatched title", "wrong search content", List.of("flowtag"));
    Long wrongTagPostId =
        createQuestionPost(
            author.accessToken(),
            "FLOW-MYPOSTS form wrong tag",
            "wrong tag content",
            List.of("etc"));
    Long otherAuthorPostId =
        createQuestionPost(
            other.accessToken(), "FLOW-MYPOSTS form other", "other content", List.of("flowtag"));

    setPostCreatedAt(olderPostId, base.minusMinutes(2));
    setPostCreatedAt(middlePostId, base.minusMinutes(1));
    setPostCreatedAt(newerPostId, base);
    setPostCreatedAt(wrongSearchPostId, base.plusMinutes(1));
    setPostCreatedAt(wrongTagPostId, base.plusMinutes(2));
    setPostCreatedAt(otherAuthorPostId, base.plusMinutes(3));

    JsonNode firstPage =
        fetchMyPosts(author.accessToken(), "?type=QUESTION&tag=FlowTag&search=FLOW-MYPOSTS&size=1");

    assertSinglePostPage(firstPage, newerPostId, true);
    String firstCursor = firstPage.at("/data/nextCursor").asText();
    assertThat(firstCursor).isNotBlank();

    JsonNode secondPage =
        fetchMyPosts(
            author.accessToken(),
            "?type=QUESTION&tag=flowtag&search=flow-myposts&size=1&cursor=" + firstCursor);

    assertSinglePostPage(secondPage, middlePostId, true);
    String secondCursor = secondPage.at("/data/nextCursor").asText();
    assertThat(secondCursor).isNotBlank();
    assertThat(secondCursor).isNotEqualTo(firstCursor);

    JsonNode thirdPage =
        fetchMyPosts(
            author.accessToken(),
            "?type=QUESTION&tag=FLOWTAG&search=Flow-MyPosts&size=1&cursor=" + secondCursor);

    assertSinglePostPage(thirdPage, olderPostId, false);
    assertThat(thirdPage.at("/data/nextCursor").isNull()).isTrue();
  }

  private Long createQuestionPost(
      String accessToken, String title, String content, List<String> tags) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/posts/question",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "title",
                    title,
                    "content",
                    content,
                    "reward",
                    100L,
                    "imageIds",
                    List.of(),
                    "tags",
                    tags),
                bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return parse(response).at("/data/postId").asLong();
  }

  private JsonNode fetchMyPosts(String accessToken, String query) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/v2/users/me/posts" + query,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return parse(response);
  }

  private void setPostCreatedAt(Long postId, LocalDateTime createdAt) {
    jdbcTemplate.update(
        "UPDATE posts SET created_at = ?, updated_at = ? WHERE id = ?",
        createdAt,
        createdAt,
        postId);
  }

  private void assertSinglePostPage(JsonNode page, Long expectedPostId, boolean expectedHasNext) {
    assertThat(page.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(page.at("/data/posts").size()).isEqualTo(1);
    assertThat(page.at("/data/posts/0/postId").asLong()).isEqualTo(expectedPostId);
    assertThat(page.at("/data/posts/0/type").asText()).isEqualTo("QUESTION");
    assertThat(page.at("/data/posts/0/writer/nickname").asText()).isEqualTo("my-posts-author");
    assertThat(page.at("/data/hasNext").asBoolean()).isEqualTo(expectedHasNext);
  }

  private JsonNode parse(ResponseEntity<String> response) throws Exception {
    return objectMapper.readTree(response.getBody());
  }
}
