package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraft;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.BuildQnaAdminExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.config.QnaAdminExecutionConfigurationValidator;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.reward-token.enabled=true",
      "web3.eip7702.enabled=false",
      "web3.execution.internal-issuer.enabled=true"
    })
@Tag("e2e")
@DisplayName("[E2E] QnA admin manual settle/refund flow")
class QnaAdminEscrowE2ETest extends E2ETestBase {

  private static final String SIGNER_ADDRESS = "0xd799cd2b5258edc2157bec7e2cd069f31f2678c2";
  private static final String CALL_TARGET = "0x0000000000000000000000000000000000000002";
  private static final String TOKEN_ADDRESS = "0x1111111111111111111111111111111111111111";
  private static final BigInteger REWARD_AMOUNT_WEI = new BigInteger("50000000000000000000");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;

  @MockitoBean
  private QnaAdminExecutionConfigurationValidator qnaAdminExecutionConfigurationValidator;

  @MockitoBean private LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;
  @MockitoBean private LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  @MockitoBean private QnaContractCallSupport qnaContractCallSupport;
  @MockitoBean private BuildQnaAdminExecutionDraftPort buildQnaAdminExecutionDraftPort;

  @BeforeEach
  void setUp() {
    BDDMockito.given(loadExecutionInternalIssuerPolicyPort.loadPolicy())
        .willReturn(
            new LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy(
                true, true, true));
    BDDMockito.given(loadQnaAdminSignerAddressPort.loadSignerAddress()).willReturn(SIGNER_ADDRESS);
    BDDMockito.given(qnaContractCallSupport.isRelayerRegistered(anyString(), anyString()))
        .willReturn(true);
    BDDMockito.given(buildQnaAdminExecutionDraftPort.build(any()))
        .willAnswer(invocation -> adminDraft(invocation.getArgument(0)));
  }

