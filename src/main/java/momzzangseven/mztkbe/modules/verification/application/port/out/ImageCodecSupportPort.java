package momzzangseven.mztkbe.modules.verification.application.port.out;

public interface ImageCodecSupportPort {

  boolean isHeifDecodeAvailable();

  boolean isWebpWriteAvailable();
}
