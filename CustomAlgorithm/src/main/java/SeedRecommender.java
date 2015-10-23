import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;

import it.unimi.dsi.fastutil.longs.Long2DoubleArrayMap;
import it.unimi.dsi.fastutil.longs.LongSet;

public class SeedRecommender extends AbstractItemRecommender {

	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private ItemItemModel model;

	@Inject
	public SeedRecommender(EventDAO dao, UserEventDAO uedao, ItemScorer scorer, ItemItemModel model) {
		this.uedao = uedao;
		this.dao = dao;
		this.scorer = scorer;
		this.model = model;
	}

	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {

		ScoredItemAccumulator recommendations = new TopNScoredItemAccumulator(n);

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap();
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		Set<Long> seeds = new HashSet<Long>();

		if(userHistory == null || userHistory.size() < 20) {
			SeedItemSet set = new SeedItemSet(dao);
			seeds.addAll(set.getSeedItemSet());

			for (long seed : seeds) {
				double score = scorer.score(user, seed);
				seedMap.put(seed, score);
				recommendations.put(seed, score);
			}
		}

		if (userHistory != null)
			for (Rating rating : userHistory){
				seeds.add(rating.getItemId());
				seedMap.put(rating.getItemId(), rating.getValue());
			}

		for (Long s : seeds) {
			SparseVector neighbors = model.getNeighbors(s);
			if (!neighbors.isEmpty()) 
				for (Long i : neighbors.keysByValue(true))
					if (i != s && !seeds.contains(i)) {
						double simISnorm = normalize(-1, 1, neighbors.get(i), 0, 1);
						double scoreSeed = seedMap.get(s);
						recommendations.put(i, simISnorm*scoreSeed);
						break;
					}
		}


		return recommendations.finish();
	}


	private double normalize(double oldMin, double oldMax, double oldVal, double newMin, double newMax) {
		double scale = (newMax-newMin)/(oldMax-oldMin);
		return newMin + ( (oldVal-oldMin) * scale );
	}


}
