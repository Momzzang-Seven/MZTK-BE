package momzzangseven.mztkbe.modules.image.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.modules.image.application.dto.LambdaCallbackCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.domain.vo.LambdaCallbackStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the Lambda image-processing webhook callback. Transitions the image status (PENDING →
 * COMPLETED or FAILED) within a single transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandleLambdaCallbackService implements HandleLambdaCallbackUseCase {

  private final LoadImagePort loadImagePort;
  private final UpdateImagePort updateImagePort;

  @Override
  @Transactional
  public void execute(LambdaCallbackCommand command) {
    command.validate();
    log.info(
        "Lambda callback status={}, tmpObjectKey={}, finalObjectKey={}, errorReason={}",
        command.status(),
        command.tmpObjectKey(),
        command.finalObjectKey(),
        command.errorReason());

    Image image =
        loadImagePort
            .findByTmpObjectKeyForUpdate(command.tmpObjectKey())
            .orElseThrow(() -> new ImageNotFoundException(command.tmpObjectKey()));

    // Idempotency: already COMPLETED → skip silently
    if (image.getStatus() == ImageStatus.COMPLETED) {
      log.info(
          "Lambda callback ignored (already COMPLETED): tmpObjectKey={}", command.tmpObjectKey());
      return;
    }

    Image updated;
    if (command.status() == LambdaCallbackStatus.COMPLETED) {
      updated = image.complete(command.finalObjectKey());
    } else {
      updated = image.fail(command.errorReason());
    }

    updateImagePort.update(updated);
  }
}
