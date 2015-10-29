package com.thesis.scorers;

import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.baseline.MeanDamping;
import org.grouplens.lenskit.baseline.UserMeanBaseline;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.core.Shareable;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.history.UserHistorySummarizer;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * It implements the UserMeanIterScorer, but considers only the positive ratings of the user
 */
@Shareable
public class PositiveUserMeanItemScorer extends AbstractItemScorer implements Serializable {
	private static final long serialVersionUID = -3347157296323783545L;

	private final UserEventDAO uedao;
	private final ItemScorer baseline;
	private final UserHistorySummarizer summarizer;
	private final double damping;

	@Inject
	public PositiveUserMeanItemScorer(UserEventDAO dao,
			@UserMeanBaseline ItemScorer base,
			UserHistorySummarizer sum,
			@MeanDamping double damp) {
		Preconditions.checkArgument(damp >= 0, "Negative damping not allowed");
		this.uedao = dao;
		this.baseline = base;
		this.summarizer = sum;
		this.damping = damp;
	}

	
	@Override
	public void score(long user, @Nonnull MutableSparseVector scores) {
		// Get the user's profile
		UserHistory<Rating> profile = uedao.getEventsForUser(user, Rating.class);
		if (profile == null)
			baseline.score(user, scores);
		else
		{
			UserHistory<Rating> posProfile = profile.filter(new Predicate<Rating>() {
				@Override
				public boolean apply(Rating r) {
					return r.getValue() >= meanValue(profile);
				}
			});
		
			MutableSparseVector vec = summarizer.summarize(posProfile).mutableCopy();

			// score everything, both rated and not, for offsets
			LongSet allItems = new LongOpenHashSet(vec.keySet());
			allItems.addAll(scores.keyDomain());

			SparseVector baseScores = baseline.score(user, allItems);

			// subtract scores from ratings, yielding offsets
			for (VectorEntry e : vec) {
				double base = baseScores.get(e.getKey());
				vec.set(e, e.getValue() - base);
			}

			double meanOffset = vec.sum() / (vec.size() + damping);

			// to score: fill with baselines, add user mean offset
			for(long key : scores.keyDomain())
				scores.set(key, baseScores.get(key)+ meanOffset);
		}

	}


	/**
	 * @param userHistory ratings by user
	 * @return ratings mean value
	 */
	private double meanValue(UserHistory<Rating> userHistory){
		double sum=0.0;
		for(Rating rate : userHistory) 
			sum += rate.getValue();
		return sum/userHistory.size();
	}
}
