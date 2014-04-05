package walaTest;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import java.util.*;

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.loader.JavaScriptLoader;
import com.ibm.wala.cast.js.ssa.JavaScriptInvoke;
import com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil.CGBuilderType;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.types.JavaScriptMethods;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.cast.js.rhino.test.HTMLCGBuilder;
import com.ibm.wala.cast.js.rhino.test.HTMLCGBuilder.CGBuilderResult;
 
public class javascriptCallGraph {
	String[] slice_seed = {
			//	"userAgent",
				"innerHTML"
		};
    public static void main(String[] args) throws IllegalArgumentException, IOException, CancelException, WalaException {
    	javascriptCallGraph jcg = new javascriptCallGraph();
    	jcg.getCallGraph();
    }
    public  void getCallGraph() throws IllegalArgumentException, IOException, CancelException, WalaException{
    	final String dir = "./dat";
		final String fileName = "test";
		
		
		JavaScriptLoader.addBootstrapFile("prologue.js");
	    JavaScriptLoader.addBootstrapFile("preamble.js");

		JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
		CGBuilderResult builder = HTMLCGBuilder.buildHTMLCG("dat/test.html", 0, CGBuilderType.ONE_CFA);
		File  f = new File("./dat/test.html");
		URL url = f.toURI().toURL();
		
		CallGraph CG = com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil.makeHTMLCG(url);
		//printNode(CG);
		//printIR(CG);
		CGNode htmlNode = findNode(CG,"./dat/test");
		ArrayList<Statement> seeds = findStmt(htmlNode);
		System.out.println("seeds arraylist size is :"+seeds.size());
		for (Statement s : seeds) {

			System.out.println("SEEDS: ");
			printStatement(s);
			System.out.println("BACKWARD: ");
			slice_using_seed(s, CG, builder.pa, true);
			System.out.println("FORWARD: ");
			slice_using_seed(s, CG, builder.pa, false);
		}
    }
    private void printStatement(Statement s) 
	{
		//System.out.println(s.toString());
		
		if(s.getKind() == Statement.Kind.NORMAL)
		{
			int insindex = ((NormalStatement) s).getInstructionIndex();
			int byindex = s.getNode().getMethod().getLineNumber(insindex);
		//	AstMethod am = (AstMethod) s.getNode().getMethod();
		//	CAstSourcePositionMap.Position cpm = am.getSourcePosition(insindex);
		
			
			System.out.println("line number "+byindex +" ir index "+insindex);
			//System.out.println(s.getNode().getIR());
			//System.out.println(s.toString());
		}
		else
		{
			//System.out.println(s.toString());
		}
		
		
	}
    private void slice_using_seed(Statement seed, CallGraph cg, PointerAnalysis pa, boolean backward) throws IllegalArgumentException, CancelException
	{
		Collection<Statement> slice ;
		if(backward)
			slice = Slicer.computeBackwardSlice(seed, cg, pa,DataDependenceOptions.NO_HEAP, ControlDependenceOptions.FULL);
		else
			slice = Slicer.computeForwardSlice(seed, cg, pa, DataDependenceOptions.NO_HEAP, ControlDependenceOptions.FULL);
		
		dumpSlice(slice);
	}
    private void dumpSlice(Collection<Statement> slice) {
		int i = 1;
		System.out.println("SLICE\n");
		for (Statement s : slice) {
			printStatement(s);
			
		}
	}
 // find interesting seed 
 	public ArrayList<Statement> findStmt(CGNode n)
 	{
 		ArrayList<Statement> s = new ArrayList<Statement>();
 		IR ir = n.getIR();
 		System.out.println("ir function is "+ir.getMethod().toString());
 		//System.out.println(ir.toString());
 	
 		String[] splited_ir = ir.toString().split("\n");
 		
 		for( String key:slice_seed)
 		{
 			String temp_ir = getIR(splited_ir, key);
 			
 			if(temp_ir!=null)
 			{
 				int pc = Integer.parseInt(temp_ir.split(" ")[0]);
 				s.add(new NormalStatement(n, pc));
 			}
 		}
 		//return new NormalStatement(n,19);
 		return s;
 	}
 	
