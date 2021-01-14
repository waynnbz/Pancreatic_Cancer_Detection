package common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Slice {
	public String id; // ordinal as string
	public Map<String, List<Polygon>> nameToTruthPolygons = new HashMap<>();
	public Map<String, List<Polygon>> nameToSolutionPolygons = new HashMap<>();
	public Map<String, List<P2>> nameToSeedPoints = new HashMap<>(); // point coords in pixels
	
	@Override
	public String toString() {
		return id;
	}
}