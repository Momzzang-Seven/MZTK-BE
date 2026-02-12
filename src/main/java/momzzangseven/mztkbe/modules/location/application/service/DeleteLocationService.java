package momzzangseven.mztkbe.modules.location.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.location.LocationAlreadyDeletedException;
import momzzangseven.mztkbe.global.error.location.LocationNotFoundException;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.DeleteLocationResult;
import momzzangseven.mztkbe.modules.location.application.port.in.DeleteLocationUseCase;
import momzzangseven.mztkbe.modules.location.application.port.out.DeleteLocationPort;
import momzzangseven.mztkbe.modules.location.application.port.out.LoadLocationPort;
import momzzangseven.mztkbe.modules.location.domain.model.Location;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delete Location Service
 *
 * <p>사용자가 직접 요청한 위치 삭제 (Hard Delete)
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DeleteLocationService implements DeleteLocationUseCase {
  private final LoadLocationPort loadLocationPort;
  private final DeleteLocationPort deleteLocationPort;

  @Override
  public DeleteLocationResult execute(DeleteLocationCommand command) {
    log.info(
        "Location deletion requested: userId={}, locationId={}",
        command.userId(),
        command.locationId());

    command.validate();

    // 1. Location 조회
    Location location =
        loadLocationPort
            .findByLocationId(command.locationId())
            .orElseThrow(
                () ->
                    new LocationNotFoundException(
                        "Location not found: id=" + command.locationId()));

    // 2. 권한 검증
    if (!location.isOwnedBy(command.userId())) {
      log.warn(
          "Unauthorized location deletion attempt: userId={}, locationId={}, ownerId={}",
          command.userId(),
          command.locationId(),
          location.getUserId());
      throw new UserNotAuthenticatedException("You can only delete your own locations");
    }

    // 3. 이미 삭제되었는지 확인 (Soft Delete 상태)
    if (location.isDeleted()) {
      log.warn(
          "Attempted to delete already soft-deleted location: locationId={}", command.locationId());
      throw new LocationAlreadyDeletedException(
          "Location is already deleted: id=" + command.locationId());
    }

    // 4. Hard Delete 수행
    deleteLocationPort.deleteById(command.locationId());
    log.info(
        "Location deleted successfully: locationId={}, userId={}",
        command.locationId(),
        command.userId());

    // 5. 삭제 결과 반환
    return DeleteLocationResult.from(location);
  }
}
