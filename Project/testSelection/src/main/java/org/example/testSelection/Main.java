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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        String project_target, change_info, project_name;
        if (args.length == 3 && (args[0].equals("-c") || args[0].equals("-m"))) {
            // 0.命令参数的处理
            project_target = args[1];
            change_info = args[2];
            BufferedReader change_infoBR = new BufferedReader(new InputStreamReader(new FileInputStream(new File(change_info))));
            project_name = getProjectName(project_target);
            System.out.println(getNowTime() + "正在解析项目：" + project_name);
            // 1.构建分析域（AnalysisScope）对象scope
            AnalysisScope scope = createAnalysisScope(project_target);
            // 2.生成类层次关系对象
            ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
            // 3.生成进入点
            Iterable<Entrypoint> eps = new AllApplicationEntrypoints(scope, cha);
            // 4.利用CHA算法构建调用图
            CHACallGraph cg = new CHACallGraph(cha);
            cg.init(eps);
            // 5.遍历cg所有的节点，获取依赖关系list
            ArrayList<CGNode[]> cgNodeRelation = traverseGetRelation(cg);
            // 6.分别从类级和方法级来处理
            // 具体分别处理-c 和-m
            if (args[0].equals("-c")) {
                //类级测试选择
                System.out.println(getNowTime() + "正在做class级测试选择");
                ClassSelectionUtil.selection(cgNodeRelation, project_name, change_infoBR);
            } else {
                //方法级测试选择
                System.out.println(getNowTime() + "正在做method级测试选择");
                MethodSelectionUtil.selection(cgNodeRelation, project_name, change_infoBR);
            }
        } else {
            throw new Exception("参数输错了");
        }
    }

    /**
     * 遍历调用图的每个节点，生成调用关系的list
     *
     * @param cg 调用图
     * @return
     */
    private static ArrayList<CGNode[]> traverseGetRelation(CHACallGraph cg) {
        ArrayList<CGNode[]> cgNodeRelation = new ArrayList<CGNode[]>();
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
                    // 如果类名有$就先continue，不考虑这个node
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
                                // 如果类名有$就先continue，不考虑这个node
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
                                // 如果类名有$就先continue，不考虑这个node
                                if (preClassInnerName.contains("$")) {
                                    continue;
                                }
                                cgNodeRelation.add(new CGNode[]{node, preNode});
                            }
                        }
                    }
                }
            }
        }
        return cgNodeRelation;
    }

    /**
     * 通过项目target路径获取项目名
     *
     * @param project_target 项目target路径
     * @return 项目名
     * @throws Exception io异常
     */
    public static String getProjectName(String project_target) throws Exception {
        String fileSep = "/|\\\\"; // 用split所以不能\\
        List<String> fileList = Arrays.asList(project_target.split(fileSep));
        if (!fileList.get(fileList.size() - 1).equals("target")) {
            throw new Exception("target路径出错");
        } else {
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
    }

    /**
     * 构建分析域（AnalysisScope）对象scope
     *
     * @param project_target target路径
     * @throws Exception
     */
    public static AnalysisScope createAnalysisScope(String project_target) throws Exception {
        File targetDir = new File(project_target); // target文件夹
        ArrayList<String> classNames = new ArrayList<String>();
        getAllClassNames(targetDir, classNames);
        if (classNames.size() == 0) {
            throw new Exception("target路径下没有class文件");
        }
        AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", new File("exclusion.txt"), Main.class.getClassLoader());

        for (String className : classNames) {
            scope.addClassFileToScope(ClassLoaderReference.Application, new File(className));
        }
        return scope;
    }

    /**
     * 递归从target路径下获取所有的class文件的路径
     *
     * @param rootFile   target路径
     * @param classNames 名字list
     * @throws IOException
     */
    public static void getAllClassNames(File rootFile, ArrayList<String> classNames) throws IOException {
        File[] allFiles = rootFile.listFiles();
        assert allFiles != null;
        for (File file : allFiles) {
            if (file.isDirectory()) {
                getAllClassNames(file, classNames);
            } else {
                String path = file.getCanonicalPath();
                int i = path.indexOf(".class");
                if (i != -1) {
                    classNames.add(path);
                }
            }
        }
    }

    /**
     * 获取当前的时间信息，方便在控制台打印
     *
     * @return string
     */
    public static String getNowTime() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        Date d = new Date();
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(d) + " INFO ";
    }
}