  @Test
  @DisplayName("admin settlement review/settle가 processable review와 direct EIP-1559 intent를 만든다")
  void adminSettlementFlow_createsExecutionIntentAndMarksPendingAccept() throws Exception {
    AdminUser admin = createAdminAndLogin();
    TestUser asker = signupAndLogin("manual-settle-asker");
    TestUser responder = signupAndLogin("manual-settle-responder");
    SeededSettlementScenario scenario =
        seedSettlementScenario(
            asker.userId(), responder.userId(), "manual settle question", "manual settle answer");

    ResponseEntity<String> reviewResponse =
        restTemplate.exchange(
            baseUrl()
                + "/admin/web3/qna/questions/"
                + scenario.postId()
                + "/answers/"
                + scenario.answerId()
                + "/settlement-review",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode reviewData = objectMapper.readTree(reviewResponse.getBody()).path("data");
    assertThat(reviewData.path("processable").asBoolean()).isTrue();
    assertThat(reviewData.path("questionConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("answerConflictingActiveIntent").asBoolean()).isFalse();

    ResponseEntity<String> settleResponse =
        restTemplate.exchange(
            baseUrl()
                + "/admin/web3/qna/questions/"
                + scenario.postId()
                + "/answers/"
                + scenario.answerId()
                + "/settle",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(settleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode settleData = objectMapper.readTree(settleResponse.getBody()).path("data");
    assertThat(settleData.path("actionType").asText()).isEqualTo("QNA_ADMIN_SETTLE");
    assertThat(settleData.path("execution").path("mode").asText()).isEqualTo("EIP1559");
    assertThat(settleData.path("execution").path("requiresUserSignature").asBoolean()).isFalse();
    assertThat(settleData.path("execution").path("authorityModel").asText())
        .isEqualTo("SERVER_RELAYER_ONLY");
    assertThat(settleData.path("signRequest").isMissingNode()).isTrue();

    Map<String, Object> latestIntent = latestExecutionIntent(scenario.postId());
    assertThat(latestIntent.get("action_type")).isEqualTo("QNA_ADMIN_SETTLE");
    assertThat(latestIntent.get("status")).isEqualTo("AWAITING_SIGNATURE");
    assertThat(postStatus(scenario.postId())).isEqualTo("PENDING_ACCEPT");
    assertThat(countAdminAudit("QNA_ADMIN_SETTLE", "post:" + scenario.postId(), admin.userId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("admin refund review/refund가 processable review와 direct EIP-1559 intent를 만든다")
  void adminRefundFlow_createsExecutionIntent() throws Exception {
    AdminUser admin = createAdminAndLogin();
    TestUser asker = signupAndLogin("manual-refund-asker");
    Long postId = seedRefundScenario(asker.userId(), "manual refund question");

    ResponseEntity<String> reviewResponse =
        restTemplate.exchange(
            baseUrl() + "/admin/web3/qna/questions/" + postId + "/refund-review",
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(reviewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode reviewData = objectMapper.readTree(reviewResponse.getBody()).path("data");
    assertThat(reviewData.path("processable").asBoolean()).isTrue();
    assertThat(reviewData.path("questionConflictingActiveIntent").asBoolean()).isFalse();
    assertThat(reviewData.path("answerConflictingActiveIntent").asBoolean()).isFalse();

    ResponseEntity<String> refundResponse =
        restTemplate.exchange(
            baseUrl() + "/admin/web3/qna/questions/" + postId + "/refund",
            HttpMethod.POST,
            new HttpEntity<>(bearerJsonHeaders(admin.accessToken())),
            String.class);

    assertThat(refundResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode refundData = objectMapper.readTree(refundResponse.getBody()).path("data");
    assertThat(refundData.path("actionType").asText()).isEqualTo("QNA_ADMIN_REFUND");
    assertThat(refundData.path("execution").path("mode").asText()).isEqualTo("EIP1559");
    assertThat(refundData.path("execution").path("requiresUserSignature").asBoolean()).isFalse();
    assertThat(refundData.path("execution").path("authorityModel").asText())
        .isEqualTo("SERVER_RELAYER_ONLY");
    assertThat(refundData.path("signRequest").isMissingNode()).isTrue();

    Map<String, Object> latestIntent = latestExecutionIntent(postId);
    assertThat(latestIntent.get("action_type")).isEqualTo("QNA_ADMIN_REFUND");
    assertThat(latestIntent.get("status")).isEqualTo("AWAITING_SIGNATURE");
    assertThat(postStatus(postId)).isEqualTo("OPEN");
    assertThat(countAdminAudit("QNA_ADMIN_REFUND", "post:" + postId, admin.userId())).isEqualTo(1);
  }

  private AdminUser createAdminAndLogin() {
    String email = randomEmail();
    Long userId = signupUser(email, DEFAULT_TEST_PASSWORD, "QnaAdminE2E");
    jdbcTemplate.update("UPDATE users SET role = 'ADMIN_GENERATED' WHERE id = ?", userId);

    String loginId = String.valueOf(10000000 + (int) (Math.random() * 90000000));
    jdbcTemplate.update(
        "INSERT INTO admin_accounts (user_id, login_id, password_hash, created_by,"
            + " last_login_at, password_last_rotated_at, deleted_at, created_at, updated_at)"
            + " VALUES (?, ?, ?, NULL, NULL, NULL, NULL, NOW(), NOW())",
        userId,
        loginId,
        passwordEncoder.encode(DEFAULT_TEST_PASSWORD));

    ResponseEntity<String> loginResponse =
        restTemplate.exchange(
            baseUrl() + "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "provider",
                    "LOCAL_ADMIN",
                    "loginId",
                    loginId,
                    "password",
                    DEFAULT_TEST_PASSWORD),
                jsonOnlyHeaders()),
            String.class);

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    try {
      String accessToken =
          objectMapper.readTree(loginResponse.getBody()).at("/data/accessToken").asText();
      return new AdminUser(userId, accessToken);
    } catch (Exception e) {
      throw new IllegalStateException(
          "failed to parse admin login response: " + loginResponse.getBody(), e);
    }
  }

  private SeededSettlementScenario seedSettlementScenario(
      Long askerUserId, Long responderUserId, String questionContent, String answerContent) {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(2);
    Long postId = insertQuestionPost(askerUserId, "admin settle title", questionContent, createdAt);
    Long answerId = insertAnswer(postId, responderUserId, answerContent, createdAt.plusMinutes(5));
    insertQuestionProjection(
        postId, askerUserId, questionContent, 1, QnaQuestionState.ANSWERED, createdAt);
    insertAnswerProjection(
        postId, answerId, responderUserId, answerContent, createdAt.plusMinutes(5));
    return new SeededSettlementScenario(postId, answerId);
  }

  private Long seedRefundScenario(Long askerUserId, String questionContent) {
    LocalDateTime createdAt = LocalDateTime.now().minusHours(1);
    Long postId = insertQuestionPost(askerUserId, "admin refund title", questionContent, createdAt);
    insertQuestionProjection(
        postId, askerUserId, questionContent, 0, QnaQuestionState.CREATED, createdAt);
    return postId;
  }

  private Long insertQuestionPost(
      Long userId, String title, String content, LocalDateTime createdAt) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "insert into posts (user_id, type, title, content, reward, status, created_at, updated_at)"
                      + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, "QUESTION");
          ps.setString(3, title);
          ps.setString(4, content);
          ps.setLong(5, 50L);
          ps.setString(6, "OPEN");
          ps.setTimestamp(7, Timestamp.valueOf(createdAt));
          ps.setTimestamp(8, Timestamp.valueOf(createdAt));
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private Long insertAnswer(Long postId, Long userId, String content, LocalDateTime createdAt) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "insert into answers (post_id, user_id, content, is_accepted, created_at, updated_at)"
                      + " values (?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, postId);
          ps.setLong(2, userId);
          ps.setString(3, content);
          ps.setBoolean(4, false);
          ps.setTimestamp(5, Timestamp.valueOf(createdAt));
          ps.setTimestamp(6, Timestamp.valueOf(createdAt));
          return ps;
        },
        keyHolder);
    return keyHolder.getKey().longValue();
  }

  private void insertQuestionProjection(
      Long postId,
      Long askerUserId,
      String questionContent,
      int answerCount,
      QnaQuestionState state,
      LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_qna_questions (post_id, question_id, asker_user_id, token_address,"
            + " reward_amount_wei, question_hash, accepted_answer_id, answer_count, state, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        QnaEscrowIdCodec.questionId(postId),
        askerUserId,
        TOKEN_ADDRESS,
        REWARD_AMOUNT_WEI,
        QnaContentHashFactory.hash(questionContent),
        QnaEscrowIdCodec.zeroBytes32(),
        answerCount,
        state.code(),
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private void insertAnswerProjection(
      Long postId,
      Long answerId,
      Long responderUserId,
      String answerContent,
      LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_qna_answers (answer_id, post_id, question_id, answer_key, responder_user_id,"
            + " content_hash, accepted, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        answerId,
        postId,
        QnaEscrowIdCodec.questionId(postId),
        QnaEscrowIdCodec.answerId(answerId),
        responderUserId,
        QnaContentHashFactory.hash(answerContent),
        false,
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private QnaExecutionDraft adminDraft(QnaEscrowExecutionRequest request) throws Exception {
    String callData =
        request.actionType() == QnaExecutionActionType.QNA_ADMIN_SETTLE
            ? "0x1234abcd"
            : "0xabcd1234";
    QnaEscrowExecutionPayload payload =
        new QnaEscrowExecutionPayload(
            request.actionType(),
            request.postId(),
            request.answerId(),
            null,
            request.tokenAddress(),
            request.rewardAmountWei(),
            request.questionHash(),
            request.contentHash(),
            CALL_TARGET,
            callData);
    return new QnaExecutionDraft(
        request.resourceType(),
        request.resourceId(),
        QnaExecutionResourceStatus.PENDING_EXECUTION,
        request.actionType(),
        request.requesterUserId(),
        request.counterpartyUserId(),
        "root-admin-"
            + request.actionType().name()
            + "-"
            + request.postId()
            + "-"
            + UUID.randomUUID(),
        "0x" + "a".repeat(64),
        objectMapper.writeValueAsString(payload),
        List.of(new QnaExecutionDraftCall(CALL_TARGET, BigInteger.ZERO, callData)),
        false,
        null,
        null,
        null,
        null,
        new QnaUnsignedTxSnapshot(
            1337L,
            SIGNER_ADDRESS,
            CALL_TARGET,
            BigInteger.ZERO,
            callData,
            21L,
            BigInteger.valueOf(210_000),
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L)),
        "0x" + "b".repeat(64),
        LocalDateTime.now().plusMinutes(5));
  }

  private Map<String, Object> latestExecutionIntent(Long postId) {
    return jdbcTemplate.queryForMap(
        "select action_type, status, public_id from web3_execution_intents"
            + " where resource_id = ? order by created_at desc limit 1",
        postId.toString());
  }

  private String postStatus(Long postId) {
    return jdbcTemplate.queryForObject(
        "select status from posts where id = ?", String.class, postId);
  }

  private int countAdminAudit(String actionType, String targetId, Long operatorId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from admin_action_audits where action_type = ? and target_id = ? and operator_id = ?",
            Integer.class,
            actionType,
            targetId,
            operatorId);
    return count == null ? 0 : count;
  }

  private record AdminUser(Long userId, String accessToken) {}

  private record SeededSettlementScenario(Long postId, Long answerId) {}
}
