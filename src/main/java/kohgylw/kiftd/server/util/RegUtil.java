package kohgylw.kiftd.server.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegUtil {
    public static boolean isImg(String fileName){
        String reg = ".+(.JPEG|.jpeg|.JPG|.jpg|PNG|png)$";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(fileName);
        return matcher.find();
    }
}
