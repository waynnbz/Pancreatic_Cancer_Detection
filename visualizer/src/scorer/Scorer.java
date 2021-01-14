package scorer;

import static common.Polygon.STRUCTURES;
import static common.Polygon.TUMOR_NAME;
import static common.Utils.coordStringToPoints;
import static common.Utils.f;
import static common.Utils.f6;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import common.Metric;
import common.P2;
import common.Polygon;
import common.Scan;
import common.Slice;

public class Scorer {
	public static boolean DEBUG = false;
	private boolean isProvisional;
	private PrintWriter infoLog;	
	public Map<String, Scan> idToScan = new TreeMap<>();
	private double score;
	private String outDir;
		
	public double run(String testPhase, String truthPath, String solutionPath, String outD) throws Exception {
		outDir = outD;
		infoLog = new PrintWriter(new File(outDir, "info.txt"));
		
		isProvisional = testPhase.equalsIgnoreCase("provisional");

		boolean ok = load(truthPath, true);
		if (!ok) { // shouldn't happen
			System.exit(1);
		}
		
		File sol = new File(solutionPath);
		if (!sol.exists()) {
			info("Solution file not found");
			writeScore(-1);
			System.exit(2);
		}
		
		ok = load(solutionPath, false);
		if (!ok) {
			writeScore(-1);
			System.exit(3);
		}

		// Most of this is copied from visualizer, to be updated if changes
		Map<String, Double> totals = new HashMap<>();
		for (String struct: STRUCTURES) totals.put(struct, 0.0);
		
		for (Scan scan: idToScan.values()) {
			debug(" " + scan.id);
			Metric[] result = score(scan);
			Metric scanSums = new Metric();
			
			for (int i = 0; i < result.length; i++) {
				Metric m = result[i];
				for (String struct: STRUCTURES) {
					add(scanSums.name2tp, struct, m.name2tp.get(struct));
					add(scanSums.name2fp, struct, m.name2fp.get(struct));
					add(scanSums.name2fn, struct, m.name2fn.get(struct));
				}
			}
			
			for (String struct: STRUCTURES) {
				double tp = scanSums.name2tp.get(struct);
				double fp = scanSums.name2fp.get(struct);
				double fn = scanSums.name2fn.get(struct);
				double f = 0;
				if (tp > 0) {
					double prec = tp / (tp + fp);
					double rec  = tp / (tp + fn);
					f = 2 * prec * rec / (prec + rec);
				}
				add(totals, struct, f);
				
				debug("  " + struct);
				debug("    tp    : " + f(tp));
				debug("    fp    : " + f(fp));
				debug("    fn    : " + f(fn));
				debug("    score : " + f6(f));
			}
		
			for (int i = 0; i < result.length; i++) {
				Metric m = result[i];
				double tp = m.name2tp.get(TUMOR_NAME);
				double fp = m.name2fp.get(TUMOR_NAME);
				double fn = m.name2fn.get(TUMOR_NAME);
				
				if (tp > 0 || fp > 0 || fn > 0) {
					int i1 = i + 1;
					double f = 0;
					if (tp > 0) {
						double prec = tp / (tp + fp);
						double rec  = tp / (tp + fn);
						f = 2 * prec * rec / (prec + rec);
					}
					debug("    #" + i1 + ": \t" + (int)(tp) + "\t" + (int)(fp) + "\t" + 
							(int)(fn) + "\t" + f(f));
				}
			}
		}
		
		score = 0;
		double sumW = 0;
		for (String s: STRUCTURES) {
			double v = totals.get(s) / idToScan.size();
			totals.put(s, v);
			double w = s.equals(TUMOR_NAME) ? 7 : 1;
			score += w * v;
			sumW += w;
		}
		score /= sumW;
		String result = "Overall f-score: " + f6(score);
		for (String s: STRUCTURES) {
			result += "\n  " + s + ":\t" + f6(totals.get(s));
		}
		
		debug(result);
		
		score *= 100;
		
		writeScore(score);
		infoLog.close();
		return score;
	}

