package edu.uci.seal.analyses;

import java.util.Map;

import edu.uci.seal.Config;
import soot.SceneTransformer;

public class ApkSceneTransformer extends SceneTransformer {
	
	public ApkSceneTransformer(String apkFilePath) {
		soot.G.reset();
		Config.apkFilePath = apkFilePath;
	}

	@Override
	protected void internalTransform(String phaseName, Map<String, String> options) {
		// TODO Auto-generated method stub

	}

}
