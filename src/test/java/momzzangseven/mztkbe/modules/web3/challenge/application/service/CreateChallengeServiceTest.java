package momzzangseven.mztkbe.modules.web3.challenge.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.CreateChallengeResult;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.LoadChallengeConfigPort;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.out.SaveChallengePort;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CreateChallengeService
 *
 * <p>Uses Mockito to isolate service logic from dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateChallengeService Unit Test")
class CreateChallengeServiceTest {

  @Mock private SaveChallengePort saveChallengePort;

  @Mock private LoadChallengeConfigPort loadChallengeConfigPort;

  @InjectMocks private CreateChallengeService createChallengeService;

  private static final Long VALID_USER_ID = 1L;
  private static final ChallengePurpose VALID_PURPOSE = ChallengePurpose.WALLET_REGISTRATION;
  private static final String VALID_WALLET_ADDRESS = "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed";
  private static final int VALID_TTL = 300;

  private ChallengeConfig mockConfig;

  @BeforeEach
  void setUp() {
    mockConfig = new ChallengeConfig(VALID_TTL, "example.com", "https://example.com", "1", "1");
  }

  // ========================================
  // Success Cases
  // ========================================

  @Nested
  @DisplayName("Success Cases")
  class SuccessCases {

    @Test
    @DisplayName("정상적인 Challenge 생성 성공")
    void execute_ValidCommand_ReturnsResult() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      when(loadChallengeConfigPort.loadConfig()).thenReturn(mockConfig);
      when(saveChallengePort.save(any(Challenge.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      CreateChallengeResult result = createChallengeService.execute(command);

      // Then
      assertThat(result).isNotNull();
      assertThat(result.nonce()).isNotNull();
      assertThat(result.message()).isNotNull();
      assertThat(result.expiresIn()).isEqualTo(VALID_TTL);

      verify(loadChallengeConfigPort, times(1)).loadConfig();
      verify(saveChallengePort, times(1)).save(any(Challenge.class));
    }

    @Test
    @DisplayName("Config가 올바르게 로드되고 사용됨")
    void execute_LoadsAndUsesConfig() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      when(loadChallengeConfigPort.loadConfig()).thenReturn(mockConfig);
      when(saveChallengePort.save(any(Challenge.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      CreateChallengeResult result = createChallengeService.execute(command);

      // Then
      assertThat(result.expiresIn()).isEqualTo(mockConfig.ttlSeconds());
      assertThat(result.message()).contains(mockConfig.domain());
      verify(loadChallengeConfigPort, times(1)).loadConfig();
    }

    @Test
    @DisplayName("생성된 Challenge가 저장됨")
    void execute_SavesChallenge() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      when(loadChallengeConfigPort.loadConfig()).thenReturn(mockConfig);
      when(saveChallengePort.save(any(Challenge.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // When
      createChallengeService.execute(command);

      // Then
      verify(saveChallengePort, times(1)).save(any(Challenge.class));
    }
  }

  // ========================================
  // Validation Failure Cases
  // ========================================

  @Nested
  @DisplayName("Validation Failure Cases")
  class ValidationFailureCases {

    @Test
    @DisplayName("userId가 null일 때 예외 발생")
    void execute_NullUserId_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(null, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("userId가 0일 때 예외 발생")
    void execute_ZeroUserId_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(0L, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("userId가 음수일 때 예외 발생")
    void execute_NegativeUserId_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(-1L, VALID_PURPOSE, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("User ID must be positive");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("purpose가 null일 때 예외 발생")
    void execute_NullPurpose_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, null, VALID_WALLET_ADDRESS);

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Purpose must not be null");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress가 null일 때 예외 발생")
    void execute_NullWalletAddress_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, null);

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress가 빈 문자열일 때 예외 발생")
    void execute_EmptyWalletAddress_ThrowsException() {
      // Given
      CreateChallengeCommand command = new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, "");

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress가 공백만 있을 때 예외 발생")
    void execute_WhitespaceWalletAddress_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, "   ");

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Wallet address must not be blank");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress가 잘못된 형식일 때 예외 발생")
    void execute_InvalidWalletAddressFormat_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, "invalid-address");

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress에 0x 접두사가 없을 때 예외 발생")
    void execute_WalletAddressWithout0x_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(
              VALID_USER_ID, VALID_PURPOSE, "5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      // Validation 실패 시 아무것도 호출되지 않음
      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }

    @Test
    @DisplayName("walletAddress가 너무 짧을 때 예외 발생")
    void execute_WalletAddressTooShort_ThrowsException() {
      // Given
      CreateChallengeCommand command =
          new CreateChallengeCommand(VALID_USER_ID, VALID_PURPOSE, "0x5aAeb6053");

      // When & Then
      assertThatThrownBy(() -> createChallengeService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid Ethereum address format");

      // Validation 실패 시 아무것도 호출되지 않음
      verify(loadChallengeConfigPort, never()).loadConfig();
      verify(saveChallengePort, never()).save(any());
    }
  }
}
