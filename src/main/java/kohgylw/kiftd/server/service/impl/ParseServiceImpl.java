package kohgylw.kiftd.server.service.impl;

import com.google.gson.*;
import kohgylw.kiftd.server.mapper.NodeMapper;
import kohgylw.kiftd.server.model.Node;
import kohgylw.kiftd.server.service.ParseService;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.FileBlockUtil;
import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.buildobjects.process.ProcBuilder;
import org.buildobjects.process.ProcResult;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service

public class ParseServiceImpl implements ParseService {

    @Resource
    private NodeMapper fm;
    @Resource
    private FileBlockUtil fileBlockUtil;


    @Override
    public Node parseImg(String fileId) throws IOException {
        Node file=fm.queryById(fileId);
        String fileName=file.getFileName();
        final String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        if((Integer.valueOf(file.getFileSize())<=2)&&
                (suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("gif") || suffix.equals("bmp")
                || suffix.equals("png")))
        {
            File oldFile=fileBlockUtil.getFileFromBlocks(file);
            if(Objects.nonNull(oldFile))
            {
                File newFile = new File(ConfigureReader.instance().getTemporaryfilePath()+File.separator+file.getFileName());
                FileUtils.copyFile(oldFile,newFile);
                String data=null;
                try
                {
                    data= ocrImgByLocal(newFile);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                if(data!=null&&!"".equals(data))
                {
                    file.setParseContent(data);
                    file.setParseStatus("2");
                }
                else
                {
                    //解析失败
                    file.setParseStatus("3");
                }
                FileUtils.forceDeleteOnExit(newFile);
            }
        }
        return file;
    }

    @Override
    public String ocrImgByLocal(File file) {

        ApplicationHome h = new ApplicationHome(getClass());
        File jarDir = h.getSource();
        String command="python3.6";
        if(System.getProperty("os.name").contains("indows"))
        {
            command="python";
        }
        ProcResult procResult= new ProcBuilder(command)
                        .withArgs(jarDir.getParentFile().getAbsolutePath()+File.separator+"cnocr"+File.separator+"ocr.py",file.getAbsolutePath())
                        .withTimeoutMillis(1000*120)
                        .run();
        int exitValue=procResult.getExitValue();
        if(exitValue==0)
        {
            String resultStr=procResult.getOutputString();
            JsonParser parser = new JsonParser();
            JsonArray result = parser.parse(resultStr).getAsJsonArray();
            StringBuffer content = new StringBuffer();
            for (JsonElement r:result)
            {
                JsonArray charArray=r.getAsJsonArray();
                for(JsonElement c :charArray)
                {
                    content.append(c.getAsString());
                }
            }
            return content.toString();
        }
        else{
            String resultStr=procResult.getErrorString();
            System.out.println("ocr error:"+resultStr);
        }
        return null;
    }

    @Override
    public String ocrImgByUrl(File file) {
        String ocrUrl=ConfigureReader.instance().getServerp().getProperty("ocr.url");
        FileSystemResource resource = new FileSystemResource(file);
        MultiValueMap<String, Object> param = new LinkedMultiValueMap<>();
        param.add("myfile", resource);
        HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<MultiValueMap<String, Object>>(param);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(120000);
        requestFactory.setReadTimeout(120000);
        RestTemplate restTemplate=new RestTemplate(requestFactory);

        ResponseEntity<String> responseEntity = restTemplate.exchange(ocrUrl, HttpMethod.POST, httpEntity, String.class);
        if(responseEntity.getStatusCode()==HttpStatus.OK)
        {
            JsonParser parser = new JsonParser();
            JsonArray result = parser.parse(responseEntity.getBody()).getAsJsonArray();
            StringBuffer content = new StringBuffer();
            for (JsonElement r:result)
            {
                content.append(r.getAsJsonArray().get(1).getAsString());
            }
            return content.toString();
        }
        return null;
    }

    @Override
    public List<Node> queuryFile(String keyword) throws IOException, SolrServerException {
        List<Node> nodeList = new ArrayList<>();
        String q="fileName:"+keyword+" || parseContent:"+keyword;
        final SolrQuery query = new SolrQuery(q);
        query.setRows(50);
        final QueryResponse response = getSolr().query( query);
        final SolrDocumentList documents =response.getResults();
        for(SolrDocument document : documents) {
            Node n = new Node();
            n.setId(Integer.valueOf((String)document.getFirstValue("id")));
            n.setFileId((String)document.getFirstValue("fileId"));
            n.setFileName((String)document.getFirstValue("fileName"));
            n.setFileSize((String)document.getFirstValue("fileSize"));
            n.setFileParentFolder((String)document.getFirstValue("fileParentFolder"));
            n.setFileCreationDate((String)document.getFirstValue("fileCreationDate"));
            n.setFileCreator((String)document.getFirstValue("fileCreator"));
            n.setFilePath((String)document.getFirstValue("filePath"));
            String parseContent=(String)document.getFirstValue("parseContent");
            if(!StringUtils.isEmpty(parseContent))
            {
                parseContent=shortContent(parseContent,keyword);
                parseContent=parseContent.replaceAll(keyword,"<b>"+keyword+"</b>");
                n.setParseContent(parseContent);
            }
            n.setSearch(true);
            nodeList.add(n);
        }
        Collections.reverse(nodeList);
        return nodeList;
    }

    private String shortContent(String content,String keyword){
        if(content.length()<85)
        {
            return content;
        }
        int index=content.indexOf(keyword);
        if(index<20)
        {
            return content;
        }
        return "..."+content.substring(index-15);
    }

    @Override
    public Node addIndexFile(Node node) throws IOException, SolrServerException {
        SolrClient client=getSolr();
        final UpdateResponse response = client.addBean(node);
        int responseStatus=response.getStatus();
        if(responseStatus==0)
        {
            node.setIsIndex("1");
            client.commit();
        }
        else
        {
            System.out.println("error: 上传到solr失败。"+response);
        }
        return node;
    }

    @Override
    public void deleteIndexFile(String id) throws IOException, SolrServerException {
        SolrClient client=getSolr();
        final UpdateResponse response = client.deleteById(id);
        client.commit();
    }

    @Override
    public Node upadteIndexFile(Node node) throws IOException, SolrServerException {
        deleteIndexFile(node.getFileId());
        addIndexFile(node);
        return node;
    }

    private SolrClient getSolr(){
        final String solrUrl =ConfigureReader.instance().getServerp().getProperty("solr.url");
        return new HttpSolrClient.Builder(solrUrl)
                .withConnectionTimeout(10000)
                .withSocketTimeout(60000)
                .build();
    }

//    public static void main(String[] args) throws IOException {
////        System.out.println(new ParseServiceImpl().parseImg("6443a370-e9fa-4591-8d50-96720d035e17");
////        System.out.println(new ParseServiceImpl().shortContent("由于服务端是严格按照 MQTT 协议规范实现的，上述端侧设备不按规范接入，实际上消息调度不到 MQTT 应用协议层。MQTT 服务端依赖 Keep Alive 机制做超时检测，当一段时间接收不到客户端的心跳和业务消息时，就会触发心跳超时，关闭连接。针对上述两种接入场景，由于 MQTT 的连接流程没有完成，MQTT 协议栈不认为这个是合法的 MQTT 连接，因此心跳保护机制无法对上述 TCP 连接做检测。客户端和服务端都没有主动关闭这个连接，导致 TCP 连接一直保持。","心跳"));
////        System.out.println(System.getProperty("os.name"));
//    }
}
