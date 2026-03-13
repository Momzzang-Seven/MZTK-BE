package momzzangseven.mztkbe.modules.verification.infrastructure.external.exif.adapter;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.verification.application.dto.ExifMetadataInfo;
import momzzangseven.mztkbe.modules.verification.application.port.out.ExifMetadataPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetadataExtractorExifAdapter implements ExifMetadataPort {

  private final ZoneId appZoneId;

  @Override
  public Optional<ExifMetadataInfo> extract(byte[] bytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
      ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      if (directory == null) {
        return Optional.empty();
      }
      Date originalDate = directory.getDateOriginal();
      if (originalDate == null) {
        return Optional.empty();
      }
      LocalDateTime shotAt = LocalDateTime.ofInstant(originalDate.toInstant(), appZoneId);
      return Optional.of(new ExifMetadataInfo(shotAt));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }
}
