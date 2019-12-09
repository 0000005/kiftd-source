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
    FileBlockUtil fileBlockUtil;
    @Resource
    ParseService parseService;
    public void parse() {
        System.out.println("开始解析文件：" + new Date());
        List<Node> list=nodeMapper.queryListByStatus("1");
        list.stream().forEach(n->{
            try {
                File oldFile=fileBlockUtil.getFileFromBlocks(n);
                if(Objects.nonNull(oldFile))
                {
                    File newFile = new File(ConfigureReader.instance().getTemporaryfilePath()+File.separator+n.getFileName());
                    FileUtils.copyFile(oldFile,newFile);
                    String data=parseService.ocrImg(newFile);
                    if(data!=null&&!"".equals(data))
                    {
                        n.setParseContent(data);
                        n.setParseStatus("2");
                    }
                    else{
                        //解析失败
                        n.setParseStatus("3");
                    }
                    FileUtils.forceDeleteOnExit(newFile);
                }
            }catch (Exception e)
            {
                n.setParseStatus("3");
                e.printStackTrace();
            }
            nodeMapper.update(n);
        });
    }
}
