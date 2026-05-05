package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort.ImportParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import software.amazon.awssdk.services.kms.model.KeyMetadata;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyState;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.OriginType;
import software.amazon.awssdk.services.kms.model.ScheduleKeyDeletionRequest;
import software.amazon.awssdk.services.kms.model.UpdateAliasRequest;
import software.amazon.awssdk.services.kms.model.WrappingKeySpec;

/**
 * Unit tests for {@link KmsKeyLifecycleAdapter} — covers [M-98] .. [M-106] and [M-36] (prod gate).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KmsKeyLifecycleAdapter 단위 테스트")
class KmsKeyLifecycleAdapterTest {

  private static final String KMS_KEY_ID = "kms-key-id-123";
  private static final String ALIAS = "reward-treasury";
  private static final String QUALIFIED_ALIAS = "alias/reward-treasury";

  @Mock private KmsClient kmsClient;

  @InjectMocks private KmsKeyLifecycleAdapter adapter;

  @Nested
  @DisplayName("A. createKey")
  class CreateKey {

    @Test
    @DisplayName("[M-98] createKey — ECC_SECG_P256_K1 / SIGN_VERIFY / EXTERNAL 요청 + keyId 반환")
    void createKey_sendsCorrectRequestAndReturnsKeyId() {
      CreateKeyResponse response =
          CreateKeyResponse.builder()
              .keyMetadata(KeyMetadata.builder().keyId(KMS_KEY_ID).build())
              .build();
      when(kmsClient.createKey(any(CreateKeyRequest.class))).thenReturn(response);
      ArgumentCaptor<CreateKeyRequest> captor = ArgumentCaptor.forClass(CreateKeyRequest.class);

      String result = adapter.createKey();

      verify(kmsClient).createKey(captor.capture());
      CreateKeyRequest req = captor.getValue();
      assertThat(result).isEqualTo(KMS_KEY_ID);
      assertThat(req.keySpec()).isEqualTo(KeySpec.ECC_SECG_P256_K1);
      assertThat(req.keyUsage()).isEqualTo(KeyUsageType.SIGN_VERIFY);
      assertThat(req.origin()).isEqualTo(OriginType.EXTERNAL);
    }

    @Test
    @DisplayName("[M-99] createKey — KmsException은 그대로 전파 (WARN 후 rethrow)")
    void createKey_kmsExceptionPropagated() {
      when(kmsClient.createKey(any(CreateKeyRequest.class)))
          .thenThrow(KmsInvalidStateException.builder().message("internal").build());

      assertThatThrownBy(() -> adapter.createKey()).isInstanceOf(KmsInvalidStateException.class);
    }
  }

  @Nested
  @DisplayName("B. getParametersForImport")
  class GetParametersForImport {

    @Test
    @DisplayName(
        "[M-100] getParametersForImport — RSA_4096 + RSAES_OAEP_SHA_256 사용 + ImportParams 반환")
    void getParametersForImport_sendsRsa4096AndOaepSha256() {
      byte[] pubKey = new byte[] {1, 2, 3};
      byte[] importToken = new byte[] {4, 5, 6};
      GetParametersForImportResponse response =
          GetParametersForImportResponse.builder()
              .publicKey(SdkBytes.fromByteArray(pubKey))
              .importToken(SdkBytes.fromByteArray(importToken))
              .build();
      when(kmsClient.getParametersForImport(any(GetParametersForImportRequest.class)))
          .thenReturn(response);
      ArgumentCaptor<GetParametersForImportRequest> captor =
          ArgumentCaptor.forClass(GetParametersForImportRequest.class);

      ImportParams result = adapter.getParametersForImport(KMS_KEY_ID);

      verify(kmsClient).getParametersForImport(captor.capture());
      GetParametersForImportRequest req = captor.getValue();
      assertThat(req.keyId()).isEqualTo(KMS_KEY_ID);
      assertThat(req.wrappingAlgorithm()).isEqualTo(AlgorithmSpec.RSAES_OAEP_SHA_256);
      assertThat(req.wrappingKeySpec()).isEqualTo(WrappingKeySpec.RSA_4096);
      assertThat(result.wrappingPublicKey()).isEqualTo(pubKey);
      assertThat(result.importToken()).isEqualTo(importToken);
    }
  }

  @Nested
  @DisplayName("C. importKeyMaterial")
  class ImportKeyMaterial {

    @Test
    @DisplayName("[M-101] importKeyMaterial — KEY_MATERIAL_DOES_NOT_EXPIRE 만료 모델 사용")
    void importKeyMaterial_sendsExpirationModel() {
      byte[] enc = new byte[] {0x10};
      byte[] tok = new byte[] {0x20};
      ArgumentCaptor<ImportKeyMaterialRequest> captor =
          ArgumentCaptor.forClass(ImportKeyMaterialRequest.class);

      adapter.importKeyMaterial(KMS_KEY_ID, enc, tok);

      verify(kmsClient).importKeyMaterial(captor.capture());
      ImportKeyMaterialRequest req = captor.getValue();
      assertThat(req.keyId()).isEqualTo(KMS_KEY_ID);
      assertThat(req.expirationModel()).isEqualTo(ExpirationModelType.KEY_MATERIAL_DOES_NOT_EXPIRE);
      assertThat(req.encryptedKeyMaterial().asByteArray()).isEqualTo(enc);
      assertThat(req.importToken().asByteArray()).isEqualTo(tok);
    }
  }

  @Nested
  @DisplayName("D. createAlias / updateAlias — alias 정규화 + 충돌 변환")
  class AliasOperations {

    @Test
    @DisplayName("[M-102a] createAlias — 정규화: 'reward-treasury' → 'alias/reward-treasury'")
    void createAlias_qualifiesUnprefixedAlias() {
      ArgumentCaptor<CreateAliasRequest> captor = ArgumentCaptor.forClass(CreateAliasRequest.class);

      adapter.createAlias(ALIAS, KMS_KEY_ID);

      verify(kmsClient).createAlias(captor.capture());
      assertThat(captor.getValue().aliasName()).isEqualTo(QUALIFIED_ALIAS);
      assertThat(captor.getValue().targetKeyId()).isEqualTo(KMS_KEY_ID);
    }

    @Test
    @DisplayName("[M-102b] createAlias — 이미 'alias/' 접두사가 있으면 그대로 전달")
    void createAlias_passesThroughAlreadyQualifiedAlias() {
      ArgumentCaptor<CreateAliasRequest> captor = ArgumentCaptor.forClass(CreateAliasRequest.class);

      adapter.createAlias(QUALIFIED_ALIAS, KMS_KEY_ID);

      verify(kmsClient).createAlias(captor.capture());
      assertThat(captor.getValue().aliasName()).isEqualTo(QUALIFIED_ALIAS);
    }

    @Test
    @DisplayName(
        "[M-102c] createAlias — AlreadyExistsException → KmsAliasAlreadyExistsException(원인 보존)")
    void createAlias_alreadyExistsTranslated() {
      AlreadyExistsException aws = AlreadyExistsException.builder().message("dup").build();
      when(kmsClient.createAlias(any(CreateAliasRequest.class))).thenThrow(aws);

      assertThatThrownBy(() -> adapter.createAlias(ALIAS, KMS_KEY_ID))
          .isInstanceOf(KmsAliasAlreadyExistsException.class)
          .satisfies(
              ex -> {
                KmsAliasAlreadyExistsException ka = (KmsAliasAlreadyExistsException) ex;
                assertThat(ka.getCode()).isEqualTo("TREASURY_005");
                assertThat(ka.getCause()).isSameAs(aws);
                assertThat(ka.getMessage()).contains(ALIAS);
              });
    }

    @Test
    @DisplayName("[M-103] updateAlias — alias 정규화 + KmsException 그대로 전파")
    void updateAlias_qualifiesAndPropagates() {
      ArgumentCaptor<UpdateAliasRequest> captor = ArgumentCaptor.forClass(UpdateAliasRequest.class);

      adapter.updateAlias(ALIAS, "new-kms-id");

      verify(kmsClient).updateAlias(captor.capture());
      assertThat(captor.getValue().aliasName()).isEqualTo(QUALIFIED_ALIAS);
      assertThat(captor.getValue().targetKeyId()).isEqualTo("new-kms-id");
    }

    @Test
    @DisplayName("[M-103b] updateAlias — KmsException은 WARN 후 그대로 전파")
    void updateAlias_kmsExceptionPropagated() {
      when(kmsClient.updateAlias(any(UpdateAliasRequest.class)))
          .thenThrow(KmsInvalidStateException.builder().message("err").build());

      assertThatThrownBy(() -> adapter.updateAlias(ALIAS, "new-kms-id"))
          .isInstanceOf(KmsInvalidStateException.class);
    }
  }

  @Nested
  @DisplayName("E. describeAliasTarget — KeyState 매핑")
  class DescribeAliasTarget {

    @ParameterizedTest(name = "[M-104] AWS={0} → KmsKeyState={1}")
    @CsvSource({
      "ENABLED, ENABLED",
      "DISABLED, DISABLED",
      "PENDING_DELETION, PENDING_DELETION",
      "PENDING_IMPORT, PENDING_IMPORT",
      "CREATING, UNAVAILABLE",
      "UPDATING, UNAVAILABLE"
    })
    void describeAliasTarget_mapsAwsKeyState(String awsName, String expectedName) {
      DescribeKeyResponse response =
          DescribeKeyResponse.builder()
              .keyMetadata(KeyMetadata.builder().keyState(KeyState.valueOf(awsName)).build())
              .build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenReturn(response);

      KmsKeyState result = adapter.describeAliasTarget(ALIAS);

      assertThat(result).isSameAs(KmsKeyState.valueOf(expectedName));
    }

    @Test
    @DisplayName("[M-104b] describeAliasTarget — keyState가 null이면 UNAVAILABLE")
    void describeAliasTarget_nullKeyState_returnsUnavailable() {
      DescribeKeyResponse response =
          DescribeKeyResponse.builder()
              .keyMetadata(KeyMetadata.builder().keyState((KeyState) null).build())
              .build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenReturn(response);

      assertThat(adapter.describeAliasTarget(ALIAS)).isSameAs(KmsKeyState.UNAVAILABLE);
    }

    @Test
    @DisplayName("[M-104c] describeAliasTarget — NotFoundException → UNAVAILABLE (조용히)")
    void describeAliasTarget_notFoundReturnsUnavailable() {
      when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
          .thenThrow(NotFoundException.builder().message("missing").build());

      KmsKeyState result = adapter.describeAliasTarget(ALIAS);

      assertThat(result).isSameAs(KmsKeyState.UNAVAILABLE);
    }

    @Test
    @DisplayName("[M-104d] describeAliasTarget — 그 외 KmsException은 WARN 후 그대로 전파")
    void describeAliasTarget_kmsExceptionPropagated() {
      when(kmsClient.describeKey(any(DescribeKeyRequest.class)))
          .thenThrow(KmsInvalidStateException.builder().message("err").build());

      assertThatThrownBy(() -> adapter.describeAliasTarget(ALIAS))
          .isInstanceOf(KmsInvalidStateException.class);
    }

    @Test
    @DisplayName("[M-104e] describeAliasTarget — alias는 'alias/' 접두사로 정규화되어 KmsClient에 전달")
    void describeAliasTarget_qualifiesAlias() {
      DescribeKeyResponse response =
          DescribeKeyResponse.builder()
              .keyMetadata(KeyMetadata.builder().keyState(KeyState.ENABLED).build())
              .build();
      when(kmsClient.describeKey(any(DescribeKeyRequest.class))).thenReturn(response);
      ArgumentCaptor<DescribeKeyRequest> captor = ArgumentCaptor.forClass(DescribeKeyRequest.class);

      adapter.describeAliasTarget(ALIAS);

      verify(kmsClient).describeKey(captor.capture());
      assertThat(captor.getValue().keyId()).isEqualTo(QUALIFIED_ALIAS);
    }
  }

  @Nested
  @DisplayName("F. disableKey / scheduleKeyDeletion")
  class DisableAndScheduleDeletion {

    @Test
    @DisplayName("[M-105a] disableKey — 호출 + KmsException 전파")
    void disableKey_callsAwsAndPropagatesException() {
      adapter.disableKey(KMS_KEY_ID);
      verify(kmsClient).disableKey(any(DisableKeyRequest.class));
    }

    @Test
    @DisplayName("[M-105b] disableKey — KmsException은 WARN 후 그대로 전파")
    void disableKey_kmsExceptionPropagated() {
      when(kmsClient.disableKey(any(DisableKeyRequest.class)))
          .thenThrow(KmsInvalidStateException.builder().message("err").build());

      assertThatThrownBy(() -> adapter.disableKey(KMS_KEY_ID))
          .isInstanceOf(KmsInvalidStateException.class);
    }

    @Test
    @DisplayName("[M-106a] scheduleKeyDeletion — pendingWindowInDays 전달")
    void scheduleKeyDeletion_passesPendingWindow() {
      ArgumentCaptor<ScheduleKeyDeletionRequest> captor =
          ArgumentCaptor.forClass(ScheduleKeyDeletionRequest.class);

      adapter.scheduleKeyDeletion(KMS_KEY_ID, 30);

      verify(kmsClient).scheduleKeyDeletion(captor.capture());
      ScheduleKeyDeletionRequest req = captor.getValue();
      assertThat(req.keyId()).isEqualTo(KMS_KEY_ID);
      assertThat(req.pendingWindowInDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("[M-106b] scheduleKeyDeletion — KmsException 그대로 전파")
    void scheduleKeyDeletion_kmsExceptionPropagated() {
      when(kmsClient.scheduleKeyDeletion(any(ScheduleKeyDeletionRequest.class)))
          .thenThrow(KmsInvalidStateException.builder().message("err").build());

      assertThatThrownBy(() -> adapter.scheduleKeyDeletion(KMS_KEY_ID, 7))
          .isInstanceOf(KmsInvalidStateException.class);
    }
  }

  @Nested
  @DisplayName("G. @ConditionalOnProperty gating ([M-36])")
  class ProfileGating {

    @Test
    @DisplayName("[M-36] KmsKeyLifecycleAdapter는 @ConditionalOnProperty(web3.kms.enabled=true) 보유")
    void adapter_isKmsEnabledGated() {
      ConditionalOnProperty annotation =
          KmsKeyLifecycleAdapter.class.getAnnotation(ConditionalOnProperty.class);

      assertThat(annotation).isNotNull();
      String[] names = annotation.name().length > 0 ? annotation.name() : annotation.value();
      assertThat(names).containsExactly("web3.kms.enabled");
      assertThat(annotation.havingValue()).isEqualTo("true");
      assertThat(annotation.matchIfMissing()).isFalse();
    }
  }
}
