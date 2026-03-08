package momzzangseven.mztkbe.modules.web3.challenge.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Challenge domain model
 *
 * <p>Tests the static factory method and domain logic without external dependencies.
 */
@DisplayName("Challenge Domain Test")
class ChallengeTest {

  private static final Long VALID_USER_ID = 1L;
  private static final ChallengePurpose VALID_PURPOSE = ChallengePurpose.WALLET_REGISTRATION;
  private static final String VALID_WALLET_ADDRESS = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
  private static final int VALID_TTL = 300;
  private static final String VALID_DOMAIN = "example.com";
  private static final String VALID_URI = "https://example.com";
  private static final String VALID_VERSION = "1";
  private static final String VALID_CHAIN_ID = "1";

  private ChallengeConfig createValidConfig() {
    return new ChallengeConfig(VALID_TTL, VALID_DOMAIN, VALID_URI, VALID_VERSION, VALID_CHAIN_ID);
  }

  // ========================================
  // Success Cases
  // ========================================

  @Test
  @DisplayName("정상적인 Challenge 생성")
  void create_ValidInputs_CreatesChallenge() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    assertThat(challenge).isNotNull();
    assertThat(challenge.getUserId()).isEqualTo(VALID_USER_ID);
    assertThat(challenge.getPurpose()).isEqualTo(VALID_PURPOSE);
    assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.PENDING);
    assertThat(challenge.getCreatedAt()).isNotNull();
    assertThat(challenge.getUsedAt()).isNull();
  }

  @Test
  @DisplayName("Nonce가 생성되고 null이 아님")
  void create_GeneratesNonce() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    assertThat(challenge.getNonce()).isNotNull();
    assertThat(challenge.getNonce()).isNotEmpty();
    // UUID 형식 확인 (대략적)
    assertThat(challenge.getNonce())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  @DisplayName("WalletAddress가 소문자로 정규화됨")
  void create_NormalizesAddressToLowercase() {
    // Given
    String mixedCaseAddress = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge = Challenge.create(VALID_USER_ID, VALID_PURPOSE, mixedCaseAddress, config);

    // Then
    assertThat(challenge.getWalletAddress()).isEqualTo(mixedCaseAddress.toLowerCase());
    assertThat(challenge.getWalletAddress())
        .isEqualTo("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed");
  }

  @Test
  @DisplayName("Message가 EIP-4361 형식을 따름")
  void create_GeneratesEIP4361Message() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    String message = challenge.getMessage();
    assertThat(message).isNotNull();
    assertThat(message).contains(VALID_DOMAIN); // domain
    assertThat(message).contains("register your wallet"); // action
    assertThat(message).contains(VALID_WALLET_ADDRESS); // address
    assertThat(message).contains("URI: " + VALID_URI); // URI
    assertThat(message).contains("Version: " + VALID_VERSION); // version
    assertThat(message).contains("Chain ID: " + VALID_CHAIN_ID); // chainId
    assertThat(message).contains("Nonce:"); // nonce
    assertThat(message).contains("Issued At:"); // issuedAt
  }

  @Test
  @DisplayName("ExpiresAt이 현재시간 + TTL로 설정됨")
  void create_SetsCorrectExpirationTime() {
    // Given
    ChallengeConfig config = createValidConfig();
    Instant beforeCreation = Instant.now();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    Instant afterCreation = Instant.now();
    Instant expectedExpiration = beforeCreation.plusSeconds(VALID_TTL);

    assertThat(challenge.getExpiresAt()).isAfter(expectedExpiration.minusSeconds(1));
    assertThat(challenge.getExpiresAt())
        .isBefore(afterCreation.plusSeconds(VALID_TTL).plusSeconds(1));
  }

  @Test
  @DisplayName("Status가 PENDING으로 초기화됨")
  void create_SetsStatusToPending() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    assertThat(challenge.getStatus()).isEqualTo(ChallengeStatus.PENDING);
  }

  @Test
  @DisplayName("CreatedAt이 현재 시간으로 설정됨")
  void create_SetsCreatedAtToNow() {
    // Given
    ChallengeConfig config = createValidConfig();
    Instant before = Instant.now();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    Instant after = Instant.now();
    assertThat(challenge.getCreatedAt()).isAfter(before.minusSeconds(1));
    assertThat(challenge.getCreatedAt()).isBefore(after.plusSeconds(1));
  }

  @Test
  @DisplayName("UsedAt이 null로 초기화됨")
  void create_SetsUsedAtToNull() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    assertThat(challenge.getUsedAt()).isNull();
  }

  @Test
  @DisplayName("각 생성마다 고유한 Nonce 생성")
  void create_GeneratesUniqueNonce() {
    // Given
    ChallengeConfig config = createValidConfig();

    // When
    Challenge challenge1 =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);
    Challenge challenge2 =
        Challenge.create(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS, config);

    // Then
    assertThat(challenge1.getNonce()).isNotEqualTo(challenge2.getNonce());
  }
}
