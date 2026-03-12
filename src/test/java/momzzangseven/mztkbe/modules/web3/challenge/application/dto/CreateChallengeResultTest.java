package momzzangseven.mztkbe.modules.web3.challenge.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.challenge.domain.model.Challenge;
import momzzangseven.mztkbe.modules.web3.challenge.domain.model.ChallengePurpose;
import momzzangseven.mztkbe.modules.web3.challenge.domain.vo.ChallengeConfig;
import org.junit.jupiter.api.Test;

class CreateChallengeResultTest {

  @Test
  void from_mapsChallengeFields() {
    Challenge challenge =
        Challenge.create(
            1L,
            ChallengePurpose.WALLET_REGISTRATION,
            "0x5Aaeb6053f3E94C9b9A09f33669435E7Ef1BeAed",
            new ChallengeConfig(300, "example.com", "https://example.com", "1", "1"));

    CreateChallengeResult result = CreateChallengeResult.from(challenge, 300);

    assertThat(result.nonce()).isEqualTo(challenge.getNonce());
    assertThat(result.message()).isEqualTo(challenge.getMessage());
    assertThat(result.expiresIn()).isEqualTo(300);
  }

  @Test
  void from_throws_whenChallengeNull() {
    assertThatThrownBy(() -> CreateChallengeResult.from(null, 300))
        .isInstanceOf(NullPointerException.class);
  }
}
