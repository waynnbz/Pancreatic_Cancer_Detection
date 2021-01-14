package common;

import java.io.File;
import java.util.List;
import java.util.Vector;

public class Scan {
	public String id;
	public File dir;
	public int w, h; // image size
	public int N; // slice count
	//public double x0, y0, z; // real space (mm)
	public double dx, dy, dz; // pixel size (mm / pixel)
	public List<Slice> slices = new Vector<>();
	
	public Scan(String id) {
		this.id = id;
	}
}