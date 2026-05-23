package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.global.error.web3.Web3TransferException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentIdempotencyMismatchPolicy;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionCallHashPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.BuildExecutionDigestPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip1559TtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadEip7702AuthorizationTtlPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.PublishExecutionIntentTerminatedPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.SponsorDailyUsagePersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ValidateExecutionDraftPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.event.ExecutionIntentTerminatedEvent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.SponsorDailyUsage;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SponsorPolicy;

/**
 * Creates a new {@link ExecutionIntent} from a domain-provided draft.
 *
 * <p>This service applies idempotency reuse rules, selects execution mode (EIP-7702 or EIP-1559),
 * reserves sponsor exposure when required, and returns the sign request contract used by clients.
 */
@RequiredArgsConstructor
public class CreateExecutionIntentService implements CreateExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final SponsorDailyUsagePersistencePort sponsorDailyUsagePersistencePort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;
  private final LoadSponsorPolicyPort loadSponsorPolicyPort;
  private final LoadEip7702AuthorizationTtlPort loadEip7702AuthorizationTtlPort;
  private final LoadEip1559TtlPort loadEip1559TtlPort;
  private final BuildExecutionDigestPort buildExecutionDigestPort;
  private final BuildExecutionCallHashPort buildExecutionCallHashPort;
  private final ValidateExecutionDraftPolicyPort validateExecutionDraftPolicyPort;
  private final ExecutionModeSelector executionModeSelector;
  private final PublishExecutionIntentTerminatedPort publishExecutionIntentTerminatedPort;
  private final Clock appClock;

  /**
   * Creates or reuses an execution intent for the given draft command.
   *
   * <p>If an active intent with the same root idempotency key and payload exists, it is reused.
   * Otherwise a new attempt is created.
   */
  @Override
  public CreateExecutionIntentResult execute(CreateExecutionIntentCommand command) {
    LocalDateTime now = LocalDateTime.now(appClock);
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
      validateExecutionDraftPolicyPort.validate(
          command.draft().delegateTarget(), command.draft().calls());
      validateEip7702ExpiresAt(expiresAt, now);
      modeDecision =
          modeDecision.withExecutionDigest(
              buildExecutionDigestPort.buildExecutionDigestHex(
                  command.draft().authorityAddress(),
                  publicId,
                  buildExecutionCallHashPort.hashCalls(command.draft().calls()),
                  ExecutionDeadlineEpoch.toEpochSeconds(expiresAt, appClock)));
      reserveSponsorExposure(modeDecision);
    }
    ExecutionIntent created =
        ExecutionIntent.create(
            publicId,
            command.draft().rootIdempotencyKey(),
            attemptNo,
            toModelResourceType(command.draft().resourceType()),
            command.draft().resourceId(),
            toModelActionType(command.draft().actionType()),
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

    return toResult(created, toModelResourceStatus(command.draft().resourceStatus()), false);
  }

  private CreateExecutionIntentResult tryReuseExisting(
      ExecutionIntent existing, CreateExecutionIntentCommand command, LocalDateTime now) {
    if (existing.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE
        && shouldExpireExistingBeforeReuse(existing, now)) {
      ExecutionIntent expired =
          executionIntentPersistencePort.update(
              existing.expire(
                  ErrorCode.AUTH_EXPIRED.name(), ErrorCode.AUTH_EXPIRED.getMessage(), now));
      if (existing.getMode() == ExecutionMode.EIP7702
          && existing.getReservedSponsorCostWei().signum() > 0) {
        releaseSponsorExposure(
            existing.getRequesterUserId(),
            existing.resolveSponsorUsageDateKst(),
            existing.getReservedSponsorCostWei());
      }
      publishTerminated(expired, ExecutionIntentStatus.EXPIRED, ErrorCode.AUTH_EXPIRED.name());
      return null;
    }

    if (!existing.hasSamePayload(command.draft().payloadHash())) {
      if (existing.getStatus() == ExecutionIntentStatus.AWAITING_SIGNATURE) {
        if (command.mismatchPolicy()
            == ExecutionIntentIdempotencyMismatchPolicy.REJECT_ON_MISMATCH) {
          throw new Web3TransferException(ErrorCode.IDEMPOTENCY_CONFLICT, false);
        }
        ExecutionIntent canceled =
            executionIntentPersistencePort.update(
                existing.cancel(
                    ErrorCode.IDEMPOTENCY_CONFLICT.name(), "superseded by new payload", now));
        if (existing.getMode() == ExecutionMode.EIP7702
            && existing.getReservedSponsorCostWei().signum() > 0) {
          releaseSponsorExposure(
              existing.getRequesterUserId(),
              existing.resolveSponsorUsageDateKst(),
              existing.getReservedSponsorCostWei());
        }
        publishTerminated(
            canceled, ExecutionIntentStatus.CANCELED, ErrorCode.IDEMPOTENCY_CONFLICT.name());
        return null;
      }
      if (existing.getStatus().isInFlight()
          || existing.getStatus() == ExecutionIntentStatus.CONFIRMED) {
        throw new Web3TransferException(ErrorCode.IDEMPOTENCY_CONFLICT, false);
      }
      return null;
    }

    if (existing.isActiveForReuse()) {
      return toResult(existing, toModelResourceStatus(command.draft().resourceStatus()), true);
    }

    return null;
  }

  private ModeDecision finalizeModeDecision(
      CreateExecutionIntentCommand command,
      ExecutionModeSelector.ExecutionModeSelection modeSelection) {
    if (modeSelection.mode() == ExecutionMode.EIP7702) {
      BigInteger reservedCostWei = modeSelection.reservedSponsorCostWei();
      LocalDate usageDateKst = modeSelection.sponsorUsageDateKst();
      SponsorDailyUsage sponsorUsageToReserve = null;
      if (reservedCostWei.signum() > 0
          && (sponsorUsageToReserve =
                  lockSponsorUsageIfEligible(
                      command.draft().requesterUserId(),
                      usageDateKst,
                      reservedCostWei,
                      loadSponsorPolicyPort.loadSponsorPolicy()))
              == null) {
        if (command.draft().fallbackAllowed() && command.draft().unsignedTxSnapshot() != null) {
          return finalizeModeDecision(
              command,
              new ExecutionModeSelector.ExecutionModeSelection(
                  ExecutionMode.EIP1559, BigInteger.ZERO, usageDateKst));
        }
        throw new Web3TransferException(ErrorCode.SPONSOR_DAILY_LIMIT_EXCEEDED, true);
      }
      return selectMode(command, modeSelection, sponsorUsageToReserve);
    }
    return selectMode(command, modeSelection, null);
  }

  private ModeDecision selectMode(
      CreateExecutionIntentCommand command,
      ExecutionModeSelector.ExecutionModeSelection modeSelection,
      SponsorDailyUsage sponsorUsageToReserve) {
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
          modeSelection.reservedSponsorCostWei(),
          sponsorUsageToReserve);
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
        BigInteger.ZERO,
        null);
  }

  private SponsorDailyUsage lockSponsorUsageIfEligible(
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
      return null;
    }
    return usage;
  }

  private void reserveSponsorExposure(ModeDecision modeDecision) {
    if (modeDecision.sponsorUsageToReserve() == null
        || modeDecision.reservedSponsorCostWei().signum() <= 0) {
      return;
    }
    sponsorDailyUsagePersistencePort.update(
        modeDecision.sponsorUsageToReserve().reserve(modeDecision.reservedSponsorCostWei()));
  }

  private void releaseSponsorExposure(
      Long userId, LocalDate usageDateKst, BigInteger reservedCostWei) {
    SponsorDailyUsage usage =
        sponsorDailyUsagePersistencePort.getOrCreateForUpdate(userId, usageDateKst);
    sponsorDailyUsagePersistencePort.update(usage.release(reservedCostWei));
  }

  private void publishTerminated(
      ExecutionIntent intent, ExecutionIntentStatus terminalStatus, String failureReason) {
    publishExecutionIntentTerminatedPort.publish(
        new ExecutionIntentTerminatedEvent(intent.getPublicId(), terminalStatus, failureReason));
  }

  private LocalDateTime selectedExpiresAt(
      CreateExecutionIntentCommand command, ExecutionMode mode, LocalDateTime now) {
    if (mode == ExecutionMode.EIP7702) {
      return command.draft().expiresAt();
    }
    return now.plusSeconds(loadEip1559TtlPort.loadTtlSeconds());
  }

  private void validateEip7702ExpiresAt(LocalDateTime expiresAt, LocalDateTime now) {
    if (!expiresAt.isAfter(now)) {
      throw new Web3InvalidInputException("EIP-7702 expiresAt must be in the future");
    }
    long minimumRemainingSeconds = loadEip7702AuthorizationTtlPort.loadMinimumRemainingSeconds();
    if (expiresAt.isBefore(now.plusSeconds(minimumRemainingSeconds))) {
      throw new Web3InvalidInputException(
          "EIP-7702 expiresAt must be at least "
              + minimumRemainingSeconds
              + " seconds in the future");
    }
  }

  private CreateExecutionIntentResult toResult(
      ExecutionIntent intent, ExecutionResourceStatus resourceStatus, boolean existing) {
    return new CreateExecutionIntentResult(
        intent.getResourceType(),
        intent.getResourceId(),
        resourceStatus,
        intent.getPublicId(),
        intent.getStatus(),
        intent.getExpiresAt(),
        ExecutionDeadlineEpoch.toEpochSecondsLong(intent.getExpiresAt(), appClock),
        intent.getMode(),
        intent.getMode().requiredSignCount(),
        buildSignRequest(intent),
        existing,
        intent.getPayloadSnapshotJson());
  }

  private boolean shouldExpireExistingBeforeReuse(ExecutionIntent existing, LocalDateTime now) {
    if (!existing.getExpiresAt().isAfter(now)) {
      return true;
    }
    return existing.getMode() == ExecutionMode.EIP7702
        && !ExecutionSignRequestAvailability.hasMinimumRemainingTime(
            existing, now, loadEip7702AuthorizationTtlPort.loadMinimumRemainingSeconds());
  }

  private ExecutionResourceType toModelResourceType(ExecutionResourceTypeCode resourceType) {
    return ExecutionResourceType.valueOf(resourceType.name());
  }

  private ExecutionResourceStatus toModelResourceStatus(
      ExecutionResourceStatusCode resourceStatus) {
    return ExecutionResourceStatus.valueOf(resourceStatus.name());
  }

  private ExecutionActionType toModelActionType(ExecutionActionTypeCode actionType) {
    return ExecutionActionType.valueOf(actionType.name());
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
              intent.getExecutionDigest(),
              ExecutionDeadlineEpoch.toEpochSecondsLong(intent.getExpiresAt(), appClock)));
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
      BigInteger reservedSponsorCostWei,
      SponsorDailyUsage sponsorUsageToReserve) {

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
          reservedSponsorCostWei,
          sponsorUsageToReserve);
    }
  }
}
