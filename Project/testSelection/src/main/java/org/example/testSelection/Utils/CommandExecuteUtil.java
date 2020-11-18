package org.example.testSelection.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class CommandExecuteUtil {
    /*
     * 执行dos命令的方法
     * @param command 需要执行的dos命令
     * @param file 指定开始执行的文件目录
     *
     * @return true 转换成功，false 转换失败
     */
    public static String executeCommand(String command, File file) {
        StringBuffer output = new StringBuffer();
        Process p;
        InputStreamReader inputStreamReader = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(command, null, file);
            p.waitFor();
            inputStreamReader = new InputStreamReader(p.getInputStream(), "GBK");
            reader = new BufferedReader(inputStreamReader);
            String line = "";
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return output.toString();
    }

}
