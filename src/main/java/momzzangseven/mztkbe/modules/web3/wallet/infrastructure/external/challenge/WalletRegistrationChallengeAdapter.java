package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.challenge;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.ChallengeSnapshot;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.LoadChallengeQuery;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeExpiredCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.dto.MarkChallengeUsedCommand;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.LoadChallengeUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.MarkChallengeExpiredUseCase;
import momzzangseven.mztkbe.modules.web3.challenge.application.port.in.MarkChallengeUsedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationChallengeView;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationChallengePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeExpiredPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.MarkWalletRegistrationChallengeUsedPort;
import org.springframework.stereotype.Component;

/** Adapter between wallet registration and challenge use cases. */
@Component
@RequiredArgsConstructor
public class WalletRegistrationChallengeAdapter
    implements LoadWalletRegistrationChallengePort,
        MarkWalletRegistrationChallengeUsedPort,
        MarkWalletRegistrationChallengeExpiredPort {

  private static final String PURPOSE = "WALLET_REGISTRATION";

  private final LoadChallengeUseCase loadChallengeUseCase;
  private final MarkChallengeUsedUseCase markChallengeUsedUseCase;
  private final MarkChallengeExpiredUseCase markChallengeExpiredUseCase;

  @Override
  public Optional<WalletRegistrationChallengeView> load(String nonce) {
    return loadChallengeUseCase.execute(new LoadChallengeQuery(nonce, PURPOSE)).map(this::toView);
  }

  @Override
  public void markUsed(String nonce) {
    markChallengeUsedUseCase.execute(new MarkChallengeUsedCommand(nonce, PURPOSE));
  }

  @Override
  public void markExpired(String nonce) {
    markChallengeExpiredUseCase.execute(new MarkChallengeExpiredCommand(nonce, PURPOSE));
  }

  private WalletRegistrationChallengeView toView(ChallengeSnapshot snapshot) {
    return new WalletRegistrationChallengeView(
        snapshot.userId(),
        snapshot.walletAddress(),
        snapshot.nonce(),
        snapshot.message(),
        snapshot.used(),
        snapshot.expired());
  }
}
