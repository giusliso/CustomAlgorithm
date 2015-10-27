package com.thesis.recommender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.baseline.UserMeanItemScorer;
import org.grouplens.lenskit.basic.AbstractItemRecommender;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
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
import it.unimi.dsi.fastutil.longs.LongSet;


public class SeedRecommender extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(SeedRecommender.class);
	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private boolean activate_standard_seed = true;
	private HashMap<Integer, ModelTriple> models;
	private Set<Long> seed_itemset;

	@Inject
	public SeedRecommender(EventDAO dao, UserEventDAO uedao, ItemScorer scorer, 
			@CoOccurrenceModel ItemItemModel coOccModel, @CosineSimilarityModel ItemItemModel cosModel) {
		this.uedao = uedao;
		this.dao = dao;
		this.scorer = scorer;
		this.models= new HashMap<Integer,ModelTriple>();
		this.models.put(models.size(), new ModelTriple(coOccModel, 1.0));
		this.models.put(models.size(), new ModelTriple(cosModel, 1.0));
	}

	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {
		LinkedList<RecommendationTriple> reclist = new LinkedList<RecommendationTriple>();
		Set<Long> recItemsSet = new HashSet<Long>();

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap();
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		Set<Long> seeds = new HashSet<Long>();


		if(userHistory == null || userHistory.size() < 20) {
			//situazione cold start

			if(this.activate_standard_seed){
				SeedItemSet set = new SeedItemSet(dao);
				seeds.addAll(set.getSeedItemSet());	

				for (long seed : seeds) {
					double score = scorer.score(user, seed);
					seedMap.put(seed, score);	
					reclist.add(new RecommendationTriple(seed, score, -1)); 
					// per i seed standard non c'è una matrice di provenienza. ----------------------------------------
					// associamo matID= -1 e diamo peso pari a 1
				}

				logger.debug("aggiungo standard seed");
			}

			//aggiungo i seeds esterni se ci sono 
			if(this.seed_itemset != null){
				logger.debug("aggiungo {} item di seed_itemset", this.seed_itemset.size());
				seeds.addAll(this.seed_itemset);
				for (long seed : seed_itemset) {
					double score = scorer.score(user, seed);
					seedMap.put(seed, score);				
				}
			}

			//prendo i positivi e gli aggiungo a seedmap e seeds
			if(userHistory != null){
				logger.debug("valore medio voti per user {}: {}", user, this.meanValue(user));
				double meanUserRating = meanValue(user);
				for(Rating rate : userHistory)
					if(rate.getValue() >= meanUserRating){
						seedMap.put(rate.getItemId(),rate.getValue());	
						seeds.add(rate.getItemId());
					}		
			}

			/*if(positiveRate.size()==0){
				this.get_recommendation_list(n, seed_itemset, activate_standard_seed);
			}
			else{

				/*UserMeanItemScorer(S)		stima lo score di ogni seed in R sulla base della media dei rating in POS 
			}*/

		}
		else{
			//da implementare
			logger.debug("user {} ha votato {} items - non in coldstart",user,userHistory.size());
			for (Rating rating : userHistory){
				seeds.add(rating.getItemId());
				seedMap.put(rating.getItemId(), rating.getValue());
			}
		}

		int k=0;
		while(recItemsSet.size() < 20) {
			for (Long s : seeds)
				for(Integer matID : models.keySet()) {
					SparseVector neighbors = models.get(matID).getModel().getNeighbors(s);
					if (!neighbors.isEmpty()){
						LongArrayList neighs = neighbors.keysByValue(true);
						Long i = neighs.get(k);
						if (!seeds.contains(i) ) {

							double simISnorm = normalize(-1, 1, neighbors.get(i), 0, 1); /// SISTEMARE...............................................
							if(models.get(matID).getModel() instanceof CoOccurrenceMatrixModel)
								simISnorm = neighbors.get(i);

							double scoreSeed = seedMap.get(s);
							reclist.add(new RecommendationTriple(i, simISnorm*scoreSeed, matID));
							recItemsSet.add(i);
						}
					}
				}
			k++;
		}

		//		//STAMPA ITEM IN RECCOMMENDATION TRIPLE
		//		logger.debug("RECOMMENDATION TRIPLE per re-rank(R,N)");
		//		Iterator it = reclist.iterator();
		//		while(it.hasNext()){
		//			RecommendationTriple r=(RecommendationTriple) it.next();
		//			logger.debug("itemID:{} score:{} matID:{}",r.getItemID(),r.getScore(),r.getMatID());
		//		}

		// effettua un re-ranking di R, prendendo i top-N
		return getRankedRecommendationsList(n, reclist);
	}


	private double normalize(double oldMin, double oldMax, double oldVal, double newMin, double newMax) {
		double scale = (newMax-newMin)/(oldMax-oldMin);
		return newMin + ( (oldVal-oldMin) * scale );
	}


	/**
	 * Metodo che restituisce il voto medio per un utente l
	 * 
	 * @param l utente
	 * @return voto medio utente
	 */
	private double meanValue(long l){
		double sum=0.0;
		UserHistory<Rating> userHistory = uedao.getEventsForUser(l, Rating.class);
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
				double wm = (mat == -1) ? 1 : models.get(mat).getWeight();
				totWm += wm;
				score += si*wm;
			}

			// sorted first by number of occurrences, then by score
			sortedList.add(new OccScoreTriple(item, unsortedMap.get(item).size(), score/totWm));	
		}

		for(OccScoreTriple item : sortedList)
			recommendations.put(item.getItemID(), item.getScore());

		return recommendations.finish();
	}

}
