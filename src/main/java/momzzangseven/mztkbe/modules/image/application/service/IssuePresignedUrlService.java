package momzzangseven.mztkbe.modules.image.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.dto.PresignedUrlItem;
import momzzangseven.mztkbe.modules.image.application.port.in.IssuePresignedUrlUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
import momzzangseven.mztkbe.modules.image.application.port.out.SaveImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.AllowedImageExtension;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates presigned URL generation and PENDING image record creation.
 *
 * <p>Standard flow (non-MARKET): 1. Validate command (count limit, extension whitelist) 2. Build
 * one ImageSpec per filename (referenceType + UUID-based tmp key) 3. Generate presigned PUT URLs
 * for each spec 4. Persist PENDING Image records 5. Return assembled result
 *
 * <p>MARKET flow: The first filename is expanded into two specs — one for the
 * thumbnail(MARKET_THUMB) and one for the detail view (MARKET_DETAIL). All remaining filenames
 * produce a single MARKET_DETAIL spec each. For n input filenames, n+1 presigned URLs and DB rows
 * are produced (n <= 5).
 */
@Service
@RequiredArgsConstructor
public class IssuePresignedUrlService implements IssuePresignedUrlUseCase {

  private final GeneratePresignedUrlPort generatePresignedUrlPort;
  private final SaveImagePort saveImagePort;

  /**
   * Internal value object that pairs a resolved reference type and tmp object key with its source
   * filename. Used to decouple key-building from URL generation and DB persistence.
   */
  private record ImageSpec(
      ImageReferenceType referenceType, String tmpObjectKey, String originalFilename) {}

  @Override
  @Transactional
  public IssuePresignedUrlResult execute(IssuePresignedUrlCommand command) {
    command.validate();

    List<ImageSpec> specs = buildImageSpecs(command);

    List<PresignedUrlItem> items = new ArrayList<>();
    List<Image> images = new ArrayList<>();

    for (ImageSpec spec : specs) {
      // Get presigned url for each spec
      String presignedUrl =
          generatePresignedUrlPort.generatePutPresignedUrl(
              spec.tmpObjectKey(), resolveContentType(spec.originalFilename()));

      // Build item to return to the client.
      items.add(new PresignedUrlItem(presignedUrl, spec.tmpObjectKey()));

      // Build image object to save into DB.
      images.add(Image.createPending(command.userId(), spec.referenceType(), spec.tmpObjectKey()));
    }

    saveImagePort.saveAll(images);

    return IssuePresignedUrlResult.of(items);
  }

  /**
   * Dispatches to the appropriate spec-building strategy based on the reference type.
   *
   * @param command validated command
   * @return list of ImageSpec entries (could be larger than the input filename count for MARKET)
   */
  private List<ImageSpec> buildImageSpecs(IssuePresignedUrlCommand command) {
    if (command.referenceType() == ImageReferenceType.MARKET) {
      return buildMarketSpecs(command.imageFilenames());
    }
    return buildStandardSpecs(command.referenceType(), command.imageFilenames());
  }

  /**
   * Builds one ImageSpec per filename using the same reference type for all entries.
   *
   * @param refType reference type to use for every spec
   * @param filenames list of original filenames from the request
   * @return list of ImageSpec, one per filename
   */
  private List<ImageSpec> buildStandardSpecs(ImageReferenceType refType, List<String> filenames) {
    List<ImageSpec> specs = new ArrayList<>();
    for (String filename : filenames) {
      String uuid = UUID.randomUUID().toString();
      String ext = AllowedImageExtension.extractExtension(filename);
      specs.add(new ImageSpec(refType, refType.buildTmpObjectKey(uuid, ext), filename));
    }
    return specs;
  }

  /**
   * Builds n+1 ImageSpec entries for n marketplace filenames.
   *
   * <ul>
   *   <li>First filename → MARKET_THUMB spec + MARKET_DETAIL spec (2 entries)
   *   <li>Each remaining filename → MARKET_DETAIL spec (1 entry each)
   * </ul>
   *
   * @param filenames list of original filenames from the request (size 1–5)
   * @return list of ImageSpec entries with size = filenames.size() + 1
   */
  private List<ImageSpec> buildMarketSpecs(List<String> filenames) {
    List<ImageSpec> specs = new ArrayList<>();

    String firstFile = filenames.get(0);
    String firstExt = AllowedImageExtension.extractExtension(firstFile);

    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_THUMB,
            ImageReferenceType.MARKET_THUMB.buildTmpObjectKey(
                UUID.randomUUID().toString(), firstExt),
            firstFile));
    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_DETAIL,
            ImageReferenceType.MARKET_DETAIL.buildTmpObjectKey(
                UUID.randomUUID().toString(), firstExt),
            firstFile));

    for (int i = 1; i < filenames.size(); i++) {
      String file = filenames.get(i);
      String ext = AllowedImageExtension.extractExtension(file);
      specs.add(
          new ImageSpec(
              ImageReferenceType.MARKET_DETAIL,
              ImageReferenceType.MARKET_DETAIL.buildTmpObjectKey(UUID.randomUUID().toString(), ext),
              file));
    }

    return specs;
  }

  /**
   * Resolves the MIME content type from a filename extension.
   *
   * @param filename original filename
   * @return MIME type string
   */
  private String resolveContentType(String filename) {
    String ext = AllowedImageExtension.extractExtension(filename);
    return switch (ext) {
      case "png" -> "image/png";
      case "gif" -> "image/gif";
      default -> "image/jpeg";
    };
  }
}
