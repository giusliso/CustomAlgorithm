package it.maivisto.models;
import java.io.Serializable;
import java.util.Map;
import org.grouplens.grapht.annotation.DefaultProvider;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Shareable;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * Item content similarity model using a normalized in-memory similarity matrix.
 */
@DefaultProvider(ItemContentMatrixModelBuilder.class)
@Shareable
public class ItemContentMatrixModel implements Serializable, ItemItemModel {
	private static final long serialVersionUID = 1L;

	private final Map<Long,ImmutableSparseVector> model;

	public ItemContentMatrixModel(Map<Long,ImmutableSparseVector> nbrs) {
		model = nbrs;
	}


	@Override
	public LongSortedSet getItemUniverse() {	
		return LongUtils.packedSet(model.keySet());
	}


	@Override
	public SparseVector getNeighbors(long item) {
		if (model.containsKey(item))
			return model.get(item);
		else
			return ImmutableSparseVector.empty();
	}
}
