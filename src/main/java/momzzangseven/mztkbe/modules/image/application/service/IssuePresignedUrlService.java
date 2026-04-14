package momzzangseven.mztkbe.modules.image.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.DataProcessingMismatchException;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlCommand;
import momzzangseven.mztkbe.modules.image.application.dto.IssuePresignedUrlResult;
import momzzangseven.mztkbe.modules.image.application.dto.PresignedUrlItem;
import momzzangseven.mztkbe.modules.image.application.port.in.IssuePresignedUrlUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.GeneratePresignedUrlPort;
import momzzangseven.mztkbe.modules.image.application.port.out.PresignedUrlWithKey;
import momzzangseven.mztkbe.modules.image.application.port.out.SaveImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.AllowedImageExtension;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Note on transaction scope:
// S3Presigner.presignPutObject() performs local HMAC computation only (no network call), so
// holding the DB connection during Phase 1 has negligible real-world impact. However, to make
// the intent clear and isolate the failure boundary, the two phases are kept structurally
// separate. If a true network-bound S3 call is introduced in the future, the presigned-URL
// generation phase should be moved outside the @Transactional boundary (e.g., via
// TransactionTemplate or a dedicated non-transactional Spring bean).

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
   * ImageSpec is a value object that ties up a resolved reference type, uuid, extension, and
   * original filename.
   */
  private record ImageSpec(
      ImageReferenceType referenceType, String uuid, String extension, String originalFilename) {}

  @Override
  @Transactional
  public IssuePresignedUrlResult execute(IssuePresignedUrlCommand command) {
    command.validate();

    List<ImageSpec> specs = buildImageSpecs(command);

    // Phase 1: Generate presigned URLs (local HMAC computation — no network, no DB connection).
    // Each URL is paired with its tmp object key.
    List<PresignedUrlWithKey> presignedUrls = generatePresignedUrls(specs);

    // Phase 2: Persist PENDING image records to DB.
    // saveAll returns the persisted images with DB-generated IDs.
    List<Image> pendingImages = buildPendingImages(command.userId(), specs, presignedUrls);
    List<Image> savedImages = saveImagePort.saveAll(pendingImages);

    // Phase 3: Assemble final result items, pairing each saved image ID with its presigned URL.
    List<PresignedUrlItem> items = assembleItems(savedImages, presignedUrls);

    return IssuePresignedUrlResult.of(items);
  }

  /**
   * Generates a presigned PUT URL for every spec.
   *
   * <p>This is purely a local HMAC computation (no network call). It is separated from the DB
   * persistence phase to make the intent clear and simplify future refactoring if a real network
   * bound S3 call is introduced.
   *
   * @param specs list of resolved image specs
   * @return list of {@link PresignedUrlWithKey}, one per spec
   */
  private List<PresignedUrlWithKey> generatePresignedUrls(List<ImageSpec> specs) {
    List<PresignedUrlWithKey> urls = new ArrayList<>();
    for (ImageSpec spec : specs) {
      urls.add(
          generatePresignedUrlPort.generatePutPresignedUrl(
              spec.referenceType(), spec.uuid(), spec.extension()));
    }
    return urls;
  }

  /**
   * Builds the list of PENDING {@link Image} domain objects from the given specs.
   *
   * @param userId authenticated user ID
   * @param specs list of resolved image specs
   * @param presignedUrls presigned URL results providing the tmp object key per spec
   * @return list of unsaved {@link Image} domain objects
   */
  private List<Image> buildPendingImages(
      Long userId, List<ImageSpec> specs, List<PresignedUrlWithKey> presignedUrls) {
    if (specs.size() != presignedUrls.size()) {
      throw new DataProcessingMismatchException(
          "Specs and presigned URLs do not match. This could not be happened in any case.");
    }

    List<Image> images = new ArrayList<>();
    int imgOrder = 0;
    for (int i = 0; i < specs.size(); i++) {
      ImageSpec spec = specs.get(i);
      String tmpObjectKey = presignedUrls.get(i).tmpObjectKey();
      images.add(Image.createPending(userId, spec.referenceType(), tmpObjectKey, ++imgOrder));
    }
    return images;
  }

  /**
   * Assembles the final result items by correlating each saved image with its presigned URL via
   * {@code tmpObjectKey}.
   *
   * <p>Uses {@code tmpObjectKey} as a natural correlation key rather than relying on index-based
   * ordering, which would implicitly depend on the undefined ordering contract of {@code
   * SaveImagePort.saveAll}.
   *
   * @param savedImages persisted images with DB-generated IDs
   * @param presignedUrls presigned URL results, each carrying its tmpObjectKey
   * @return list of {@link PresignedUrlItem} containing imageId, presignedUrl, and tmpObjectKey,
   *     ordered to match the presignedUrls input list
   */
  private List<PresignedUrlItem> assembleItems(
      List<Image> savedImages, List<PresignedUrlWithKey> presignedUrls) {
    Map<String, Long> imageIdByTmpKey =
        savedImages.stream().collect(Collectors.toMap(Image::getTmpObjectKey, Image::getId));

    return presignedUrls.stream()
        .map(
            url -> {
              Long imageId = imageIdByTmpKey.get(url.tmpObjectKey());
              if (imageId == null) {
                throw new DataProcessingMismatchException(
                    "No saved image found for tmpObjectKey: "
                        + url.tmpObjectKey()
                        + ". This could not be happened in any case.");
              }
              return new PresignedUrlItem(imageId, url.presignedUrl(), url.tmpObjectKey());
            })
        .toList();
  }

  /**
   * Dispatches to the appropriate spec-building strategy based on the reference type.
   *
   * @param command validated command
   * @return list of ImageSpec entries (could be larger than the input filename count for MARKET)
   */
  private List<ImageSpec> buildImageSpecs(IssuePresignedUrlCommand command) {
    if (command.referenceType() == ImageReferenceType.MARKET_CLASS) {
      return buildMarketClassSpecs(command.imageFilenames());
    }
    if (command.referenceType() == ImageReferenceType.MARKET_STORE) {
      return buildMarketStoreSpecs(command.imageFilenames());
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
      specs.add(new ImageSpec(refType, uuid, ext, filename));
    }
    return specs;
  }

  /**
   * Builds n+1 ImageSpec entries for n marketplace class filenames.
   *
   * <ul>
   *   <li>First filename → MARKET_CLASS_THUMB spec + MARKET_CLASS_DETAIL spec (2 entries)
   *   <li>Each remaining filename → MARKET_CLASS_DETAIL spec (1 entry each)
   * </ul>
   *
   * @param filenames list of original filenames from the request (size 1–5)
   * @return list of ImageSpec entries with size = filenames.size() + 1
   */
  private List<ImageSpec> buildMarketClassSpecs(List<String> filenames) {
    List<ImageSpec> specs = new ArrayList<>();

    String firstFile = filenames.get(0);
    String firstExt = AllowedImageExtension.extractExtension(firstFile);

    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_CLASS_THUMB,
            UUID.randomUUID().toString(),
            firstExt,
            firstFile));
    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_CLASS_DETAIL,
            UUID.randomUUID().toString(),
            firstExt,
            firstFile));

    for (int i = 1; i < filenames.size(); i++) {
      String file = filenames.get(i);
      String ext = AllowedImageExtension.extractExtension(file);
      specs.add(
          new ImageSpec(
              ImageReferenceType.MARKET_CLASS_DETAIL, UUID.randomUUID().toString(), ext, file));
    }

    return specs;
  }

  /**
   * Builds n+1 ImageSpec entries for n marketplace store filenames.
   *
   * <ul>
   *   <li>First filename → MARKET_STORE_THUMB spec + MARKET_STORE_DETAIL spec (2 entries)
   *   <li>Each remaining filename → MARKET_STORE_DETAIL spec (1 entry each)
   * </ul>
   *
   * @param filenames list of original filenames from the request (size 1–5)
   * @return list of ImageSpec entries with size = filenames.size() + 1
   */
  private List<ImageSpec> buildMarketStoreSpecs(List<String> filenames) {
    List<ImageSpec> specs = new ArrayList<>();

    String firstFile = filenames.get(0);
    String firstExt = AllowedImageExtension.extractExtension(firstFile);

    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_STORE_THUMB,
            UUID.randomUUID().toString(),
            firstExt,
            firstFile));
    specs.add(
        new ImageSpec(
            ImageReferenceType.MARKET_STORE_DETAIL,
            UUID.randomUUID().toString(),
            firstExt,
            firstFile));

    for (int i = 1; i < filenames.size(); i++) {
      String file = filenames.get(i);
      String ext = AllowedImageExtension.extractExtension(file);
      specs.add(
          new ImageSpec(
              ImageReferenceType.MARKET_STORE_DETAIL, UUID.randomUUID().toString(), ext, file));
    }

    return specs;
  }
}
