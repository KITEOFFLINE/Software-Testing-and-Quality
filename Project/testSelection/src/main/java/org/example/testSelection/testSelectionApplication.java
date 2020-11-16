package org.example.testSelection;

public class testSelectionApplication {
    public static void main(String[] args) throws Exception {
        String project_target, change_info;
        if (args.length == 3 && (args[0].equals("-c") || args[0].equals("-m"))) {
            project_target = args[1];
            change_info = args[2];
            if (args[0].equals("-c")) {
                //类级测试选择
            } else {
                //方法级测试选择
            }
        } else {
            throw new Exception("参数输错了");
        }
    }
}
