/******************************************************************************
 * Copyright (c) 2002 - 2012 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.js.callgraph.fieldbased;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.cast.ipa.callgraph.AstContextInsensitiveSSAContextInterpreter;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraph;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.AbstractVertexVisitor;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.CallVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.FuncVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.VarVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.Vertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.VertexFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSAnalysisOptions;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraph;
import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptConstructTargetSelector;
import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptEntryPoints;
import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptFunctionDotCallTargetSelector;
import com.ibm.wala.cast.js.types.JavaScriptTypes;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.ContextInsensitiveSelector;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DefaultSSAInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.DelegatingSSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nCFAContextSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.collections.Util;

/**
 * Abstract call graph builder class for building a call graph from a field-based flow graph.
 * The algorithm for building the flow graph is left unspecified, and is implemented differently
 * by subclasses.
 * 
 * @author mschaefer
 *
 */
public abstract class FieldBasedCallGraphBuilder {
	// class hierarchy of the program to be analysed
	protected final IClassHierarchy cha;
	
	// standard call graph machinery
	protected final AnalysisOptions options;
	protected final AnalysisCache cache;
	protected final MethodTargetSelector targetSelector;
	
	private static final boolean LOG_TIMINGS = true;
	
	public FieldBasedCallGraphBuilder(IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache) {
		this.cha = cha;
		this.options = options;
		this.cache = cache;
		this.targetSelector = setupMethodTargetSelector(cha, options);
	}

  private MethodTargetSelector setupMethodTargetSelector(IClassHierarchy cha, AnalysisOptions options) {
    MethodTargetSelector result = new JavaScriptConstructTargetSelector(cha, options.getMethodTargetSelector());
    if (options instanceof JSAnalysisOptions && ((JSAnalysisOptions)options).handleCallApply()) {
      // TODO handle Function.prototype.apply
      result = new JavaScriptFunctionDotCallTargetSelector(result);
    }
    return result;
  }
	
	/**
	 * Build a flow graph for the program to be analysed.
	 */
	public abstract FlowGraph buildFlowGraph(IProgressMonitor monitor) throws CancelException;
	
	/**
	 * Main entry point: builds a flow graph, then extracts a call graph and returns it.
	 */
	public JSCallGraph buildCallGraph(IProgressMonitor monitor) throws CancelException {
		long fgBegin, fgEnd, cgBegin, cgEnd;
	
		if(LOG_TIMINGS) fgBegin = System.currentTimeMillis();
		
		FlowGraph flowGraph = buildFlowGraph(monitor);
		
		if(LOG_TIMINGS) {
			fgEnd = System.currentTimeMillis();
			System.out.println("flow graph construction took " + (fgEnd-fgBegin)/1000.0 + " seconds");
			cgBegin = System.currentTimeMillis();
		}
		
		JSCallGraph cg = extract(flowGraph, monitor);
		
		if(LOG_TIMINGS) {
			cgEnd = System.currentTimeMillis();
			System.out.println("call graph extraction took " + (cgEnd-cgBegin)/1000.0 + " seconds");
		}
		
		return cg;
	}

	/**
	 * Extract a call graph from a given flow graph.
	 */
	@SuppressWarnings("deprecation")
	protected JSCallGraph extract(FlowGraph flowgraph, IProgressMonitor monitor) throws CancelException {
		// set up call graph
		final JSCallGraph cg = new JSCallGraph(cha, options, cache);
		cg.init();
		cg.setInterpreter(new DelegatingSSAContextInterpreter(new AstContextInsensitiveSSAContextInterpreter(options, cache), new DefaultSSAInterpreter(options, cache)));
	    
		
	    // set up call edges from fake root to all script nodes
		JavaScriptEntryPoints eps = new JavaScriptEntryPoints(cha, cha.getLoader(JavaScriptTypes.jsLoader));
		AbstractRootMethod fakeRootMethod = (AbstractRootMethod)cg.getFakeRootNode().getMethod();
		CGNode fakeRootNode = cg.findOrCreateNode(fakeRootMethod, Everywhere.EVERYWHERE);
		for(Iterator<Entrypoint> iter = eps.iterator(); iter.hasNext();) {
			Entrypoint ep = iter.next();
			CGNode nd = cg.findOrCreateNode(ep.getMethod(), Everywhere.EVERYWHERE);
			SSAAbstractInvokeInstruction invk = ep.addCall(fakeRootMethod);
			fakeRootNode.addTarget(invk.getCallSite(), nd);
		}
		// register the fake root as the "true" entrypoint
		cg.registerEntrypoint(fakeRootNode);
		
		// now add genuine call edges
		Set<Pair<CallVertex, FuncVertex>> edges = extractCallGraphEdges(flowgraph, monitor);


    for (Pair<CallVertex, FuncVertex> edge : edges) {
      CallVertex callVertex = edge.fst;
      FuncVertex targetVertex = edge.snd;
      IClass kaller = callVertex.getCaller().getIClass();
      CGNode caller = cg.findOrCreateNode(kaller.getMethod(AstMethodReference.fnSelector), Everywhere.EVERYWHERE);
      CallSiteReference site = callVertex.getSite();
      IMethod target = targetSelector.getCalleeTarget(caller, site, targetVertex.getIClass());
      boolean isFunctionPrototypeCall = target != null
          && target.getName().toString().startsWith(JavaScriptFunctionDotCallTargetSelector.SYNTHETIC_CALL_METHOD_PREFIX);
      if (isFunctionPrototypeCall) {
        handleFunctionPrototypeCallInvocation(flowgraph, monitor, cg, callVertex, caller, site, target);
      } else {
        addEdgeToJSCallGraph(cg, site, target, caller);
      }
    }
		
		return cg;
	}

