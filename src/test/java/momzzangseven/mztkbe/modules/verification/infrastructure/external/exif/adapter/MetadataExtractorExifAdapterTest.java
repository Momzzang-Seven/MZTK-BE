package momzzangseven.mztkbe.modules.verification.infrastructure.external.exif.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MetadataExtractorExifAdapterTest {

  @Test
  void returnsEmptyWhenExifIsMissing() {
    MetadataExtractorExifAdapter adapter =
        new MetadataExtractorExifAdapter(ZoneId.of("Asia/Seoul"));

    assertThat(adapter.extract(new ByteArrayInputStream(new byte[] {1, 2, 3}))).isEmpty();
  }
}
