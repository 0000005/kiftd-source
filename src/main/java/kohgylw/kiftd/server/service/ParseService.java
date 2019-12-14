package kohgylw.kiftd.server.service;

import java.io.File;
import java.io.IOException;

/**
 * 用于ocr识别
 */
public interface ParseService {
    /**
     * ocr识别
     */
    String ocrImg(File file);

    void ocrImg(String fileId) throws IOException;
}
