package kohgylw.kiftd.server.service;

import kohgylw.kiftd.server.model.Node;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 用于ocr识别
 */
public interface ParseService {
    /**
     * ocr识别
     */
    String ocrImgByUrl(File file);

    Node parseImg(String fileId) throws IOException;

    Node addIndexFile(Node node) throws IOException, SolrServerException;

    void deleteIndexFile(String id) throws IOException, SolrServerException;

    Node upadteIndexFile(Node node) throws IOException, SolrServerException;

    List<Node> queuryFile(String keyword) throws IOException, SolrServerException;

    String ocrImgByLocal(File file);
}
