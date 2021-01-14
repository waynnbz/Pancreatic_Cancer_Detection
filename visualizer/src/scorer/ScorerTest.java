package scorer;

public class ScorerTest {

	public static void main(String[] args) throws Exception {
		String phase = "provisional";
		String truthPath = 
				//"../data/train-meta-gt.txt";
				//"../data/sample/truth.csv";
				"../scorer-git/provisional_data/provisional_truth.csv";
		String solPath = 
				//"../data/train-meta-gt.txt";
				//"../data/sample/sol.csv";
				"../tester-data/zero-score-prov.csv";
		String outDir = "../tester-data/out"; 
		Scorer.DEBUG = true;
		double score = new Scorer().run(phase, truthPath, solPath, outDir);
		System.out.println("Score: " + score);
	}
}
