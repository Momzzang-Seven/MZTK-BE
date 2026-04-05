package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class CreateExecutionIntentService implements CreateExecutionIntentUseCase {

  private static final long EIP1559_TTL_SECONDS = 90L;

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;
  private final LoadSponsorPolicyPort loadSponsorPolicyPort;
  private final BuildExecutionDigestPort buildExecutionDigestPort;
  private final ExecutionModeSelector executionModeSelector;

  @Override
  public CreateExecutionIntentResult execute(CreateExecutionIntentCommand command) {
    LocalDateTime now = LocalDateTime.now();
    ExecutionIntent existing =
        executionIntentPersistencePort
            .findLatestByRootIdempotencyKeyForUpdate(command.draft().rootIdempotencyKey())
            .orElse(null);

    if (existing != null) {
      CreateExecutionIntentResult reused = tryReuseExisting(existing, command, now);
      if (reused != null) {
        return reused;
      }
    }

    int attemptNo = existing == null ? 1 : existing.getAttemptNo() + 1;
    String publicId = UUID.randomUUID().toString();

    ExecutionModeSelector.ExecutionModeSelection preliminarySelection =
        executionModeSelector.select(command);
    ModeDecision modeDecision = finalizeModeDecision(command, preliminarySelection);
    LocalDateTime expiresAt = selectedExpiresAt(command, modeDecision.mode(), now);
    if (modeDecision.mode() == ExecutionMode.EIP7702) {
      modeDecision =
          modeDecision.withExecutionDigest(
              buildExecutionDigestPort.buildExecutionDigestHex(
                  command.draft().authorityAddress(),
                  publicId,
                  hashDraftCalls(command),
                  BigInteger.valueOf(expiresAt.toEpochSecond(ZoneOffset.UTC))));
    }
    ExecutionIntent created =
        ExecutionIntent.create(
            publicId,
            command.draft().rootIdempotencyKey(),
            attemptNo,
            command.draft().resourceType(),
            command.draft().resourceId(),
            command.draft().actionType(),
            command.draft().requesterUserId(),
            command.draft().counterpartyUserId(),
            modeDecision.mode(),
            command.draft().payloadHash(),
            command.draft().payloadSnapshotJson(),
            modeDecision.authorityAddress(),
            modeDecision.authorityNonce(),
            modeDecision.delegateTarget(),
            expiresAt,
            modeDecision.authorizationPayloadHash(),
            modeDecision.executionDigest(),
            modeDecision.unsignedTxSnapshot(),
            modeDecision.unsignedTxFingerprint(),
            modeDecision.reservedSponsorCostWei(),
            preliminarySelection.sponsorUsageDateKst(),
            now);

    created = executionIntentPersistencePort.create(created);

    return toResult(created, command.draft().resourceStatus(), false);
  }

  private CreateExecutionIntentResult tryReuseExisting(
      ExecutionIntent existing, CreateExecutionIntentCommand command, LocalDateTime now) {
    if (existing.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE
        && existing.getExpiresAt().isBefore(now)) {
      executionIntentPersistencePort.update(
          existing.expire(ErrorCode.AUTH_EXPIRED.name(), ErrorCode.AUTH_EXPIRED.getMessage()));
      if (existing.getMode() == ExecutionMode.EIP7702
          && existing.getReservedSponsorCostWei().signum() > 0) {
        releaseSponsorExposure(
            existing.getRequesterUserId(),
            existing.resolveSponsorUsageDateKst(),
            existing.getReservedSponsorCostWei());
      }
      return null;
    }

    if (!existing.hasSamePayload(command.draft().payloadHash())) {
      if (existing.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE) {
        executionIntentPersistencePort.update(
            existing.cancel(ErrorCode.IDEMPOTENCY_CONFLICT.name(), "superseded by new payload"));
        if (existing.getMode() == ExecutionMode.EIP7702
            && existing.getReservedSponsorCostWei().signum() > 0) {
          releaseSponsorExposure(
              existing.getRequesterUserId(),
              existing.resolveSponsorUsageDateKst(),
              existing.getReservedSponsorCostWei());
        }
        return null;
      }
      if (existing.getStatus().isInFlight()
          || existing.getStatus() == ExecutionIntentStatus.CONFIRMED) {
        throw new Web3TransferException(ErrorCode.IDEMPOTENCY_CONFLICT, false);
      }
      return null;
    }

    if (existing.isActiveForReuse()) {
      return toResult(existing, command.draft().resourceStatus(), true);
    }

    return null;
  }

  private ModeDecision finalizeModeDecision(
      CreateExecutionIntentCommand command,
      ExecutionModeSelector.ExecutionModeSelection modeSelection) {
    if (modeSelection.mode() == ExecutionMode.EIP7702) {
      BigInteger reservedCostWei = modeSelection.reservedSponsorCostWei();
      LocalDate usageDateKst = modeSelection.sponsorUsageDateKst();
      if (reservedCostWei.signum() > 0
          && !reserveSponsorExposureIfEligible(
              command.draft().requesterUserId(),
              usageDateKst,
              reservedCostWei,
              loadSponsorPolicyPort.loadSponsorPolicy())) {
        if (command.draft().fallbackAllowed() && command.draft().unsignedTxSnapshot() != null) {
          return finalizeModeDecision(
              command,
              new ExecutionModeSelector.ExecutionModeSelection(
                  ExecutionMode.EIP1559, BigInteger.ZERO, usageDateKst));
        }
        throw new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true);
      }
    }
    return selectMode(command, modeSelection);
  }

  private ModeDecision selectMode(
      CreateExecutionIntentCommand command,
      ExecutionModeSelector.ExecutionModeSelection modeSelection) {
    if (modeSelection.mode() == ExecutionMode.EIP7702) {
      return new ModeDecision(
          ExecutionMode.EIP7702,
          command.draft().authorityAddress(),
          command.draft().authorityNonce(),
          command.draft().delegateTarget(),
          command.draft().authorizationPayloadHash(),
          null,
          null,
          null,
          modeSelection.reservedSponsorCostWei());
    }
    return new ModeDecision(
        ExecutionMode.EIP1559,
        null,
        null,
        null,
        null,
        null,
        command.draft().unsignedTxSnapshot(),
        command.draft().unsignedTxFingerprint(),
        BigInteger.ZERO);
  }

  private boolean reserveSponsorExposureIfEligible(
      Long userId,
      LocalDate usageDateKst,
      BigInteger reservedCostWei,
      SponsorPolicy sponsorPolicy) {
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(userId, usageDateKst);
    if (usage
            .totalExposureWei()
            .add(reservedCostWei)
            .compareTo(sponsorPolicy.perDayUserCapEth().movePointRight(18).toBigIntegerExact())
        > 0) {
      return false;
    }
    sponsorDailyUsagePersistencePort.update(usage.reserve(reservedCostWei));
    return true;
  }

  private void releaseSponsorExposure(
      Long userId, LocalDate usageDateKst, BigInteger reservedCostWei) {
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(userId, usageDateKst);
    sponsorDailyUsagePersistencePort.update(usage.release(reservedCostWei));
  }

  private String hashDraftCalls(CreateExecutionIntentCommand command) {
    StringBuilder canonical = new StringBuilder();
    command
        .draft()
        .calls()
        .forEach(
            call ->
                canonical
                    .append(call.toAddress().toLowerCase())
                    .append('|')
                    .append(call.valueWei().toString())
                    .append('|')
                    .append(call.data().toLowerCase())
                    .append(';'));
    return org.web3j.crypto.Hash.sha3String(canonical.toString());
  }

  private LocalDateTime selectedExpiresAt(
      CreateExecutionIntentCommand command, ExecutionMode mode, LocalDateTime now) {
    if (mode == ExecutionMode.EIP7702) {
      return command.draft().expiresAt();
    }
    return now.plusSeconds(EIP1559_TTL_SECONDS);
  }

  private CreateExecutionIntentResult toResult(
      ExecutionIntent intent, String resourceStatus, boolean existing) {
    return new CreateExecutionIntentResult(
        intent.getResourceType(),
        intent.getResourceId(),
        resourceStatus,
        intent.getPublicId(),
        intent.getStatus(),
        intent.getExpiresAt(),
        intent.getMode(),
        intent.getMode() == ExecutionMode.EIP7702 ? 2 : 1,
        buildSignRequest(intent),
        existing);
  }

  private SignRequestBundle buildSignRequest(ExecutionIntent intent) {
    if (!intent.shouldExposeSignRequest()) {
      return null;
    }
    if (intent.getMode() == ExecutionMode.EIP7702) {
      return SignRequestBundle.forEip7702(
          new SignRequestBundle.AuthorizationSignRequest(
              loadExecutionChainIdPort.loadChainId(),
              intent.getDelegateTarget(),
              intent.getAuthorityNonce(),
              intent.getAuthorizationPayloadHash()),
          new SignRequestBundle.SubmitSignRequest(
              intent.getExecutionDigest(), intent.getExpiresAt().toEpochSecond(ZoneOffset.UTC)));
    }

    return SignRequestBundle.forEip1559(
        new SignRequestBundle.TransactionSignRequest(
            intent.getUnsignedTxSnapshot().chainId(),
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot().toAddress(),
            org.web3j.utils.Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().valueWei()),
            intent.getUnsignedTxSnapshot().data(),
            intent.getUnsignedTxSnapshot().expectedNonce(),
            org.web3j.utils.Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().gasLimit()),
            org.web3j.utils.Numeric.encodeQuantity(
                intent.getUnsignedTxSnapshot().maxPriorityFeePerGas()),
            org.web3j.utils.Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().maxFeePerGas()),
            intent.getUnsignedTxSnapshot().expectedNonce()));
  }

  private record ModeDecision(
      ExecutionMode mode,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      String executionDigest,
      momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint,
      BigInteger reservedSponsorCostWei) {

    private ModeDecision withExecutionDigest(String newExecutionDigest) {
      return new ModeDecision(
          mode,
          authorityAddress,
          authorityNonce,
          delegateTarget,
          authorizationPayloadHash,
          newExecutionDigest,
          unsignedTxSnapshot,
          unsignedTxFingerprint,
          reservedSponsorCostWei);
    }
  }
}
