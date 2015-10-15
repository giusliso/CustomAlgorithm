import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemDAO;
import org.grouplens.lenskit.data.dao.PrefetchingUserEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.item.ItemSimilarity;
import org.grouplens.lenskit.knn.item.ItemVectorSimilarity;
import org.grouplens.lenskit.knn.item.model.BasicNeighborIterationStrategy;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContextProvider;
import org.grouplens.lenskit.knn.item.model.ItemItemModelBuilder;
import org.grouplens.lenskit.knn.item.model.NeighborIterationStrategy;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.transform.normalize.DefaultUserVectorNormalizer;
import org.grouplens.lenskit.transform.threshold.AbsoluteThreshold;
import org.grouplens.lenskit.transform.threshold.Threshold;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.DistanceVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.PearsonCorrelation;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;


public class CustomItemRecommender extends AbstractItemRecommender {

	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemDAO idao;
	private ItemScorer scorer;
	private int minRatedItems = 20;

	private LinkedList<SimilarityMatrixModel> models = null;
	private LinkedList<VectorSimilarity> similarities = null;

	@Inject
    public CustomItemRecommender(UserEventDAO uedao, ItemDAO idao, ItemScorer scorer) {
		this.uedao = uedao;
        this.idao = idao;
        this.scorer = scorer;
        
        this.models = new LinkedList<SimilarityMatrixModel>();
        this.similarities = new LinkedList<VectorSimilarity>();
        addSimilarityMeasure(new PearsonCorrelation());
		addSimilarityMeasure(new CosineVectorSimilarity());
		addSimilarityMeasure(new DistanceVectorSimilarity());
		createModels();
    }
	



	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {

		if(similarities.isEmpty())
			similarities.addLast(new PearsonCorrelation()); //default

		if(models.isEmpty())
			for(VectorSimilarity sim : similarities)
				models.addLast(buildSimilarityMatrix(sim));
		return null;
	}






	private void addSimilarityMeasure(VectorSimilarity sim){
		similarities.addLast(sim);
	}

	public void createModels(){
		for(VectorSimilarity sim : similarities)
		{
			SimilarityMatrixModel model = buildSimilarityMatrix(sim);
			models.add(model);
			saveSimilarityMatrixModel(model, sim.getClass().getName());
		}
	}

	private void saveSimilarityMatrixModel(SimilarityMatrixModel model, String simName){

		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File("models/"+simName+"Matrix.ser")));
			oos.writeObject(model);
			oos.close();
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private SimilarityMatrixModel loadSimilarityMatrixModel(String simName){
		SimilarityMatrixModel model = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(new File("models/"+simName+"Matrix.ser")));
			ois.readObject();
			ois.close();
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return model;
	}
	
	private SimilarityMatrixModel buildSimilarityMatrix(VectorSimilarity sim){
		ItemSimilarity similarity = new ItemVectorSimilarity(sim);
		ItemItemBuildContext context = new ItemItemBuildContextProvider(uedao, new DefaultUserVectorNormalizer(), new RatingVectorUserHistorySummarizer()).get();
		Threshold thresh = new AbsoluteThreshold(0);
		NeighborIterationStrategy nbrStrat = new BasicNeighborIterationStrategy();
		int size = idao.getItemIds().size();

		ItemItemModelBuilder builder = new ItemItemModelBuilder(similarity, context, thresh, nbrStrat, size);

		return builder.get();
	}


}
