import java.io.File;
import java.util.List;

import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.RecommenderBuildException;
import org.grouplens.lenskit.basic.TopNItemRecommender;
import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.core.LenskitRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.pref.PreferenceDomain;
import org.grouplens.lenskit.data.source.CSVDataSourceBuilder;
import org.grouplens.lenskit.data.source.DataSource;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.knn.item.ItemItemScorer;
import org.grouplens.lenskit.knn.item.ItemSimilarity;
import org.grouplens.lenskit.knn.user.NeighborFinder;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;



public class MyMain {

	public static void main(String[] args) throws RecommenderBuildException {

		CSVDataSourceBuilder dataset = new  CSVDataSourceBuilder(new File("dataset/ml-1m/ratings.dat"));
		dataset.setDelimiter("::");
		dataset.setDomain(new PreferenceDomain(1,5,1));
		DataSource data = dataset.build();

		//		CustomItemRecommender recom = new CustomItemRecommender(data.getEventDAO(), new CustomItemScorer());
		//		recom.addSimilarityMeasure(new PearsonCorrelation());
		//		recom.addSimilarityMeasure(new CosineVectorSimilarity());
		//		recom.addSimilarityMeasure(new DistanceVectorSimilarity());
		//		recom.createModels();

		LenskitConfiguration config = new LenskitConfiguration();
		config.bind(EventDAO.class).to(data.getEventDAO());
		config.bind(ItemScorer.class).to(ItemItemScorer.class);
		config.bind(ItemRecommender.class).to(SeedRecommender.class);
		config.within(ItemSimilarity.class).bind(VectorSimilarity.class).to(CosineVectorSimilarity.class);

			
		
		LenskitRecommender rec = LenskitRecommender.build(config);

		List<ScoredId> recommendations1 = rec.getItemRecommender().recommend(12345, 10);
		List<ScoredId> recommendations2 = rec.getItemRecommender().recommend(123234545, 10);
		List<ScoredId> recommendations3 = rec.getItemRecommender().recommend(1, 10);
		System.out.println("\nCASO = 0\n"+ recommendations2);
		System.out.println("\nCASO < 20\n"+ recommendations1);
		System.out.println("\nCASO > 20\n"+ recommendations3);
		System.out.println("\n\nFine");
	}

	public static void testSeed() {

		CSVDataSourceBuilder dataset = new  CSVDataSourceBuilder(new File("dataset/ml-1m/ratings.dat"));
		dataset.setDelimiter("::");
		dataset.setDomain(new PreferenceDomain(1,5,1));
		DataSource data = dataset.build();

		SeedItemSet seedItemSet = new SeedItemSet(data);

		System.out.println("Most popular EVER       = " + seedItemSet.getMostPopularItem(SeedItemSet.Period.EVER));
		System.out.println("Most popular LAST WEEK  = " + seedItemSet.getMostPopularItem(SeedItemSet.Period.LAST_WEEK));
		System.out.println("Most popular LAST MONTH = " + seedItemSet.getMostPopularItem(SeedItemSet.Period.LAST_MONTH));
		System.out.println("Most popular LAST YEAR  = " + seedItemSet.getMostPopularItem(SeedItemSet.Period.LAST_YEAR));
		System.out.println("Last Positively Rated   = " + seedItemSet.getLastPositivelyRatedItem());

		//new seed();
		seedItemSet.getLastItemAddedNotRated();
	}
}
