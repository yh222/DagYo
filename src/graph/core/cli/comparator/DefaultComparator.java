package graph.core.cli.comparator;

import graph.core.cli.DAGPortHandler;

import java.util.Comparator;

public abstract class DefaultComparator implements Comparator<Object> {
	private DAGPortHandler handler_;

	@Override
	public final int compare(Object o1, Object o2) {
		if (o1 == null)
			if (o2 == null)
				return 0;
			else
				return 1;
		else if (o2 == null)
			return -1;
		
		// Convert where appropriate
		if (o1.getClass().equals(o2.getClass())) {
			o1 = handler_.convertToComparable(o1);
			o2 = handler_.convertToComparable(o2);
		}

		// Perform internal check
		int result = compareInternal(o1, o2);
		if (result != 0)
			return result;

		// Default to hashCode and classname comparison
		result = Integer.compare(o1.hashCode(), o2.hashCode());
		if (result != 0)
			return result;

		return o1.getClass().getCanonicalName()
				.compareTo(o2.getClass().getCanonicalName());
	}

	protected abstract int compareInternal(Object o1, Object o2);

	public void setHandler(DAGPortHandler handler) {
		handler_ = handler;
	}
}
