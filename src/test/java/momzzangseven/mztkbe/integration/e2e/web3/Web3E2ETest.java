package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.out.Eip7702ChainPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

/**
 * Web3 Challenge & Wallet E2E 테스트.
 *
 * <p>테스트 시나리오:
 *
 * <ul>
 *   <li>챌린지 발급 (EIP-4361 메시지 생성)
 *   <li>EIP-712 소유권 서명 생성 후 wallet registration session + EIP-7702 approval intent 생성
 *   <li>지갑 등록 상태 조회
 *   <li>잘못된 서명/nonce 로 지갑 등록 시 에러
 *   <li>인증 없이 접근 시 401 반환
 * </ul>
 *
 * <p>테스트용 개인키는 Hardhat 기본 계정 키를 사용합니다 (실제 자산 없음).
 */
@TestPropertySource(
    properties = {
      "web3.eip7702.enabled=true",
      "web3.eip7702.sponsor.enabled=true",
      "web3.eip7702.sponsor.per-tx-cap-eth=0.1",
      "web3.eip7702.sponsor.per-day-user-cap-eth=1.0",
      "web3.wallet.registration.approval.enabled=true"
    })
@DisplayName("[E2E] Web3 Challenge & Wallet 전체 흐름 테스트")
class Web3E2ETest extends E2ETestBase {

  /** 테스트 전용 Ethereum 개인키 (Hardhat 첫 번째 계정, 실제 자산 없음). */
  private static final String TEST_PRIVATE_KEY =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"; // gitleaks:allow

  private static final String TEST_WALLET_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private Eip7702ChainPort eip7702ChainPort;

  private String accessToken;

  @BeforeEach
  void setUp() {
    accessToken = signupAndLogin("Web3E2E유저").accessToken();
    given(eip7702ChainPort.loadPendingAccountNonce(anyString())).willReturn(BigInteger.ZERO);
  }

  // ============================================================
  // Helper Methods
  // ============================================================

  /**
   * 챌린지를 발급하고 [nonce, message] 를 반환합니다.
   *
   * @return {@code [nonce, message]}
   */
  private String[] createChallenge(String walletAddress) throws Exception {
    Map<String, Object> body =
        Map.of("purpose", "WALLET_REGISTRATION", "walletAddress", walletAddress);
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/challenges",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("챌린지 발급이 2xx여야 함: " + response.getBody())
        .isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    return new String[] {root.at("/data/nonce").asText(), root.at("/data/message").asText()};
  }

  /**
   * 서버의 {@code EIP712SignatureVerifier} 와 동일한 EIP-712 구조로 서명을 생성합니다.
   *
   * @param message challenge message
   * @param nonce challenge nonce
   * @param privateKey 서명에 사용할 Ethereum 개인키
   * @return 0x-prefixed 서명 hex 문자열 (130 hex chars)
   */
  private String signEip712(String message, String nonce, String privateKey) throws Exception {
    Credentials credentials = Credentials.create(privateKey);

    Map<String, Object> eip712Json = new LinkedHashMap<>();
    Map<String, List<Map<String, String>>> types = new LinkedHashMap<>();
    types.put(
        "EIP712Domain",
        List.of(
            Map.of("name", "name", "type", "string"),
            Map.of("name", "version", "type", "string"),
            Map.of("name", "chainId", "type", "uint256"),
            Map.of("name", "verifyingContract", "type", "address")));
    types.put(
        "AuthRequest",
        List.of(
            Map.of("name", "content", "type", "string"),
            Map.of("name", "nonce", "type", "string")));
    eip712Json.put("types", types);
    eip712Json.put("primaryType", "AuthRequest");

    Map<String, Object> domain = new LinkedHashMap<>();
    domain.put("name", "MomzzangSeven");
    domain.put("version", "1");
    domain.put("chainId", 1337);
    domain.put("verifyingContract", "0x0000000000000000000000000000000000000000");
    eip712Json.put("domain", domain);

    Map<String, String> msgData = new LinkedHashMap<>();
    msgData.put("content", message);
    msgData.put("nonce", nonce);
    eip712Json.put("message", msgData);

    String jsonString = objectMapper.writeValueAsString(eip712Json);
    StructuredDataEncoder encoder = new StructuredDataEncoder(jsonString);
    byte[] digest = encoder.hashStructuredData();

    Sign.SignatureData sig = Sign.signMessage(digest, credentials.getEcKeyPair(), false);

    byte[] signatureBytes = new byte[65];
    System.arraycopy(sig.getR(), 0, signatureBytes, 0, 32);
    System.arraycopy(sig.getS(), 0, signatureBytes, 32, 32);
    signatureBytes[64] = sig.getV()[0];

    return "0x" + Numeric.toHexStringNoPrefix(signatureBytes);
  }

