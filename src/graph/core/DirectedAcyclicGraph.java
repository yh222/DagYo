package graph.core;

import graph.module.DAGModule;
import graph.module.NodeAliasModule;
import graph.module.RelatedEdgeModule;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.activity.InvalidActivityException;

import util.FSTDAGObjectSerialiser;
import util.UtilityMethods;
import util.collection.HashIndexedCollection;
import util.collection.IndexedCollection;
import util.serialisation.FSTSerialisationMechanism;
import util.serialisation.SerialisationMechanism;

/**
 * The class representing the access point of the directed acyclic graph.
 * 
 * @author Sam Sarjant
 */
public class DirectedAcyclicGraph {
	private static final String EDGE_FILE = "edges.dat";

	private static final String EDGE_ID_FIELD = "edgeID";

	private static final String NODE_FILE = "nodes.dat";

	private static final String NODE_ID_FIELD = "nodeID";

	private static final String NUM_EDGES_FIELD = "numEdges";

	private static final String NUM_NODES_FIELD = "numNodes";

	public static final int DEFAULT_NUM_EDGES = 1000000;

	public static final int DEFAULT_NUM_NODES = 100000;

	public static final File DEFAULT_ROOT = new File("dag");

	public static final String GLOBALS_FILE = "dagDetails";

	public static final File MODULE_FILE = new File("activeModules.config");

	public static DirectedAcyclicGraph selfRef_;

	private Map<String, DAGModule<?>> modules_;

	private File rootDir_;

	protected final Lock edgeLock_;

	protected IndexedCollection<DAGEdge> edges_;

	protected final Lock nodeLock_;

	protected IndexedCollection<DAGNode> nodes_;

	protected final Random random_;

	public boolean noChecks_ = false;

	public final long startTime_;

	public DirectedAcyclicGraph() {
		this(DEFAULT_ROOT, DEFAULT_NUM_NODES, DEFAULT_NUM_EDGES);
	}

	public DirectedAcyclicGraph(File rootDir) {
		this(rootDir, DEFAULT_NUM_NODES, DEFAULT_NUM_EDGES);
	}

	@SuppressWarnings("unchecked")
	public DirectedAcyclicGraph(File rootDir, int initialNodeSize,
			int initialEdgeSize) {
		startTime_ = System.currentTimeMillis();
		System.out.print("Initialising... ");

		FSTSerialisationMechanism.conf.registerSerializer(DAGObject.class,
				new FSTDAGObjectSerialiser(), true);
		selfRef_ = this;

		random_ = new Random();
		nodes_ = (IndexedCollection<DAGNode>) readDAGFile(initialNodeSize,
				rootDir, NODE_FILE);
		nodeLock_ = new ReentrantLock();
		edges_ = (IndexedCollection<DAGEdge>) readDAGFile(initialEdgeSize,
				rootDir, EDGE_FILE);
		edgeLock_ = new ReentrantLock();

		// Load the modules in
		modules_ = new HashMap<>();
		rootDir_ = rootDir;
		readModules(rootDir);

		System.out.println("Done!");
	}

