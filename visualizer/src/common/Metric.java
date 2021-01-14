package common;

import java.util.HashMap;
import java.util.Map;

import static common.Polygon.STRUCTURES;

public class Metric {
	public Map<String, Double> name2tp = new HashMap<>(); 
	public Map<String, Double> name2fp = new HashMap<>(); 
	public Map<String, Double> name2fn = new HashMap<>(); 
	
	public Metric() {
		for (String s: STRUCTURES) {
			name2tp.put(s, 0.0);
			name2fp.put(s, 0.0);
			name2fn.put(s, 0.0);
		}
	}
}