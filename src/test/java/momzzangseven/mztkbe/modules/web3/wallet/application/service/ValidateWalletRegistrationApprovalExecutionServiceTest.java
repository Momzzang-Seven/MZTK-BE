package momzzangseven.mztkbe.modules.web3.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ValidateWalletRegistrationApprovalExecutionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidateWalletRegistrationApprovalExecutionServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-13T10:00:00");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-05-13T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final String REGISTRATION_ID = "registration-1";
  private static final String INTENT_ID = "intent-1";

  @Mock private LoadWalletRegistrationSessionPort loadSessionPort;

  private ValidateWalletRegistrationApprovalExecutionService service;

  @BeforeEach
  void setUp() {
    service = new ValidateWalletRegistrationApprovalExecutionService(loadSessionPort, CLOCK);
  }

  @Test
  void execute_whenSessionMatches_succeeds() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    service.execute(command(1L, INTENT_ID));
  }

  @Test
  void execute_whenRequesterDiffers_throwsInvalidInput() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    assertThatThrownBy(() -> service.execute(command(2L, INTENT_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("owner mismatch");
  }

  @Test
  void execute_whenIntentIsNotLatest_throwsInvalidInput() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID))
        .thenReturn(Optional.of(approvalRequiredSession()));

    assertThatThrownBy(() -> service.execute(command(1L, "old")))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("not latest");
  }

  @Test
  void execute_whenSessionDeadlinePassed_throwsInvalidInput() {
    when(loadSessionPort.loadByPublicId(REGISTRATION_ID)).thenReturn(Optional.of(expiredSession()));

    assertThatThrownBy(() -> service.execute(command(1L, INTENT_ID)))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("expired");
  }

  private static ValidateWalletRegistrationApprovalExecutionCommand command(
      Long userId, String intentId) {
    return new ValidateWalletRegistrationApprovalExecutionCommand(
        REGISTRATION_ID, intentId, userId);
  }

  private static WalletRegistrationSession approvalRequiredSession() {
    return WalletRegistrationSession.create(
            REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.plusMinutes(30), NOW)
        .attachApprovalIntent(INTENT_ID, NOW.plusMinutes(30), NOW.plusSeconds(1));
  }

  private static WalletRegistrationSession expiredSession() {
    LocalDateTime createdAt = NOW.minusMinutes(31);
    return WalletRegistrationSession.create(
            REGISTRATION_ID, 1L, "0x" + "a".repeat(40), "nonce-1", NOW.minusSeconds(1), createdAt)
        .attachApprovalIntent(INTENT_ID, NOW.minusSeconds(1), createdAt.plusSeconds(1));
  }
}
