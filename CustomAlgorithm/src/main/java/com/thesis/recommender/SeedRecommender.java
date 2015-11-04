package com.thesis.recommender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.knn.NeighborhoodSize;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.SparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thesis.models.CoOccurrenceMatrixModel;
import com.thesis.qualifiers.CoOccurrenceModel;
import com.thesis.qualifiers.CosineSimilarityModel;
import com.thesis.recommender.RecommendationTriple;

import it.unimi.dsi.fastutil.longs.Long2DoubleArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;


public class SeedRecommender extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(SeedRecommender.class);

	private EventDAO dao;
	private ItemDAO idao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private final int neighborhoodSize;
	private boolean activate_standard_seed = true;
	private ModelsManager models;
	private Set<Long> seed_itemset = null;

	@Inject
	public SeedRecommender(EventDAO dao, ItemDAO idao, UserEventDAO uedao, 
			ItemScorer scorer, 
			@CoOccurrenceModel ItemItemModel coOccModel, @CosineSimilarityModel ItemItemModel cosModel,
			@NeighborhoodSize int nnbrs) {
		this.uedao = uedao;
		this.dao = dao;
		this.idao = idao;
		this.scorer = scorer;
		this.neighborhoodSize = nnbrs;
		this.models = new ModelsManager();
		this.models.addModel(cosModel, 1.0);
		this.models.addModel(coOccModel, 1.0);
	}



	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		if(userHistory == null || userHistory.size() < 20)
			return coldStartSituationAlgorithm(user, n);
		else
			return noColdStartSituationAlgorithm(user, n);	
	}


	/**
	 * Implements the algorithm to use in case of cold start situation (i.e. user ratings < 20).
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @return The result list.
	 */
	private List<ScoredId> coldStartSituationAlgorithm(long user, int n) {

		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);

		logger.debug("[ User {} rated {} items --> Cold Start situation ]", user, ((userHistory != null) ? userHistory.size() : 0));

		LinkedList<RecommendationTriple> reclist = new LinkedList<RecommendationTriple>();
		Set<Long> recItemsSet = new HashSet<Long>();

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap();

		if(this.activate_standard_seed) {
			SeedItemSet set = new SeedItemSet(dao);

			for (long seed : set.getSeedItemSet()) {
				double score = scorer.score(user, seed);
				seedMap.put(seed, score);	

				// if user hasn't rated the seed yet, add it to recommendations list
				if(!hasRatedItem(userHistory, seed)){
					reclist.add(new RecommendationTriple(seed, score, -1)); 
					recItemsSet.add(seed);
					logger.debug("Standard seed {} added with score {}", seed, score);
				}
				// per i seed standard non c'è una matrice di provenienza. ----------------------------------------
				// associamo matID= -1 e diamo peso pari a 1
			}

			logger.debug("Added standard seeds");
		}

		// aggiungo i seeds esterni se ci sono 
		if(this.seed_itemset != null) {
			for (long seed : seed_itemset)
				if(!seedMap.containsKey(seed)) {
					double score = scorer.score(user, seed);
					seedMap.put(seed, score);		
					logger.debug("Added seed {} from seed_itemset", seed);
				}
		}

		//prendo i positivi e gli aggiungo a seedmap e seeds
		if(userHistory != null) {			
			double meanUserRating = meanValue(userHistory);
			logger.debug("Ratings mean value for user {}: {}", user, meanUserRating);
			for(Rating rate : userHistory)
				if(rate.getValue() >= meanUserRating)
					seedMap.put(rate.getItemId(),rate.getValue());			
		}


		int k=0;
		int neighsSize = Integer.MAX_VALUE;
		while(k<neighsSize && recItemsSet.size() < n) {
			for (Long s : seedMap.keySet()){
				Set<Integer> matIdsSet = models.modelsIdSet();
				for(Integer matID : matIdsSet) {
					ItemItemModel model = models.getModel(matID);
					SparseVector neighbors = model.getNeighbors(s);
					if(neighsSize > neighbors.size())
						neighsSize = neighbors.size();
					if (!neighbors.isEmpty()){
						LongArrayList neighs = neighbors.keysByValue(true);
						Long i = neighs.get(k);
						if (!seedMap.containsKey(i) ) {

							double simISnorm = normalize(-1, 1, neighbors.get(i), 0, 1); /// SISTEMARE...............................................
							if(model instanceof CoOccurrenceMatrixModel)
								simISnorm = neighbors.get(i);

							double scoreSeed = seedMap.get(s);
							reclist.add(new RecommendationTriple(i, simISnorm*scoreSeed, matID));
							recItemsSet.add(i);
						}
					}
				}
			}
			k++;
		}

		return getRankedRecommendationsList(n, reclist);
	}

	/**
	 * Implements the algorithm to use not in case of cold start situation (i.e. user ratings ≥ 20).
	 * @param user The user ID.
	 * @param n The number of recommendations to produce, or a negative value to produce unlimited recommendations.
	 * @return The result list.
	 */
	private List<ScoredId> noColdStartSituationAlgorithm(long user, int n){
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);

		logger.debug("[ User {} rated {} items --> Not in Cold Start situation ]", user, userHistory.size());

		TopNScoredItemAccumulator reclist = new TopNScoredItemAccumulator(n);		

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap();

		if(this.activate_standard_seed) {
			SeedItemSet set = new SeedItemSet(dao);

			for (long seed : set.getSeedItemSet()) {
				double score = scorer.score(user, seed);
				seedMap.put(seed, score);	
				// if user hasn't rated the seed yet, add it to recommendations list
				if(!hasRatedItem(userHistory, seed)){
					reclist.put(seed, score); 
					logger.debug("Standard seed {} added with score {}", seed, score);
				}
			}
			logger.debug("Added standard seeds");
		}

		// aggiungo i seeds esterni se ci sono 
		if(this.seed_itemset != null) {
			for (long seed : seed_itemset)
				if(!seedMap.containsKey(seed)) {
					double score = scorer.score(user, seed);
					seedMap.put(seed, score);		
					logger.debug("Added seed {} from seed_itemset", seed);
				}
		}

		//prendo i rating dell'utente e li aggiungo a seedMap
		for (Rating rating : userHistory)
			seedMap.put(rating.getItemId(), rating.getValue());


		//prendo gli elementi del catalogo e non considero gli item votati dall'utente
		LongSet recommendableItems = idao.getItemIds();
		for(long s : seedMap.keySet())
			recommendableItems.remove(s);

		logger.debug("Scoring {} items.", recommendableItems.size());
		for(long itemId : recommendableItems){
			double recscoreI=0;
			double simI=0; 
			Set<Integer> matIdsSet = models.modelsIdSet();		
			for(Integer matID : matIdsSet) {
				ItemItemModel model = models.getModel(matID);

				//prendo gli "neighborhoodSize" item più vicini all'item considerato e li memorizzo in neighs
				SparseVector neighbors = model.getNeighbors(itemId);

				LongList neighs = (!neighbors.isEmpty()) ? neighbors.keysByValue(true) : new LongArrayList();
				int nnbrs = (neighs.size() >= neighborhoodSize) ? neighborhoodSize : neighs.size();
				neighs = neighs.subList(0, nnbrs);

				//seleziono il vicinato che si sovrappone ai seed
				for (long neigh : neighs) {
					//se il vicinato contiene un item presente in seedMap lo uso per il calcolo dello score di itemId
					if(seedMap.containsKey(neigh)){

						double simISnorm = normalize(-1, 1, neighbors.get(neigh), 0, 1); 
						if(model instanceof CoOccurrenceMatrixModel)
							simISnorm = neighbors.get(neigh);

						double scoreSeed = seedMap.get(neigh);

						recscoreI += simISnorm*scoreSeed;
						simI += simISnorm;
					}
				}			
			}
			reclist.put(itemId, recscoreI/simI);
		}

		return reclist.finish();
	}


	/**
	 * Sorts the list of possible recommendations. 
	 * Since the list can contain multiple instance of the same item coming from different similarity matrices, 
	 * it is sorted first by number of occurrences and then by score. 
	 * The score for an item is computed considering the weight of the matrix that has recommended it.
	 * @param n The number of recommendations to produce.
	 * @param items The list of possible recommendations. It can contain multiple instance of the same item.
	 * @return The result list .
	 */
	private List<ScoredId> getRankedRecommendationsList(int n, List<RecommendationTriple> items){
		logger.debug("Ranking the recommendations list");

		ScoredItemAccumulator recommendations = new TopNScoredItemAccumulator(n);

		// groups items by id
		HashMap<Long,List<RecommendationTriple>> unsortedMap = new HashMap<Long,List<RecommendationTriple>>();
		for(RecommendationTriple triple : items){
			long item = triple.getItemID();
			if(!unsortedMap.containsKey(item)){
				unsortedMap.put(item, new LinkedList<RecommendationTriple>());
				unsortedMap.get(item).add(triple);
			}
			else
				unsortedMap.get(item).add(triple);
		}

		TreeSet<OccScoreTriple> sortedList = new TreeSet<OccScoreTriple>();

		// computes the score of each item (weighted mean)
		for(Long item : unsortedMap.keySet()){
			double score = 0;
			double totWm = 0;
			for(RecommendationTriple i : unsortedMap.get(item)){
				double si = i.getScore();
				int mat = i.getMatID();
				double wm = (mat == -1) ? 1 : models.getModelWeight(mat);
				totWm += wm;
				score += si*wm;
			}

			// sorted first by number of occurrences, then by score
			sortedList.add(new OccScoreTriple(item, unsortedMap.get(item).size(), score/totWm));	
		}

		for(OccScoreTriple item : sortedList)
			recommendations.put(item.getItemID(), item.getScore());

		logger.debug("Ranking completed");
		return recommendations.finish();
	}

	/**
	 * Checks if a user has already rated an item
	 * @param userHistory list of user ratings
	 * @param seed the item to check
	 * @return true if the user has already rated the item, otherwise false
	 */
	private boolean hasRatedItem(UserHistory<Rating> userHistory, long seed) {
		if(userHistory == null)
			return false;
		for(Rating r : userHistory)
			if(r.getItemId() == seed)
				return true;
		return false;
	}

	/**
	 * Converts the double "oldVal" from the range [oldMin,oldMax] to the range [newMin, newMax]
	 * @param oldMin 
	 * @param oldMax
	 * @param oldVal
	 * @param newMin
	 * @param newMax
	 * @return the normalized value of oldVal
	 */
	private double normalize(double oldMin, double oldMax, double oldVal, double newMin, double newMax) {
		double scale = (newMax-newMin)/(oldMax-oldMin);
		return newMin + ( (oldVal-oldMin) * scale );
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



	/**
	 * List<ScoredId> get_recommendation_list(userID,N,seed_itemset,activate_standard_seed)
	 * produce recommendation list di dimensione N per l�utente registrato userID, avendo in input anche 
	 * una lista di item da utilizzare come seed esterni, ovvero come item iniziali che innescano l�algoritmo. 
	 * Il parametro activate_standard_seed � un boolean che regola l�uso dei 4 seed standard in aggiunta a 
	 * quelli esterni, passati come parametro. Si pu� invocare quando ad esempio un utente ha effettuato 
	 * una ricerca, passando come seed_itemset tutto il result set oppure l�item del result set su cui userID 
	 * ha cliccato.
	 * 		
	 * @param user utente a cui si vogliono fornire raccomandazioni
	 * @param n numero di raccomandazioni
	 * @param seed_itemset insieme di seed ulteriori
	 * @param activate_standard_seed attiva seed standard
	 * @return Lista di n raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(long user, int n, Set<Long> seed_itemset, boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(user, n);
	}

	/**
	 * List<ScoredId> get_recommendation_list(userID,N,activate_standard_seed)
	 * Produce recommendation list di dimensione N per l�utente registrato userID, in assenza di seed esterni. Si pu� invocare quando userID entra nella piattaforma e non ha effettuato alcuna azione. La recommendation list che si ottiene consente di avere i primi suggerimenti per userID  
	 * @param user utente a cui si vogliono fornire raccomandazioni
	 * @param n  numero di raccomandazioni
	 * @param activate_standard_seed attiva seed standard
	 * @return lista di raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(long user, int n, boolean activate_standard_seed){
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(user, n);
	}

	/**
	 * List<ScoredId> get_recommendation_list(userID,N,activate_standard_seed)
	 * 
	 * Produce recommendation list di dimensione N per l�utente registrato userID, in assenza di seed esterni. 
	 * Si pu� invocare quando userID entra nella piattaforma e non ha effettuato alcuna azione.
	 * La recommendation list che si ottiene consente di avere i primi suggerimenti per userID 
	 *  
	 * @param n: raccomandazioni
	 * @param seed_itemset: insieme di seed aggiuntivi
	 * @param activate_standard_seed: attivare seed standard
	 * @return lista di raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(int n, Set<Long> seed_itemset, boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;
		return this.recommend(-1,n);
	}

	/**
	 * Servizio get_recommendation_list(n)
	 * 
	 * Produce recommendation list di dimensione N per l�utente anonimo, 
	 * analogamente al servizio per l�utente registrato con passaggio di seed
	 * 
	 * @param n NUMERO RACCOMANDAZIONI
	 * @return List<ScoredId> LISTA RACCOMANDAZIONI
	 */
	public List<ScoredId> get_recommendation_list(int n){
		return this.recommend(-1, n);
	}

}