  private void handleFunctionPrototypeCallInvocation(FlowGraph flowgraph, IProgressMonitor monitor, final JSCallGraph cg,
      CallVertex callVertex, CGNode caller, CallSiteReference site,
      IMethod target) throws CancelException {
    // use to get 1-level of call string for Function.prototype.call, to
    // preserve the precision of the field-based call graph
    final nCFAContextSelector functionPrototypeCallSelector = new nCFAContextSelector(1, new ContextInsensitiveSelector());
    Context calleeContext = functionPrototypeCallSelector.getCalleeTarget(caller, site, target, null);
    addCGEdgeWithContext(cg, site, target, caller, calleeContext);
    CGNode functionPrototypeCallNode = cg.findOrCreateNode(target, calleeContext);
    // need to create nodes for reflective targets of call, and then add them
    // as callees of the synthetic method
    Collection<FuncVertex> reflectiveTargets = getReflectiveTargets(flowgraph, callVertex, monitor);
    // there should only be one call site in the synthetic method
    CallSiteReference reflectiveCallSite = functionPrototypeCallNode.getIR().iterateCallSites().next();
    for (FuncVertex f : reflectiveTargets) {
      IMethod reflectiveTgtMethod = targetSelector.getCalleeTarget(functionPrototypeCallNode, reflectiveCallSite, f.getIClass());
      addEdgeToJSCallGraph(cg, reflectiveCallSite, reflectiveTgtMethod, functionPrototypeCallNode);
    }
  }

  private IMethod addEdgeToJSCallGraph(final JSCallGraph cg, CallSiteReference site, IMethod target, CGNode caller)
      throws CancelException {
    return addCGEdgeWithContext(cg, site, target, caller, Everywhere.EVERYWHERE);
  }

  Everywhere targetContext = Everywhere.EVERYWHERE;
  @SuppressWarnings("deprecation")
  private IMethod addCGEdgeWithContext(final JSCallGraph cg, CallSiteReference site, IMethod target, CGNode caller,
      Context targetContext) throws CancelException {
    if(target != null) {
      CGNode callee = cg.findOrCreateNode(target, targetContext);
    	// add nodes first, to be on the safe side
    	cg.addNode(caller);	cg.addNode(callee);
    	// add callee as successor of caller
    	cg.addEdge(caller, callee);
    	// add as site-specific target
    	caller.addTarget(site, callee);
    }
    return target;
  }

  /**
   * Given a callVertex that can invoke Function.prototype.call, get the
   * FuncVertex nodes for the reflectively-invoked methods
   * @throws CancelException 
   */
	private Collection<FuncVertex> getReflectiveTargets(FlowGraph flowGraph, CallVertex callVertex, IProgressMonitor monitor) throws CancelException {
	  SSAAbstractInvokeInstruction invoke = callVertex.getInstruction();
	  VarVertex functionParam = flowGraph.getVertexFactory().makeVarVertex(callVertex.getCaller(), invoke.getUse(1));
	  return Util.filterByType(flowGraph.getReachingSet(functionParam, monitor), FuncVertex.class);
  }

  /**
	 * Extract call edges from the flow graph into high-level representation.
	 */
	protected Set<Pair<CallVertex, FuncVertex>> extractCallGraphEdges(FlowGraph flowgraph, IProgressMonitor monitor) throws CancelException {
		VertexFactory factory = flowgraph.getVertexFactory();
		final Set<Pair<CallVertex, FuncVertex>> result = HashSetFactory.make();
		
		// find all pairs <call, func> such that call is reachable from func in the flow graph
		for(final CallVertex callVertex : factory.getCallVertices()) {
			for(Vertex v : flowgraph.getReachingSet(callVertex, monitor)) {
				v.accept(new AbstractVertexVisitor<Void>() {
					@Override
					public Void visitFuncVertex(FuncVertex funcVertex) {
						result.add(Pair.make(callVertex, funcVertex));
						return null;
					}
				});
			}
		}
		
		return result;
	}
}
