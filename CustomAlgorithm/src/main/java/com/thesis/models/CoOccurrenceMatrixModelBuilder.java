package com.thesis.models;
import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

@NotThreadSafe
public class CoOccurrenceMatrixModelBuilder implements Provider<ItemItemModel> {

	private final ItemItemBuildContext context;


	@Inject
	public CoOccurrenceMatrixModelBuilder(@Transient ItemItemBuildContext context) {
		this.context = context;
	}


	@Override
	public ItemItemModel get() {

		LongSortedSet allItems = context.getItems();

		Long2ObjectMap<ScoredItemAccumulator> rows = makeAccumulators(allItems);

		for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext() ; ) {
			Long i = itI.next();
			SparseVector vecI = context.itemVector(i);
			
			for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
				Long j = itJ.next();
				SparseVector vecJ = context.itemVector(j);
				int coOccurences = vecJ.countCommonKeys(vecI);
				rows.get(i).put(j, coOccurences);
				rows.get(j).put(i, coOccurences);
				System.out.println("("+i+","+j+")");
			}
		}
		return new CoOccurrenceMatrixModel(finishRows(rows));
	}

	
	private Long2ObjectMap<ScoredItemAccumulator> makeAccumulators(LongSet items) {
        Long2ObjectMap<ScoredItemAccumulator> rows = new Long2ObjectOpenHashMap<ScoredItemAccumulator>(items.size());
        for(Long item : items)
        	rows.put(item, new UnlimitedScoredItemAccumulator());       
        return rows;
    }
	
	private Long2ObjectMap<ImmutableSparseVector> finishRows(Long2ObjectMap<ScoredItemAccumulator> rows) {
        Long2ObjectMap<ImmutableSparseVector> results = new Long2ObjectOpenHashMap<ImmutableSparseVector>(rows.size());
        for (Long2ObjectMap.Entry<ScoredItemAccumulator> e: rows.long2ObjectEntrySet()) 
            results.put(e.getLongKey(), e.getValue().finishVector().freeze());      
        return results;
    }
}
