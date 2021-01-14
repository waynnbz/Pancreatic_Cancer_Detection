package common;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Vector;

public class Utils {
		
	private static DecimalFormat df; 
	private static DecimalFormat df6; 
	static {
		df = new DecimalFormat("0.###");
		df6 = new DecimalFormat("0.######");
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();
		dfs.setDecimalSeparator('.');
		df.setDecimalFormatSymbols(dfs);
		df6.setDecimalFormatSymbols(dfs);		
	}

	/**
	 * Pretty print a double
	 */
	public static String f(double d) {
		return df.format(d);
	}
	public static String f6(double d) {
		return df6.format(d);
	}
	
	// Gets the lines of a text file at the given path 
	public static List<String> readTextLines(String path) {
		List<String> ret = new Vector<>();
		try {
			InputStream is = new FileInputStream(path);
	        InputStreamReader isr = new InputStreamReader(is, "UTF-8");
	        LineNumberReader lnr = new LineNumberReader(isr);
	        while (true) {
				String line = lnr.readLine();
				if (line == null) break;
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) continue;
				ret.add(line);
			}
			lnr.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}
	public static P2[] coordStringToPoints(String line) {
		// x1,y1,x2,y2,... 
		String[] parts = line.split(",");
		int n = parts.length / 2; 
		P2[] points = new P2[n];
		for (int i = 0; i < n; i++) {
			double x = Double.parseDouble(parts[2*i]);
			double y = Double.parseDouble(parts[2*i+1]);
			points[i] = new P2(x, y);
		}
		return points;		
	}
}


