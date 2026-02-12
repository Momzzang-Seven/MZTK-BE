package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3NonceStateEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3NonceStateJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NonceStatePersistenceAdapter implements ReserveNoncePort {

  private final Web3NonceStateJpaRepository repository;

  @Override
  @Transactional
  public long reserveNextNonce(String fromAddress) {
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new Web3InvalidInputException("fromAddress is required");
    }

    String normalizedAddress = fromAddress.toLowerCase();

    Web3NonceStateEntity state =
        repository
            .findByFromAddressForUpdate(normalizedAddress)
            .orElseGet(
                () ->
                    Web3NonceStateEntity.builder()
                        .fromAddress(normalizedAddress)
                        .nextNonce(0L)
                        .build());

    long reservedNonce = state.getNextNonce();
    state.setNextNonce(reservedNonce + 1);
    repository.save(state);

    return reservedNonce;
  }
}
