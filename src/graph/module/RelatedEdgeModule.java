package graph.module;

import graph.core.DAGNode;
import graph.core.Edge;
import graph.core.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;

import util.MultiMap;
import util.Pair;

/**
 * The related edge module indexes sets of edges related to a node. The execute
 * method can take both Nodes and ints, representing Nodes present in the edge
 * and the positions of those nodes (if provided).
 * 
 * @author Sam Sarjant
 */
// TODO Refactor class to use DAGEdges instead of Edges
public class RelatedEdgeModule extends DAGModule<Collection<Edge>> {
	private static final long serialVersionUID = 1588174113071358990L;
	protected ConcurrentMap<Node, MultiMap<Object, Edge>> relatedEdges_ = new ConcurrentHashMap<>();

	protected Object[] asIndexed(Node... nodes) {
		Object[] indexedNodes = new Object[nodes.length * 2];
		for (int i = 0; i < nodes.length; i++) {
			indexedNodes[i * 2] = nodes[i];
			indexedNodes[i * 2 + 1] = i + 1;
		}
		return indexedNodes;
	}

	protected Collection<Edge> filterNonDAGs(Collection<Edge> edges,
			Object[] args) {
		Collection<Pair<Node, Integer>> nonDAGNodes = new ArrayList<>(
				args.length);
		for (int i = 0; i < args.length; i++) {
			Node node = (Node) args[i];
			int index = -1;
			if (i < args.length - 1 && args[i + 1] instanceof Integer) {
				i++;
				index = (int) args[i] - 1;
			}

			if (!(node instanceof DAGNode))
				nonDAGNodes.add(new Pair<Node, Integer>(node, index));
		}

		if (nonDAGNodes.isEmpty() || edges == null) {
			if (idModule_) {
				Collection<Edge> dagEdges = new ArrayList<>(edges.size());
				for (Edge edge : edges)
					dagEdges.add(dag_.getEdgeByID(edge.getID()));
				return dagEdges;
			}
			return edges;
		}

		// Check every edge (EXPENSIVE)
		Collection<Edge> filtered = new HashSet<>();
		for (Edge e : edges) {
			Node[] edgeNodes = e.getNodes();
			boolean matches = true;
			for (Pair<Node, Integer> nonDAG : nonDAGNodes) {
				if (nonDAG.objB_ == -1) {
					if (!ArrayUtils.contains(edgeNodes, nonDAG.objA_)) {
						matches = false;
						break;
					}
				} else if (!edgeNodes[nonDAG.objB_].equals(nonDAG.objA_)) {
					matches = false;
					break;
				}
			}

			if (matches) {
				if (idModule_)
					filtered.add(dag_.getEdgeByID(e.getID()));
				else
					filtered.add(e);
			}
		}
		return filtered;
	}

	protected Collection<Edge> getEdges(Node node, Object edgeKey,
			boolean createNew) {
		MultiMap<Object, Edge> indexedEdges = relatedEdges_.get(node);
		if (indexedEdges == null) {
			if (createNew) {
				indexedEdges = MultiMap.createConcurrentHashSetMultiMap();
				relatedEdges_.put(node, indexedEdges);
			} else
				return new ConcurrentLinkedQueue<>();
		}

		Collection<Edge> edges = (edgeKey != null) ? indexedEdges.get(edgeKey)
				: indexedEdges.values();
		if (edges == null) {
			if (createNew)
				edges = indexedEdges
						.putCollection(edgeKey, new TreeSet<Edge>());
			else
				return new ConcurrentLinkedQueue<>();
		}
		return edges;
	}

	protected List<EdgeCol> locateEdgeCollections(boolean createNew,
			Object... args) {
		List<EdgeCol> edgeCols = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			Node n = (Node) args[i];
			boolean additive = true;

			// Get index of node
			Integer index = null;
			if (i < args.length - 1 && args[i + 1] instanceof Integer) {
				i++;
				index = (int) args[i];
				if (index < 0) {
					additive = false;
					index *= -1;
				}
			}

			if (!(n instanceof DAGNode))
				continue;

			Collection<Edge> edgeCol = getEdges(n, index, createNew);
			edgeCols.add(new EdgeCol(additive, edgeCol));
		}
		return edgeCols;
	}

	@Override
	public boolean addEdge(Edge edge) {
		Collection<EdgeCol> edgeCollections = locateEdgeCollections(true,
				asIndexed(edge.getNodes()));
		for (EdgeCol edgeCol : edgeCollections)
			edgeCol.add(edge);
		return true;
	}

	@Override
	public void clear() {
		relatedEdges_.clear();
	}

	@Override
	public Collection<Edge> execute(Object... args)
			throws IllegalArgumentException {
		List<EdgeCol> edgeCollections = locateEdgeCollections(false, args);
		Collections.sort(edgeCollections, new SmallestFirstComparator());
		Collection<Edge> edges = null;
		for (EdgeCol edgeCol : edgeCollections) {
			if (edges == null)
				edges = edgeCol.edgeCol_;
			else if (edgeCol.additive_)
				edges = CollectionUtils.retainAll(edges, edgeCol.edgeCol_);
			else if (!edgeCol.additive_)
				edges = CollectionUtils.removeAll(edges, edgeCol.edgeCol_);

			if (edges.isEmpty())
				return edges;
		}

		edges = filterNonDAGs(edges, args);
		return edges;
	}

	public Collection<Edge> findEdgeByNodes(Node... nodes) {
		Object[] indexedNodes = asIndexed(nodes);
		return execute(indexedNodes);
	}

	@Override
	public boolean removeEdge(Edge edge) {
		boolean result = false;
		Collection<EdgeCol> indexedEdges = locateEdgeCollections(false,
				asIndexed(edge.getNodes()));

		for (EdgeCol col : indexedEdges)
			result |= col.remove(edge);
		return result;
	}

	@Override
	public String toString() {
		return "Related Edges: " + relatedEdges_.size();
	}

	protected class EdgeCol {
		public boolean additive_;

		public Collection<Edge> edgeCol_;

		public EdgeCol(boolean additive, Collection<Edge> edgeCol) {
			additive_ = additive;
			edgeCol_ = edgeCol;
		}

		public boolean add(Edge edge) {
			return edgeCol_.add(edge);
		}

		public boolean remove(Edge edge) {
			return edgeCol_.remove(edge);
		}

		public int size() {
			return edgeCol_.size();
		}

		@Override
		public String toString() {
			return edgeCol_.toString() + " (" + additive_ + ")";
		}
	}

	protected class SmallestFirstComparator implements Comparator<EdgeCol> {
		@Override
		public int compare(EdgeCol o1, EdgeCol o2) {
			if (o1.additive_ && !o2.additive_)
				return -1;
			else if (!o1.additive_ && o2.additive_)
				return 1;
			return Integer.compare(o1.size(), o2.size());
		}
	}
}