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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates presigned URL generation and PENDING image record creation.
 *
 * <p>Flow: 1. Validate command (count limit, extension whitelist) 2. Generate tmp object keys
 * (referenceType prefix + UUID + ext) 3. Call S3 to generate presigned PUT URLs 4. Persist PENDING
 * ImageEntity records to DB 5. Return assembled result
 */
@Service
@RequiredArgsConstructor
public class IssuePresignedUrlService implements IssuePresignedUrlUseCase {
  private final GeneratePresignedUrlPort generatePresignedUrlPort;
  private final SaveImagePort saveImagePort;

  @Override
  @Transactional
  public IssuePresignedUrlResult execute(IssuePresignedUrlCommand command) {
    // Command validation.
    command.validate();

    // Make tmp object key for each image.
    List<String> tmpObjectKeys = buildTmpObjectKeys(command);

    // Get presigned Url for each image.
    List<String> presignedUrls = generatePresignedUrls(tmpObjectKeys, command.imageFilenames());

    // Make list of image objects, status=PENDING.
    List<Image> images = buildPendingImages(command, tmpObjectKeys);

    // Save the images to DB.
    saveImagePort.saveAll(images);

    // Assemble Item object, contains pairs of presignedUrl and tmpObjectkey.
    List<PresignedUrlItem> items = assembleItems(presignedUrls, tmpObjectKeys);

    return IssuePresignedUrlResult.of(items);
  }

  /**
   * Helper method making tmp object key. The key doesn't contain raw filename. every key is made up
   * of UUID.
   *
   * @param command
   * @return List of tmp object keys
   */
  private List<String> buildTmpObjectKeys(IssuePresignedUrlCommand command) {
    List<String> keys = new ArrayList<>();
    for (String filename : command.imageFilenames()) {
      String uuid = UUID.randomUUID().toString();
      String ext = AllowedImageExtension.extractExtension(filename);
      String key = command.referenceType().buildTmpObjectKey(uuid, ext);
      keys.add(key);
    }
    return keys;
  }

  /**
   * Helper method generating presigned urls for each image.
   *
   * @param objectKeys List of object keys
   * @param filenames List of filenames
   * @return List of presigned urls
   */
  private List<String> generatePresignedUrls(List<String> objectKeys, List<String> filenames) {
    List<String> urls = new ArrayList<>();
    for (int i = 0; i < objectKeys.size(); i++) {
      String contentType = resolveContentType(filenames.get(i));
      urls.add(generatePresignedUrlPort.generatePutPresignedUrl(objectKeys.get(i), contentType));
    }
    return urls;
  }

  /**
   * Helper method building pending images.
   *
   * @param command Command object
   * @param tmpObjectKeys List of tmp object keys
   * @return List of pending images
   */
  private List<Image> buildPendingImages(
      IssuePresignedUrlCommand command, List<String> tmpObjectKeys) {
    List<Image> images = new ArrayList<>();
    for (String key : tmpObjectKeys) {
      images.add(Image.createPending(command.userId(), command.referenceType(), key));
    }
    return images;
  }

  /**
   * Helper method assembling presigned url items.
   *
   * @param urls List of presigned urls
   * @param keys List of tmp object keys
   * @return List of presigned url items
   */
  private List<PresignedUrlItem> assembleItems(List<String> urls, List<String> keys) {
    List<PresignedUrlItem> items = new ArrayList<>();
    for (int i = 0; i < urls.size(); i++) {
      items.add(new PresignedUrlItem(urls.get(i), keys.get(i)));
    }
    return items;
  }

  /**
   * Helper method resolving content type from filename.
   *
   * @param filename Filename
   * @return Content type
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