  private ResponseEntity<String> registerWallet(String walletAddress) throws Exception {
    String[] challengeData = createChallenge(walletAddress);
    String signature = signEip712(challengeData[1], challengeData[0], TEST_PRIVATE_KEY);
    Map<String, Object> registerBody =
        Map.of("walletAddress", walletAddress, "signature", signature, "nonce", challengeData[0]);

    return restTemplate.exchange(
        baseUrl() + "/web3/wallets",
        HttpMethod.POST,
        new HttpEntity<>(registerBody, bearerJsonHeaders(accessToken)),
        String.class);
  }

  // ============================================================
  // E2E Tests — Challenge
  // ============================================================

  @Test
  @DisplayName("챌린지 발급 → nonce, message, expiresIn 반환")
  void createChallenge_success_returnsNonceAndMessage() throws Exception {
    Map<String, Object> body =
        Map.of("purpose", "WALLET_REGISTRATION", "walletAddress", TEST_WALLET_ADDRESS);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/challenges",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/nonce").asText()).isNotBlank();
    assertThat(root.at("/data/message").asText()).isNotBlank();
    assertThat(root.at("/data/expiresIn").asInt()).isPositive();
  }

  @Test
  @DisplayName("잘못된 purpose 값으로 챌린지 발급 시 400 반환")
  void createChallenge_invalidPurpose_returns400() {
    Map<String, Object> body =
        Map.of("purpose", "INVALID_PURPOSE", "walletAddress", TEST_WALLET_ADDRESS);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/challenges",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("인증 없이 챌린지 발급 시 401 반환")
  void createChallenge_withoutAuth_returns401() {
    Map<String, Object> body =
        Map.of("purpose", "WALLET_REGISTRATION", "walletAddress", TEST_WALLET_ADDRESS);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/challenges",
            HttpMethod.POST,
            new HttpEntity<>(body, jsonOnlyHeaders()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // ============================================================
  // E2E Tests — Wallet Registration
  // ============================================================

  @Test
  @DisplayName("EIP-712 서명 검증 후 지갑 등록 세션과 EIP-7702 approval signRequest 를 반환한다")
  void registerWallet_withValidSignature_returnsApprovalIntent() throws Exception {
    ResponseEntity<String> response = registerWallet(TEST_WALLET_ADDRESS);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    JsonNode data = root.path("data");
    String registrationId = data.path("registrationId").asText();
    String executionIntentId = data.path("web3").path("executionIntent").path("id").asText();

    assertThat(registrationId).isNotBlank();
    assertThat(data.path("status").asText()).isEqualTo("APPROVAL_REQUIRED");
    assertThat(root.at("/data/walletAddress").asText()).isEqualToIgnoringCase(TEST_WALLET_ADDRESS);
    assertThat(data.path("walletId").isNull()).isTrue();
    assertThat(data.path("registeredAt").isNull()).isTrue();
    assertThat(data.path("nextAction").asText()).isEqualTo("SIGN_APPROVAL");
    assertThat(data.path("web3").path("resource").path("type").asText())
        .isEqualTo("WALLET_REGISTRATION");
    assertThat(data.path("web3").path("resource").path("id").asText()).isEqualTo(registrationId);
    assertThat(data.path("web3").path("actionType").asText()).isEqualTo("WALLET_ESCROW_APPROVE");
    assertThat(data.path("web3").path("executionIntent").path("status").asText())
        .isEqualTo("AWAITING_SIGNATURE");
    assertThat(data.path("web3").path("executionIntent").path("expiresAtEpochSeconds").asLong())
        .isPositive();
    assertThat(data.path("web3").path("execution").path("mode").asText()).isEqualTo("EIP7702");
    assertThat(data.path("web3").path("execution").path("signCount").asInt()).isEqualTo(2);
    assertThat(data.path("web3").path("signRequest").path("authorization").isMissingNode())
        .isFalse();
    assertThat(data.path("web3").path("signRequest").path("submit").isMissingNode()).isFalse();
    assertThat(data.path("web3").path("existing").asBoolean()).isFalse();

    String sessionStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_wallet_registration_sessions WHERE public_id = ?",
            String.class,
            registrationId);
    assertThat(sessionStatus).isEqualTo("APPROVAL_REQUIRED");

    String intentStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM web3_execution_intents WHERE public_id = ?",
            String.class,
            executionIntentId);
    assertThat(intentStatus).isEqualTo("AWAITING_SIGNATURE");
  }

  @Test
  @DisplayName("approval 확정 전 pending registration 은 ACTIVE wallet 으로 연결 해제할 수 없다")
  void unlinkWallet_beforeApprovalFinalization_returnsWalletNotFound() throws Exception {
    ResponseEntity<String> registerResponse = registerWallet(TEST_WALLET_ADDRESS);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    ResponseEntity<String> unlinkResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets/" + TEST_WALLET_ADDRESS,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(unlinkResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode root = objectMapper.readTree(unlinkResponse.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("FAIL");
    assertThat(root.at("/code").asText()).isEqualTo("WALLET_004");
  }

  @Test
  @DisplayName("잘못된 서명으로 지갑 등록 시 4xx 에러 반환")
  void registerWallet_withInvalidSignature_returnsError() throws Exception {
    String[] challengeData = createChallenge(TEST_WALLET_ADDRESS);
    String nonce = challengeData[0];

    String invalidSignature = "0x" + "a".repeat(130);

    Map<String, Object> body =
        Map.of(
            "walletAddress", TEST_WALLET_ADDRESS,
            "signature", invalidSignature,
            "nonce", nonce);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError()).as("잘못된 서명으로 등록 시 4xx 에러여야 함").isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 nonce로 지갑 등록 시 4xx 에러 반환")
  void registerWallet_withNonExistentNonce_returnsError() {
    String fakeNonce = UUID.randomUUID().toString();
    String fakeSignature = "0x" + "b".repeat(130);

    Map<String, Object> body =
        Map.of(
            "walletAddress", TEST_WALLET_ADDRESS,
            "signature", fakeSignature,
            "nonce", fakeNonce);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode().is4xxClientError())
        .as("존재하지 않는 nonce로 등록 시 4xx 에러여야 함")
        .isTrue();
  }

  @Test
  @DisplayName("인증 없이 지갑 등록 시 401 반환")
  void registerWallet_withoutAuth_returns401() {
    HttpHeaders noAuthHeaders = jsonOnlyHeaders();
    Map<String, Object> body =
        Map.of(
            "walletAddress",
            TEST_WALLET_ADDRESS,
            "signature",
            "0x" + "c".repeat(130),
            "nonce",
            "test-nonce");

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(body, noAuthHeaders),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("동일 유저와 동일 지갑의 pending registration 재등록은 기존 approval intent 를 재사용한다")
  void registerWallet_samePendingRegistration_reusesExistingSession() throws Exception {
    ResponseEntity<String> firstResponse = registerWallet(TEST_WALLET_ADDRESS);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode firstData = objectMapper.readTree(firstResponse.getBody()).path("data");
    String registrationId = firstData.path("registrationId").asText();
    String executionIntentId = firstData.path("web3").path("executionIntent").path("id").asText();

    ResponseEntity<String> secondResponse = registerWallet(TEST_WALLET_ADDRESS);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode secondData = objectMapper.readTree(secondResponse.getBody()).path("data");
    assertThat(secondData.path("registrationId").asText()).isEqualTo(registrationId);
    assertThat(secondData.path("status").asText()).isEqualTo("APPROVAL_REQUIRED");
    assertThat(secondData.path("nextAction").asText()).isEqualTo("SIGN_APPROVAL");
    assertThat(secondData.path("web3").path("executionIntent").path("id").asText())
        .isEqualTo(executionIntentId);
    assertThat(secondData.path("web3").path("existing").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("지갑 등록 상태 조회는 소유자에게 approval 진행 상태와 signRequest 를 반환한다")
  void getWalletRegistrationStatus_withOwner_returnsApprovalStatus() throws Exception {
    ResponseEntity<String> registerResponse = registerWallet(TEST_WALLET_ADDRESS);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String registrationId =
        objectMapper
            .readTree(registerResponse.getBody())
            .path("data")
            .path("registrationId")
            .asText();

    ResponseEntity<String> statusResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallet-registrations/" + registrationId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode data = objectMapper.readTree(statusResponse.getBody()).path("data");
    assertThat(data.path("registrationId").asText()).isEqualTo(registrationId);
    assertThat(data.path("status").asText()).isEqualTo("APPROVAL_REQUIRED");
    assertThat(data.path("latestExecutionStatus").asText()).isEqualTo("AWAITING_SIGNATURE");
    assertThat(data.path("nextAction").asText()).isEqualTo("SIGN_APPROVAL");
    assertThat(data.path("web3").path("signRequest").path("authorization").isMissingNode())
        .isFalse();
  }

  @Test
  @DisplayName("다른 유저가 지갑 등록 상태를 조회하면 404 를 반환한다")
  void getWalletRegistrationStatus_withOtherUser_returns404() throws Exception {
    ResponseEntity<String> registerResponse = registerWallet(TEST_WALLET_ADDRESS);
    assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String registrationId =
        objectMapper
            .readTree(registerResponse.getBody())
            .path("data")
            .path("registrationId")
            .asText();
    String otherAccessToken = signupAndLogin("Web3E2EOther유저").accessToken();

    ResponseEntity<String> statusResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallet-registrations/" + registrationId,
            HttpMethod.GET,
            new HttpEntity<>(bearerJsonHeaders(otherAccessToken)),
            String.class);

    assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode root = objectMapper.readTree(statusResponse.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("FAIL");
    assertThat(root.at("/code").asText()).isEqualTo("WALLET_004");
  }
}
