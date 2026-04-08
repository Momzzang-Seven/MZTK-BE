package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.adapter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.ReserveNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3NonceStateEntity;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository.Web3NonceStateJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.http.HttpService;

@Slf4j
@Component
@RequiredArgsConstructor
public class NonceStatePersistenceAdapter implements ReserveNoncePort {

  private final Web3NonceStateJpaRepository repository;
  private final Web3CoreProperties web3CoreProperties;
  private final Clock appClock;

  private Web3j mainWeb3j;
  private Web3j subWeb3j;

  @PostConstruct
  void init() {
    mainWeb3j = Web3j.build(new HttpService(web3CoreProperties.getRpc().getMain()));
    subWeb3j = Web3j.build(new HttpService(web3CoreProperties.getRpc().getSub()));
  }

  @PreDestroy
  void shutdown() {
    if (mainWeb3j != null) {
      mainWeb3j.shutdown();
    }
    if (subWeb3j != null) {
      subWeb3j.shutdown();
    }
  }

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
                        .updatedAt(LocalDateTime.now(appClock))
                        .build());

    // If the local nonce tracker is behind the chain (e.g. DB reset, external txs),
    // bump it to the pending nonce to avoid broadcasting "nonce too low" txs.
    Long onchainPendingNonce = loadPendingNonceOrNull(normalizedAddress);
    if (onchainPendingNonce != null && onchainPendingNonce > state.getNextNonce()) {
      log.warn(
          "Sync nonce state to chain: address={}, dbNextNonce={}, chainPendingNonce={}",
          normalizedAddress,
          state.getNextNonce(),
          onchainPendingNonce);
      state.setNextNonce(onchainPendingNonce);
    }

    long reservedNonce = state.getNextNonce();
    state.setNextNonce(reservedNonce + 1);
    state.setUpdatedAt(LocalDateTime.now(appClock));
    repository.save(state);

    return reservedNonce;
  }

  private Long loadPendingNonceOrNull(String fromAddress) {
    BigInteger nonce = loadPendingNonce(mainWeb3j, fromAddress);
    if (nonce == null) {
      nonce = loadPendingNonce(subWeb3j, fromAddress);
    }
    if (nonce == null || nonce.signum() < 0) {
      return null;
    }
    try {
      return nonce.longValueExact();
    } catch (ArithmeticException ex) {
      log.warn("Pending nonce overflow: address={}, nonce={}", fromAddress, nonce);
      return null;
    }
  }

  private BigInteger loadPendingNonce(Web3j web3j, String fromAddress) {
    if (web3j == null) {
      return null;
    }
    try {
      EthGetTransactionCount response =
          web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING).send();
      if (response == null || response.hasError()) {
        return null;
      }
      return response.getTransactionCount();
    } catch (Exception ex) {
      return null;
    }
  }
}
