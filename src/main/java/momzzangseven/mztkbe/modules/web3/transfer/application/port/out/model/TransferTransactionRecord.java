package momzzangseven.mztkbe.modules.web3.transfer.application.port.out.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3ReferenceType;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferTransactionRecord {

  private Long id;
  private String idempotencyKey;
  private Web3ReferenceType referenceType;
  private String referenceId;
  private Long fromUserId;
  private Long toUserId;
  private String fromAddress;
  private String toAddress;
  private BigInteger amountWei;
  private Long nonce;
  private Web3TxType txType;
  private String authorityAddress;
  private Long authorizationNonce;
  private String delegateTarget;
  private LocalDateTime authorizationExpiresAt;
  private Web3TxStatus status;
  private String txHash;
  private LocalDateTime signedAt;
  private LocalDateTime broadcastedAt;
  private LocalDateTime confirmedAt;
  private String signedRawTx;
  private String failureReason;
  private LocalDateTime processingUntil;
  private String processingBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
