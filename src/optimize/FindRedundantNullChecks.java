package optimize;

import java.util.Set;
import java.util.TreeSet;


import joeq.Class.jq_Class;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.QuadIterator;
import joeq.Compiler.Quad.QuadVisitor;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Main.Helper;
import joeq.Class.jq_InstanceMethod;
import joeq.Class.jq_StaticMethod;


public class FindRedundantNullChecks implements Flow.Analysis{
  
	   public static class VarSet implements Flow.DataflowObject {
	        private Set<String> set;
	        public static Set<String> universalSet;
	        public VarSet() { set = new TreeSet<String>(); }

	        public void setToTop() { set = new TreeSet<String>(); }
	        public void setToBottom() { set = new TreeSet<String>(universalSet); }

	        public void meetWith(Flow.DataflowObject o) 
	        {
	            VarSet a = (VarSet)o;
	            set.addAll(a.set);
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
	    }

	    public void postprocess(ControlFlowGraph cfg) {
	    	
	    }

	    /* Is this a forward dataflow analysis? */
	    public boolean isForward() { 
	    	System.out.println("inside forward I am");
	    	return false; 
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

	    private TransferFunction transferfn = new TransferFunction ();
	    public void processQuad(Quad q) {
	        transferfn.val.copy(out[q.getID()]);
	        transferfn.visitQuad(q);
	        in[q.getID()].copy(transferfn.val);
	    }

	    /* The QuadVisitor that actually does the computation */
	    public static class TransferFunction extends QuadVisitor.EmptyVisitor {
	        VarSet val;
	        @Override
	        public void visitQuad(Quad q) {
	        
	        }
	    }

	
    /*
     * args is an array of class names 
     * method should print out a list of quad ids of redundant null checks
     * for each function as described on the course webpage     
     */
    public static void main(String[] args) {	        
        System.out.println("I am here");
        
        ReferenceSolver solver = new ReferenceSolver();
        FindRedundantNullChecks nullCheckAnalyzer = new FindRedundantNullChecks();
        solver.registerAnalysis(nullCheckAnalyzer);
        
        jq_Class[] classes = new jq_Class[args.length];
        for (int i=0; i < classes.length; i++)
            classes[i] = (jq_Class)Helper.load(args[i]);

        for (int i=0; i < classes.length; i++) {
            System.out.println("Now analyzing "+classes[i].getName());
            jq_InstanceMethod[] imethods = classes[i].getDeclaredInstanceMethods();
            jq_StaticMethod[] smethods = classes[i].getDeclaredStaticMethods();
            for (int j=0; j < imethods.length ; j++ ){
                Helper.runPass(imethods[j], solver);
            }
            for (int j=0; j < smethods.length ; j++ ){
                Helper.runPass(smethods[j], solver);
            }

        }
        System.out.println("I am there");
    }
}
