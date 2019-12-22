package kohgylw.kiftd.server.task;

import kohgylw.kiftd.server.mapper.NodeMapper;
import kohgylw.kiftd.server.model.Node;
import kohgylw.kiftd.server.service.ParseService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ParseTask {
    @Resource
    NodeMapper nodeMapper;
    @Resource
    ParseService parseService;

    ExecutorService fixedThreadPool = Executors.newFixedThreadPool(1);

    public static boolean isParsing=false;

    @Scheduled(cron="0 1 22 * * ?")
    public String parse() {
        String result="wait";
        if(isParsing)
        {
            return "isParsing";
        }
        isParsing=true;
        List<Node> list=nodeMapper.queryListByIndex("0");
        if(list.size()>0)
        {
            fixedThreadPool.execute(() -> {
                    System.out.println(new Date()+" 本次预计索引"+list.size()+"个内容");
                    isParsing=true;
                    for(int i=0;i<list.size();i++)
                    {
                        Node n = list.get(i);
                        try
                        {
                            if(StringUtils.isEmpty(n.getParseContent())){
                                n=parseService.parseImg(n.getFileId());
                            }
                            n=parseService.addIndexFile(n);
                            nodeMapper.update(n);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        if(!isContinue())
                        {
                            System.out.println("当前时间为："+new Date()+"，已经处理了"+(i+1)+"个文档，时间已到，停止执行剩余任务");
                            break;
                        }
                    }
                    isParsing=false;
            });
        }
        else
        {
            isParsing=false;
            result="complete";
        }
        return result;
    }

    //只允许晚上10点后到早上7点中运行
    public boolean isContinue(){
        String hour= LocalDateTime.now().format( DateTimeFormatter.ofPattern("HH"));
        if(Integer.valueOf(hour)>7&&Integer.valueOf(hour)<22)
        {
            return false;
        }
        return true;
    }

//    public static void main(String[] args) {
//        new ParseTask().isContinue();
//    }
}
