package momzzangseven.mztkbe.modules.verification.infrastructure.external.exif.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MetadataExtractorExifAdapterTest {

  @Test
  void returnsEmptyWhenExifIsMissing() {
    MetadataExtractorExifAdapter adapter =
        new MetadataExtractorExifAdapter(ZoneId.of("Asia/Seoul"));

    assertThat(adapter.extract(new ByteArrayInputStream(new byte[] {1, 2, 3}))).isEmpty();
  }

  @Test
  void parsesDateOriginalUsingAppZoneEvenWhenJvmDefaultTimezoneDiffers() throws Exception {
    TimeZone previousDefault = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    try {
      Metadata metadata = new Metadata();
      ExifSubIFDDirectory directory = new ExifSubIFDDirectory();
      directory.setString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, "2026:03:13 20:15:00");
      metadata.addDirectory(directory);

      try (MockedStatic<ImageMetadataReader> imageMetadataReader =
          mockStatic(ImageMetadataReader.class)) {
        imageMetadataReader
            .when(() -> ImageMetadataReader.readMetadata(any(InputStream.class)))
            .thenReturn(metadata);

        MetadataExtractorExifAdapter adapter =
            new MetadataExtractorExifAdapter(ZoneId.of("Asia/Seoul"));

        assertThat(adapter.extract(new ByteArrayInputStream(new byte[] {1, 2, 3})))
            .hasValueSatisfying(
                info ->
                    assertThat(info.shotAtKst()).isEqualTo(LocalDateTime.of(2026, 3, 13, 20, 15)));
      }
    } finally {
      TimeZone.setDefault(previousDefault);
    }
  }
}
