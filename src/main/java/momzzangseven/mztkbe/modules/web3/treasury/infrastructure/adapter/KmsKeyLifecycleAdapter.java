package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.AlgorithmSpec;
import software.amazon.awssdk.services.kms.model.AlreadyExistsException;
import software.amazon.awssdk.services.kms.model.CreateAliasRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyRequest;
import software.amazon.awssdk.services.kms.model.CreateKeyResponse;
import software.amazon.awssdk.services.kms.model.DescribeKeyRequest;
import software.amazon.awssdk.services.kms.model.DescribeKeyResponse;
import software.amazon.awssdk.services.kms.model.DisableKeyRequest;
import software.amazon.awssdk.services.kms.model.ExpirationModelType;
import software.amazon.awssdk.services.kms.model.GetParametersForImportRequest;
import software.amazon.awssdk.services.kms.model.GetParametersForImportResponse;
import software.amazon.awssdk.services.kms.model.ImportKeyMaterialRequest;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.WrappingKeySpec;

/**
 * Production-only AWS KMS implementation of {@link KmsKeyLifecyclePort}.
 *
 * <p>Drives the KMS control-plane API for treasury-wallet provisioning and retirement: creating
 * external-origin keys, retrieving the RSA-4096 wrapping bundle, importing pre-wrapped key
 * material, binding a human-readable alias, and disabling / scheduling deletion when an operator
 * retires a wallet.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class KmsKeyLifecycleAdapter implements KmsKeyLifecyclePort {

  private static final String ALIAS_PREFIX = "alias/";

  private final KmsClient kmsClient;

  @Override
  public String createKey() {
    CreateKeyRequest request =
        CreateKeyRequest.builder()
            .keySpec(KeySpec.ECC_SECG_P256_K1)
            .keyUsage(KeyUsageType.SIGN_VERIFY)
            .origin(OriginType.EXTERNAL)
            .build();
    try {
      CreateKeyResponse response = kmsClient.createKey(request);
      return response.keyMetadata().keyId();
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS CreateKey failed (awsErrorCode={})",
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public ImportParams getParametersForImport(String kmsKeyId) {
    GetParametersForImportRequest request =
        GetParametersForImportRequest.builder()
            .keyId(kmsKeyId)
            .wrappingAlgorithm(AlgorithmSpec.RSAES_OAEP_SHA_256)
            .wrappingKeySpec(WrappingKeySpec.RSA_4096)
            .build();
    try {
      GetParametersForImportResponse response = kmsClient.getParametersForImport(request);
      return new ImportParams(
          response.publicKey().asByteArray(), response.importToken().asByteArray());
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS GetParametersForImport failed (kmsKeyId={}, awsErrorCode={})",
          kmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public void importKeyMaterial(String kmsKeyId, byte[] encryptedKeyMaterial, byte[] importToken) {
    ImportKeyMaterialRequest request =
        ImportKeyMaterialRequest.builder()
            .keyId(kmsKeyId)
            .encryptedKeyMaterial(SdkBytes.fromByteArray(encryptedKeyMaterial))
            .importToken(SdkBytes.fromByteArray(importToken))
            .expirationModel(ExpirationModelType.KEY_MATERIAL_DOES_NOT_EXPIRE)
            .build();
    try {
      kmsClient.importKeyMaterial(request);
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS ImportKeyMaterial failed (kmsKeyId={}, awsErrorCode={})",
          kmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public void createAlias(String alias, String kmsKeyId) {
    CreateAliasRequest request =
        CreateAliasRequest.builder().aliasName(qualifyAlias(alias)).targetKeyId(kmsKeyId).build();
    try {
      kmsClient.createAlias(request);
    } catch (AlreadyExistsException ex) {
      log.warn(
          "AWS KMS CreateAlias rejected — alias already exists (alias={}, kmsKeyId={})",
          alias,
          kmsKeyId);
      throw new KmsAliasAlreadyExistsException(
          "alias '" + alias + "' is already bound to a KMS key", ex);
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS CreateAlias failed (alias={}, kmsKeyId={}, awsErrorCode={})",
          alias,
          kmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public void updateAlias(String alias, String newKmsKeyId) {
    UpdateAliasRequest request =
        UpdateAliasRequest.builder()
            .aliasName(qualifyAlias(alias))
            .targetKeyId(newKmsKeyId)
            .build();
    try {
      kmsClient.updateAlias(request);
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS UpdateAlias failed (alias={}, newKmsKeyId={}, awsErrorCode={})",
          alias,
          newKmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public KmsKeyState describeAliasTarget(String alias) {
    try {
      DescribeKeyResponse response =
          kmsClient.describeKey(DescribeKeyRequest.builder().keyId(qualifyAlias(alias)).build());
      KeyState keyState = response.keyMetadata().keyState();
      return mapKeyState(keyState);
    } catch (NotFoundException ex) {
      return KmsKeyState.UNAVAILABLE;
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS DescribeKey on alias failed (alias={}, awsErrorCode={})",
          alias,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public void disableKey(String kmsKeyId) {
    try {
      kmsClient.disableKey(DisableKeyRequest.builder().keyId(kmsKeyId).build());
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS DisableKey failed (kmsKeyId={}, awsErrorCode={})",
          kmsKeyId,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  @Override
  public void scheduleKeyDeletion(String kmsKeyId, int pendingWindowDays) {
    ScheduleKeyDeletionRequest request =
        ScheduleKeyDeletionRequest.builder()
            .keyId(kmsKeyId)
            .pendingWindowInDays(pendingWindowDays)
            .build();
    try {
      kmsClient.scheduleKeyDeletion(request);
    } catch (KmsException ex) {
      log.warn(
          "AWS KMS ScheduleKeyDeletion failed (kmsKeyId={}, days={}, awsErrorCode={})",
          kmsKeyId,
          pendingWindowDays,
          ex.awsErrorDetails() == null ? "n/a" : ex.awsErrorDetails().errorCode());
      throw ex;
    }
  }

  private static String qualifyAlias(String alias) {
    return alias.startsWith(ALIAS_PREFIX) ? alias : ALIAS_PREFIX + alias;
  }

  private static KmsKeyState mapKeyState(KeyState keyState) {
    if (keyState == null) {
      return KmsKeyState.UNAVAILABLE;
    }
    return switch (keyState) {
      case ENABLED -> KmsKeyState.ENABLED;
      case DISABLED -> KmsKeyState.DISABLED;
      case PENDING_DELETION -> KmsKeyState.PENDING_DELETION;
      case PENDING_IMPORT -> KmsKeyState.PENDING_IMPORT;
      default -> KmsKeyState.UNAVAILABLE;
    };
  }
}
