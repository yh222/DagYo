package test;

import static org.junit.Assert.assertEquals;
import graph.core.DAGNode;
import graph.core.DirectedAcyclicGraph;
import graph.core.StringNode;
import graph.module.NodeAliasModule;

import java.io.File;
import java.util.Collection;

import javax.naming.NamingException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NodeAliasModuleTest {
	private NodeAliasModule sut_;
	private DirectedAcyclicGraph dag_;

	/**
	 * 
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		dag_ = new DirectedAcyclicGraph(new File("test"));
		sut_ = (NodeAliasModule) dag_.getModule(NodeAliasModule.class);
		sut_.clear();
	}

	@After
	public void tearDown() {
		sut_.clear();
	}

	/**
	 * Test method for
	 * {@link graph.core.DirectedAcyclicGraph#findNodeByName(java.lang.String, boolean)}
	 * .
	 * 
	 * @throws NamingException
	 */
	@Test
	public void testFindNodeByName() {
		dag_.findOrCreateNode("Test", new StringNode("TestCreator"), true, true, true);
		Collection<DAGNode> result = sut_.findNodeByName("Test", true);
		assertEquals(result.size(), 1);
		result = sut_.findNodeByName("test", true);
		assertEquals(result.size(), 0);
		result = sut_.findNodeByName("test", false);
		assertEquals(result.size(), 1);

		dag_.findOrCreateNode("Test", new StringNode("TestCreator"), true, true, true);
		result = sut_.findNodeByName("Test", true);
		assertEquals(result.size(), 1);
		dag_.findOrCreateNode("Pants", new StringNode("TestCreator"), true, true, true);
		result = sut_.findNodeByName("Pants", true);
		assertEquals(result.size(), 1);
		dag_.findOrCreateNode("Pants", new StringNode("TestCreator"), true, true, true);
		result = sut_.findNodeByName("Pants", true);
		assertEquals(result.size(), 1);
	}

	// TODO @Test
	// public void testRemoveEdge() {
	// DAGEdge aliasEdge = new AliasEdge(new DAGNode("dog"), new StringNode(
	// "Dog"), new StringNode("Canine"));
	// dag_.findOrCreateEdge(new StringNode("TestCreator"), alias, dogStr,
	// canine);
	// dag_.addEdge(aliasEdge);
	// assertEquals(sut_.findNodeByAlias("Dog", true, true).size(), 1);
	// dag_.removeEdge(aliasEdge);
	// assertEquals(sut_.findNodeByAlias("Dog", true, true).size(), 0);
	// }

	/**
	 * Test method for
	 * {@link graph.core.DirectedAcyclicGraph#findNodeByAlias(java.lang.String, boolean, boolean)}
	 * .
	 * 
	 * @throws NamingException
	 */
	@Test
	public void testFindNodeByAlias() {
		dag_.findOrCreateNode("Test", new StringNode("TestCreator"), true, true, true);
		Collection<DAGNode> result = sut_.findNodeByAlias("Test", true, true);
		assertEquals(result.size(), 1);
		result = sut_.findNodeByAlias("Tes", true, false);
		assertEquals(result.size(), 1);
		result = sut_.findNodeByAlias("tes", true, false);
		assertEquals(result.size(), 0);
		result = sut_.findNodeByAlias("tes", false, false);
		assertEquals(result.size(), 1);
		result = sut_.findNodeByAlias("te", false, false);
		assertEquals(result.size(), 0);
		
		dag_.findOrCreateNode("FruitFn", null, true, true, true);
		dag_.findOrCreateNode("Fruit", null, true, true, true);
		result = sut_.findNodeByAlias("Fruit", true, false);
		assertEquals(result.size(), 2);
		result = sut_.findNodeByAlias("Fruit", true, true);
		assertEquals(result.size(), 1);
	}

}