	private void add(Map<String, Double> map, String key, Double value) {
		if (value == null || value == 0) return;
		Double old = map.get(key);
		if (old == null) old = 0.0;
		old += value;
		map.put(key, old);
	}
	
    private boolean load(String path, boolean truth) {
		File f = new File(path);
		String what = truth ? "truth" : "solution";
		debug("Loading " + what + " file from " + path);
		String line = null;
		int lineNo = 0;
		LineNumberReader lnr = null;
		try {
			lnr = new LineNumberReader(new FileReader(f));
			while (true) {
				line = lnr.readLine();
				lineNo++;
				if (line == null) break;
				line = line.replace(" " , "");
				if (line.isEmpty()) continue;
				
				// Patient_1,100,struct,x1,y1,x2,y2,...
				// or special lines:
				// #Patient_1,0,SIZES,w,h,N,dx,dy,dz
				// #Patient_1,slice,SEED,x,y // not used in scorer
				
				boolean specLine = false;
				if (line.startsWith("#")) {
					if (!truth) continue;
					line = line.substring(1);
					specLine = true;
				}
				String[] parts = line.split(",");
				String id = parts[0];
				Scan scan = idToScan.get(id);
				if (scan == null) {
					if (truth) {
						scan = new Scan(id);
		    			idToScan.put(id, scan);
					}
					else {
						info("Unknown scan id found in solution file at line " + lineNo + ": " + id);
						return false;
					}
				}
				
				int sliceOrdinal = Integer.parseInt(parts[1]);
				String struct = parts[2];
				
				if (specLine) {
					if (struct.equals("SIZES")) {
						//          0 1     2 3 4 5  6  7  8
						// #Patient_1,0,SIZES,w,h,N,dx,dy,dz
						scan.w = Integer.parseInt(parts[3]);
						scan.h = Integer.parseInt(parts[4]);
						scan.N = Integer.parseInt(parts[5]);
						scan.dx = Double.parseDouble(parts[6]);
						scan.dy = Double.parseDouble(parts[7]);
						scan.dz = Double.parseDouble(parts[8]);
						for (int i = 1; i <= scan.N; i++) {
							Slice s = new Slice();
							s.id = "" + i;
							scan.slices.add(s);
						}
						continue;
					}
					else {
						continue; // don't use seed points
					}
				}
				
				boolean ok = false;
				for (String s: STRUCTURES) {
					if (s.equals(struct)) ok = true;
				}
				if (!ok) {
					info("Unknown structure name found in solution file at line " + lineNo + ": " + struct);
					return false;
				}
				
				if (scan.slices.size() < sliceOrdinal) {
					if (!truth) {
						info("Unknown slice id found in solution file at line " + lineNo + ": " + id + ", " + sliceOrdinal);
						return false;
					}
				}
				Slice slice = scan.slices.get(sliceOrdinal - 1);				
				
				Map<String, List<Polygon>> nameToPolys = truth ? slice.nameToTruthPolygons : slice.nameToSolutionPolygons;
				List<Polygon> polygons = nameToPolys.get(struct);
				if (polygons == null) {
					polygons = new Vector<>();
					nameToPolys.put(struct, polygons);
				}
				
				int pos = line.lastIndexOf(struct) + struct.length() + 1;
				line = line.substring(pos);
				P2[] points = coordStringToPoints(line);
				
		    	Polygon p = new Polygon(points); 
				polygons.add(p);
			}
		} 
		catch (Exception e) {
			info("Error reading solution file");
			info("Line #" + lineNo + ": " + line);
			e.printStackTrace();
			return false;
		}
		finally {
			try {
				lnr.close();
			} 
			catch (Exception e) {
				// nothing
			}
		}
		return true;
	}
    