	private void readDAGDetails(File rootDir) {
		File details = new File(rootDir, GLOBALS_FILE);
		try {
			if (details.exists()) {
				BufferedReader in = new BufferedReader(new FileReader(details));
				String input = null;
				while ((input = in.readLine()) != null) {
					if (!input.startsWith("%")) {
						String[] split = input.split("=");
						if (split[0].equals(NUM_NODES_FIELD))
							nodes_.setSize(Integer.parseInt(split[1]));
						else if (split[0].equals(NODE_ID_FIELD))
							DAGNode.idCounter_ = Integer.parseInt(split[1]);
						else if (split[0].equals(NUM_EDGES_FIELD))
							edges_.setSize(Integer.parseInt(split[1]));
						else if (split[0].equals(EDGE_ID_FIELD))
							DAGEdge.idCounter_ = Integer.parseInt(split[1]);
					}
				}
				in.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private IndexedCollection<? extends DAGObject> readDAGFile(int initialSize,
			File rootDir, String collectionFile) {
		IndexedCollection<DAGObject> indexedCollection = null;
		File serFile = new File(rootDir, collectionFile);
		if (serFile.exists()) {
			// Read it in
			try {
				System.out.println("Loading " + collectionFile + "...");
				indexedCollection = (IndexedCollection<DAGObject>) SerialisationMechanism.FST
						.getSerialiser().deserialize(serFile);
			} catch (InvalidActivityException e) {
				System.err.println("Exception while deserialising '"
						+ serFile.getPath() + "'. Creating new collection.");
			}
		}

		// Otherwise, create new collection
		if (indexedCollection == null)
			indexedCollection = new HashIndexedCollection<DAGObject>(
					initialSize);
		return indexedCollection;
	}

	private void readModules(File rootDir) {
		try {
			if (!MODULE_FILE.exists()) {
				MODULE_FILE.createNewFile();
				BufferedWriter out = new BufferedWriter(new FileWriter(
						MODULE_FILE));
				out.write("% Put the utilised modules here. One per line, in the form <classpath>\n");
				out.write("% E.g.:\n");
				out.write("% graph.module.RelatedEdgeModule");
				out.close();
				return;
			}

			BufferedReader reader = new BufferedReader(new FileReader(
					MODULE_FILE));
			String input = null;
			Collection<String> modules = new ArrayList<>();
			while ((input = reader.readLine()) != null) {
				if (input.startsWith("%"))
					continue;
				modules.add(input);
			}

			for (String module : modules) {
				System.out.println("Loading " + module + " module...");
				DAGModule<?> dagModule = DAGModule.loadCreateModule(rootDir_,
						Class.forName(module));
				addModule(dagModule);
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void saveDAGFile(IndexedCollection<? extends DAGObject> collection,
			File rootDir, String collectionFile) {
		File serFile = new File(rootDir, collectionFile);
		try {
			serFile.getParentFile().mkdirs();
			serFile.createNewFile();
			SerialisationMechanism.FST.getSerialiser().serialize(collection,
					serFile, false);
		} catch (IOException e) {
			System.err.println("Error serialising '" + serFile + "'.");
		}
	}

	private void writeDAGDetails(BufferedWriter out) throws IOException {
		out.write("% Do not change the contents of this file "
				+ "unless you know what you're doing!\n");
		out.write(NUM_NODES_FIELD + "=" + nodes_.size() + "\n");
		out.write(NODE_ID_FIELD + "=" + DAGNode.idCounter_ + "\n");
		out.write(NUM_EDGES_FIELD + "=" + edges_.size() + "\n");
		out.write(EDGE_ID_FIELD + "=" + DAGEdge.idCounter_ + "\n");
	}

	protected synchronized void addModule(DAGModule<?> module) {
		module.setDAG(this);
		modules_.put(module.getClass().getCanonicalName(), module);
	}

	protected void initialiseInternal() {
		// Read in the global index file
		readDAGDetails(rootDir_);
	}

	protected String preParseNode(String nodeStr, Node creator,
			boolean createNew, boolean dagNodeOnly) {
		return nodeStr;
	}

	public synchronized void addProperty(DAGObject dagObj, String key,
			String value) {
		dagObj.put(key, value);
		if (dagObj instanceof DAGNode)
			nodes_.update((DAGNode) dagObj);
		else if (dagObj instanceof DAGEdge)
			edges_.update((DAGEdge) dagObj);

		for (DAGModule<?> module : modules_.values())
			module.addProperty(dagObj, key, value);
	}

	public void clear() {
		nodes_.clear();
		edges_.clear();

		// Trigger modules
		for (DAGModule<?> module : modules_.values())
			module.clear();
	}

	public DAGNode findDAGNode(String nodeName) {
		NodeAliasModule nodeAlias = (NodeAliasModule) getModule(NodeAliasModule.class);
		Collection<DAGNode> nodes = nodeAlias.findNodeByName(nodeName, true);
		if (nodes.isEmpty())
			return null;
		if (nodes.size() > 1)
			System.err.println("WARNING: More than one node found with name: "
					+ nodeName);
		return nodes.iterator().next();
	}

	/**
	 * Finds an existing edge with the given nodes.
	 * 
	 * @param edgeNodes
	 *            The nodes of the edge.
	 * @return The found edge or null if it does not exist.
	 */
	public Edge findEdge(Node... edgeNodes) {
		RelatedEdgeModule relMod = (RelatedEdgeModule) getModule(RelatedEdgeModule.class);
		Collection<Edge> edges = relMod.findEdgeByNodes(edgeNodes);
		if (edges.isEmpty())
			return null;
		if (edges.size() > 1)
			System.err.println("WARNING: More than one edge found with nodes: "
					+ Arrays.toString(edgeNodes));
		return edges.iterator().next();
	}

	/**
	 * Finds or creates an edge from a set of nodes. The returned edge either
	 * already exists, or is newly created and added.
	 * 
	 * @param creator
	 *            The creator of the edge.
	 * @param createNodes
	 *            If new nodes should be created.
	 * @param edgeNodes
	 *            The nodes of the edge.
	 * @return True if the edge was not already in the graph.
	 * @throws DAGException
	 */
	public synchronized Edge findOrCreateEdge(Node creator,
			boolean createNodes, Node... edgeNodes) {
		edgeLock_.lock();
		try {
			Edge edge = findEdge(edgeNodes);
			if (edge == null) {
				// Check all the nodes are in the DAG
				if (!noChecks_) {
					for (Node n : edgeNodes)
						if (n instanceof DAGNode
								&& findOrCreateNode(n.getIdentifier(), null,
										createNodes, false, false) == null)
							return DAGErrorEdge.NON_EXISTENT_NODE;
				}

				edge = new DAGEdge(creator, true, edgeNodes);
				boolean result = edges_.add((DAGEdge) edge);
				if (result) {
					// Trigger modules
					for (DAGModule<?> module : modules_.values())
						module.addEdge(edge);
				}
			}
			return edge;
		} finally {
			edgeLock_.unlock();
		}
	}

	/**
	 * Finds or creates a node by parsing the string and searching for a node.
	 * String and Primitive nodes can always be found/created. A node is only
	 * created if a creator is specified. If no node is found, a new node is
	 * created and added.
	 * 
	 * @param nodeStr
	 *            The node string to search with.
	 * @param creator
	 *            If creating new nodes, a creator must be given.
	 * @param createNew
	 *            If a new node can be created.
	 * @param dagNodeOnly
	 *            If only DAG nodes can be found/created.
	 * @param allowVariables
	 *            If variables nodes can be created.
	 * @return Either a found node, a created node, or null if impossible to
	 *         parse.
	 */
	public synchronized Node findOrCreateNode(String nodeStr, Node creator,
			boolean createNew, boolean dagNodeOnly, boolean allowVariables) {
		nodeStr = preParseNode(nodeStr, creator, createNew, dagNodeOnly);

		nodeLock_.lock();
		try {
			if (nodeStr == null) {
				return null;
			} else if (createNew && nodeStr.isEmpty()) {
				return new DAGNode(creator);
			} else if (!dagNodeOnly && nodeStr.startsWith("\"")) {
				return new StringNode(nodeStr);
			} else if (nodeStr.matches("\\d+")) {
				return getNodeByID(Long.parseLong(nodeStr));
			} else if (!dagNodeOnly && nodeStr.startsWith("'")) {
				return PrimitiveNode.parseNode(nodeStr.substring(1));
			}

			DAGNode node = findDAGNode(nodeStr);
			if (node == null && createNew && DAGNode.isValidName(nodeStr)) {
				// Create a new node
				node = new DAGNode(nodeStr, creator);
				boolean result = nodes_.add(node);
				if (result) {
					// Trigger modules
					for (DAGModule<?> module : modules_.values())
						module.addNode(node);
				} else
					return null;
			}
			return node;
		} finally {
			nodeLock_.unlock();
		}
	}

	/**
	 * Finds an edge by its ID.
	 * 
	 * @param id
	 *            The ID of the edge.
	 * @return The edge with the provided ID, or null if no edge exists.
	 */
	public DAGEdge getEdgeByID(long id) {
		return edges_.get(id);
	}

	public DAGModule<?> getModule(Class<? extends DAGModule<?>> moduleClass) {
		DAGModule<?> module = modules_.get(moduleClass.getCanonicalName());
		if (module == null) {
			for (DAGModule<?> mod : modules_.values()) {
				if (moduleClass.isAssignableFrom(mod.getClass())) {
					modules_.put(moduleClass.getCanonicalName(), mod);
					return mod;
				}
			}
		}
		return module;
	}

	public Map<String, DAGModule<?>> getModules() {
		return modules_;
	}

	/**
	 * Finds a node by its ID.
	 * 
	 * @param id
	 *            The ID of the node.
	 * @return The node with the provided ID, or null if no node exists.
	 */
	public DAGNode getNodeByID(long id) {
		return nodes_.get(id);
	}

	public int getNumEdges() {
		return edges_.size();
	}

	public int getNumNodes() {
		return nodes_.size();
	}

	public Edge getRandomEdge() {
		while (DAGEdge.idCounter_ > 0) {
			long maxID = DAGEdge.idCounter_ + 1;
			long id = (long) (random_.nextDouble() * maxID);
			Edge e = getEdgeByID(id);
			if (e != null)
				return e;
		}
		return null;
	}

	public Node getRandomNode() {
		while (DAGNode.idCounter_ > 0) {
			long maxID = DAGNode.idCounter_ + 1;
			long id = (long) (random_.nextDouble() * maxID);
			Node n = getNodeByID(id);
			if (n != null)
				return n;
		}
		return null;
	}

	public final void initialise() {
		initialiseInternal();
		for (DAGModule<?> module : modules_.values())
			module.initialisationComplete(nodes_, edges_);
	}

	public Node[] parseNodes(String strNodes, Node creator,
			boolean createNodes, boolean allowVariables) {
		if (strNodes.startsWith("("))
			strNodes = UtilityMethods.shrinkString(strNodes, 1);
		ArrayList<String> split = UtilityMethods.split(strNodes, ' ');

		Node[] nodes = new Node[split.size()];
		int i = 0;
		for (String arg : split) {
			if (!allowVariables && arg.startsWith("?"))
				return null;
			nodes[i] = findOrCreateNode(arg, creator, createNodes, false,
					allowVariables);

			if (nodes[i] == null)
				return null;
			i++;
		}
		return nodes;
	}

	/**
	 * Removes an edge from the DAG.
	 * 
	 * @param edge
	 *            The edge to be removed.
	 * @return True if the edge was removed.
	 */
	public synchronized boolean removeEdge(Edge edge) {
		if (edge == null)
			return false;

		// Remove the edge
		edgeLock_.lock();
		try {
			boolean result = edges_.remove(edge);

			if (result) {
				// Trigger modules
				for (DAGModule<?> module : modules_.values())
					module.removeEdge(edge);
			}
			return result;
		} finally {
			edgeLock_.unlock();
		}
	}

	/**
	 * Removes an edge from the DAG.
	 * 
	 * @param edgeID
	 *            The ID of the edge to be removed.
	 * @return True if the edge was removed.
	 */
	public synchronized boolean removeEdge(long edgeID) {
		return removeEdge(getEdgeByID(edgeID));
	}

	/**
	 * Removes a node from the DAG, and also removes all information associated
	 * with the node.
	 * 
	 * @param node
	 *            The node to be removed.
	 * @return True if the node was removed.
	 */
	@SuppressWarnings("unchecked")
	public synchronized boolean removeNode(DAGNode node) {
		if (node == null)
			return false;

		// Remove node
		nodeLock_.lock();
		try {
			boolean result = nodes_.remove(node);

			if (result) {
				// Remove edges associated with node.
				if (modules_.containsKey(RelatedEdgeModule.class
						.getCanonicalName())) {
					Collection<DAGEdge> relatedEdges = (Collection<DAGEdge>) modules_
							.get(RelatedEdgeModule.class.getCanonicalName())
							.execute(node);
					for (DAGEdge edge : relatedEdges)
						removeEdge(edge);
				} else {
					Collection<DAGEdge> removed = new ArrayList<>();
					for (DAGEdge edge : edges_) {
						if (edge.containsNode(node))
							removed.add(edge);
					}

					for (DAGEdge edge : removed)
						removeEdge(edge);
				}

				// Trigger modules
				for (DAGModule<?> module : modules_.values())
					module.removeNode(node);
			}
			return result;
		} finally {
			nodeLock_.unlock();
		}
	}

	/**
	 * Removes a node from the DAG, and also removes all information associated
	 * with the node.
	 * 
	 * @param nodeID
	 *            The ID of the node to be removed.
	 * @return True if the node was removed.
	 */
	public synchronized boolean removeNode(long nodeID) {
		return removeNode(getNodeByID(nodeID));
	}

	public synchronized void removeProperty(DAGObject dagObj, String key) {
		dagObj.remove(key);

		for (DAGModule<?> module : modules_.values())
			module.removeProperty(dagObj, key);
	}

	public synchronized void saveState() {
		// Save 'global' values
		System.out.print("Please wait while saving state... ");
		File globals = new File(rootDir_, GLOBALS_FILE);
		try {
			globals.createNewFile();
			BufferedWriter out = new BufferedWriter(new FileWriter(globals));
			writeDAGDetails(out);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Save node and edge collections
		saveDAGFile(nodes_, rootDir_, NODE_FILE);
		saveDAGFile(edges_, rootDir_, EDGE_FILE);

		// Save modules
		Set<DAGModule<?>> saved = new HashSet<>();
		for (DAGModule<?> module : modules_.values()) {
			if (!saved.contains(module))
				module.saveModule(rootDir_);
			saved.add(module);
		}

		System.out.println("Done!");
	}

	public void shutdown() {
		System.out.println("Saving state and shutting down.");
		saveState();
		System.exit(0);
	}
}
