package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;

/** Decodes exact custom-error selectors used by MarketplaceEscrow. */
@Component
public class MarketplaceEscrowRevertReasonDecoder {

  private final Map<String, String> selectors =
      Map.ofEntries(
          selector("InvalidAddress()"),
          selector("InvalidId()"),
          selector("InvalidPrice()"),
          selector("UnsupportedToken()"),
          selector("CannotBuyOwnClass()"),
          selector("OrderAlreadyExists()"),
          selector("OrderNotFound()"),
          selector("OnlyBuyer()"),
          selector("OnlyBuyerOrTrainer()"),
          selector("AlreadySettled()"),
          selector("DeadlineExpired()"),
          selector("DeadlineNotExpired()"),
          selector("InvalidSignature()"),
          selector("SignatureExpired()"),
          selector("SafeERC20FailedOperation(address)"));

  public Optional<String> decode(String revertDataHex) {
    if (revertDataHex == null || revertDataHex.length() < 10) {
      return Optional.empty();
    }
    String selector = revertDataHex.substring(0, 10).toLowerCase();
    return Optional.ofNullable(selectors.get(selector));
  }

  private static Map.Entry<String, String> selector(String signature) {
    return Map.entry(Hash.sha3String(signature).substring(0, 10).toLowerCase(), signature);
  }
}
