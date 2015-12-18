package edu.uci.seal.analyses;

import java.util.Map;

import edu.uci.seal.Config;
import soot.Body;
import soot.BodyTransformer;

public class ApkBodyTransformer extends BodyTransformer {
	
	public ApkBodyTransformer(String apkFilePath) {
		soot.G.reset();
		Config.apkFilePath = apkFilePath;
	}

	@Override
	protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
		// TODO Auto-generated method stub

	}

}
