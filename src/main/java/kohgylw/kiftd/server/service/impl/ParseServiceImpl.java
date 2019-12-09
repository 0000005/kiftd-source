package kohgylw.kiftd.server.service.impl;

import com.google.gson.*;
import kohgylw.kiftd.server.service.ParseService;
import kohgylw.kiftd.server.util.ConfigureReader;
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

import javax.annotation.Resource;
import java.io.File;

@Service
public class ParseServiceImpl implements ParseService {


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

    public static void main(String[] args) {
        System.out.println(new ParseServiceImpl().ocrImg(new File("D:\\workplace\\jee\\kiftd-source\\filesystem\\temporaryfiles\\111.jpg")));
    }
}
