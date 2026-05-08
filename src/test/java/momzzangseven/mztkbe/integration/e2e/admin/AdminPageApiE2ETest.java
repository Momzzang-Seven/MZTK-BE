package momzzangseven.mztkbe.integration.e2e.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** E2E coverage for MOM-239/240/241/242 admin page APIs. */
@DisplayName("[E2E] Admin page APIs")
class AdminPageApiE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @Test
  @DisplayName("[E2E-1] Admin can inspect stats/list and change managed user account status")
  void adminAuthUserStatsListStatusFlow() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    TestAppUser user = createAppUser("USER", "ACTIVE", "StatusUser");
    TestAppUser trainer = createAppUser("TRAINER", "BLOCKED", "BlockedTrainer");

    ResponseEntity<String> statsResponse =
        getWithBearer("/admin/dashboard/user-stats", admin.accessToken());
    assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode stats = data(statsResponse);
    assertThat(stats.at("/totalUserCount").asLong()).isEqualTo(2L);
    assertThat(stats.at("/activeUserCount").asLong()).isEqualTo(1L);
    assertThat(stats.at("/blockedUserCount").asLong()).isEqualTo(1L);
    assertThat(stats.at("/roleCounts/USER").asLong()).isEqualTo(1L);
    assertThat(stats.at("/roleCounts/TRAINER").asLong()).isEqualTo(1L);

    ResponseEntity<String> listResponse =
        getWithBearer("/admin/users?page=0&size=10", admin.accessToken());
    assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode users = data(listResponse).path("content");
    assertThat(users).hasSize(2);
    assertThat(findById(users, "userId", user.userId()).at("/role").asText()).isEqualTo("USER");
    assertThat(findById(users, "userId", trainer.userId()).at("/status").asText())
        .isEqualTo("BLOCKED");
    assertThat(findById(users, "userId", admin.userId()).isMissingNode()).isTrue();

    TestLogin userLogin = loginAppUser(user.email());
    ResponseEntity<String> blockResponse =
        patchWithBearer(
            "/admin/users/" + user.userId() + "/status",
            admin.accessToken(),
            Map.of("status", "BLOCKED", "reason", "E2E block"));
    assertThat(blockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(blockResponse).at("/status").asText()).isEqualTo("BLOCKED");
    assertThat(accountStatus(user.userId())).isEqualTo("BLOCKED");
    assertThat(userRole(user.userId())).isEqualTo("USER");

    ResponseEntity<String> blockedReissueResponse = reissue(userLogin.refreshToken());
    assertThat(blockedReissueResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode blockedReissueBody = objectMapper.readTree(blockedReissueResponse.getBody());
    assertThat(blockedReissueBody.at("/status").asText()).isEqualTo("FAIL");
    assertThat(blockedReissueBody.at("/code").asText()).isEqualTo("USER_006");

    ResponseEntity<String> blockedStatsResponse =
        getWithBearer("/admin/dashboard/user-stats", admin.accessToken());
    JsonNode blockedStats = data(blockedStatsResponse);
    assertThat(blockedStats.at("/activeUserCount").asLong()).isZero();
    assertThat(blockedStats.at("/blockedUserCount").asLong()).isEqualTo(2L);

    ResponseEntity<String> activeResponse =
        patchWithBearer(
            "/admin/users/" + user.userId() + "/status",
            admin.accessToken(),
            Map.of("status", "ACTIVE", "reason", "E2E restore"));
    assertThat(activeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(accountStatus(user.userId())).isEqualTo("ACTIVE");

    ResponseEntity<String> selfBlockResponse =
        patchWithBearer(
            "/admin/users/" + admin.userId() + "/status",
            admin.accessToken(),
            Map.of("status", "BLOCKED", "reason", "self block"));
    assertThat(selfBlockResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("[E2E-2] Admin endpoints require admin role")
  void adminSecurityGuardFlow() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    TestAppUser user = createAppUser("USER", "ACTIVE", "SecurityUser");
    String userToken = loginUser(user.email(), DEFAULT_TEST_PASSWORD);
    Long postId = seedPost(user.userId(), "FREE", "OPEN", "Security title", "Security body", 0L);
    Long commentId = seedComment(postId, user.userId(), "Security comment", null, false);

    ResponseEntity<String> anonymousResponse =
        restTemplate.exchange(
            baseUrl() + "/admin/users",
            HttpMethod.GET,
            new HttpEntity<>(jsonOnlyHeaders()),
            String.class);
    assertThat(anonymousResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<String> userListResponse = getWithBearer("/admin/users", userToken);
    assertThat(userListResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> userBoardResponse = getWithBearer("/admin/boards/posts", userToken);
    assertThat(userBoardResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> adminListResponse = getWithBearer("/admin/users", admin.accessToken());
    assertThat(adminListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> userBanResponse =
        postWithBearer(
            "/admin/boards/comments/" + commentId + "/ban",
            userToken,
            Map.of("reasonCode", "SPAM", "reasonDetail", "not allowed"));
    assertThat(userBanResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("[E2E-3] Admin can query board posts and comments with paging filters")
  void adminBoardQueryFlow() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    TestAppUser user = createAppUser("USER", "ACTIVE", "BoardUser");
    TestAppUser trainer = createAppUser("TRAINER", "ACTIVE", "BoardTrainer");
    Long freePostId = seedPost(user.userId(), "FREE", "OPEN", "Alpha Free", "Alpha free body", 0L);
    Long questionPostId =
        seedPost(
            trainer.userId(), "QUESTION", "OPEN", "Alpha Question", "Alpha question body", 100L);
    Long rootComment = seedComment(freePostId, trainer.userId(), "Root comment", null, false);
    Long reply = seedComment(freePostId, user.userId(), "Reply comment", rootComment, false);
    Long deletedComment = seedComment(freePostId, trainer.userId(), "Deleted comment", null, true);

    ResponseEntity<String> postsResponse =
        getWithBearer(
            "/admin/boards/posts?status=OPEN&page=0&size=10&sort=postId", admin.accessToken());
    assertThat(postsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode posts = data(postsResponse).path("content");
    assertThat(posts).hasSize(2);
    assertThat(posts.get(0).at("/postId").asLong()).isEqualTo(questionPostId);

    JsonNode freePost = findById(posts, "postId", freePostId);
    assertThat(freePost.at("/type").asText()).isEqualTo("FREE");
    assertThat(freePost.at("/status").asText()).isEqualTo("OPEN");
    assertThat(freePost.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(freePost.at("/moderationStatus").asText()).isEqualTo("NORMAL");
    assertThat(freePost.at("/title").asText()).isEqualTo("Alpha Free");
    assertThat(freePost.at("/contentPreview").asText()).isEqualTo("Alpha free body");
    assertThat(freePost.at("/writerId").asLong()).isEqualTo(user.userId());
    assertThat(freePost.at("/writerNickname").asText()).isEqualTo(user.nickname());
    assertThat(freePost.at("/commentCount").asLong()).isEqualTo(2L);

    ResponseEntity<String> commentsResponse =
        getWithBearer(
            "/admin/boards/posts/" + freePostId + "/comments?page=0&size=10", admin.accessToken());
    assertThat(commentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode comments = data(commentsResponse).path("content");
    assertThat(comments).hasSize(3);

    JsonNode root = findById(comments, "commentId", rootComment);
    assertThat(root.at("/postId").asLong()).isEqualTo(freePostId);
    assertThat(root.at("/writerId").asLong()).isEqualTo(trainer.userId());
    assertThat(root.at("/writerNickname").asText()).isEqualTo(trainer.nickname());
    assertThat(root.at("/content").asText()).isEqualTo("Root comment");
    assertThat(root.path("parentId").isNull()).isTrue();
    assertThat(root.at("/isDeleted").asBoolean()).isFalse();

    JsonNode replyNode = findById(comments, "commentId", reply);
    assertThat(replyNode.at("/parentId").asLong()).isEqualTo(rootComment);
    assertThat(replyNode.at("/content").asText()).isEqualTo("Reply comment");

    JsonNode deletedNode = findById(comments, "commentId", deletedComment);
    assertThat(deletedNode.at("/isDeleted").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("[E2E-4] Comment ban soft-deletes once and feeds post stats")
  void commentBanAndPostStatsFlow() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    TestAppUser user = createAppUser("USER", "ACTIVE", "BanUser");
    Long postId = seedPost(user.userId(), "FREE", "OPEN", "Ban title", "Ban body", 0L);
    Long commentId = seedComment(postId, user.userId(), "Spam comment", null, false);

    ResponseEntity<String> banResponse =
        postWithBearer(
            "/admin/boards/comments/" + commentId + "/ban",
            admin.accessToken(),
            Map.of("reasonCode", "SPAM", "reasonDetail", "E2E spam comment"));
    assertThat(banResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode ban = data(banResponse);
    assertThat(ban.at("/targetId").asLong()).isEqualTo(commentId);
    assertThat(ban.at("/targetType").asText()).isEqualTo("COMMENT");
    assertThat(ban.at("/reasonCode").asText()).isEqualTo("SPAM");
    assertThat(ban.at("/moderated").asBoolean()).isTrue();
    assertThat(isCommentDeleted(commentId)).isTrue();
    assertThat(moderationActionCount("COMMENT", commentId)).isEqualTo(1L);

    ResponseEntity<String> statsResponse =
        getWithBearer("/admin/dashboard/post-stats", admin.accessToken());
    JsonNode stats = data(statsResponse);
    assertThat(stats.at("/postRemovalReasonStats/SPAM").asLong()).isEqualTo(1L);
    assertThat(stats.at("/targetTypeStats/COMMENT").asLong()).isEqualTo(1L);
    assertThat(stats.at("/targetTypeStats/POST").asLong()).isZero();

    ResponseEntity<String> rebanResponse =
        postWithBearer(
            "/admin/boards/comments/" + commentId + "/ban",
            admin.accessToken(),
            Map.of("reasonCode", "SPAM", "reasonDetail", "E2E spam comment again"));
    assertThat(rebanResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(rebanResponse).at("/moderated").asBoolean()).isFalse();
    assertThat(moderationActionCount("COMMENT", commentId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("[E2E-5] Admin can block and unblock posts without changing publication status")
  void postBanUnblockFlow() throws Exception {
    TestAdmin admin = createAdminAndLogin();
    TestAppUser user = createAppUser("USER", "ACTIVE", "PostBanUser");
    TestAppUser viewer = createAppUser("USER", "ACTIVE", "PostViewer");
    String viewerToken = loginUser(viewer.email(), DEFAULT_TEST_PASSWORD);
    Long freePostId = seedPost(user.userId(), "FREE", "OPEN", "Free guard", "Free body", 0L);
    Long questionPostId =
        seedPost(user.userId(), "QUESTION", "OPEN", "Question guard", "Question body", 100L);
    markPostPublicationStatus(questionPostId, "FAILED");

    ResponseEntity<String> freeBanResponse =
        postWithBearer(
            "/admin/boards/posts/" + freePostId + "/ban",
            admin.accessToken(),
            Map.of("reasonCode", "POLICY_VIOLATION", "reasonDetail", "E2E policy guard"));
    assertThat(freeBanResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode freeBan = data(freeBanResponse);
    assertThat(freeBan.at("/targetId").asLong()).isEqualTo(freePostId);
    assertThat(freeBan.at("/targetType").asText()).isEqualTo("POST");
    assertThat(freeBan.at("/moderated").asBoolean()).isTrue();
    assertThat(freeBan.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(freeBan.at("/moderationStatus").asText()).isEqualTo("BLOCKED");
    assertThat(postPublicationStatus(freePostId)).isEqualTo("VISIBLE");
    assertThat(postModerationStatus(freePostId)).isEqualTo("BLOCKED");
    assertThat(adminActionAuditCount("ADMIN_BOARD_POST_BAN", String.valueOf(freePostId)))
        .isEqualTo(1L);
    assertThat(adminActionAuditCount("POST_BLOCK", "post:" + freePostId)).isZero();

    ResponseEntity<String> freeRebanResponse =
        postWithBearer(
            "/admin/boards/posts/" + freePostId + "/ban",
            admin.accessToken(),
            Map.of("reasonCode", "POLICY_VIOLATION", "reasonDetail", "E2E policy guard again"));
    assertThat(freeRebanResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(freeRebanResponse).at("/moderated").asBoolean()).isFalse();
    assertThat(moderationActionCount("POST", freePostId)).isEqualTo(1L);

    ResponseEntity<String> questionBanResponse =
        postWithBearer(
            "/admin/boards/posts/" + questionPostId + "/ban",
            admin.accessToken(),
            Map.of("reasonCode", "POLICY_VIOLATION", "reasonDetail", "E2E policy guard"));
    assertThat(questionBanResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode questionBan = data(questionBanResponse);
    assertThat(questionBan.at("/moderated").asBoolean()).isTrue();
    assertThat(questionBan.at("/publicationStatus").asText()).isEqualTo("FAILED");
    assertThat(questionBan.at("/moderationStatus").asText()).isEqualTo("BLOCKED");

    assertThat(postStatus(freePostId)).isEqualTo("OPEN");
    assertThat(postStatus(questionPostId)).isEqualTo("OPEN");
    assertThat(postPublicationStatus(questionPostId)).isEqualTo("FAILED");
    assertThat(postModerationStatus(questionPostId)).isEqualTo("BLOCKED");
    assertThat(moderationActionCount("POST", questionPostId)).isEqualTo(1L);
    assertThat(moderationActionTypeCount("POST")).isEqualTo(2L);

    ResponseEntity<String> publicListResponse = getWithBearer("/posts?type=FREE", viewerToken);
    assertThat(publicListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode publicPosts = data(publicListResponse).path("posts");
    assertThat(findById(publicPosts, "postId", freePostId).isMissingNode()).isTrue();

    ResponseEntity<String> blockedDetailResponse =
        getWithBearer("/posts/" + freePostId, viewerToken);
    assertThat(blockedDetailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> failedDetailResponse =
        getWithBearer("/posts/" + questionPostId, viewerToken);
    assertThat(failedDetailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> questionUnblockResponse =
        postWithBearer(
            "/admin/boards/posts/" + questionPostId + "/unblock",
            admin.accessToken(),
            Map.of("reasonCode", "OTHER", "reasonDetail", "E2E unblock failed question"));
    assertThat(questionUnblockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode questionUnblock = data(questionUnblockResponse);
    assertThat(questionUnblock.at("/moderated").asBoolean()).isTrue();
    assertThat(questionUnblock.at("/publicationStatus").asText()).isEqualTo("FAILED");
    assertThat(questionUnblock.at("/moderationStatus").asText()).isEqualTo("NORMAL");
    assertThat(postPublicationStatus(questionPostId)).isEqualTo("FAILED");
    assertThat(postModerationStatus(questionPostId)).isEqualTo("NORMAL");
    assertThat(adminActionAuditCount("ADMIN_BOARD_POST_UNBLOCK", String.valueOf(questionPostId)))
        .isEqualTo(1L);
    assertThat(adminActionAuditCount("POST_UNBLOCK", "post:" + questionPostId)).isZero();

    ResponseEntity<String> failedNormalDetailResponse =
        getWithBearer("/posts/" + questionPostId, viewerToken);
    assertThat(failedNormalDetailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<String> freeUnblockResponse =
        postWithBearer(
            "/admin/boards/posts/" + freePostId + "/unblock",
            admin.accessToken(),
            Map.of("reasonCode", "OTHER", "reasonDetail", "E2E unblock visible free"));
    assertThat(freeUnblockResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode freeUnblock = data(freeUnblockResponse);
    assertThat(freeUnblock.at("/moderated").asBoolean()).isTrue();
    assertThat(freeUnblock.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(freeUnblock.at("/moderationStatus").asText()).isEqualTo("NORMAL");
    assertThat(postPublicationStatus(freePostId)).isEqualTo("VISIBLE");
    assertThat(postModerationStatus(freePostId)).isEqualTo("NORMAL");
    assertThat(adminActionAuditCount("ADMIN_BOARD_POST_UNBLOCK", String.valueOf(freePostId)))
        .isEqualTo(1L);

    ResponseEntity<String> restoredPublicListResponse =
        getWithBearer("/posts?type=FREE", viewerToken);
    assertThat(restoredPublicListResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode restoredPublicPosts = data(restoredPublicListResponse).path("posts");
    JsonNode restoredFreePost = findById(restoredPublicPosts, "postId", freePostId);
    assertThat(restoredFreePost.isMissingNode()).isFalse();
    assertThat(restoredFreePost.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(restoredFreePost.at("/moderationStatus").asText()).isEqualTo("NORMAL");

    ResponseEntity<String> restoredFreeDetailResponse =
        getWithBearer("/posts/" + freePostId, viewerToken);
    assertThat(restoredFreeDetailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode restoredFreeDetail = data(restoredFreeDetailResponse);
    assertThat(restoredFreeDetail.at("/postId").asLong()).isEqualTo(freePostId);
    assertThat(restoredFreeDetail.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(restoredFreeDetail.at("/moderationStatus").asText()).isEqualTo("NORMAL");

    ResponseEntity<String> alreadyUnblockedResponse =
        postWithBearer(
            "/admin/boards/posts/" + questionPostId + "/unblock",
            admin.accessToken(),
            Map.of("reasonCode", "OTHER", "reasonDetail", "E2E unblock again"));
    assertThat(alreadyUnblockedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(data(alreadyUnblockedResponse).at("/moderated").asBoolean()).isFalse();
    assertThat(moderationActionTypeCount("POST")).isEqualTo(2L);

    ResponseEntity<String> statsResponse =
        getWithBearer("/admin/dashboard/post-stats", admin.accessToken());
    JsonNode postStats = data(statsResponse);
    assertThat(postStats.at("/targetTypeStats/POST").asLong()).isEqualTo(2L);
    assertThat(postStats.at("/postRemovalReasonStats/POLICY_VIOLATION").asLong()).isEqualTo(2L);
    assertThat(postStats.at("/boardTypeSplit/FREE").asLong()).isEqualTo(1L);
    assertThat(postStats.at("/boardTypeSplit/QUESTION").asLong()).isEqualTo(1L);

    ResponseEntity<String> adminPostsResponse =
        getWithBearer("/admin/boards/posts?page=0&size=10&sort=postId", admin.accessToken());
    assertThat(adminPostsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode adminPosts = data(adminPostsResponse).path("content");
    JsonNode adminFreePost = findById(adminPosts, "postId", freePostId);
    JsonNode adminQuestionPost = findById(adminPosts, "postId", questionPostId);
    assertThat(adminFreePost.at("/publicationStatus").asText()).isEqualTo("VISIBLE");
    assertThat(adminFreePost.at("/moderationStatus").asText()).isEqualTo("NORMAL");
    assertThat(adminQuestionPost.at("/publicationStatus").asText()).isEqualTo("FAILED");
    assertThat(adminQuestionPost.at("/moderationStatus").asText()).isEqualTo("NORMAL");
  }

  private TestAdmin createAdminAndLogin() throws Exception {
    String email = "admin-" + uniqueToken() + "@internal.mztk.local";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at) "
            + "VALUES (?, 'ADMIN_GENERATED', 'AdminPageAdmin', NOW(), NOW())",
        email);
    Long userId = userIdByEmail(email);
    String loginId = String.valueOf(10000000 + Math.abs(UUID.randomUUID().hashCode() % 90000000));
    String password = "AdminP@ss" + uniqueToken().substring(0, 8);
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by, "
            + "last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at) "
            + "VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(password));
    String accessToken = loginAdmin(loginId, password);
    return new TestAdmin(userId, accessToken);
  }

  private TestAppUser createAppUser(String role, String status, String nickname) {
    String email = "user-" + uniqueToken() + "@test.com";
    jdbcTemplate.update(
        "INSERT INTO users (email, role, nickname, created_at, updated_at) "
            + "VALUES (?, ?, ?, NOW(), NOW())",
        email,
        role,
        nickname);
    Long userId = userIdByEmail(email);
    jdbcTemplate.update(
        "INSERT INTO users_account (user_id, provider, provider_user_id, password_hash, status, "
            + "created_at, updated_at) VALUES (?, 'LOCAL', NULL, ?, ?, NOW(), NOW())",
        userId,
        passwordEncoder.encode(DEFAULT_TEST_PASSWORD),
        status);
    return new TestAppUser(userId, email, nickname);
  }

  private String loginAdmin(String loginId, String password) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("provider", "LOCAL_ADMIN", "loginId", loginId, "password", password),
                jsonOnlyHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
  }

  private TestLogin loginAppUser(String email) throws Exception {
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("provider", "LOCAL", "email", email, "password", DEFAULT_TEST_PASSWORD),
                jsonOnlyHeaders()),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String accessToken = objectMapper.readTree(response.getBody()).at("/data/accessToken").asText();
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    String refreshToken = setCookie.split(";")[0].replace("refreshToken=", "").trim();
    return new TestLogin(accessToken, refreshToken);
  }

  private ResponseEntity<String> reissue(String refreshToken) {
    HttpHeaders headers = jsonOnlyHeaders();
    headers.add(HttpHeaders.COOKIE, "refreshToken=" + refreshToken);
    return restTemplate.exchange(
        baseUrl() + "/auth/reissue", HttpMethod.POST, new HttpEntity<>(headers), String.class);
  }

  private ResponseEntity<String> getWithBearer(String path, String accessToken) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.GET,
        new HttpEntity<>(bearerJsonHeaders(accessToken)),
        String.class);
  }

  private ResponseEntity<String> postWithBearer(String path, String accessToken, Object body) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.POST,
        new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
        String.class);
  }

  private ResponseEntity<String> patchWithBearer(String path, String accessToken, Object body) {
    return restTemplate.exchange(
        baseUrl() + path,
        HttpMethod.PATCH,
        new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
        String.class);
  }

  private JsonNode data(ResponseEntity<String> response) throws Exception {
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    return body.path("data");
  }

  private JsonNode findById(JsonNode array, String idField, Long id) {
    return StreamSupport.stream(array.spliterator(), false)
        .filter(node -> node.path(idField).asLong() == id)
        .findFirst()
        .orElse(MissingNode.getInstance());
  }

  private Long seedPost(
      Long writerId, String type, String status, String title, String content, Long reward) {
    jdbcTemplate.update(
        "INSERT INTO posts (user_id, type, title, content, reward, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
        writerId,
        type,
        title,
        content,
        reward,
        status);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM posts WHERE title = ? ORDER BY id DESC LIMIT 1", Long.class, title);
  }

  private Long seedComment(
      Long postId, Long writerId, String content, Long parentId, boolean isDeleted) {
    jdbcTemplate.update(
        "INSERT INTO comments (target_type, post_id, writer_id, content, is_deleted, parent_id, "
            + "created_at, updated_at) VALUES ('POST', ?, ?, ?, ?, ?, NOW(), NOW())",
        postId,
        writerId,
        content,
        isDeleted,
        parentId);
    return jdbcTemplate.queryForObject(
        "SELECT id FROM comments WHERE content = ? ORDER BY id DESC LIMIT 1", Long.class, content);
  }

  private Long userIdByEmail(String email) {
    return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
  }

  private String userRole(Long userId) {
    return jdbcTemplate.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
  }

  private String accountStatus(Long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM users_account WHERE user_id = ?", String.class, userId);
  }

  private String postStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM posts WHERE id = ?", String.class, postId);
  }

  private String postPublicationStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT publication_status FROM posts WHERE id = ?", String.class, postId);
  }

  private String postModerationStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "SELECT moderation_status FROM posts WHERE id = ?", String.class, postId);
  }

  private void markPostPublicationStatus(Long postId, String publicationStatus) {
    jdbcTemplate.update(
        "UPDATE posts SET publication_status = ?, updated_at = NOW() WHERE id = ?",
        publicationStatus,
        postId);
  }

  private boolean isCommentDeleted(Long commentId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM comments WHERE id = ?", Boolean.class, commentId));
  }

  private long moderationActionCount(String targetType, Long targetId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM admin_board_moderation_actions "
            + "WHERE target_type = ? AND target_id = ?",
        Long.class,
        targetType,
        targetId);
  }

  private long moderationActionTypeCount(String targetType) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM admin_board_moderation_actions WHERE target_type = ?",
        Long.class,
        targetType);
  }

  private long adminActionAuditCount(String actionType, String targetId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM admin_action_audits WHERE action_type = ? AND target_id = ?",
        Long.class,
        actionType,
        targetId);
  }

  private static String uniqueToken() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private record TestAdmin(Long userId, String accessToken) {}

  private record TestAppUser(Long userId, String email, String nickname) {}

  private record TestLogin(String accessToken, String refreshToken) {}
}
