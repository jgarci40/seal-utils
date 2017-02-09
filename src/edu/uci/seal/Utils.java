package edu.uci.seal;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.javatuples.Quartet;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import com.google.common.collect.Lists;

import edu.uci.seal.Config;
import soot.ArrayType;
import soot.CharType;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.ConditionExpr;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;
import soot.util.Chain;

public class Utils {
	
	public static void setupDummyMainMethod() {
		SetupApplication app = new SetupApplication(Config.androidJAR,Config.apkFilePath);
		Config.applyWholeProgramSootOptions();
		try {
			app.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
			Config.applyWholeProgramSootOptions();
			// entryPoints are the entryPoints required by Soot to calculate Graph - if there is no main method,
			// we have to create a new main method and use it as entryPoint and store our real entryPoints
			Scene.v().loadNecessaryClasses();
			Scene.v().setEntryPoints(Collections.singletonList(app.getEntryPointCreator().createDummyMain()));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		}
	}
	
	public static void setupAndroidAppForBody() {
		Config.applyBodySootOptions();
		Scene.v().loadNecessaryClasses();
	}

	public static String createTabsStr(int tabs) {
		String tabsStr = "";
		for (int i=0;i<tabs;i++) {
			tabsStr += "\t";
		}
		return tabsStr;
	}
	
	public static Set<SootMethod> getApplicationMethods() {
		Chain<SootClass> appClasses = Scene.v().getApplicationClasses();
		Set<SootMethod> appMethods = new LinkedHashSet<SootMethod>();
		for (SootClass clazz : appClasses) {
			appMethods.addAll( clazz.getMethods() );
		}
		return appMethods;
	}
	
	public static List<SootMethod> getMethodsInReverseTopologicalOrder() {
		List<SootMethod> entryPoints = Scene.v().getEntryPoints();
		CallGraph cg = Scene.v().getCallGraph();
		List<SootMethod> topologicalOrderMethods = new ArrayList<SootMethod>();

		Stack<SootMethod> methodsToAnalyze = new Stack<SootMethod>();

		for (SootMethod entryPoint : entryPoints) {
			if (isApplicationMethod(entryPoint)) {
				methodsToAnalyze.push(entryPoint);
				while (!methodsToAnalyze.isEmpty()) {
					SootMethod method = methodsToAnalyze.pop();
					if (!topologicalOrderMethods.contains(method)) {
						if (method.hasActiveBody()){	
							topologicalOrderMethods.add(method);
							for (Edge edge : getOutgoingEdges(method, cg)) {
								methodsToAnalyze.push(edge.tgt());
							}
						}
					}
				}
			}
		}

		List<SootMethod> rtoMethods = Lists.reverse(topologicalOrderMethods);
		return rtoMethods;
	}
	
	public static boolean isApplicationMethod(SootMethod method) {
		Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
		for (SootClass appClass : applicationClasses) {
			if (appClass.getMethods().contains(method)) {
				return true;
			}
		}
		return false;
	}
	
	public static SootClass getLibraryClass(String className) {
		Chain<SootClass> libraryClasses = Scene.v().getLibraryClasses();
		for (SootClass libClass : libraryClasses) {
			if (libClass.getName().equals(className)) {
				return libClass;
			}
		}
		return null;
	}

	public static List<Edge> getOutgoingEdges(SootMethod method, CallGraph cg) {
		Iterator<Edge> edgeIterator = cg.edgesOutOf(method);
		List<Edge> outgoingEdges = Lists.newArrayList(edgeIterator);
		return outgoingEdges;
	}
	
	public static void printInputStream(InputStream is) throws IOException {
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;
		while ((line = br.readLine()) != null) {
			System.out.println(line);
		}
	}
	
