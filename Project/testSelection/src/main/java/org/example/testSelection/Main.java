package org.example.testSelection;

import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import org.example.testSelection.Utils.ClassSelectionUtil;
import org.example.testSelection.Utils.MethodSelectionUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        String project_target, change_info, project_name;
        if (args.length == 3 && (args[0].equals("-c") || args[0].equals("-m"))) {
            project_target = args[1];
            String fileSeparator = "/|\\\\"; // 用split所以不能\\
            List<String> fileList = Arrays.asList(project_target.split(fileSeparator));
            if (!fileList.get(fileList.size() - 1).equals("target")) {
                throw new Exception("target路径出错");
            } else {
                project_name = getProjectName(project_target);
            }
            System.out.println(project_name);
            change_info = args[2];
            // 0.为了让这个文件在jar可以读到，我只好复制一遍
            String scopePath = "my_scope.txt"; // 域配置文件
            File jarScopePath = new File(scopePath);
            jarScopePath.createNewFile();
            BufferedWriter jarScopeBWriter = new BufferedWriter(new FileWriter(jarScopePath));
            InputStream scopeIn = Main.class.getClassLoader().getResource("scope.txt").openStream();
            BufferedReader scopebr = new BufferedReader(new InputStreamReader(scopeIn));
            String line = "";
            while ((line = scopebr.readLine()) != null) {
                jarScopeBWriter.write(line);
                jarScopeBWriter.newLine();
            }
            jarScopeBWriter.flush();
            jarScopeBWriter.close();
            // 0.为了让这个文件在jar可以读到，我只好复制一遍
            String exPath = "my_exclusion.txt"; // 排除配置文件路径
            File jarExPath = new File(exPath);
            jarExPath.createNewFile();
            BufferedWriter jarExBWriter = new BufferedWriter(new FileWriter(jarExPath));
            InputStream exIn = Main.class.getClassLoader().getResource("exclusion.txt").openStream();
            BufferedReader exBr = new BufferedReader(new InputStreamReader(exIn));
            while ((line = exBr.readLine()) != null) {
                jarExBWriter.write(line);
                jarExBWriter.newLine();
            }
            jarExBWriter.flush();
            jarExBWriter.close();
            // 0.找到class文件们
            File targetFile = new File(project_target); // target文件夹
            ArrayList<String> classNames = new ArrayList<String>();
            getAllClassNames(targetFile, classNames);
            if (classNames.size() == 0) {
                throw new Exception("没有class文件");
            }
            BufferedReader change_infoBR = new BufferedReader(new InputStreamReader(new FileInputStream(new File(change_info)))); // 建立一个对象，它把文件内容转成计算机能读懂的语言
            // 0.构建分析域（AnalysisScope）对象scope
            AnalysisScope scope = AnalysisScopeReader.readJavaScope(scopePath, new File(exPath), Main.class.getClassLoader());
            for (String className : classNames) {
                scope.addClassFileToScope(ClassLoaderReference.Application, new File(className + ".class"));
            }

//            System.out.println(scope);
            // 1.生成类层次关系对象
            ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

            // 2.生成进入点
            Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);

            // 3.利用CHA算法构建调用图
            CHACallGraph cg = new CHACallGraph(cha);
            cg.init(eps);

            ArrayList<CGNode[]> cgNodeRelation = new ArrayList<CGNode[]>();
            // 4.遍历cg中所有的节点
            for (CGNode node : cg) {
                // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
                if (node.getMethod() instanceof ShrikeBTMethod) {
                    // node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                    // 一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                    ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                    // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                    if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                        // 获取声明该方法的类的内部表示
                        String classInnerName = method.getDeclaringClass().getName().toString();
                        // 如果类名有$就先continue，反正答案里面是不需要这个的。
                        if (classInnerName.contains("$")) {
                            continue;
                        }
                        // 这是后继节点，被调用者
                        Iterator<CGNode> succIter = cg.getSuccNodes(node);
                        while (succIter.hasNext()) {
                            CGNode succNode = succIter.next();
                            if (succNode.getMethod() instanceof ShrikeBTMethod) {
                                ShrikeBTMethod succMethod = (ShrikeBTMethod) succNode.getMethod();
                                if ("Application".equals(succMethod.getDeclaringClass().getClassLoader().toString())) {
                                    // 获取声明该方法的类的内部表示
                                    String succClassInnerName = succMethod.getDeclaringClass().getName().toString();
                                    // 如果类名有$就先continue，反正答案里面是不需要这个的。
                                    if (succClassInnerName.contains("$")) {
                                        continue;
                                    }
                                    cgNodeRelation.add(new CGNode[]{succNode, node});
                                }
                            }
                        }
                        // 这是前继节点，调用者
                        Iterator<CGNode> preIter = cg.getPredNodes(node);
                        while (preIter.hasNext()) {
                            CGNode preNode = preIter.next();
                            if (preNode.getMethod() instanceof ShrikeBTMethod) {
                                ShrikeBTMethod preMethod = (ShrikeBTMethod) preNode.getMethod();
                                if ("Application".equals(preMethod.getDeclaringClass().getClassLoader().toString())) {
                                    // 获取声明该方法的类的内部表示
                                    String preClassInnerName = preMethod.getDeclaringClass().getName().toString();
                                    // 如果类名有$就先continue，反正答案里面是不需要这个的。
                                    if (preClassInnerName.contains("$")) {
                                        continue;
                                    }
                                    cgNodeRelation.add(new CGNode[]{node, preNode});
                                }
                            }
                        }
//                        System.out.println("=============" + classInnerName + "===============");
                        // 获取方法签名
                        String signature = method.getSignature();
//                        System.out.println("+++++++++++" + signature + "+++++++++++");
                    }
                } else {
//                    System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s", node.getMethod(), node.getMethod().getClass()));
                }
            }
            // 5.分别从类级和方法级来处理
            // 具体分别处理-c 和-m
            if (args[0].equals("-c")) {
                //类级测试选择
                System.out.println("类级测试选择");
                ClassSelectionUtil.selection(cgNodeRelation, project_name, change_infoBR);
            } else {
                //方法级测试选择
                System.out.println("方法级测试选择");
                MethodSelectionUtil.selection(cgNodeRelation, project_name, change_infoBR);
            }
            // 6.删除my_scope和my_exclusion
            jarScopePath.delete();
            jarExPath.delete();
        } else {
            throw new Exception("参数输错了");
        }
    }

    public static String getProjectName(String project_target) {
        String fileSeparator = "\\";
        int i = project_target.lastIndexOf(fileSeparator);
        if (i == -1) {
            fileSeparator = "/";
            i = project_target.lastIndexOf(fileSeparator);
        }
        File pomFile = new File(project_target.substring(0, i) + File.separator + "pom.xml");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            NodeList nodeList = doc.getElementsByTagName("artifactId");
            return nodeList.item(0).getFirstChild().getNodeValue();
        } catch (Exception e) {
            System.err.println("读取该xml文件失败");
            e.printStackTrace();
        }
        return null;
    }

    public static void getAllClassNames(File rootFile, ArrayList<String> classNames) throws IOException {
        File[] allFiles = rootFile.listFiles();
        for (File file : allFiles) {
            if (file.isDirectory()) {
                getAllClassNames(file, classNames);
            } else {
                String path = file.getCanonicalPath();
                int i = path.indexOf(".class");
                if (i != -1) {
                    classNames.add(path.substring(0, i));
                }
            }
        }
    }
}
