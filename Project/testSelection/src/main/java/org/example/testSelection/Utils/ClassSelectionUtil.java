package org.example.testSelection.Utils;

import com.ibm.wala.ipa.callgraph.CGNode;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;

public class ClassSelectionUtil {
    public static void selection(ArrayList<CGNode[]> cgNodeRelation, String project_name, BufferedReader br) {
        // 1.将图节点关系中的类关系写在dot文件中
        HashSet<String> classDotLine = new HashSet<String>();
        for (CGNode[] map : cgNodeRelation
        ) {
            classDotLine.add("\"" + map[0].getMethod().getDeclaringClass().getName().toString() + "\" -> \"" + map[1].getMethod().getDeclaringClass().getName().toString() + "\";");
        }
        try {
            String parentPath = "reports/";
            File pfile = new File(parentPath);
            if(!pfile.exists())
            {
                pfile.mkdir();
            }
            String dotFilePath = "reports/class-" + project_name + ".dot";
            File file = new File(dotFilePath);
            PrintStream ps = new PrintStream(new FileOutputStream(file));
            ps.println("digraph " + "class {");
            for (String s : classDotLine
            ) {
                ps.println("    " + s);
            }
            ps.println("}");
            System.out.println("生成dot文件");
            // 2.使用graphviz画出pdf
            String command = "dot -T pdf -o class-" + project_name + ".pdf class-" + project_name + ".dot";
            CommandExecuteUtil.executeCommand(command, new File("reports"));
            System.out.println("转换为pdf");
            // 3.根据change_info找到改变的类
            HashSet<String> changedClass = new HashSet<String>();
            String line = "";
            line = br.readLine();
            while (line != null) {
                changedClass.add(line.split(" ")[0]);
                line = br.readLine(); // 一次读入一行数据
            }
            // 4.只要这个节点依赖与改变的类的init方法，那么这个节点就受到影响，要选出来重新测试。
            String selectionClassPath = "./selection-class.txt";
            File file1 = new File(selectionClassPath);
            PrintStream ps1 = new PrintStream(new FileOutputStream(file1));
            HashSet<String> outputs = new HashSet<>();
            for (CGNode[] nodes : cgNodeRelation
            ) {
                if (changedClass.contains(nodes[0].getMethod().getDeclaringClass().getName().toString())) {
                    if (!nodes[1].getMethod().getSignature().contains("Test")) {
                        changedClass.add(nodes[1].getMethod().getDeclaringClass().getName().toString());
                    }
                }
            }
            for (CGNode[] nodes : cgNodeRelation
            ) {
                if (changedClass.contains(nodes[0].getMethod().getDeclaringClass().getName().toString())) {
                    if (nodes[1].getMethod().getSignature().contains("Test") && !nodes[1].getMethod().getSignature().contains("<init>")) {
                        outputs.add(nodes[1].getMethod().getDeclaringClass().getName().toString() + " " + nodes[1].getMethod().getSignature());
                    }
                }
            }
            for (String s : outputs
            ) {
                ps1.println(s);
            }
            System.out.println("找到了受影响的测试");
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
