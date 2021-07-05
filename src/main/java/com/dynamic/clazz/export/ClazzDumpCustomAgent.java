package com.dynamic.clazz.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Arrays;

/**
 * 动态生成类拦截查看工具
 *
 * @date 2018/9/15
 */
public class ClazzDumpCustomAgent implements ClassFileTransformer {

    /**
     * 导出过滤表达式，此处为类名前缀， 以 -f 参数指定
     */
    private String filterStr;

    /**
     * 导出文件目录根目录, 以 -d 参数指定
     */
    private String exportBaseDir = "/tmp/";

    /**
     * 是否创建多级目录, 以 -r 参数指定
     */
    private boolean packageRecursive;

    public ClazzDumpCustomAgent(String exportBaseDir, String filterStr) {
        this(exportBaseDir, filterStr, false);
    }

    public ClazzDumpCustomAgent(String exportBaseDir, String filterStr, boolean packageRecursive) {
        if(exportBaseDir != null) {
            this.exportBaseDir = exportBaseDir;
        }
        this.packageRecursive = packageRecursive;
        this.filterStr = filterStr;
    }

    /**
     * 入口地址
     *
     * @param agentArgs agent参数
     * @param inst
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("agentArgs: " + agentArgs);
        String exportDir = null;
        String filterStr = null;
        boolean recursiveDir = false;
        if(agentArgs != null) {
            if(agentArgs.contains(";")) {
                String[] args = agentArgs.split(";");
                for (String param1 : args) {
                    String[] kv = param1.split("=");
                    if("-d".equalsIgnoreCase(kv[0])) {
                        exportDir = kv[1];
                    }
                    else if("-f".equalsIgnoreCase(kv[0])) {
                        filterStr = kv[1];
                    }
                    else if("-r".equalsIgnoreCase(kv[0])) {
                        recursiveDir = true;
                    }
                }
            }
            else {
                filterStr = agentArgs;
            }
        }
        inst.addTransformer(new ClazzDumpCustomAgent(exportDir, filterStr, recursiveDir));
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (needExportClass(className)) {
            int lastSeparatorIndex = className.lastIndexOf("/") + 1;
            String fileName = className.substring(lastSeparatorIndex) + ".class";
            String exportDir = exportBaseDir;
            if(packageRecursive) {
                exportDir += className.substring(0, lastSeparatorIndex);
            }
            exportClazzToFile(exportDir, fileName, classfileBuffer);         //"D:/server-tool/tmp/bytecode/exported/"
            System.out.println(className + " --> EXPORTED");
        }
        return classfileBuffer;
    }

    /**
     * 检测是否需要进行文件导出
     *
     * @param className class名,如 com.xx.abc.AooMock
     * @return y/n
     */
    private boolean needExportClass(String className) {
        if(filterStr != null) {
            if(className.startsWith(filterStr)) {
                return true;
            }
            else {
                return false;
            }
        }
        if (!className.startsWith("java") && !className.startsWith("sun")) {
            return true;
        }
        return false;
    }

    /**
     * 执行文件导出写入
     *
     * @param dirPath 导出目录
     * @param fileName 导出文件名
     * @param data 字节流
     */
    private void exportClazzToFile(String dirPath, String fileName, byte[] data) {
        try {
            File dir = new File(dirPath);
            if(!dir.isDirectory()) {
                dir.mkdirs();
            }
            File file = new File(dirPath + fileName);
            if (!file.exists()) {
                System.out.println(dirPath + fileName + " is not exist, creating...");
                file.createNewFile();
            }
            else {

//                String os = System.getProperty("os.name");        // 主要针对windows文件不区分大小写问题
//                if(os.toLowerCase().startsWith("win")){
//                    // it's win
//                }
                try {
                    int maxLoop = 9999;
                    int renameSuffixId = 2;
                    String[] cc = fileName.split("\\.");
                    do {
                        Long fileLen = file.length();
                        byte[] fileContent = new byte[fileLen.intValue()];
                        FileInputStream in = new FileInputStream(file);
                        in.read(fileContent);
                        in.close();
                        if(!Arrays.equals(fileContent, data)) {
                            fileName = cc[0] + "_" + renameSuffixId + "." + cc[1];
                            file = new File(dirPath + fileName);
                            if (!file.exists()) {
                                System.out.println("new create file: " + dirPath + fileName);
                                file.createNewFile();
                                break;
                            }
                        }
                        else {
                            break;
                        }
                        renameSuffixId++;
                        maxLoop--;
                    } while (maxLoop > 0);
                }
                catch (Exception e) {
                    System.err.println("exception in read class file..., path: " + dirPath + fileName);
                    e.printStackTrace();
                }
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
        }
        catch (Exception e) {
            System.err.println("exception occur while export class.");
            e.printStackTrace();
        }
    }
}