package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *   <li>EIP-712 서명 생성 후 지갑 등록 전체 흐름
 *   <li>지갑 연결 해제
 *   <li>잘못된 서명/nonce 로 지갑 등록 시 에러
 *   <li>인증 없이 접근 시 401 반환
 * </ul>
 *
 * <p>테스트용 개인키는 Hardhat 기본 계정 키를 사용합니다 (실제 자산 없음).
 */
@DisplayName("[E2E] Web3 Challenge & Wallet 전체 흐름 테스트")
class Web3E2ETest extends E2ETestBase {

  /** 테스트 전용 Ethereum 개인키 (Hardhat 첫 번째 계정, 실제 자산 없음). */
  private static final String TEST_PRIVATE_KEY =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"; // gitleaks:allow

  private static final String TEST_WALLET_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private String accessToken;

  @BeforeEach
  void setUp() {
    accessToken = signupAndLogin("Web3E2E유저").accessToken();
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
  @DisplayName("EIP-712 서명 생성 후 지갑 등록 전체 흐름 → 201 및 walletAddress 반환")
  void registerWallet_withValidSignature_returns201() throws Exception {
    String[] challengeData = createChallenge(TEST_WALLET_ADDRESS);
    String nonce = challengeData[0];
    String message = challengeData[1];

    String signature = signEip712(message, nonce, TEST_PRIVATE_KEY);

    Map<String, Object> registerBody =
        Map.of("walletAddress", TEST_WALLET_ADDRESS, "signature", signature, "nonce", nonce);

    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(registerBody, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode root = objectMapper.readTree(response.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(root.at("/data/id").asLong()).isPositive();
    assertThat(root.at("/data/walletAddress").asText()).isEqualToIgnoringCase(TEST_WALLET_ADDRESS);
  }

  @Test
  @DisplayName("지갑 등록 후 연결 해제 → 200 응답")
  void unlinkWallet_afterRegister_returns200() throws Exception {
    String[] challengeData = createChallenge(TEST_WALLET_ADDRESS);
    String nonce = challengeData[0];
    String message = challengeData[1];
    String signature = signEip712(message, nonce, TEST_PRIVATE_KEY);

    Map<String, Object> registerBody =
        Map.of("walletAddress", TEST_WALLET_ADDRESS, "signature", signature, "nonce", nonce);
    restTemplate.exchange(
        baseUrl() + "/web3/wallets",
        HttpMethod.POST,
        new HttpEntity<>(registerBody, bearerJsonHeaders(accessToken)),
        String.class);

    ResponseEntity<String> unlinkResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets/" + TEST_WALLET_ADDRESS,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(unlinkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode root = objectMapper.readTree(unlinkResponse.getBody());
    assertThat(root.at("/status").asText()).isEqualTo("SUCCESS");
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
  @DisplayName("이미 등록된 지갑 주소로 재등록 시도 → 4xx 에러 반환")
  void registerWallet_alreadyLinked_returnsError() throws Exception {
    String[] challengeData1 = createChallenge(TEST_WALLET_ADDRESS);
    String signature1 = signEip712(challengeData1[1], challengeData1[0], TEST_PRIVATE_KEY);

    Map<String, Object> firstRegister =
        Map.of(
            "walletAddress", TEST_WALLET_ADDRESS,
            "signature", signature1,
            "nonce", challengeData1[0]);
    ResponseEntity<String> firstResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(firstRegister, bearerJsonHeaders(accessToken)),
            String.class);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    String[] challengeData2 = createChallenge(TEST_WALLET_ADDRESS);
    String signature2 = signEip712(challengeData2[1], challengeData2[0], TEST_PRIVATE_KEY);

    Map<String, Object> secondRegister =
        Map.of(
            "walletAddress", TEST_WALLET_ADDRESS,
            "signature", signature2,
            "nonce", challengeData2[0]);
    ResponseEntity<String> secondResponse =
        restTemplate.exchange(
            baseUrl() + "/web3/wallets",
            HttpMethod.POST,
            new HttpEntity<>(secondRegister, bearerJsonHeaders(accessToken)),
            String.class);

    assertThat(secondResponse.getStatusCode().is4xxClientError())
        .as("이미 지갑이 연결된 유저의 재등록 시도 시 4xx 에러여야 함")
        .isTrue();
  }
}
