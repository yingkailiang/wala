package walaTest;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import java.util.*;

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
 
public class javascriptCallGraph {

    public static void main(String[] args) throws IllegalArgumentException, IOException, CancelException, WalaException {
    	getCallGraph();
    }
    public static void getCallGraph() throws IllegalArgumentException, IOException, CancelException, WalaException{
    	// use Rhino for parsing; change if you want to use a different parser
    	com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
        CallGraph CG = com.ibm.wala.cast.js.test.JSCallGraphBuilderUtil.makeScriptCG("/Users/kailiangying/Develop/Eclipse/WALA/test", "editor.js"); 
        getFunctionNode(CG,"/Users/kailiangying/Develop/Eclipse/WALA/test","editor.js");
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
}



