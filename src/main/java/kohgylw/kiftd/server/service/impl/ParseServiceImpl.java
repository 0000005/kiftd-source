package kohgylw.kiftd.server.service.impl;

import com.google.gson.*;
import kohgylw.kiftd.server.mapper.NodeMapper;
import kohgylw.kiftd.server.model.Node;
import kohgylw.kiftd.server.service.ParseService;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.FileBlockUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import sun.applet.Main;
import sun.reflect.generics.tree.VoidDescriptor;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

@Service
public class ParseServiceImpl implements ParseService {

    @Resource
    private NodeMapper fm;
    @Resource
    FileBlockUtil fileBlockUtil;

    @Override
    public void ocrImg(String fileId) throws IOException {
        Node file=fm.queryById(fileId);
        String fileName=file.getFileName();
        final String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
        if(suffix.equals("jpg") || suffix.equals("jpeg") || suffix.equals("gif") || suffix.equals("bmp")
                || suffix.equals("png"))
        {
            File oldFile=fileBlockUtil.getFileFromBlocks(file);
            if(Objects.nonNull(oldFile))
            {
                File newFile = new File(ConfigureReader.instance().getTemporaryfilePath()+File.separator+file.getFileName());
                FileUtils.copyFile(oldFile,newFile);
                String data=null;
                try
                {
                    data=ocrImg(newFile);
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
                fm.update(file);
            }
        }
    }

    @Override
    public String ocrImg(File file) {
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

    public static void main(String[] args) throws IOException {
//        System.out.println(new ParseServiceImpl().ocrImg("6443a370-e9fa-4591-8d50-96720d035e17");
    }
}
