package momzzangseven.mztkbe.modules.image.api.controller;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.image.ImageLambdaUnauthorizedException;
import momzzangseven.mztkbe.global.response.ApiResponse;
import momzzangseven.mztkbe.modules.image.api.dto.LambdaCallbackRequestDTO;
import momzzangseven.mztkbe.modules.image.application.port.in.HandleLambdaCallbackUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal controller receiving Lambda image-processing webhook callbacks. Not intended for public
 * clients — secured via a shared secret header.
 */
@Slf4j
@RestController
@RequestMapping("/internal/images")
@RequiredArgsConstructor
public class ImageInternalController {

  private static final String WEBHOOK_SECRET_HEADER = "X-Lambda-Webhook-Secret";

  @Value("${lambda.webhook.secret}")
  private String webhookSecret;

  private final HandleLambdaCallbackUseCase handleLambdaCallbackUseCase;

  /** Receives Lambda's image processing result and updates the image status in DB. */
  @PostMapping("/lambda-callback")
  public ResponseEntity<ApiResponse<Void>> handleLambdaCallback(
      @RequestHeader(WEBHOOK_SECRET_HEADER) String secret,
      @Valid @RequestBody LambdaCallbackRequestDTO request) {

    verifySecret(secret);
    handleLambdaCallbackUseCase.execute(request.toCommand());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** Validates the webhook secret using constant-time comparison to prevent timing attacks. */
  private void verifySecret(String provided) {
    boolean valid =
        MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8),
            webhookSecret.getBytes(StandardCharsets.UTF_8));
    if (!valid) {
      throw new ImageLambdaUnauthorizedException();
    }
  }
}
