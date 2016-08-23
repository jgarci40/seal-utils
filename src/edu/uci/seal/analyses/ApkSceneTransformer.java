package edu.uci.seal.analyses;

import java.util.Map;

import edu.uci.seal.Config;
import soot.PackManager;
import soot.SceneTransformer;
import soot.Transform;
import soot.options.Options;

public class ApkSceneTransformer extends SceneTransformer {

	private static final String shortAnalysisName = "apk";
	
	public ApkSceneTransformer(String apkFilePath) {
		soot.G.reset();
		Config.apkFilePath = apkFilePath;
	}

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		// TODO Auto-generated method stub

	}

	public void run() {
		Options.v().set_whole_program(true);
		Options.v().set_output_format(Options.v().output_format_none);
		PackManager.v().getPack("wjtp")
				.add(new Transform("wjtp." + shortAnalysisName, this));
		PackManager.v().getPack("wjtp").apply();
	}

}
