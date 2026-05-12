package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import org.junit.jupiter.api.Test;

class QnaExecutionIntentResultTest {

  private CreateExecutionIntentResult sampleResult() {
    return new CreateExecutionIntentResult(
        ExecutionResourceType.QUESTION,
        "101",
        ExecutionResourceStatus.PENDING_EXECUTION,
        "intent-1",
        ExecutionIntentStatus.EXPIRED,
        LocalDateTime.of(2026, 4, 14, 10, 0),
        ExecutionMode.EIP7702,
        2,
        null,
        false);
  }

  @Test
  void from_withSignedAtAndValidity_setsSignatureMetaWithComputedExpiry() {
    QnaExecutionIntentResult result =
        QnaExecutionIntentResult.from("QNA_QUESTION_CREATE", sampleResult(), 1_768_224_000L, 900);

    assertThat(result.signatureMeta()).isNotNull();
    assertThat(result.signatureMeta().signedAt()).isEqualTo(1_768_224_000L);
    assertThat(result.signatureMeta().signatureExpiresAt()).isEqualTo(1_768_224_900L);
  }

  @Test
  void from_withNullSignedAtAndNullValidity_yieldsNullSignatureMeta() {
    QnaExecutionIntentResult result =
        QnaExecutionIntentResult.from("QNA_ADMIN_SETTLE", sampleResult(), null, null);

    assertThat(result.signatureMeta()).isNull();
  }

  @Test
  void from_legacyTwoArg_yieldsNullSignatureMeta() {
    QnaExecutionIntentResult result =
        QnaExecutionIntentResult.from("QNA_ADMIN_SETTLE", sampleResult());

    assertThat(result.signatureMeta()).isNull();
  }

  @Test
  void from_withSignedAtButNullValidity_throwsWeb3InvalidInputException() {
    assertThatThrownBy(
            () ->
                QnaExecutionIntentResult.from(
                    "QNA_QUESTION_CREATE", sampleResult(), 1_768_224_000L, null))
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("sigValidityDuration");
  }

  @Test
  void signatureMeta_bothNull_isAllowed() {
    QnaExecutionIntentResult.SignatureMeta meta =
        new QnaExecutionIntentResult.SignatureMeta(null, null);

    assertThat(meta.signedAt()).isNull();
    assertThat(meta.signatureExpiresAt()).isNull();
  }

  @Test
  void signatureMeta_bothNonNull_isAllowed() {
    QnaExecutionIntentResult.SignatureMeta meta =
        new QnaExecutionIntentResult.SignatureMeta(123L, 456L);

    assertThat(meta.signedAt()).isEqualTo(123L);
    assertThat(meta.signatureExpiresAt()).isEqualTo(456L);
  }

  @Test
  void signatureMeta_mixedNullState_isRejected() {
    assertThatThrownBy(() -> new QnaExecutionIntentResult.SignatureMeta(123L, null))
        .isInstanceOf(Web3InvalidInputException.class);
    assertThatThrownBy(() -> new QnaExecutionIntentResult.SignatureMeta(null, 456L))
        .isInstanceOf(Web3InvalidInputException.class);
  }
}
