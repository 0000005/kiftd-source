package kohgylw.kiftd.server.service.impl;

import com.google.gson.*;
import kohgylw.kiftd.server.mapper.NodeMapper;
import kohgylw.kiftd.server.model.Node;
import kohgylw.kiftd.server.service.ParseService;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.FileBlockUtil;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.Canvas;
import net.coobird.thumbnailator.geometry.Positions;
import net.coobird.thumbnailator.geometry.Region;
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
import org.json.JSONArray;
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
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;

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
                File newFile = new File(ConfigureReader.instance().getTemporaryfilePath()+File.separator+new Random().nextInt()+"."+suffix);
                FileUtils.copyFile(oldFile,newFile);
                String data=null;
                try
                {
                    data= ocr2String(newFile);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                if(data!=null&&!StringUtils.isEmpty(data))
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
    public String ocr2String(File file) {
        List <File> files=ocrRegionFiles(file);
        StringBuffer result = new StringBuffer();
        for(File f:files)
        {
            String line=ocrImgByLocal(f);
            result.append(line);
            file.delete();
        }
        return result.toString();
    }

    private List<File> ocrRegionFiles(File file) {
        ApplicationHome h = new ApplicationHome(getClass());
        File jarDir = h.getSource();
        String command="python3.6";
        if(System.getProperty("os.name").contains("indows"))
        {
            command="python";
        }
        ProcResult procResult= new ProcBuilder(command)
                .withArgs(jarDir.getParentFile().getAbsolutePath()+File.separator+"darknet-ocr"+File.separator+"region.py",file.getAbsolutePath().trim())
                .withTimeoutMillis(1000*120)
                .run();
        int exitValue=procResult.getExitValue();
        if(exitValue==0)
        {
            String resultStr=procResult.getOutputString();
            return generateRegionImg(resultStr,file);
        }
        return null;
    }

    private List<File> generateRegionImg(String json,File originFile)
    {
        List <File> allFile= new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonObject result = parser.parse(json).getAsJsonObject();
        Integer errCode=result.get("errCode").getAsInt();
        if(errCode==0)
        {
            JsonArray dataArray=result.getAsJsonArray("data");
            if(Objects.nonNull(dataArray))
            {
                for (int i =0;i<dataArray.size();i++)
                {
                    JsonElement d=dataArray.get(i);
                    JsonArray positionArray=d.getAsJsonObject().getAsJsonArray("box");
                    if(Objects.nonNull(positionArray)&&positionArray.size()>0)
                    {
                        String suffix=originFile.getName().substring(originFile.getName().lastIndexOf("."));
                        String fileName=originFile.getName().substring(0,originFile.getName().lastIndexOf("."))+i+suffix;
                        String path=ConfigureReader.instance().getTemporaryfilePath()+File.separator+fileName;
                        try
                        {
                            Map<String,Double> positionData=getPostion(positionArray);
                            Thumbnails.of(originFile.getAbsolutePath())
                                    .rotate(new BigDecimal(positionData.get("angle")).multiply(new BigDecimal(-1)).doubleValue())
                                    .sourceRegion(positionArray.get(0).getAsInt(),positionArray.get(1).getAsInt(),positionData.get("width").intValue(),positionData.get("height").intValue())
                                    .size(positionData.get("width").intValue(),positionData.get("height").intValue())
                                    .addFilter(new Canvas(positionData.get("width").intValue(),positionData.get("height").intValue(), Positions.CENTER, Color.WHITE))
                                    .toFile(path);
                            allFile.add(new File(path));
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else
            {
                System.out.println("解析区域成功，但是没发现文字："+json);
            }
        }
        else
        {
            System.out.println("解析区域失败："+json);
        }
        return allFile;
    }

    private Map<String,Double> getPostion(JsonArray box)
    {

        Map<String,Double> result= new HashMap<>();
        double width=Math.sqrt(Math.pow((box.get(3).getAsInt()-box.get(1).getAsInt()),2)+Math.pow((box.get(2).getAsInt()-box.get(0).getAsInt()),2));
        double height=Math.sqrt(Math.pow((box.get(7).getAsInt()-box.get(1).getAsInt()),2)+Math.pow((box.get(6).getAsInt()-box.get(0).getAsInt()),2));
        double tan =new BigDecimal(box.get(3).getAsInt()-box.get(1).getAsInt()).divide(new BigDecimal((box.get(2).getAsInt()-box.get(0).getAsInt())),2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        double angle =new BigDecimal(Math.atan(tan)).divide(new BigDecimal((Math.PI/180)),2, BigDecimal.ROUND_HALF_EVEN).doubleValue();
        result.put("width",width);
        result.put("height",height);
        result.put("angle",angle);
        return result;
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

    public static void main(String[] args) throws IOException {
//        System.out.println(new ParseServiceImpl().parseImg("6443a370-e9fa-4591-8d50-96720d035e17");
//        System.out.println(new ParseServiceImpl().shortContent("由于服务端是严格按照 MQTT 协议规范实现的，上述端侧设备不按规范接入，实际上消息调度不到 MQTT 应用协议层。MQTT 服务端依赖 Keep Alive 机制做超时检测，当一段时间接收不到客户端的心跳和业务消息时，就会触发心跳超时，关闭连接。针对上述两种接入场景，由于 MQTT 的连接流程没有完成，MQTT 协议栈不认为这个是合法的 MQTT 连接，因此心跳保护机制无法对上述 TCP 连接做检测。客户端和服务端都没有主动关闭这个连接，导致 TCP 连接一直保持。","心跳"));
//        System.out.println(System.getProperty("os.name"));
//        JsonArray array = new JsonArray();
//        array.add(80);
//        array.add(19);
//        array.add(195);
//        array.add(16);
//        array.add(195);
//        array.add(34);
//        array.add(80);
//        array.add(36);
//        System.out.println(new ParseServiceImpl().getPostion(array));
        String json="{\"errCode\": 0, \"errMess\": \"\", \"data\": [{\"box\": [269, 1697, 1447, 1683, 1447, 1751, 269, 1765], \"prob\": 0.95, \"text\": null}, {\"box\": [269, 1983, 677, 1974, 677, 2045, 269, 2054], \"prob\": 0.98, \"text\": null}, {\"box\": [269, 1263, 1447, 1252, 1447, 1326, 269, 1337], \"prob\": 0.98, \"text\": null}, {\"box\": [269, 1767, 1447, 1753, 1447, 1824, 269, 1835], \"prob\": 0.97, \"text\": null}, {\"box\": [269, 1411, 949, 1405, 949, 1467, 269, 1476], \"prob\": 0.96, \"text\": null}, {\"box\": [541, 589, 1175, 580, 1175, 660, 541, 671], \"prob\": 0.96, \"text\": null}, {\"box\": [269, 1912, 1447, 1901, 1447, 1966, 269, 1977], \"prob\": 0.97, \"text\": null}, {\"box\": [269, 980, 1447, 966, 1447, 1034, 269, 1048], \"prob\": 0.97, \"text\": null}, {\"box\": [357, 509, 1311, 492, 1314, 583, 359, 600], \"prob\": 0.96, \"text\": null}, {\"box\": [269, 1844, 1447, 1824, 1447, 1895, 269, 1912], \"prob\": 0.98, \"text\": null}, {\"box\": [269, 1051, 1447, 1039, 1447, 1105, 269, 1116], \"prob\": 0.97, \"text\": null}, {\"box\": [269, 1620, 1447, 1612, 1447, 1688, 269, 1697], \"prob\": 0.97, \"text\": null}, {\"box\": [541, 430, 1130, 425, 1130, 515, 541, 521], \"prob\": 0.97, \"text\": null}, {\"box\": [223, 906, 1447, 892, 1447, 963, 223, 977], \"prob\": 0.97, \"text\": null}, {\"box\": [359, 1479, 858, 1473, 858, 1547, 359, 1552], \"prob\": 0.93, \"text\": null}, {\"box\": [223, 765, 903, 753, 903, 830, 223, 841], \"prob\": 0.88, \"text\": null}, {\"box\": [223, 235, 1447, 226, 1447, 376, 223, 385], \"prob\": 0.97, \"text\": null}, {\"box\": [314, 1549, 1447, 1538, 1447, 1612, 314, 1623], \"prob\": 0.93, \"text\": null}, {\"box\": [223, 1195, 1447, 1175, 1447, 1249, 223, 1269], \"prob\": 0.93, \"text\": null}, {\"box\": [223, 1122, 1447, 1102, 1447, 1178, 223, 1198], \"prob\": 0.95, \"text\": null}, {\"box\": [223, 1337, 1447, 1326, 1447, 1394, 223, 1402], \"prob\": 0.95, \"text\": null}, {\"box\": [269, 833, 1447, 818, 1447, 892, 269, 906], \"prob\": 0.91, \"text\": null}]}";
        new ParseServiceImpl().generateRegionImg(json,new File("C:\\Users\\JerryYin\\Desktop\\temp\\2019043010424129921.jpg"));
    }
}
