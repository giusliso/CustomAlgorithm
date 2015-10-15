import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.scored.ScoredIdBuilder;
import org.grouplens.lenskit.vectors.SparseVector;

import com.google.common.collect.Iterables;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

public class SeedRecommender extends AbstractItemRecommender {

	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemDAO idao;
	private ItemScorer scorer;

	@Inject
	public SeedRecommender(EventDAO dao, UserEventDAO uedao, ItemDAO idao, ItemScorer scorer) {
		this.uedao = uedao;
		this.idao = idao;
		this.dao = dao;
		this.scorer = scorer;
	}




	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {
		candidates = getEffectiveCandidates(user, candidates, excludes);
		
		List<ScoredId> recommendations = new LinkedList<>();

		int userRates=0;
		UserHistory<Event> userHistory = uedao.getEventsForUser(user);
		if (userHistory != null) 
			userRates=userHistory.size();

		if(userRates < 20) {
			SeedItemSet set = new SeedItemSet(dao);
			Set<Long> seeds = set.getSeedItemSet();
			for(Long seed : seeds)
				recommendations.add(new ScoredIdBuilder(seed,scorer.score(user,seed)).build());
		}
		
		if(userRates != 0) {   
	        SparseVector scores = scorer.score(user, candidates);
	        LongArrayList recs = scores.keysByValue(true);
	        int rs = n-recommendations.size();
	        for(int i=0; i<rs; i++)
	        	recommendations.add(new ScoredIdBuilder(recs.getLong(i), scores.get(recs.getLong(i))).build());
		}
		
		return recommendations;
	}

	
	private LongSet getEffectiveCandidates(long user, LongSet candidates, LongSet exclude) {
        if (candidates == null) 
            candidates = idao.getItemIds();
        
        if (exclude == null) {
        	UserHistory<Event> userRatings = uedao.getEventsForUser(user);
            exclude = (userRatings == null) ? LongSets.EMPTY_SET : userRatings.itemSet();
        }
        
        if (!exclude.isEmpty()) {
            candidates = LongUtils.setDifference(candidates, exclude);
        }
        return candidates;
    }




    protected LongSet getPredictableItems(long user) {
        return idao.getItemIds();
    }
}
