package momzzangseven.mztkbe.modules.web3.shared.application.util;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.global.error.web3.KmsKeyDescribeFailedException;
import momzzangseven.mztkbe.global.error.web3.KmsSignFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kms.model.DisabledException;
import software.amazon.awssdk.services.kms.model.InvalidKeyUsageException;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.KmsInvalidStateException;
import software.amazon.awssdk.services.kms.model.NotFoundException;
import software.amazon.awssdk.services.kms.model.UnsupportedOperationException;

@DisplayName("KmsClientErrorClassifier")
class KmsClientErrorClassifierTest {

  private static KmsException kmsExWithCode(String errorCode) {
    return (KmsException)
        KmsException.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(errorCode).build())
            .build();
  }

  @Test
  @DisplayName("AccessDeniedException 코드 → terminal")
  void accessDenied_isTerminal() {
    KmsSignFailedException ex =
        new KmsSignFailedException("kms denied", kmsExWithCode("AccessDeniedException"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("DisabledException → terminal")
  void disabled_isTerminal() {
    DisabledException kmsEx =
        (DisabledException)
            DisabledException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("DisabledException").build())
                .build();
    KmsSignFailedException ex = new KmsSignFailedException("kms disabled", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("KmsInvalidStateException → terminal")
  void kmsInvalidState_isTerminal() {
    KmsInvalidStateException kmsEx =
        (KmsInvalidStateException)
            KmsInvalidStateException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("KMSInvalidStateException").build())
                .build();
    KmsSignFailedException ex = new KmsSignFailedException("kms invalid state", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("InvalidKeyUsageException → terminal")
  void invalidKeyUsage_isTerminal() {
    InvalidKeyUsageException kmsEx =
        (InvalidKeyUsageException)
            InvalidKeyUsageException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("InvalidKeyUsageException").build())
                .build();
    KmsSignFailedException ex = new KmsSignFailedException("kms key cannot sign", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("NotFoundException → terminal")
  void notFound_isTerminal() {
    NotFoundException kmsEx =
        (NotFoundException)
            NotFoundException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NotFoundException").build())
                .build();
    KmsSignFailedException ex = new KmsSignFailedException("kms key not found", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("UnsupportedOperationException → terminal")
  void unsupportedOperation_isTerminal() {
    UnsupportedOperationException kmsEx =
        (UnsupportedOperationException)
            UnsupportedOperationException.builder()
                .awsErrorDetails(
                    AwsErrorDetails.builder().errorCode("UnsupportedOperationException").build())
                .build();
    KmsSignFailedException ex = new KmsSignFailedException("kms unsupported", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("Throttling-style 코드 → retryable")
  void throttling_isNotTerminal() {
    KmsSignFailedException ex =
        new KmsSignFailedException("kms throttled", kmsExWithCode("ThrottlingException"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }

  @Test
  @DisplayName("SdkClientException cause (non-KMS) → retryable")
  void sdkClientException_isNotTerminal() {
    KmsSignFailedException ex =
        new KmsSignFailedException("network issue", SdkClientException.create("connect timeout"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }

  @Test
  @DisplayName("null cause → retryable")
  void nullCause_isNotTerminal() {
    KmsSignFailedException ex = new KmsSignFailedException("opaque");
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }

  @Test
  @DisplayName("KmsException with null awsErrorDetails → retryable")
  void kmsExceptionWithoutDetails_isNotTerminal() {
    KmsException kmsEx = (KmsException) KmsException.builder().message("opaque").build();
    KmsSignFailedException ex = new KmsSignFailedException("no details", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }

  @Test
  @DisplayName("[M-11] non-KmsException cause (RuntimeException) → retryable")
  void nonKmsExceptionCause_isNotTerminal() {
    KmsSignFailedException ex =
        new KmsSignFailedException("garbage cause", new RuntimeException("garbage"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }

  @Test
  @DisplayName("KmsKeyDescribeFailedException + AccessDenied → terminal")
  void describeKeyAccessDenied_isTerminal() {
    KmsKeyDescribeFailedException ex =
        new KmsKeyDescribeFailedException(
            "describe denied", kmsExWithCode("AccessDeniedException"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("KmsKeyDescribeFailedException + NotFound → terminal")
  void describeKeyNotFound_isTerminal() {
    NotFoundException kmsEx =
        (NotFoundException)
            NotFoundException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NotFoundException").build())
                .build();
    KmsKeyDescribeFailedException ex = new KmsKeyDescribeFailedException("not found", kmsEx);
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isTrue();
  }

  @Test
  @DisplayName("KmsKeyDescribeFailedException + Throttling → retryable")
  void describeKeyThrottling_isNotTerminal() {
    KmsKeyDescribeFailedException ex =
        new KmsKeyDescribeFailedException("throttled", kmsExWithCode("ThrottlingException"));
    assertThat(KmsClientErrorClassifier.isTerminal(ex)).isFalse();
  }
}
