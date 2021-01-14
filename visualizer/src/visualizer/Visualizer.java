/*
 * Pancreatic Tumor Detector Visualizer and Offline Tester
 * by walrus71
 * 
 * Version history:
 * ================
 * 1.0 (2020.12.14)
 * 		- Version at the first contest launch
 */

package visualizer;

import static common.Polygon.STRUCTURES;
import static common.Polygon.TUMOR_NAME;
import static common.Utils.f;
import static common.Utils.f6;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.Metric;
import common.P2;
import common.Polygon;
import common.Scan;
import common.Slice;
import common.Utils;
import scorer.Scorer;

public class Visualizer implements ActionListener, ItemListener, MouseListener, ChangeListener {
	
	private boolean hasGui = true;
	public String[] scanIds;
	private Scan currentScan;
	private int currentSlice = 0;
	private int currentX = 0;
	private int currentY = 0;
	private int dataW, dataH, dataN; // x,y,z sizes of current data matrix
	private double zScale; // ratio of slice thickness to pixel size
	private int[][][] data;	
	public String dataDir;
	private String solutionPath;
	private String metaInPath, metaOutPath;
	public Map<String, Scan> idToScan;
	private int loGray = 100;
	private int hiGray = 11000;
	
	private JFrame frame;
	private JPanel viewPanel, controlsPanel;
	private JCheckBox showTruthCb, showSolutionCb;
	private JCheckBox[] structureCbs;
	private JSlider loSlider, hiSlider;
	private JLabel grayLevelsLabel;
	private JComboBox<String> scanSelectorComboBox, zoomSelectorComboBox;
	private JTextArea logArea;
	private MapView mapView;
	private Font font = new Font("SansSerif", Font.PLAIN, 20);
	private int[] zoomLevels = new int[] {2,3,4,6,8};
	private int zoom = 2;
	
	private Color truthBorderColor          = new Color(  0, 255, 255, 255);
	private Color truthFillColor            = new Color(  0, 155, 255,  50);
	private Color truthMarkerColor          = new Color(  0, 155, 255, 200);
	private Color truthSeedColor            = new Color(255, 200, 200, 255);
	private Color truthVesselBorderColor    = new Color(255,   0, 255, 200);
	private Color truthVesselFillColor      = new Color(255,   0, 255,  50);
	private Color solutionBorderColor	    = new Color(255, 255,   0, 255);
	private Color solutionFillColor         = new Color(255, 255,   0,  50);
	private Color solutionMarkerColor       = new Color(255, 255,   0, 200);
	private Color solutionVesselBorderColor = new Color(255, 255, 125, 200);
	private Color solutionVesselFillColor   = new Color(255, 255, 125,  50);
	
