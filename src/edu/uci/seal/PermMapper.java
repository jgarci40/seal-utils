package edu.uci.seal;

import javassist.bytecode.Descriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by joshua on 5/5/17.
 * @author alireza
 */
public class PermMapper {

    private static final String API_PRM_MAPPING = "jellybean_allmappings.txt";
    private static final String INTENT_PRM_MAPPING = "jellybean_intentpermissions.txt";
    private static final String PROVIDER_PRM_MAPPING = "jellybean_contentproviderpermission.txt";


    public Map<String, Set<String>> mApiPrmMap;
    private Map<String, Set<String>> mIntentPrmMap;
    private Map<String, Set<String>> mProviderPrmMap;

    public PermMapper(String mappingRoot) {
        mApiPrmMap = new HashMap<>();
        mIntentPrmMap = new HashMap<>();
        mProviderPrmMap = new HashMap<>();
        processAPIMappingFileText(new File(mappingRoot, API_PRM_MAPPING));
        processIntentMappingFile(new File(mappingRoot, INTENT_PRM_MAPPING));
        processProviderMappingFile(new File(mappingRoot, PROVIDER_PRM_MAPPING));
    }

    private static void addPrmToMap(Map<String, Set<String>> map, String prm, String key) {
        Set<String> perms = map.get(key);
        if (perms == null) {
            perms = new HashSet<>();
        }
        perms.add(prm);
        map.put(key, perms);
    }

    private static Map<String, String> processAPIMappingFileAxplorer(File mappingFile) {
        Map<String, String> result = new HashMap<>();
        Pattern mapPattern = Pattern.compile("(.*)\\.(.*)\\((.*)\\)(.*) :: (.*)");
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = mapPattern.matcher(line);
                if (matcher.find()) {
                    String className = matcher.group(1);
                    String methodName = matcher.group(2);
                    String paramTypes = matcher.group(3);
                    List<String> paramTypesList = new ArrayList<>();
                    for (String paramType : paramTypes.split(",")) {
                        if (paramType.startsWith("[")){
                            paramType = paramType.substring(1).concat("[]");
                        }
                        paramTypesList.add(paramType);
                    }
                    String returnType = matcher.group(4);
                    String perm = matcher.group(5);
                    String methodSignature =  String.format("<%s: %s %s(%s)>", className, returnType,
                            methodName, String.join(",", paramTypesList));
                    result.put(methodSignature, perm);
                } else {
                    System.err.format("Method is invalid %s", line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }


    private void processProviderMappingFile(File mappingFile) {
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            while ((line = reader.readLine()) != null) {
                final String[] segments = line.split(" ");
                if (segments.length == 3) {
                    String content = segments[0];
                    String prm = segments[2];
                    //Note: not all permissions start with android.permission, but we assume that all dynamic perms
                    // follow this pattern
                    if (prm.startsWith("android.permission.") && content.startsWith("content://")) {
                        prm = prm.substring(prm.lastIndexOf(".") + 1);
                        addPrmToMap(mProviderPrmMap, prm, content);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processIntentMappingFile(File mappingFile) {
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            while ((line = reader.readLine()) != null) {
                final String[] segments = line.split(" ");
                if (segments.length == 3) {
                    String prm = segments[1];
                    if (prm.startsWith("android.permission.")) {
                        prm = prm.substring(prm.lastIndexOf(".") + 1);
                        String intent = segments[0];
                        addPrmToMap(mIntentPrmMap, prm, intent);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processAPIMappingFileText(File mappingFile) {
        String line;
        String prm = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Permission")) {
                    prm = line.split(":")[1];
                    prm = prm.substring(prm.lastIndexOf(".") + 1);
                } else if (prm != null && line.startsWith("<")) {
                    String signature = line.substring(line.indexOf("<"), line.lastIndexOf(">") + 1);
                    addPrmToMap(mApiPrmMap, prm, signature);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void processMappingFileCsv(String mappingFile) {
        Pattern descPattern = Pattern.compile("^(\\(.*\\))(.*)");
        try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                final String[] elements = line.split(",");
                String prm = elements[3];
                final String signature = genSootMethod(elements[0], elements[1], elements[2], descPattern);
                Set<String> perms = mApiPrmMap.get(signature);
                if (perms == null) {
                    perms = new HashSet<>();
                }
                perms.add(prm);
                mApiPrmMap.put(signature, perms);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String genSootMethod(String className, String methodName, String methodDesc, Pattern descPattern) {
        Matcher matcher = descPattern.matcher(methodDesc);
        if (matcher.find()) {
            String paramTypes = Descriptor.toString(matcher.group(1));
            String returnType = Descriptor.toString(matcher.group(2));
            return String.format("<%s: %s %s%s>", className.replaceAll("/", "."), returnType, methodName, paramTypes);
        } else {
            System.err.format("Method is invalid %s", methodDesc);
            return null;
        }
    }

    public Set<String> getApiPerms(String action) {
        final Set<String> perms = mApiPrmMap.get(action);
        if (perms == null) {
            return Collections.emptySet();
        }
        return perms;
    }

    Set<String> getIntentPerms(String action) {
        final Set<String> perms = mIntentPrmMap.get(action);
        if (perms == null) {
            return Collections.emptySet();
        }
        return perms;
    }

    Set<String> getProviderPerms(String uri) {
        if (uri == null) {
            return Collections.emptySet();
        }
        Set<String> perms = new HashSet<>();
        for (String content : mProviderPrmMap.keySet()) {
            if (uri.startsWith(content)) {
                perms.addAll(mProviderPrmMap.get(content));
            }
        }
        return perms;
    }
}
