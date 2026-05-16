package momzzangseven.mztkbe.modules.web3.treasury.application.dto;

import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;

/**
 * Result of inspecting an AWS KMS alias: the {@link KmsKeyState} of the target key plus the target
 * key id itself.
 *
 * <p>The provisioning service uses {@code targetKmsKeyId} to detect alias drift — a wallet row's
 * {@code kmsKeyId} that no longer matches what the alias points to in AWS — and route the request
 * to alias-repair when that happens. {@code BindKmsAliasService} uses the same field to fail-safe
 * when ghost-recovery would otherwise bind the alias to an unintended key.
 *
 * <p>When the alias does not exist in AWS at all (e.g. {@code NotFoundException} from {@code
 * DescribeKey}), implementations return {@code state == UNAVAILABLE} together with {@code
 * targetKmsKeyId == null}. For every other state ({@code ENABLED} / {@code DISABLED} / {@code
 * PENDING_DELETION} / {@code PENDING_IMPORT} / catch-all {@code UNAVAILABLE} from an unmapped AWS
 * state) the alias exists and {@code targetKmsKeyId} is non-null.
 */
public record AliasTargetInfo(KmsKeyState state, String targetKmsKeyId) {}