	public Metric[] score(Scan scan) {
		Metric[] ret = new Metric[scan.slices.size()];
		for (int i = 0; i < scan.slices.size(); i++) {
			Slice slice = scan.slices.get(i);
			Metric m = new Metric();
			for (String struct: STRUCTURES) {
				double areaTruth = 0;
				List<Polygon> truthPolygons = slice.nameToTruthPolygons.get(struct);
				if (truthPolygons != null) {
					for (Polygon p: truthPolygons) areaTruth += p.area;
				}
				double areaSolution = 0;
				List<Polygon> solutionPolygons = slice.nameToSolutionPolygons.get(struct);
				if (solutionPolygons != null) {
					for (Polygon p: solutionPolygons) areaSolution += p.area;
				}
				if (areaTruth == 0) { 
					if (areaSolution == 0) { // neither exist
						// nothing to do
					}
					else { // no truth, false sol for Tumor, otherwise ignore
						if (struct.equals(TUMOR_NAME)) {
							m.name2fp.put(struct, areaSolution);
						}
					}
				}
				else {
					if (areaSolution == 0) { // truth, no sol
						m.name2fn.put(struct, areaTruth);
					}
					else { // both exist, calc tp,fp,fn
						Area shapeT = new Area();
						for (Polygon p: truthPolygons) shapeT.add(p.shape);
						Area shapeS = new Area();
						for (Polygon p: solutionPolygons) shapeS.add(p.shape);
						// recalc areas to use the union
						areaTruth = area(shapeT);
						areaSolution = area(shapeS);
						
						shapeT.intersect(shapeS);
						double overlap = area(shapeT);
						m.name2tp.put(struct, overlap);
						m.name2fp.put(struct, areaSolution - overlap);
						m.name2fn.put(struct, areaTruth - overlap);
					}
				}
			} // for structures
			ret[i] = m;
		}
		return ret;
	}
	
	// based on http://stackoverflow.com/questions/2263272/how-to-calculate-the-area-of-a-java-awt-geom-area
	private double area(Area shape) {
		PathIterator i = shape.getPathIterator(null);
		double a = 0.0;
        double[] coords = new double[6];
        double startX = Double.NaN, startY = Double.NaN;
        Line2D segment = new Line2D.Double(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        while (! i.isDone()) {
            int segType = i.currentSegment(coords);
            double x = coords[0], y = coords[1];
            switch (segType) {
            case PathIterator.SEG_CLOSE:
                segment.setLine(segment.getX2(), segment.getY2(), startX, startY);
                a += area(segment);
                startX = startY = Double.NaN;
                segment.setLine(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
                break;
            case PathIterator.SEG_LINETO:
                segment.setLine(segment.getX2(), segment.getY2(), x, y);
                a += area(segment);
                break;
            case PathIterator.SEG_MOVETO:
                startX = x;
                startY = y;
                segment.setLine(Double.NaN, Double.NaN, x, y);
                break;
            }
            i.next();
        }
        if (Double.isNaN(a)) {
            throw new IllegalArgumentException("PathIterator contains an open path");
        } 
        else {
            return 0.5 * Math.abs(a);
        }
    }

    private double area(Line2D seg) {
        return seg.getX1() * seg.getY2() - seg.getX2() * seg.getY1();
    }
 
	private void writeScore(double s) {
		PrintWriter resultLog = null;
		try {
			resultLog = new PrintWriter(new File(outDir, "result.txt"));
			resultLog.println("" + s);
		} 
		catch (Exception e) {
			e.printStackTrace();
		} 
		finally {
			resultLog.close();
		}		
	}
	
	private void debug(String s) {
		if (DEBUG) System.out.println(s);
	}
	
	private void info(String s) {
		System.out.println("INFO: " + s);
		infoLog.println(s);
		infoLog.flush();
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.out.println("Usage: Scorer <provisional|final> <path-to-truth> <path-to-solution> <dir-of-output> [DEBUG]");
			System.exit(1);
		}
		String phase = args[0];
		String truthPath = args[1];
		String solPath = args[2];
		String outDir = args[3]; 
		if (args.length > 4 && args[4].equals("DEBUG")) {
			Scorer.DEBUG = true;
		}
		double score = new Scorer().run(phase, truthPath, solPath, outDir);
		System.out.println("Score: " + score);
	}
}