	public static void runCmdAsProcess(String[] cmdArr) {
		List<String> cmd = Arrays.asList(cmdArr);

		ProcessBuilder builder = new ProcessBuilder(cmd);
		Map<String, String> environ = builder.environment();

		Process process;
		try {
			process = builder.start();

			InputStream is = process.getInputStream();
			System.out.println("normal output: ");
			Utils.printInputStream(is);
			
			InputStream es = process.getErrorStream();
			System.out.println("error output: ");
			Utils.printInputStream(es);

			System.out.println("Program terminated!");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static BytecodeOffsetTag extractByteCodeOffset(Unit unit) {
		for (Tag tag : unit.getTags()) {
			if (tag instanceof BytecodeOffsetTag) {
				BytecodeOffsetTag bcoTag = (BytecodeOffsetTag) tag;
				return bcoTag;
			}
		}
		return null;
	}

	public static Logger setupLogger(@SuppressWarnings("rawtypes") Class inClass, String apkName) {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	    JoranConfigurator configurator = new JoranConfigurator();
	    lc.reset();
	    lc.putProperty("toolName", inClass.getName());
	    lc.putProperty("apkName",apkName);
	    configurator.setContext(lc);
	    try {
			configurator.doConfigure("logback-fileAppender.xml");
		} catch (JoranException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    Logger logger = LoggerFactory.getLogger(inClass);
	    return logger;
	}

	public static Logger setupVerboseLogger(@SuppressWarnings("rawtypes") Class inClass, String apkName) {
		LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
		JoranConfigurator configurator = new JoranConfigurator();
		lc.reset();
		lc.putProperty("toolName", inClass.getName());
		lc.putProperty("apkName",apkName);
		configurator.setContext(lc);
		try {
			configurator.doConfigure("logback-fileAppender-verbose.xml");
		} catch (JoranException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Logger logger = LoggerFactory.getLogger(inClass);
		return logger;
	}
	
	public static void printTagsOfMethod(Logger logger, SootMethod method) {
		for (Unit unit : method.getActiveBody().getUnits()) {
			if (!unit.getTags().isEmpty()) {
				for (Tag tag : unit.getTags()) {
					logger.debug("unit: " + unit);
					logger.debug("\ttag: " + tag.getName() + "," + tag.toString());
				}
			}
		}

	}
	
	public static void makeFileEmpty(String filename) {
		BufferedWriter writer;
		try {
			writer = Files.newBufferedWriter(Paths.get(filename), Charset.defaultCharset());
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static String getFullCompName(String packageName, String compName) {
		if (compName.startsWith(".")) {
			return packageName + compName; // if the component name has a "." at the beginning just concatenate it with the packageName
		}
		else if (compName.contains(".")) { // if the component has a "." anywhere else, return just the component name
			return compName;
		}
		else {
			return packageName + "." + compName; // if the component does not match the previous two conditions, then concatenate with the package name and add the "."
		}
	}
	
	public static void deletePathIfExists(Path path) {
		if (Files.exists(path)) {
			try {
				Files.delete(path);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static InvokeExpr getInvokeExprOfAssignStmt(Unit unit) {
		if (unit instanceof AssignStmt) {
			AssignStmt assignStmt = (AssignStmt)unit;
			if (assignStmt.getRightOp() instanceof InvokeExpr) {
				InvokeExpr invokeExpr = (InvokeExpr)assignStmt.getRightOp();
				return invokeExpr;
			}
		}
		return null;

	}

	public static void storeIntentControlledTargets(File apkFile, Logger logger, Set<Triplet<Unit, BytecodeOffsetTag, SootMethod>> targets) {
		String targetsFilename = apkFile.getName() + "_ic_tgt_units.txt";
		logger.debug("Saving intent-controlled targets to " + targetsFilename);

		try {
			PrintWriter writer = new PrintWriter(targetsFilename);
			for (Triplet<Unit,BytecodeOffsetTag,SootMethod> target : targets) {
				writer.write(target.getValue1() + "#" + target.getValue2() + "\n");
			}
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void storeIntentControlledTargetsWithSpecialKeys(File apkFile,
																   Logger logger,
																   Set<Quartet<Unit, BytecodeOffsetTag, SootMethod,String>> targets) {
		String targetsFilename = apkFile.getName() + "_ic_tgt_units.txt";
		logger.debug("Saving intent-controlled targets to " + targetsFilename);

		try {
			PrintWriter writer = new PrintWriter(targetsFilename);
			int i=0;
			for (Quartet<Unit, BytecodeOffsetTag, SootMethod, String> target : targets) {
				writer.write(target.getValue1() + "#" + target.getValue2() + "#" + target.getValue3() + "\n");
				i++;
			}
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
