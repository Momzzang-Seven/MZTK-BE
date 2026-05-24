package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.adapter.nonce;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.shared.domain.vo.EvmAddress;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.nonce.LoadSponsorChainNoncePort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.http.HttpService;

@Slf4j
@Component
@RequiredArgsConstructor
public class Web3jSponsorChainNonceAdapter implements LoadSponsorChainNoncePort {

  private final Web3CoreProperties web3CoreProperties;

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
  public SponsorChainNonceSnapshot loadSnapshot(long chainId, String fromAddress) {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (chainId != web3CoreProperties.getChainId()) {
      throw new Web3InvalidInputException(
          "chainId mismatch: requested="
              + chainId
              + ", configured="
              + web3CoreProperties.getChainId());
    }
    String normalizedAddress = EvmAddress.of(fromAddress).value();
    Long mainPending = loadNonce(mainWeb3j, normalizedAddress, DefaultBlockParameterName.PENDING);
    Long subPending = loadNonce(subWeb3j, normalizedAddress, DefaultBlockParameterName.PENDING);
    Long mainLatest = loadNonce(mainWeb3j, normalizedAddress, DefaultBlockParameterName.LATEST);
    Long subLatest = loadNonce(subWeb3j, normalizedAddress, DefaultBlockParameterName.LATEST);
    Long chainPending = maxPresent(mainPending, subPending);
    Long chainLatest = maxPresent(mainLatest, subLatest);
    if (chainPending == null || chainLatest == null) {
      throw new IllegalStateException(
          "failed to load sponsor nonce snapshot: chainId="
              + chainId
              + ", fromAddress="
              + normalizedAddress);
    }
    return new SponsorChainNonceSnapshot(
        chainPending, chainLatest, mainPending, subPending, mainLatest, subLatest);
  }

  private Long loadNonce(Web3j web3j, String fromAddress, DefaultBlockParameterName block) {
    if (web3j == null) {
      return null;
    }
    try {
      EthGetTransactionCount response = web3j.ethGetTransactionCount(fromAddress, block).send();
      if (response == null || response.hasError()) {
        return null;
      }
      BigInteger nonce = response.getTransactionCount();
      if (nonce == null || nonce.signum() < 0) {
        return null;
      }
      return nonce.longValueExact();
    } catch (Exception e) {
      log.warn("failed to load sponsor nonce: address={}, block={}", fromAddress, block, e);
      return null;
    }
  }

  private Long maxPresent(Long first, Long second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return Math.max(first, second);
  }
}