 	public String getIR(String[] ir, String key)
	{
		for (String s: ir)
		{
			if(s.contains(key))
				return s;
		}
		return null;
		
	}
    private static CGNode getFunctionNode(CallGraph CG, String dir, String file) {
    	  TypeName type = TypeName.findOrCreate("L" + dir + "/" + file);
    	  if (CG != null) {
    	    Iterator<CGNode> iter = CG.iterator();
    	    CGNode node;
    	    while (iter.hasNext()) {
    	      node = iter.next();
    	      TypeName tempType = node.getMethod().getDeclaringClass().getName();
    	      if (tempType.equals(type)) {
    	    	//System.out.println(node);
    	        return node;
    	      }
    	    }
    	  }
    	  System.out.println("can not find");
    	  System.err.println("Can't find :" + dir + "/" + file);
    	  return null;
    	}
    private static void printNode(CallGraph CG) {
    	for (CGNode node: CG){
    		System.out.println(node.toString());
    		
    	}
    }
    private static void printIR(CallGraph CG) {
    	for (CGNode node: CG) {
            // Get the IR of a CGNode
            IR ir = node.getIR();

            // Get CFG from IR
            SSACFG cfg = ir.getControlFlowGraph();

            // Iterate over the Basic Blocks of CFG
            Iterator<ISSABasicBlock> cfgIt = cfg.iterator();
            while (cfgIt.hasNext()) {
              ISSABasicBlock ssaBb = cfgIt.next();

              // Iterate over SSA Instructions for a Basic Block
              Iterator<SSAInstruction> ssaIt = ssaBb.iterator();
              while (ssaIt.hasNext()) {
                SSAInstruction ssaInstr = ssaIt.next();
                //Print out the instruction
                System.out.println(ssaInstr);
              }
            }
    }
    }
    
    private static CGNode findNode(CallGraph cg, String fileName) {
        return findNode(cg, fileName, null);
}      

    private static CGNode findNode(CallGraph cg, String fileName, String moduleName) {
    	Atom funAtom = Atom.findOrCreateUnicodeAtom("do");
		String decClassName = (moduleName == null) ? "L" + fileName + ".html" : "L" + fileName + ".html/" + moduleName;
		
		System.out.println(cg.getNumberOfNodes());
		for (Iterator<? extends CGNode> it = cg.iterator(); it.hasNext();) {
			CGNode n = it.next();
			//System.out.println(n.toString());
			//System.out.println(n.getIR());
			
			//if(n.toString().contains("handleDocumentChange") & n.toString().contains("Code body"))
			if(n.toString().contains("Code body") & n.toString().contains("getLocation")){
				if(n.toString().contains("<ctor")){
					
				}
				else {
					System.out.println(n.toString());
					return n;
				}
			}
			if (n.getMethod().getName().equals(funAtom)) {
				TypeReference decCls = n.getMethod().getReference().getDeclaringClass();
			//	System.out.println(decCls.getName().toString());
				if (decCls.getName().toString().equals(decClassName)) {
					//return n;
				}
			}
		}
		System.err.println("call graph " + cg);
		Assertions.UNREACHABLE("failed to find method " + decClassName);
		return null;
	}
   
    
	
    public static Statement findDispatchStmt(CGNode n, int firstNum, int secondNum, int... restNums) {
        IR ir = n.getIR();
        for (Iterator<SSAInstruction> it = ir.iterateAllInstructions(); it.hasNext();) {
                SSAInstruction s = it.next();
                if (s instanceof JavaScriptInvoke) {
                        //System.out.println( "s: [" + s + "] is instance of jsinvoke");
                        JavaScriptInvoke invoke = (JavaScriptInvoke) s;
                        if (invoke.getCallSite().getDeclaredTarget().equals(JavaScriptMethods.dispatchReference)
                                        && invoke.getUse(0) == firstNum && invoke.getUse(1) == secondNum) {
                                boolean parameterMatch = true;
                                int i = 2;
                                for (int num : restNums) {
                                        if (invoke.getUse(i++) != num) {
                                                parameterMatch = false;
                                                break;
                                        }
                                }

                                if (parameterMatch) {
                                        IntSet indices = ir.getCallInstructionIndices(invoke.getCallSite());
                                        Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                                        return new NormalStatement(n, indices.intIterator().next());
                                }
                        }
                }
        }
        assert false;
        return null;
}
}



