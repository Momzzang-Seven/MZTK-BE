package momzzangseven.mztkbe.modules.verification.infrastructure.analysis.adapter;

import dev.matrixlab.webp4j.WebPCodec;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import momzzangseven.mztkbe.modules.verification.application.port.out.ImageCodecSupportPort;
import org.springframework.stereotype.Component;

@Component
public class ImageIoCodecSupportAdapter implements ImageCodecSupportPort {

  private static final Set<String> HEIF_SUFFIXES = Set.of("heic", "heif");

  @Override
  public boolean isHeifDecodeAvailable() {
    return HEIF_SUFFIXES.stream().anyMatch(this::hasImageReaderForSuffix);
  }

  @Override
  public boolean isWebpWriteAvailable() {
    try {
      return WebPCodec.isAvailable();
    } catch (Throwable ex) {
      return false;
    }
  }

  boolean hasImageReaderForSuffix(String suffix) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(suffix);
    if (readers.hasNext()) {
      return true;
    }
    return Arrays.stream(ImageIO.getReaderFormatNames())
        .map(it -> it.toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet())
        .contains(suffix.toLowerCase(Locale.ROOT));
  }
}
