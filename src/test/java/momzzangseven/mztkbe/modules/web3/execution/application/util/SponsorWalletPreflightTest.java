package momzzangseven.mztkbe.modules.web3.execution.application.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SponsorWalletGate;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.VerifyTreasuryWalletForSignPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SponsorWalletPreflightTest {

  private static final String SPONSOR_ALIAS = "sponsor-treasury";
  private static final String SPONSOR_KMS_KEY_ID = "alias/sponsor-treasury";
  private static final String SPONSOR_ADDRESS = "0x" + "6".repeat(40);

  @Mock private LoadSponsorTreasuryWalletPort loadSponsorTreasuryWalletPort;
  @Mock private VerifyTreasuryWalletForSignPort verifyTreasuryWalletForSignPort;

  private SponsorWalletPreflight preflight;

  @BeforeEach
  void setUp() {
    preflight =
        new SponsorWalletPreflight(loadSponsorTreasuryWalletPort, verifyTreasuryWalletForSignPort);
  }

  private TreasuryWalletInfo activeWallet() {
    return new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, true);
  }

  @Test
  void preflight_returnsGateWhenAllChecksPass() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeWallet()));

    SponsorWalletGate gate = preflight.preflight();

    assertThat(gate.walletInfo().walletAlias()).isEqualTo(SPONSOR_ALIAS);
    assertThat(gate.signer().walletAlias()).isEqualTo(SPONSOR_ALIAS);
    assertThat(gate.signer().kmsKeyId()).isEqualTo(SPONSOR_KMS_KEY_ID);
    assertThat(gate.signer().walletAddress()).isEqualTo(SPONSOR_ADDRESS);
    verify(verifyTreasuryWalletForSignPort).verify(SPONSOR_ALIAS);
  }

  @Test
  void preflight_throwsWhenWalletMissing() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenWalletInactive() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, SPONSOR_ADDRESS, false)));

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenKmsKeyIdNull() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, null, SPONSOR_ADDRESS, true)));

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenKmsKeyIdBlank() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, "", SPONSOR_ADDRESS, true)));

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenWalletAddressNull() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, null, true)));

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenWalletAddressBlank() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, "", true)));

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessage("sponsor signer key is missing");
  }

  @Test
  void preflight_throwsWhenAddressMalformed() {
    when(loadSponsorTreasuryWalletPort.load())
        .thenReturn(
            Optional.of(
                new TreasuryWalletInfo(SPONSOR_ALIAS, SPONSOR_KMS_KEY_ID, "not-an-address", true)));

    assertThatThrownBy(() -> preflight.preflight()).isInstanceOf(Web3InvalidInputException.class);
  }

  @Test
  void preflight_propagatesTreasuryWalletStateException_fromVerify() {
    when(loadSponsorTreasuryWalletPort.load()).thenReturn(Optional.of(activeWallet()));
    doThrow(new TreasuryWalletStateException("kms key disabled"))
        .when(verifyTreasuryWalletForSignPort)
        .verify(SPONSOR_ALIAS);

    assertThatThrownBy(() -> preflight.preflight())
        .isInstanceOf(TreasuryWalletStateException.class)
        .hasMessageContaining("kms key disabled");
  }
}
