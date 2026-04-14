package momzzangseven.mztkbe.modules.verification.infrastructure.external.image.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.image.application.dto.GetImageByTmpObjectKeyResult;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImageByTmpObjectKeyUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutUploadLookupAdapterTest {

  @Mock private GetImageByTmpObjectKeyUseCase getImageByTmpObjectKeyUseCase;

  private WorkoutUploadLookupAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new WorkoutUploadLookupAdapter(getImageByTmpObjectKeyUseCase);
  }

  @Test
  void resolvesReadObjectKeyFromFinalObjectKey() {
    when(getImageByTmpObjectKeyUseCase.execute("private/workout/tmp.jpg"))
        .thenReturn(
            Optional.of(
                new GetImageByTmpObjectKeyResult(
                    10L,
                    ImageReferenceType.WORKOUT,
                    "private/workout/tmp.jpg",
                    "private/workout/final.jpg")));

    var resolved = adapter.findByTmpObjectKey("private/workout/tmp.jpg");

    assertThat(resolved).isPresent();
    assertThat(resolved.orElseThrow().readObjectKey()).isEqualTo("private/workout/final.jpg");
  }

  @Test
  void ignoresNonWorkoutReferenceType() {
    when(getImageByTmpObjectKeyUseCase.execute("private/workout/tmp.jpg"))
        .thenReturn(
            Optional.of(
                new GetImageByTmpObjectKeyResult(
                    10L,
                    ImageReferenceType.COMMUNITY_FREE,
                    "private/workout/tmp.jpg",
                    "private/workout/final.jpg")));

    var resolved = adapter.findByTmpObjectKey("private/workout/tmp.jpg");

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolvesLockedWorkoutReference() {
    when(getImageByTmpObjectKeyUseCase.executeForUpdate("private/workout/tmp.jpg"))
        .thenReturn(
            Optional.of(
                new GetImageByTmpObjectKeyResult(
                    10L,
                    ImageReferenceType.WORKOUT,
                    "private/workout/tmp.jpg",
                    "private/workout/final.jpg")));

    var resolved = adapter.findByTmpObjectKeyForUpdate("private/workout/tmp.jpg");

    assertThat(resolved).isPresent();
    assertThat(resolved.orElseThrow().ownerUserId()).isEqualTo(10L);
  }
}
