import gurobi.*;
import java.util.*;
import javax.swing.*;
import java.applet.*;
import java.awt.*;
import java.awt.event.*;

public class CakeCutting extends Applet implements ActionListener {
	
	private static final long serialVersionUID = 1L;
	private static double start;
	private static double end;
	private static int n; //number of players 
	private static int k; //number of intervals
	private static double[] vals;

	private static JTextField cakeStart = new JTextField("0", 3);
	private static JTextField cakeEnd = new JTextField("1", 3);
	private static JTextField num = new JTextField("3", 5);
	private static JTextArea people = new JTextArea(5,20);

	public void init() {
		setSize(600,300); setBackground(new Color(204,213,227)); setLayout(new BorderLayout());

		JPanel cake = new JPanel();
		cakeStart.setHorizontalAlignment(JTextField.CENTER); cakeEnd.setHorizontalAlignment(JTextField.CENTER);
		cake.add(new JLabel("Cake [")); cake.add(cakeStart); cake.add(new JLabel(",")); cake.add(cakeEnd); cake.add(new JLabel("]"));
		add(cake, BorderLayout.NORTH);

		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.add(Box.createRigidArea(new Dimension(0, 3)));
		JPanel panel2 = new JPanel();
		num.setHorizontalAlignment(JTextField.CENTER);
		panel2.add(new JLabel("Players")); panel2.add(num);
		Button calc = new Button("solve LP!"); calc.addActionListener(this);
		panel.add(panel2); panel.add(people); panel.add(calc);
		add(panel, BorderLayout.CENTER);
	}

	public void actionPerformed(ActionEvent e) {
		start = Double.parseDouble(cakeStart.getText());
		end = Double.parseDouble(cakeEnd.getText());
		n = Integer.parseInt(num.getText());

		CakeCutter[] players = new CakeCutter[n];
		ArrayList<Double> boundaries = new ArrayList<Double>();
		boundaries.add(start); boundaries.add(end);

		StringTokenizer st = new StringTokenizer(people.getText());
		for (int i = 1; i <= n; i++) {
			players[i-1] = new CakeCutter("Player " + i,  Double.parseDouble(st.nextToken()), Double.parseDouble(st.nextToken()), start, end);
			if (boundaries.indexOf(players[i-1].left()) == -1)
				boundaries.add(players[i-1].left());
			if (boundaries.indexOf(players[i-1].right()) == -1)
				boundaries.add(players[i-1].right());
		}

		Collections.sort(boundaries);
		k = boundaries.size() - 1;

		try {
			vals = solveLP(players, boundaries);
			Object[][] cellData = new Object[n+1][k+2];
			String[] cols = new String[k+2];
			cellData[0][0] = ""; cols[0] = ""; cols[k+1] = "Wt. Total"; cellData[0][k+1] = cols[k+1];
			for (int j = 1; j <= k; j++) {
				cols[j] = "[" + boundaries.get(j-1) + "," + boundaries.get(j) + "]";
				cellData[0][j] = cols[j]; 
			}
			for (int i = 1; i <= n; i++) {
				double total = 0;
				for (int j = 0; j <= k; j++) {
					if (j == 0)
						cellData[i][j] = players[i-1].name();
					else {
						double value = vals[5*(i-1) + (j-1)];
						cellData[i][j] = value;
						total += value;
					}
				}
				cellData[i][k+1] = players[i-1].coeff() * total;
			}
			JTable table = new JTable(cellData, cols);
			add(table, BorderLayout.SOUTH);
			table.setEnabled(false);

		} catch (Throwable e1) { e1.printStackTrace(); }

	}

	public static double[] solveLP(CakeCutter[] players, ArrayList<Double> boundaries) throws Throwable {
		vals = new double[15];

		GRBEnv env = new GRBEnv("cakecut.log");
		GRBModel model = new GRBModel(env);

		ArrayList<GRBVar> vars = new ArrayList<GRBVar>();
		char varType = GRB.CONTINUOUS;

		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= k; j++)
				vars.add(model.addVar(0.0, Integer.MAX_VALUE, 0.0, varType, "x_" + i + "," + j));
		}
		model.update();

		GRBLinExpr happiness = new GRBLinExpr();
		for (int i = 0; i < n; i++) {
			GRBLinExpr portion = new GRBLinExpr();
			for (int j = 0; j < k; j++) {
				if (players[i].like(boundaries.get(j)))
					portion.addTerm(players[i].coeff(), vars.get(5*i + j));
			}
			model.addConstr(portion, GRB.GREATER_EQUAL, 1.0/n, players[i].name());
			happiness.add(portion);
		}
		model.setObjective(happiness, GRB.MAXIMIZE);

		for (int j = 0; j < boundaries.size() - 1; j++) {
			GRBLinExpr interval = new GRBLinExpr();
			for (int i = 0; i < n; i++) {
				if (players[i].like(boundaries.get(j)))
					interval.addTerm(1.0, vars.get(5*i + j));
			}
			model.addConstr(interval, GRB.LESS_EQUAL, boundaries.get(j+1) - boundaries.get(j), "interval " + (j+1));
		}

		model.getEnv().set(GRB.IntParam.OutputFlag, 0); // suppress Gurobi output
		model.optimize();
		int status = model.get(GRB.IntAttr.Status);	// 2 opt; 3 infeas; 9 timed out

		if (status == GRB.Status.OPTIMAL) {
			for(int index = 0; index < vals.length; index++) {	// set variable values
				GRBVar var = vars.get(index);
				vals[index] = var.get(GRB.DoubleAttr.X);
			}
		}
		else if (status == GRB.Status.INFEASIBLE) {
			for(int index = 0; index < vals.length; index++)
				vals[index] = -1;		
		}
		else if (status == GRB.Status.TIME_LIMIT){	// timed out
			for(int index = 0; index < vals.length; index++)
				vals[index] = -2;
		}
		else {
			System.err.println("unknown Gurobi status: " + status);
			System.exit(2);
		}

		// ----- clean up
		model.dispose();
		env.dispose();
		return vals;
	}

}

class CakeCutter {
	private String name;
	private double start;
	private double end;
	private double left;
	private double right;

	public CakeCutter(String n, double l, double r, double s, double e) {
		name = n;
		left = l;
		right = r;
		start = s;
		end = e;
	}

	public CakeCutter(String n, double l, double r) {
		name = n;
		left = l;
		right = r;
	}

	public String name() {
		return name;
	}

	public double left() {
		return left;
	}

	public double right() {
		return right;
	}

	public double coeff() {
		return (end - start)/(right - left);
	}

	public boolean like(double d) {
		return d >= left && d < right;
	}

}