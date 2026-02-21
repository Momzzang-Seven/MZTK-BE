package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferTransaction {

  private final Long id;
  private final String idempotencyKey;
  private final Web3ReferenceType referenceType;
  private final String referenceId;
  private final Long fromUserId;
  private final Long toUserId;
  private final String fromAddress;
  private final String toAddress;
  private final BigInteger amountWei;
  private final Long nonce;
  private final Web3TxType txType;
  private final String authorityAddress;
  private final Long authorizationNonce;
  private final String delegateTarget;
  private final LocalDateTime authorizationExpiresAt;
  private final Web3TxStatus status;
  private final String txHash;
  private final LocalDateTime signedAt;
  private final LocalDateTime broadcastedAt;
  private final LocalDateTime confirmedAt;
  private final String signedRawTx;
  private final String failureReason;
  private final LocalDateTime processingUntil;
  private final String processingBy;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
}