	private void run() {
		loadMetaData();
		if (metaOutPath != null) {
			writeMetaData();
		}
		boolean solutionOk = false;
		if (solutionPath != null) {
			solutionOk = load(solutionPath, false);
		}
		
		if (hasGui) {
			DefaultComboBoxModel<String> cbm = new DefaultComboBoxModel<>(scanIds);
			scanSelectorComboBox.setModel(cbm);
			scanSelectorComboBox.setSelectedIndex(0);
			scanSelectorComboBox.addItemListener(this);
		}
		
		String detailsMarker = "Details:";
		log(detailsMarker);
		log("    #img: \tTP\tFP\tFN\tF-score");
		Map<String, Double> totals = new HashMap<>();
		for (String struct: STRUCTURES) totals.put(struct, 0.0);
		
		Scorer scorer = new Scorer();
		
		for (String id: scanIds) {
			log(id);
			Metric[] result = scorer.score(idToScan.get(id));
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
				
				log("  " + struct);
				log("    tp    : " + f(tp));
				log("    fp    : " + f(fp));
				log("    fn    : " + f(fn));
				log("    score : " + f6(f));
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
					log("    #" + i1 + ": \t" + (int)(tp) + "\t" + (int)(fp) + "\t" + 
							(int)(fn) + "\t" + f(f));
				}
			}
		}
		
		if (solutionOk) {
			double score = 0;
			double sumW = 0;
			for (String s: STRUCTURES) {
				double v = totals.get(s) / scanIds.length;
				totals.put(s, v);
				double w = s.equals(TUMOR_NAME) ? 7 : 1;
				score += w * v;
				sumW += w;
			}
			score /= sumW;
			String result = "Overall score: " + f6(score);
			for (String s: STRUCTURES) {
				result += "\n  " + s + ":\t" + f6(totals.get(s));
			}
			
			if (hasGui) { // display final result at the top
				String allText = logArea.getText();
				int pos = allText.indexOf(detailsMarker);
				String s1 = allText.substring(0, pos);
				String s2 = allText.substring(pos);
				allText = s1 + result + "\n\n" + s2;
				logArea.setText(allText);
				logArea.setCaretPosition(0);
				System.out.println(result);
			}
			else {
				log(result);
			}
		}
		else {
			log("Can't score.");
		}
		
		// the rest is for UI, not needed for scoring
		if (!hasGui) return;
		
		currentScan = idToScan.get(scanIds[0]);
		loadImages(0);
		repaintMap();
	}
	
	private void add(Map<String, Double> map, String key, Double value) {
		if (value == null || value == 0) return;
		Double old = map.get(key);
		if (old == null) old = 0.0;
		old += value;
		map.put(key, old);
	}
 
    public void loadMetaData() {
    	idToScan = new HashMap<>();
    	boolean hasMetaIn = metaInPath != null;
    	if (hasMetaIn) {
    		load(metaInPath, true);
    	}
    	else {
    		log("Loading scan list from " + dataDir + " ...");
			// gather scan ids
	    	for (File f: new File(dataDir).listFiles()) {
	    		if (f.isDirectory()) {
	    			String id = f.getName();
	    			Scan scan = new Scan(id);
	    			idToScan.put(id, scan);
	    		}
	    	}
    	}
	    
    	scanIds = idToScan.keySet().toArray(new String[0]);
		Arrays.sort(scanIds);
		
		int scanCnt = scanIds.length;
		int progressN =  Math.max(1, scanCnt / 20);		
		
		String line = null;
		int lineNo = 0;
		
		int cnt = 0;
		paintProgress(0);
    	// load scan and slice meta data
		for (Scan scan: idToScan.values()) {
			File scanDirTop = new File(dataDir, scan.id);
			File scanDir = null;
			for (File f: scanDirTop.listFiles()) { // find one with a name like Set_xxx
				if (f.getName().startsWith("Set_")) {
					scanDir = f; 
					break;
				}
			}
			scan.dir = scanDir;
			
			if (!hasMetaIn) {
				// read scan metric metadata
				File header = new File(scanDir, "CT_Image.txt");
				line = null;
				lineNo = 0;
				try {
					LineNumberReader lnr = new LineNumberReader(new FileReader(header));
					while (true) {
						line = lnr.readLine();
						lineNo++;
						if (line == null) break;
						line = line.trim();
						/*
						ImageSize = 512 512 136
						PixelSpacing = 0.46875 0.46875 0.5
						*/
						String[] parts = line.split("=");
						String tag = parts[0].trim();
						String[] values = parts[1].trim().split(" ");
						if (tag.equals("ImageSize")) {
							scan.w = Integer.parseInt(values[0]);
							scan.h = Integer.parseInt(values[1]);
							scan.N = Integer.parseInt(values[2]);
						}
						else if (tag.equals("PixelSpacing")) {
							scan.dx = Double.parseDouble(values[0]);
							scan.dy = Double.parseDouble(values[1]);
							scan.dz = Double.parseDouble(values[2]);
						}
					}
					lnr.close();
				} 
				catch (Exception e) {
					log("Error reading CT_Image.txt for : " + scan.id);
					log("Line #" + lineNo + ": " + line);
					e.printStackTrace();
					System.exit(0);
				}
				
				for (int i = 0; i < scan.N; i++) {
					Slice s = new Slice();
					int ordinal = i+1;
					s.id = "" + ordinal;
					scan.slices.add(s);
				}
				
				// load contours and seed points
				for (File f: scanDir.listFiles()) {
					String name = f.getName();
					if (!name.endsWith(".txt")) continue;
					if (name.startsWith("Slice")) {
						// Slice_122_Region_1_Structure_CA_CHA.txt
						name = name.replace(".txt", "");
						String[] parts = name.split("_");
						int sliceOrdinal = Integer.parseInt(parts[1]);
						Slice slice = scan.slices.get(sliceOrdinal - 1);
						for (String struct: Polygon.STRUCTURES) {
							if (name.contains(struct)) {
								loadContour(scan, slice, struct, f);
							}
						}
					}
					else if (name.startsWith("Structure")) {
						for (String struct: Polygon.STRUCTURES) {
							if (name.contains(struct)) {
								loadSeedPoint(scan, struct, f);
							}
						}
					}
			    	
				} // for scan txt files
			
				if (cnt % progressN == 0) {
			    	paintProgress((double)cnt / scanCnt);
			    }
			} // if !hasMetaIn
			
			cnt++;			
		} // for scans
	}
    
    private boolean load(String path, boolean truth) {
		File f = new File(path);
		String what = truth ? "truth" : "solution";
		if (f.exists()) {
			log("Loading " + what + " file from " +path);
			String line = null;
			int lineNo = 0;
			try {
				LineNumberReader lnr = new LineNumberReader(new FileReader(f));
				while (true) {
					line = lnr.readLine();
					lineNo++;
					if (line == null) break;
					line = line.replace(" " , "");
					if (line.isEmpty()) continue;
					
					// Patient_1,100,struct,x1,y1,x2,y2,...
					// or special lines:
					// #Patient_1,0,SIZES,w,h,N,dx,dy,dz
					// #Patient_1,slice,SEED,x,y
					
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
							log("Unknown scan id found in " + what + " file at line " + lineNo + ": " + id);
							System.exit(0);
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
						else if (struct.equals("SEED")) {
							// #Patient_1,slice,SEED,x,y
							Slice slice = scan.slices.get(sliceOrdinal - 1);
							double x = Double.parseDouble(parts[3]);
							double y = Double.parseDouble(parts[4]);
							List<P2> seeds = slice.nameToSeedPoints.get(TUMOR_NAME);
							if (seeds == null) {
								seeds = new Vector<>();
								slice.nameToSeedPoints.put(TUMOR_NAME, seeds);
							}
							seeds.add(new P2(x, y));							
							continue;
						}						
					}
					
					boolean ok = false;
					for (String s: STRUCTURES) {
						if (s.equals(struct)) ok = true;
					}
					if (!ok) {
						log("Unknown structure name found in " + what + " file at line " + lineNo + ": " + struct);
						System.exit(0);
					}
					
					if (scan.slices.size() < sliceOrdinal) {
						log("Unknown slice id found in " + what + " file at line " + lineNo + ": " + id + ", " + sliceOrdinal);
						System.exit(0);
					}
					Slice slice = scan.slices.get(sliceOrdinal - 1);
										
					Map<String, List<Polygon>> nameToPs = truth ? slice.nameToTruthPolygons : slice.nameToSolutionPolygons;
					List<Polygon> polygons = nameToPs.get(struct);
					if (polygons == null) {
						polygons = new Vector<>();
						nameToPs.put(struct, polygons);
					}
					
					int pos = line.lastIndexOf(struct) + struct.length() + 1;
					line = line.substring(pos);
					P2[] points = Utils.coordStringToPoints(line);
					
			    	Polygon p = new Polygon(points); 
					polygons.add(p);
				}
				lnr.close();
			} 
			catch (Exception e) {
				log("Error reading " + what + " file");
				log("Line #" + lineNo + ": " + line);
				e.printStackTrace();
				System.exit(0);
			}
		}
		else {
			log("Can't find " + what + " file " + f.getAbsolutePath());
			return false;
		}
		return true;
	}
    
    private void loadContour(Scan scan, Slice slice, String struct, File f) {
		String line = null;
		int lineNo = 0;
		List<P2> points = new Vector<>();
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(f));
			while (true) {
				line = lnr.readLine();
				lineNo++;
				if (line == null) break;
				/*
				{X=260, Y=171}
				{X=262, Y=171}
				*/
				line = line.replaceAll("[={}XY ]", "");
				String[] parts = line.split(",");
				int x = Integer.parseInt(parts[0]);
				int y = Integer.parseInt(parts[1]);
				points.add(new P2(x, y));
			}
			lnr.close();
			P2[] pArr = points.toArray(new P2[0]);
			Polygon p = new Polygon(pArr);
			List<Polygon> polygons = slice.nameToTruthPolygons.get(struct);
			if (polygons == null) {
				polygons = new Vector<>();
				slice.nameToTruthPolygons.put(struct, polygons);
			}
			polygons.add(p);
		} 
		catch (Exception e) {
			log("Error reading contour file " + f.getName() + " for scan: " + scan.id);
			log("Line #" + lineNo + ": " + line);
			e.printStackTrace();
			System.exit(0);
		}
	}
    
    private void loadSeedPoint(Scan scan, String struct, File f) {
		String line = null;
		int lineNo = 0;
		try {
			LineNumberReader lnr = new LineNumberReader(new FileReader(f));
			while (true) { // only one line
				line = lnr.readLine();
				lineNo++;
				if (line == null) break;
				// StructureCenterPoint = 291.103455275512, 192.705993389945, 108.092211756183
				line = line.substring(line.indexOf("=") + 1);
				line = line.replace(" ", "");
				String[] parts = line.split(",");
				double x = Double.parseDouble(parts[0]);
				double y = Double.parseDouble(parts[1]);
				double z = Double.parseDouble(parts[2]);
				int sliceN = (int)(z + 0.5 - 1);
				if (sliceN < 0) sliceN = 0;
				if (sliceN > scan.N - 1) sliceN = scan.N - 1;
				P2 p = new P2(x, y);
				Slice slice = scan.slices.get(sliceN);
				List<P2> ps = slice.nameToSeedPoints.get(struct);
				if (ps == null) {
					ps = new Vector<>();
					slice.nameToSeedPoints.put(struct, ps);
				}
				ps.add(p);
			}
			lnr.close();
		} 
		catch (Exception e) {
			log("Error reading seed point file " + f.getName() + " for scan: " + scan.id);
			log("Line #" + lineNo + ": " + line);
			e.printStackTrace();
			System.exit(0);
		}
	}
    
	private void writeMetaData() {
		/*
		String[] ss = new String[] {
				"Patient_1", "Patient_10", "Patient_101", "Patient_103", "Patient_104", "Patient_106", "Patient_107", "Patient_109", "Patient_112", "Patient_113", "Patient_114", "Patient_115", "Patient_116", "Patient_117", "Patient_119", "Patient_121", "Patient_122", "Patient_125", "Patient_131", "Patient_133", "Patient_134", "Patient_137", "Patient_139", "Patient_140", "Patient_141", "Patient_142", "Patient_143", "Patient_145", "Patient_146", "Patient_147", "Patient_148", "Patient_149", "Patient_150", "Patient_153", "Patient_156", "Patient_158", "Patient_16", "Patient_160", "Patient_163", "Patient_165", "Patient_17", "Patient_172", "Patient_174", "Patient_175", "Patient_176", "Patient_178", "Patient_182", "Patient_183", "Patient_185", "Patient_188", "Patient_189", "Patient_19", "Patient_194", "Patient_195", "Patient_198", "Patient_2", "Patient_201", "Patient_203", "Patient_204", "Patient_206", "Patient_207", "Patient_208", "Patient_209", "Patient_21", "Patient_211", "Patient_212", "Patient_215", "Patient_216", "Patient_217", "Patient_219", "Patient_22", "Patient_220", "Patient_221", "Patient_225", "Patient_229", "Patient_230", "Patient_231", "Patient_232", "Patient_233", "Patient_234", "Patient_235", "Patient_236", "Patient_238", "Patient_241", "Patient_242", "Patient_26", "Patient_27", "Patient_3", "Patient_31", "Patient_32", "Patient_33", "Patient_36", "Patient_39", "Patient_4", "Patient_41", "Patient_42", "Patient_43", "Patient_45", "Patient_46", "Patient_50", "Patient_52", "Patient_57", "Patient_6", "Patient_62", "Patient_63", "Patient_66", "Patient_67", "Patient_7", "Patient_70", "Patient_72", "Patient_76", "Patient_81", "Patient_82", "Patient_84", "Patient_86", "Patient_88", "Patient_9", "Patient_90", "Patient_95", "Patient_98"
		};*/
		
		try {
			FileOutputStream out = new FileOutputStream(metaOutPath);
			StringBuilder sb = null;
			for (String id: scanIds) {
				Scan scan = idToScan.get(id);
				
				// spec line for truth:
				// #Patient1,0,SIZES,w,h,N,dx,dy,dz
				sb = new StringBuilder();
				sb.append("#").append(id).append(",0,SIZES,");
				sb.append(scan.w).append(",").append(scan.h).append(",").append(scan.N).append(",");
				sb.append(scan.dx).append(",").append(scan.dy).append(",").append(scan.dz).append("\n");
				out.write(sb.toString().getBytes());
				
				for (int i = 0; i < scan.N; i++) {
					Slice s = scan.slices.get(i);
					for (String struct: STRUCTURES) {
						List<Polygon> pList = s.nameToTruthPolygons.get(struct);
						if (pList == null) continue;
						for (Polygon p: pList) {
							sb = new StringBuilder();
							// Patient_1,100,SMA,x1,y1,x2,y2,...
							sb.append(id).append(",").append(s.id).append(",").append(struct);
							for (P2 p2: p.points) {
								sb.append(",").append(f(p2.x)).append(",").append(f(p2.y));
							}
							sb.append("\n");
							out.write(sb.toString().getBytes());
						}
					}
					// spec line for truth: seed point, if we have it on this slice
					// #Patient1,slice,SEED,x,y
					List<P2> pList = s.nameToSeedPoints.get(TUMOR_NAME);
					if (pList != null && !pList.isEmpty()) {
						P2 p = pList.get(0);
						sb = new StringBuilder();
						sb.append("#").append(id).append(",").append(s.id).append(",SEED,");
						sb.append(p.x).append(",").append(p.y).append("\n");
						out.write(sb.toString().getBytes());						
					}
				}
			}
			out.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void loadImages(int sliceToLoad) {
		paintProgress(0);
		File scanDir = currentScan.dir;
		dataW = currentScan.w;
		dataH = currentScan.h;
		dataN = currentScan.N;
		data = new int[dataW][dataH][dataN];
		zScale = currentScan.dz * currentScan.N / (currentScan.dx * currentScan.w);
		int progressN = dataN / 20;
		for (int k = 0; k < dataN; k++) {
			File f = null;
			try {
				int k1 = k+1;
				f = new File(scanDir, "Slice_" + k1 + "_CT_Image.png");
				if (!f.exists()) {
					log("Can't find image file: " + f.getAbsolutePath());
					return;
				}
				BufferedImage img2 = ImageIO.read(f);
			    Raster r2 = img2.getRaster();
			    for (int i = 0; i < dataW; i++) for (int j = 0; j < dataH; j++) {
			    	int[] samples = r2.getPixel(i, j, new int[4]);
			    	int c = samples[0];
			    	if (c < 0) c += 65536; // stored as short
				    data[i][j][k] = c;
			    }
			    if (k % progressN == 0) {
			    	paintProgress((double)k / dataN);
			    }
			}
			catch (Exception e) {
				log("Error reading image " + f.getAbsolutePath());
				e.printStackTrace();
			}
		}
		currentSlice = sliceToLoad;
		currentY = dataH / 2;
		currentX = dataW / 2;
		
		paintProgress(-1);
		if (mapView != null) mapView.clearMetrics();		
	}


	/**************************************************************************************************
	 * 
	 *              THINGS BELOW THIS ARE UI-RELATED, NOT NEEDED FOR SCORING
	 * 
	 **************************************************************************************************/
	
	public void setupGUI() {
		if (!hasGui) return;
		int fullW = (int) Toolkit.getDefaultToolkit().getScreenSize().getWidth();
		frame = new JFrame("Pancreatic Tumor Detection Visualizer");
		int W = 800;
		int H = 900;
		frame.setSize(Math.min(fullW, W * 5 / 3), H);
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		Container cp = frame.getContentPane();
		cp.setLayout(new BorderLayout());
		
		viewPanel = new JPanel();
		viewPanel.setPreferredSize(new Dimension(W, H));
		cp.add(viewPanel, BorderLayout.WEST);
		
		controlsPanel = new JPanel();
		cp.add(controlsPanel, BorderLayout.CENTER);

		viewPanel.setLayout(new BorderLayout());
		mapView = new MapView();
		viewPanel.add(mapView, BorderLayout.CENTER);
		
		controlsPanel.setLayout(new GridBagLayout());
		GridBagConstraints c2 = new GridBagConstraints();
		c2.fill = GridBagConstraints.BOTH;
		c2.gridx = 0;
		c2.weightx = 1;
		int y = 0;
		
		showTruthCb = new JCheckBox("Show truth contours");
		showTruthCb.setSelected(true);
		showTruthCb.addActionListener(this);
		c2.gridy = y++;
		controlsPanel.add(showTruthCb, c2);
		
		showSolutionCb = new JCheckBox("Show solution contours");
		showSolutionCb.setSelected(true);
		showSolutionCb.addActionListener(this);
		c2.gridy = y++;
		controlsPanel.add(showSolutionCb, c2);
		
		structureCbs = new JCheckBox[4];
		for (int i = 0; i < 4; i++) {
			JCheckBox cb = new JCheckBox(STRUCTURES[i]);
			structureCbs[i] = cb;
			cb.setSelected(true);
			cb.addActionListener(this);
			c2.gridy = y++;
			controlsPanel.add(cb, c2);
		}
		
		grayLevelsLabel = new JLabel();
		grayLevelsLabel.setBorder(new EmptyBorder(2, 2, 5, 2));
		setGrayLevelText();
		c2.gridy = y++;
		controlsPanel.add(grayLevelsLabel, c2);
		
		Dictionary<Integer, JLabel> dict = new Hashtable<>();
		dict.put(  0, new JLabel("1"));
		dict.put(100, new JLabel("10"));
		dict.put(200, new JLabel("100"));
		dict.put(300, new JLabel("1k"));
		dict.put(400, new JLabel("10k"));
		dict.put(500, new JLabel("100k"));
		loSlider = new JSlider(0, 500);
		loSlider.setPaintTicks(true);
		loSlider.setLabelTable(dict);
		loSlider.setPaintLabels(true);
		loSlider.setValue(grayToSlider(loGray));
		loSlider.addChangeListener(this);
		c2.gridy = y++;
		controlsPanel.add(loSlider, c2);
		
		hiSlider = new JSlider(0, 500);
		hiSlider.setValue(grayToSlider(hiGray));
		hiSlider.addChangeListener(this);
		c2.gridy = y++;
		controlsPanel.add(hiSlider, c2);		
		
		String[] zooms = new String[zoomLevels.length];
		for (int i = 0; i < zoomLevels.length; i++) {
			zooms[i] = zoomLevels[i] + "x zoom";
		}
		zoomSelectorComboBox = new JComboBox<>(zooms);
		zoomSelectorComboBox.setSelectedIndex(0);
		zoomSelectorComboBox.addItemListener(this);
		c2.gridy = y++;
		controlsPanel.add(zoomSelectorComboBox, c2);
		
		scanSelectorComboBox = new JComboBox<>(new String[] {"..."});
		c2.gridy = y++;
		controlsPanel.add(scanSelectorComboBox, c2);
		
		JScrollPane sp = new JScrollPane();
		logArea = new JTextArea("", 10, 20);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
		logArea.addMouseListener(this);
		sp.getViewport().setView(logArea);
		c2.gridy = y++;
		c2.weighty = 10;
		controlsPanel.add(sp, c2);

		frame.setVisible(true);
	}
	
	private void setGrayLevelText() {
		grayLevelsLabel.setText("Gray levels: " + loGray + " - " + hiGray);
	}
	
	private int sliderToGray(int v) {
		return (int)(Math.pow(10, (double)v / 100));
	}
	private int grayToSlider(int v) {
		if (v == 0) return 0;
		return (int)(100 * Math.log10(v));
	}
	
	private void repaintMap() {
		if (mapView != null) mapView.repaint();
	}
	
	private void paintProgress(double d) {
		if (mapView != null) mapView.paintProgress(d);
	}
	
	@SuppressWarnings("serial")
	private class MapView extends JLabel implements MouseListener, MouseWheelListener, MouseMotionListener {
		
		private int mouseX;
		private int mouseY;
		private double progress = 0;
		private boolean metricsValid = false;
		private int W, H;
		private int smallX0, frontY0, sideY0, smallW, smallH;
		private double smallZScale, smallXScale, smallYScale;
		private int zoomY0, zoomH;
		private final int M = 5; // margin		
		private final Color markerColor = new Color(255,255,0,150);
		private BufferedImage sliceImage, sideImage, frontImage, zoomImage;
		private int[] grays;
		
		public MapView() {
			super();
			this.addMouseListener(this);
			this.addMouseWheelListener(this);
			this.addMouseMotionListener(this);
			grays = new int[256];
			for (int i = 0; i < 256; i++) grays[i] = i | (i<<8) | (i<<16);
		}	
		
		public void clearMetrics() {
			metricsValid = false;
		}
		
		public void paintProgress(double d) {
			progress = d;
			if (d >= 0) {
				W = this.getWidth();
				H = this.getHeight();
				Rectangle rect = new Rectangle(0, H/2 - 20, W, 40);
				this.paintImmediately(rect);
			}
		}

		private void calcMetrics() {
			W = this.getWidth();
			H = this.getHeight();
			smallX0 = dataW + M;
			smallW = W - M - smallX0;
			smallH = (int)(smallW * zScale);
			if (dataH > 2 * smallH + M) {
				frontY0 = dataH - M - 2*smallH;
				sideY0 = dataH - smallH;
			}
			else {
				frontY0 = M;
				sideY0 = 2*M + smallH;
			}
			smallZScale = (double)smallH / dataN;
			smallXScale = (double)smallW / dataW;
			smallYScale = (double)smallW / dataH;
			zoomY0 = dataH + M;
			zoomH = H - M - zoomY0;
			sliceImage = new BufferedImage(dataW, dataH, BufferedImage.TYPE_INT_RGB);
			frontImage = new BufferedImage(smallW, smallH, BufferedImage.TYPE_INT_RGB);
			sideImage = new BufferedImage(smallW, smallH, BufferedImage.TYPE_INT_RGB);
			zoomImage = new BufferedImage(W-M+1, zoomH, BufferedImage.TYPE_INT_RGB);
			metricsValid = true;
		}

		@Override
		public void paint(Graphics gr) {
			Graphics2D g2 = (Graphics2D) gr;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			
			W = this.getWidth();
			H = this.getHeight();
			g2.setColor(Color.black);
			g2.fillRect(0, 0, W, H);
			
			if (progress >= 0) {
				g2.setColor(new Color(50,50,50));
				g2.fillRect(0, H/2 - 20, W, 40);
				int w = (int) (W * progress);
				g2.setColor(new Color(50,200,50));
				g2.fillRect(0, H/2 - 20, w, 40);
				return;
			}
			
			if (currentScan == null || data == null) return;
			
			if (!metricsValid) {
				calcMetrics();
			}
			
			// main view
			double graySpan = hiGray - loGray;
            for (int i = 0; i < dataW; i++) for (int j = 0; j < dataH; j++) {
				int v = data[i][j][currentSlice];
				int c = (int) (255 * (v - loGray) / graySpan);
				if (c < 0) c = 0;
				if (c > 255) c = 255;
                sliceImage.setRGB(i, j, grays[c]);
			}
            g2.drawImage(sliceImage, 0, 0, null);
            
            // zoomed view
            if (mouseX < dataW && mouseY < dataH) {
				for (int i = M; i < W-M; i++) for (int j = 0; j < zoomH; j++) {
					int x = mouseX + (i - W/2) / zoom;
					if (x < 0 || x >= dataW) {
						zoomImage.setRGB(i-M, j, grays[0]);
						continue;
					}
					int y = mouseY + (j - zoomH/2) / zoom;
					if (y < 0 || y >= dataH) {
						zoomImage.setRGB(i-M, j, grays[0]);
						continue;
					}
					
					int v = data[x][y][currentSlice];
					int c = (int) (255 * (v - loGray) / graySpan);
					if (c < 0) c = 0;
					if (c > 255) c = 255;
                    zoomImage.setRGB(i-M, j, grays[c]);
				}
			    g2.drawImage(zoomImage, M, zoomY0, null);

				g2.setColor(markerColor);
				int d = 10;
				int xc = W/2;
				int yc = zoomY0 + zoomH/2;
				g2.drawLine(xc-2*d, yc, xc-d, yc);
				g2.drawLine(xc+2*d, yc, xc+d, yc);
				g2.drawLine(xc, yc-2*d, xc, yc-d);
				g2.drawLine(xc, yc+2*d, xc, yc+d);
			}

			int ySlice = (int)(currentSlice * smallZScale + 0.5);
			
			// front view
            for (int i = 0; i < smallW; i++) for (int j = 0; j < smallH; j++) {
				int x = (int)(i / smallXScale);
				if (x >= dataW) x = dataW-1;
				int slice = (int)(j / smallZScale);
				if (slice >= dataN) slice = dataN-1;
				int v = data[x][currentY][slice];
				int c = (int) (255 * (v - loGray) / graySpan);
				if (c < 0) c = 0;
				if (c > 255) c = 255;
                frontImage.setRGB(i, j, grays[c]);
			}
            g2.drawImage(frontImage, smallX0,frontY0, null);

			g2.setColor(markerColor);
			g2.drawLine(smallX0, frontY0 + ySlice, smallX0 + smallW, frontY0 + ySlice);
			int xPos = (int) (currentX * smallXScale);
			g2.drawLine(smallX0 + xPos, frontY0, smallX0 + xPos, frontY0 + smallH);
			
			// side view
            for (int i = 0; i < smallW; i++) for (int j = 0; j < smallH; j++) {
				int y = (int)(i / smallYScale);
				if (y >= dataH) y = dataH-1;
				int slice = (int)(j / smallZScale);
				if (slice >= dataN) slice = dataN-1;
				int v = data[currentX][y][slice];
				int c = (int) (255 * (v - loGray) / graySpan);
				if (c < 0) c = 0;
				if (c > 255) c = 255;
                sideImage.setRGB(i, j, grays[c]);
			}
            g2.drawImage(sideImage, smallX0, sideY0, null);
            for (int j = 0; j < smallH; j++) {
				int slice = (int)(j / smallZScale);
				if (slice >= dataN) slice = dataN-1;
				if (currentScan.slices.get(slice).nameToTruthPolygons.containsKey(TUMOR_NAME)) {
					g2.setColor(truthMarkerColor);
					g2.drawLine(smallX0 + smallW - 3*M, j + sideY0, smallX0 + smallW - M, j + sideY0);
				}
				if (currentScan.slices.get(slice).nameToSolutionPolygons.containsKey(TUMOR_NAME)) {
					g2.setColor(solutionMarkerColor);
					g2.drawLine(smallX0 + smallW - 5*M, j + sideY0, smallX0 + smallW - 3*M, j + sideY0);
				}
            }

			g2.setColor(markerColor);
			g2.drawLine(smallX0, sideY0 + ySlice, smallX0 + smallW, sideY0 + ySlice);
			int yPos = (int) (currentY * smallYScale);
			g2.drawLine(smallX0 + yPos, sideY0, smallX0 + yPos, sideY0 + smallH);
			
			String levelInfo = "";
			if (mouseX < dataW && mouseY < dataH) {
				levelInfo = " (" + data[mouseX][mouseY][currentSlice] + ")";
			}
			g2.setColor(Color.white);
			g2.setFont(font);
			int s1 = currentSlice + 1;
			g2.drawString("#" + s1 + levelInfo, 2*M, 2 * font.getSize());
			
			Slice slice = currentScan.slices.get(currentSlice);
			if (showTruthCb.isSelected()) {
				for (int i = 0; i < 4; i++) {
					if (!structureCbs[i].isSelected()) continue;
					String struct = STRUCTURES[i];
					Color borderC = i == 0 ? truthBorderColor : truthVesselBorderColor;
					Color fillC = i == 0 ? truthFillColor : truthVesselFillColor;
					List<Polygon> truthPolygons = slice.nameToTruthPolygons.get(struct);
					if (truthPolygons != null) {
						for (Polygon p: truthPolygons) {
							drawPoly(p, g2, borderC, fillC);
						}
					}
					if (struct.equals(TUMOR_NAME) && slice.nameToSeedPoints.get(TUMOR_NAME) != null) {
						for (P2 seed: slice.nameToSeedPoints.get(TUMOR_NAME)) {
							g2.setColor(truthSeedColor);
							int x = (int)seed.x;
							int y = (int)seed.y;
							g2.drawLine(x - M, y - M, x + M, y + M);
							g2.drawLine(x - M, y + M, x + M, y - M);
						}
					}
				}
			}
			
			if (showSolutionCb.isSelected()) {
				for (int i = 0; i < 4; i++) {
					if (!structureCbs[i].isSelected()) continue;
					String struct = STRUCTURES[i];
					Color borderC = i == 0 ? solutionBorderColor : solutionVesselBorderColor;
					Color fillC = i == 0 ? solutionFillColor : solutionVesselFillColor;
					List<Polygon> ps = slice.nameToSolutionPolygons.get(struct);
					if (ps != null) {
						for (Polygon p: ps) {
							drawPoly(p, g2, borderC, fillC);
						}
					}
				}
			}
		}

		private void drawPoly(Polygon p, Graphics2D g2, Color border, Color fill) {
			g2.setColor(border);
			g2.draw(p.shape);
			g2.setColor(fill);
			g2.fill(p.shape);
		}

		@Override
		public void mouseClicked(java.awt.event.MouseEvent e) {
			if (!metricsValid) return;
			
			boolean needRepaint = false;
			
			int x = e.getX();
			int y = e.getY();
			if (x < dataW && y < dataH) {
				currentX = x;
				currentY = y;
				needRepaint = true;
			}
			else if (x >= smallX0 && x < smallX0 + smallW) {
				if (y >= frontY0 && y < frontY0 + smallH) {
					currentX = (int)((x - smallX0) / smallXScale);
					currentSlice = (int)((y - frontY0) / smallZScale);
					needRepaint = true;
				}
				else if (y >= sideY0 && y < sideY0 + smallH) {
					currentY = (int)((x - smallX0) / smallYScale);
					currentSlice = (int)((y - sideY0) / smallZScale);
					needRepaint = true;
				}
			}
			
			if (needRepaint) repaintMap();
		}
		
		@Override
		public void mouseMoved(MouseEvent e) {
			if (!metricsValid) return;
			
			int x = e.getX();
			int y = e.getY();
			if (x < dataW && y < dataH) {
				mouseX = x;
				mouseY = y;
				repaintMap();
			}
		}
				
		@Override
		public void mouseDragged(MouseEvent e) {
			// nothing			
		}
		@Override
		public void mouseReleased(java.awt.event.MouseEvent e) {
			// nothing
		}
		@Override
		public void mouseEntered(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		}
		@Override
		public void mouseExited(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getDefaultCursor());
		}
		@Override
		public void mousePressed(java.awt.event.MouseEvent e) {
			// nothing
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			boolean changed = false;
			if (e.getWheelRotation() > 0 && currentSlice < dataN - 1) {
				currentSlice++;
				changed = true;
			}
			else if (e.getWheelRotation() < 0 && currentSlice > 0) {
				currentSlice--;
				changed = true;
			}
			
			if (changed) repaintMap();
		}
	} // class MapView
	
	@Override
	public void actionPerformed(ActionEvent e) {
		// check boxes clicked
		repaintMap();
	}
	
	@Override
	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			if (e.getSource() == scanSelectorComboBox) {
				// new image selected
				String id = (String) scanSelectorComboBox.getSelectedItem();
				if (!id.equals(currentScan.id)) {
					currentScan = idToScan.get(id);
					loadImages(0);
					repaintMap();
				}
			}
			else if (e.getSource() == zoomSelectorComboBox) {
				int i = zoomSelectorComboBox.getSelectedIndex();
				zoom = zoomLevels[i];
				repaintMap();
			}
		}
	}	

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getSource() != logArea) return;
		Set<String> idSet = new HashSet<>();
		for (String id: scanIds) idSet.add(id);
		try {
			int lineIndex = logArea.getLineOfOffset(logArea.getCaretPosition());
			String text = logArea.getText();
			String[] lines = text.split("\n");
			String line = lines[lineIndex].trim();
			if (idSet.contains(line) && !line.equals(currentScan.id)) {
				currentScan = idToScan.get(line);
				scanSelectorComboBox.setSelectedItem(line);
				loadImages(0);
				repaintMap();
			}
			else if (line.startsWith("#")) { // #xxx:
				try {
					line = line.substring(1);
					line = line.substring(0, line.indexOf(":"));
					int slice = Integer.parseInt(line) - 1;
					for (int i = lineIndex - 1; i > 0; i--) {
						line = lines[i].trim();
						if (idSet.contains(line)) {
							if (line.equals(currentScan.id)) {
								currentSlice = slice;
								repaintMap();
								break;
							}
							else {
								currentScan = idToScan.get(line);
								scanSelectorComboBox.setSelectedItem(line);
								loadImages(slice);
								repaintMap();
								break;
							}
						}
					}
				}
				catch (Exception ex) {
					// nothing
				}
			}
		} 
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	
	private void log(String s) {
		if (logArea != null) logArea.append(s + "\n");
		System.out.println(s);
	}
	
	@Override
	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == loSlider) {
			loGray = sliderToGray(loSlider.getValue());
		}
		else if (e.getSource() == hiSlider) {
			hiGray = sliderToGray(hiSlider.getValue());
		}
		setGrayLevelText();
		repaintMap();		
	}
	
	private static void exit(String s) {
		System.out.println(s);
		System.exit(1);
	}
	
	public static void main(String[] args) throws Exception {
		boolean setDefaults = true;
		for (int i = 0; i < args.length; i++) { // to change settings easily from Eclipse
			if (args[i].equals("-no-defaults")) setDefaults = false;
		}
		
		Visualizer v = new Visualizer();
		String dir;
		
		// These are just some default settings for local testing, can be ignored.
		
		// sample data
		dir = "../data/sample/";
		v.solutionPath = dir + "sol.csv";
		v.metaInPath = dir + "sample-meta-gt.txt";
		
		// all
//		dir = "../data/all/";
//		v.metaInPath  = "../data/all-meta.txt";
//		v.metaOutPath = "../data/train-meta-gt.txt";
		
		// training data
//		dir = "../data/train/";
//		v.metaInPath = "../data/train-meta-gt.txt";
		
		// test data
//		dir = "../data/test/";
//		v.metaInPath = "../data/test-meta-gt.txt";
		
		v.dataDir = dir;
		v.hasGui = true;
		
		if (setDefaults) {
			v.hasGui = true;
			v.dataDir = null;
			v.solutionPath = null;
			v.metaInPath = null;
			v.metaOutPath = null;
		}
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-data-dir")) v.dataDir = args[i+1];
			if (args[i].equals("-solution")) v.solutionPath = args[i+1];
			if (args[i].equals("-meta-in")) v.metaInPath = args[i+1];
			if (args[i].equals("-meta-out")) v.metaOutPath = args[i+1];
			if (args[i].equals("-lo-gray")) v.loGray = Integer.parseInt(args[i+1]);
			if (args[i].equals("-hi-gray")) v.hiGray = Integer.parseInt(args[i+1]);
			if (args[i].equals("-no-gui")) v.hasGui = false;
		}
		
		if (v.dataDir == null) exit("Data directory not set.");
		
		v.setupGUI();
		v.run();
	}
}