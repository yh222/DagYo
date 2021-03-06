package graph.module;

import graph.core.DAGEdge;
import graph.core.DAGNode;
import graph.core.DAGObject;
import graph.core.DirectedAcyclicGraph;
import graph.core.Edge;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

import util.serialisation.DefaultSerialisationMechanism;
import util.serialisation.SerialisationMechanism;

/**
 * A DAG module defines the basic framework a DAG module must conform to.
 * 
 * @author Sam Sarjant
 */
public abstract class DAGModule<T> implements Serializable {
	private static final String MODULE_DIR = "modules";
	private static final long serialVersionUID = -1752235659675219252L;
	protected transient DirectedAcyclicGraph dag_;

	protected DAGModule() {
	}

	/**
	 * Called after an edge is added to the DAG.
	 * 
	 * @param edge
	 *            The edge that was added.
	 * @return False if the edge should be removed.
	 */
	public boolean addEdge(Edge edge) {
		return true;
	}

	/**
	 * Called after a node is added to the DAG.
	 * 
	 * @param node
	 *            The node that was added.
	 * @return False if the node should be removed.
	 */
	public boolean addNode(DAGNode node) {
		return true;
	}

	public void addProperty(DAGObject dagObj, String key, String value) {

	}

	public void clear() {

	}

	public abstract T execute(Object... args) throws IllegalArgumentException,
			ModuleException;

	/**
	 * A method that is called once initialisation of a DAG is complete. Note
	 * that this method may not be called, and no functionality is required.
	 * 
	 * @param nodes
	 *            The collection of all existing nodes.
	 * @param edges
	 *            The collection of all existing edges.
	 */
	public void initialisationComplete(Collection<DAGNode> nodes,
			Collection<DAGEdge> edges) {
	}

	/**
	 * Called after 'edge' is removed.
	 * 
	 * @param edge
	 *            The edge being removed.
	 * @return Returns boolean (no default meaning).
	 */
	public boolean removeEdge(Edge edge) {
		return false;
	}

	/**
	 * Called after 'node' is removed.
	 * 
	 * @param node
	 *            The node being removed.
	 * @return Returns boolean (no default meaning).
	 */
	public boolean removeNode(DAGNode node) {
		return false;
	}

	/**
	 * Removes a property from a DAG Object.
	 * 
	 * @param dagObj
	 *            The object removing the property.
	 * @param key
	 *            The key being removed.
	 */
	public void removeProperty(DAGObject dagObj, String key) {

	}

	public boolean saveModule(File rootDir) {
		DefaultSerialisationMechanism serialiser = SerialisationMechanism.FST
				.getSerialiser();
		File modFile = moduleFile(rootDir, getClass().getSimpleName());
		try {
			modFile.createNewFile();
			// If a module should only save IDs for the nodes/edges.
			serialiser.serialize(this, modFile, true);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public void setDAG(DirectedAcyclicGraph directedAcyclicGraph) {
		dag_ = directedAcyclicGraph;
	}

	private static File moduleFile(File rootDir, String moduleName) {
		File file = new File(rootDir, MODULE_DIR + File.separatorChar
				+ moduleName);
		file.getParentFile().mkdirs();
		return file;
	}

	public static DAGModule<?> loadCreateModule(File rootDir,
			Class<?> moduleClass) throws InstantiationException,
			IllegalAccessException {
		DefaultSerialisationMechanism serialiser = SerialisationMechanism.FST
				.getSerialiser();
		File modFile = moduleFile(rootDir, moduleClass.getSimpleName());
		if (modFile.exists()) {
			try {
				DAGModule<?> module = (DAGModule<?>) serialiser
						.deserialize(modFile);
				return module;
			} catch (Exception e) {
				System.err
						.println("Could not deserialize module. Creating a new one.");
			}
		}

		// TODO In this case, need to rescan dirs.
		return (DAGModule<?>) moduleClass.newInstance();
	}
}
