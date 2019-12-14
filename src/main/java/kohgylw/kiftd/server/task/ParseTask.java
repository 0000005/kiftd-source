package kohgylw.kiftd.server.task;

import kohgylw.kiftd.server.mapper.NodeMapper;
import kohgylw.kiftd.server.model.Node;
import kohgylw.kiftd.server.service.ParseService;
import kohgylw.kiftd.server.util.ConfigureReader;
import kohgylw.kiftd.server.util.FileBlockUtil;
import org.apache.commons.io.FileUtils;
import org.apache.poi.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class ParseTask {
    @Resource
    NodeMapper nodeMapper;
    @Resource
    ParseService parseService;
    boolean isParsing=false;
    public String parse() {
        String result="success";
        if(isParsing)
        {
            return "isParsing";
        }
        isParsing=true;
        List<Node> list=nodeMapper.queryListByStatus("1");
        System.out.println(new Date()+" 本次预计索引"+list.size()+"个内容");
        new Thread(() -> {
            try
            {
                for(Node n:list)
                {
                    parseService.ocrImg(n.getFileId());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        isParsing=false;
        return result;
    }
}
