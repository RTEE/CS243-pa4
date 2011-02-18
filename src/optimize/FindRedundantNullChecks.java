package optimize;

import java.util.Set;
import java.util.TreeSet;


import joeq.Class.jq_Class;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadIterator;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Operator.NullCheck;
import joeq.Main.Helper;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_StaticMethod;


public class FindRedundantNullChecks implements Flow.Analysis{

	private TransferFunction transferFunction = new TransferFunction();

	public static class VarSet implements Flow.DataflowObject {
		private Set<String> set;
		public static Set<String> universalSet;
		public VarSet() { set = new TreeSet<String>(); }

		public void setToTop() { set = new TreeSet<String>(universalSet); }
		public void setToBottom() { set = new TreeSet<String>(); }

		public boolean containsReg(String reg){
			return set.contains(reg);
		}
		
		public void meetWith(Flow.DataflowObject o) 
		{
			VarSet a = (VarSet)o;
			set.retainAll(a.set); //intersection
		}

		public void copy(Flow.DataflowObject o) 
		{
			VarSet a = (VarSet) o;
			set = new TreeSet<String>(a.set);
		}

		@Override
		public boolean equals(Object o) 
		{
			if (o instanceof VarSet) 
			{
				VarSet a = (VarSet) o;
				return set.equals(a.set);
			}
			return false;
		}
		@Override
		public int hashCode() {
			return set.hashCode();
		}
		@Override
		public String toString() 
		{
			return set.toString();
		}

		public void genVar(String v) {set.add(v);}
		public void killVar(String v) {set.remove(v);}
	}

	private VarSet[] in, out;
	private VarSet entry, exit;

	public void preprocess(ControlFlowGraph cfg) {
		// adapted from ReachingDefinitions
		// find the maximum quad ID
		QuadIterator quadIterator = new QuadIterator(cfg);
		int maxID = 0;
		int quadID;
		while(quadIterator.hasNext()){
			quadID = quadIterator.next().getID();
			maxID = (quadID > maxID ? quadID : maxID);
		}

		// create the definition set arrays
		maxID++;
		in = new VarSet[maxID];
		out = new VarSet[maxID];
		// initialize their values
		for(int i=0; i<in.length; i++){
			in[i] = new VarSet();
			out[i] = new VarSet();
		}
		// create the entry and exit nodes
		entry = new VarSet();
		exit = new VarSet();
		// create the transfer function value
		transferFunction.val = (VarSet)newTempVar();
		// create the universal set out of all the registers mentioned by a quad
		
		//TODO args?
		quadIterator = new QuadIterator(cfg);
		
		VarSet.universalSet = new TreeSet<String>();
		while(quadIterator.hasNext()){
			Quad quad = quadIterator.next();
			
			for (RegisterOperand def : quad.getDefinedRegisters()){
				VarSet.universalSet.add(def.getRegister().toString());
			}
			
			for (RegisterOperand use : quad.getUsedRegisters()){
				VarSet.universalSet.add(use.getRegister().toString());
			}
		}
	}

	public void postprocess(ControlFlowGraph cfg) {
		QuadIterator qit = new QuadIterator(cfg);
		
		while(qit.hasNext()){
			Quad quad = qit.next();
			
			// if a null check and in already contains it (ie already checked on all paths)
			if(quad.getOperator() instanceof joeq.Compiler.Quad.Operator.NullCheck.NULL_CHECK){
				String checkedRegister = ((RegisterOperand)NullCheck.getSrc(quad)).getRegister().toString();
				if(in[quad.getID()].containsReg(checkedRegister)){
					System.out.print(quad.getID() + " "); //TODO order these in treeset
				}
			}
		}
	}

	/* Is this a forward dataflow analysis? */
	public boolean isForward() { 
		return true; 
	}

	/* Routines for interacting with dataflow values. */

	public Flow.DataflowObject getEntry() 
	{ 
		Flow.DataflowObject result = newTempVar();
		result.copy(entry); 
		return result;
	}
	public Flow.DataflowObject getExit() 
	{ 
		Flow.DataflowObject result = newTempVar();
		result.copy(exit); 
		return result;
	}
	public Flow.DataflowObject getIn(Quad q) 
	{
		Flow.DataflowObject result = newTempVar();
		result.copy(in[q.getID()]); 
		return result;
	}
	public Flow.DataflowObject getOut(Quad q) 
	{
		Flow.DataflowObject result = newTempVar();
		result.copy(out[q.getID()]); 
		return result;
	}
	public void setIn(Quad q, Flow.DataflowObject value) 
	{ 
		in[q.getID()].copy(value); 
	}
	public void setOut(Quad q, Flow.DataflowObject value) 
	{ 
		out[q.getID()].copy(value); 
	}
	public void setEntry(Flow.DataflowObject value) 
	{ 
		entry.copy(value); 
	}
	public void setExit(Flow.DataflowObject value) 
	{ 
		exit.copy(value); 
	}

	public Flow.DataflowObject newTempVar() { return new VarSet(); }

	/* Actually perform the transfer operation on the relevant
	 * quad. */

	public void processQuad(Quad q) {
		transferFunction.val.copy(in[q.getID()]);
		transferFunction.visitQuad(q);
		out[q.getID()].copy(transferFunction.val);
	}

	/* The QuadVisitor that actually does the computation */
	public static class TransferFunction extends QuadVisitor.EmptyVisitor {
		VarSet val;
		@Override
		public void visitQuad(Quad q){
			
			for (Operand.RegisterOperand def : q.getDefinedRegisters()){
				val.killVar(def.getRegister().toString());
			}
			
			// if null check - add (these two actions should be disjoint anyway, null checks don't assign)
			if(q.getOperator() instanceof joeq.Compiler.Quad.Operator.NullCheck.NULL_CHECK){
				RegisterOperand op = (RegisterOperand)NullCheck.getSrc(q);
				val.genVar(op.getRegister().toString());
			}
		}
	}


	/*
	 * args is an array of class names 
	 * method should print out a list of quad ids of redundant null checks
	 * for each function as described on the course webpage     
	 */
	public static void main(String[] args) {	        
		ReferenceSolver solver = new ReferenceSolver();
		FindRedundantNullChecks nullCheckAnalyzer = new FindRedundantNullChecks();
		solver.registerAnalysis(nullCheckAnalyzer);

		jq_Class[] classes = new jq_Class[args.length];
		for (int i=0; i < classes.length; i++)
			classes[i] = (jq_Class)Helper.load(args[i]);

		for (int i=0; i < classes.length; i++) { //TODO get this order right
			jq_InstanceMethod[] imethods = classes[i].getDeclaredInstanceMethods();
			jq_StaticMethod[] smethods = classes[i].getDeclaredStaticMethods();
			for (int j=0; j < imethods.length ; j++ ){
				System.out.print(imethods[j].getName() + " ");
				Helper.runPass(imethods[j], solver);
				System.out.println();
			}
			for (int j=0; j < smethods.length ; j++ ){
				System.out.print(smethods[j].getName() + " ");
				Helper.runPass(smethods[j], solver);
				System.out.println();
			}

		}
	}
}
