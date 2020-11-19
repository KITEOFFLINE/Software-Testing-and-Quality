package org.example.testSelection.Utils;

import com.ibm.wala.ipa.callgraph.CGNode;
import org.example.testSelection.Main;

import java.io.*;
import java.util.*;

public class MethodSelectionUtil {
    public static void selection(ArrayList<CGNode[]> cgNodeRelation, String project_name, BufferedReader br) {
        // 1.将图节点关系中的类关系写在dot文件中
        HashSet<String> methodDotLine = new HashSet<>();
        for (
                CGNode[] map : cgNodeRelation
        ) {
            methodDotLine.add("\"" + map[0].getMethod().getSignature() + "\" -> \"" + map[1].getMethod().getSignature() + "\";");
        }
        List<String> sortedMethodDotLine = new ArrayList<String>(methodDotLine);
        Collections.sort(sortedMethodDotLine);
        try {
            String parentPath = "reports/";
            File pfile = new File(parentPath);
            if (!pfile.exists()) {
                pfile.mkdir();
            }
            String dotFilePath = "reports/method-" + project_name + ".dot";
            File file = new File(dotFilePath);
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            PrintStream ps = new PrintStream(new FileOutputStream(file));
            ps.println("digraph " + "method {");
            for (String s : sortedMethodDotLine
            ) {
                ps.println("    " + s);
            }
            ps.println("}");
            // 2.使用graphviz画出pdf
            String command = "dot -T pdf -o method-" + project_name + ".pdf method-" + project_name + ".dot";
            CommandExecuteUtil.executeCommand(command, new File("reports"));
            System.out.println(Main.getNowTime() + "生成代码依赖图");
            // 3.根据change_info找到改变的方法
            HashSet<String> changedMethod = new HashSet<String>();
            String line = "";
            line = br.readLine();
            while (line != null) {
                changedMethod.add(line.split(" ")[1]);
                line = br.readLine(); // 一次读入一行数据
            }
            // 4.只要这个节点依赖与改变的节点的方法或者依赖改变的节点的节点的方法，那么这个节点就受到影响，要选出来重新测试。
            String selectionMethodPath = "./selection-method.txt";
            File file1 = new File(selectionMethodPath);
            PrintStream ps1 = new PrintStream(new FileOutputStream(file1));
            HashSet<String> outputs = new HashSet<>();
            for (CGNode[] nodes : cgNodeRelation
            ) {
                if (changedMethod.contains(nodes[0].getMethod().getSignature())) {
                    if (!nodes[1].getMethod().getSignature().contains("Test")) {
                        changedMethod.add(nodes[1].getMethod().getSignature());
                    }
                }
            }
            for (CGNode[] nodes : cgNodeRelation
            ) {
                if (changedMethod.contains(nodes[0].getMethod().getSignature())) {
                    if (nodes[1].getMethod().getSignature().contains("Test") && !nodes[1].getMethod().getSignature().contains("<init>")) {
                        outputs.add(nodes[1].getMethod().getDeclaringClass().getName().toString() + " " + nodes[1].getMethod().getSignature());
                    }
                }
            }
            for (String s : outputs
            ) {
                ps1.println(s);
            }
            System.out.println(Main.getNowTime() + "完成测试选择");
        } catch (
                IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
