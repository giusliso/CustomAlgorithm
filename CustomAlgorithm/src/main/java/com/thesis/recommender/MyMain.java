package com.thesis.recommender;
import java.io.File;
import java.util.List;

import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer;
import org.grouplens.lenskit.baseline.MeanDamping;
import org.grouplens.lenskit.baseline.UserMeanBaseline;
import org.grouplens.lenskit.baseline.UserMeanItemScorer;
import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.core.LenskitRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.pref.PreferenceDomain;
import org.grouplens.lenskit.data.source.CSVDataSourceBuilder;
import org.grouplens.lenskit.data.source.DataSource;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;

import com.thesis.models.CoOccurrenceMatrixModel;
import com.thesis.qualifiers.CoOccurrenceModel;
import com.thesis.qualifiers.CosineSimilarityModel;



public class MyMain {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws RecommenderBuildException {

		// load data
		CSVDataSourceBuilder dataset = new CSVDataSourceBuilder(new File("data/u.data"));
		dataset.setDelimiter("\t");
		dataset.setDomain(new PreferenceDomain(1,5,1));
		DataSource data = dataset.build();

		// --- CONFIGURATE THE RECOMMENDER
		LenskitConfiguration config = new LenskitConfiguration();

		// data
		config.bind(EventDAO.class).to(data.getEventDAO());

		// scorer
		//		config.bind(ItemScorer.class).to(PositiveUserMeanItemScorer.class);
		config.bind(ItemScorer.class).to(UserMeanItemScorer.class);
		config.bind(UserMeanBaseline.class, ItemScorer.class).to(ItemMeanRatingItemScorer.class);
		config.set(MeanDamping.class).to(5.0);
		config.set(NeighborhoodSize.class).to(20);


		// models
		config.bind(CoOccurrenceModel.class, ItemItemModel.class).to(CoOccurrenceMatrixModel.class);
		config.bind(CosineSimilarityModel.class, ItemItemModel.class).to(SimilarityMatrixModel.class);
		config.within(CosineSimilarityModel.class, ItemItemModel.class).bind(VectorSimilarity.class).to(CosineVectorSimilarity.class);

		// recommender
		config.bind(ItemRecommender.class).to(SeedRecommender.class);

		// --- RECOMMENDATIONS
		LenskitRecommender rec = LenskitRecommender.build(config);

		List<ScoredId> recommendations1 = ((SeedRecommender) rec.getItemRecommender()).get_recommendation_list(54321, 5, true);
		List<ScoredId> recommendations2 = rec.getItemRecommender().recommend(12345, 5);
		List<ScoredId> recommendations3 = rec.getItemRecommender().recommend(1, 5);

		System.out.println("\nCASO = 0\n"+ recommendations1);
		System.out.println("\nCASO < 20\n"+ recommendations2);
		System.out.println("\nCASO >= 20\n"+ recommendations3);
		System.out.println("\n\nFine");

	}
}