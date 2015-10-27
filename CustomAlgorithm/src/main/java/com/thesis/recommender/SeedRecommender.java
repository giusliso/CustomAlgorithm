package com.thesis.recommender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import org.grouplens.lenskit.ItemScorer;
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

import com.thesis.models.CoOccurrenceMatrixModelBuilder;
import com.thesis.qualifiers.CoOccurrenceModel;
import com.thesis.qualifiers.CosineSimilarityModel;
import com.thesis.recommender.RecommendationTriple;

import it.unimi.dsi.fastutil.longs.Long2DoubleArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * @author Peppe
 *
 */
public class SeedRecommender extends AbstractItemRecommender {
	private static final Logger logger = LoggerFactory.getLogger(SeedRecommender.class);
	private EventDAO dao;
	private UserEventDAO uedao;
	private ItemScorer scorer;
	private boolean activate_standard_seed;
	private LinkedList<ItemItemModel> models;
	private ArrayList seed_itemset;

	@Inject
	public SeedRecommender(EventDAO dao, UserEventDAO uedao, ItemScorer scorer, @CoOccurrenceModel ItemItemModel coOccModel, @CosineSimilarityModel ItemItemModel cosModel) {
		this.uedao = uedao;
		this.dao = dao;
		this.scorer = scorer;
		this.models= new LinkedList();
		models.add(coOccModel);
		models.add(cosModel);



	}

	@Override
	protected List<ScoredId> recommend(long user, int n, LongSet candidates, LongSet excludes) {




		ScoredItemAccumulator recommendations = new TopNScoredItemAccumulator(n);

		Long2DoubleArrayMap seedMap = new Long2DoubleArrayMap();
		UserHistory<Rating> userHistory = uedao.getEventsForUser(user, Rating.class);
		Set<Long> seeds = new HashSet<Long>();

		//situazione cold start
		if(userHistory == null || userHistory.size() < 20) {


			if(this.activate_standard_seed){
				SeedItemSet set = new SeedItemSet(dao);
				seeds.addAll(set.getSeedItemSet());

				logger.debug("aggiungo standard seed");
			}

			//aggiungo i seeds esterni se ci sono 
			if(this.seed_itemset!=null){
				logger.debug("aggiungo {} item di seed_itemset", this.seed_itemset.size());

				seeds.addAll(this.seed_itemset);
			}



			for (long seed : seeds) {
				double score = scorer.score(user, seed);
				seedMap.put(seed, score);
				recommendations.put(seed, score);
			}

			//prendo i positivi e gli aggiungo a seedmap e seeds
			if(userHistory!=null){
				Iterator it = userHistory.iterator();

				logger.debug("valore medio voti per user {}: {}", user, this.meanValue(user));
				while (it.hasNext()) {
					Rating rate = (Rating)it.next();
					if(rate.getValue()>=this.meanValue(user)){
						seedMap.put(rate.getItemId(),rate.getValue());	
						seeds.add(rate.getItemId());
					}
				}
			}


			/*if(positiveRate.size()==0){
				this.get_recommendation_list(n, seed_itemset, activate_standard_seed);
			}

			else{

				/*UserMeanItemScorer(S)		stima lo score di ogni seed in R
													sulla base della media dei rating
												in POS 


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


		int modelId=1; //MODELLO PROVVISORIO

		ArrayList reclist=new ArrayList<RecommendationTriple>();
		Set<Long> tempRec=new HashSet();
		int k=0;
		while(tempRec.size()<20){
			for (Long s : seeds) {
				for(ItemItemModel model : models){
					SparseVector neighbors = model.getNeighbors(s);
					if (!neighbors.isEmpty()){
						LongArrayList neighs=neighbors.keysByValue(true);
						Long i=neighs.get(k);
						if (!seeds.contains(i) ) {
							double simISnorm = normalize(-1, 1, neighbors.get(i), 0, 1);
							double scoreSeed = seedMap.get(s);
							recommendations.put(i, simISnorm*scoreSeed);
							reclist.add(new RecommendationTriple(i,simISnorm*scoreSeed,modelId));
							tempRec.add(i);
							break;
						}
					}
				}
			}

			k++;
		}

		//STAMPA ITEM IN RECCOMMENDATION TRIPLE
		logger.debug("RECOMMENDATION TRIPLE per re-rank(R,N)");
		Iterator it = reclist.iterator();
		while(it.hasNext()){
			RecommendationTriple r=(RecommendationTriple) it.next();
			logger.debug("itemID:{} score:{} matID:{}",r.getItemID(),r.getScore(),r.getMatID());
		}

		//re-rank(R,N) // effettua un re-ranking di R, prendendo i top-N

		return recommendations.finish();
	}


	private double normalize(double oldMin, double oldMax, double oldVal, double newMin, double newMax) {
		double scale = (newMax-newMin)/(oldMax-oldMin);
		return newMin + ( (oldVal-oldMin) * scale );
	}


	/**
	 * Metodo che restituisce il voto medio per un utente l
	 * 
	 * @param utente
	 * @return voto medio utente
	 */
	private double meanValue(long l){

		UserHistory<Event> userHistory = uedao.getEventsForUser(l);
		Iterator it = userHistory.iterator();
		Double sum=0.0;
		while (it.hasNext()) {
			Rating rate = (Rating)it.next();
			sum=sum+rate.getValue();

		}
		return sum/userHistory.size();
	}



	/**
	 * List<ScoredId> get_recommendation_list(userID,N,seed_itemset,activate_standard_seed)
	 * produce recommendation list di dimensione N per l’utente registrato userID, avendo in input anche 
	 * una lista di item da utilizzare come seed esterni, ovvero come item iniziali che innescano l’algoritmo. 
	 * Il parametro activate_standard_seed è un boolean che regola l’uso dei 4 seed standard in aggiunta a 
	 * quelli esterni, passati come parametro. Si può invocare quando ad esempio un utente ha effettuato 
	 * una ricerca, passando come seed_itemset tutto il result set oppure l’item del result set su cui userID 
	 * ha cliccato.
	 * 		
	 * @param user utente a cui si vogliono fornire raccomandazioni
	 * @param n numero di raccomandazioni
	 * @param seed_itemset insieme di seed ulteriori
	 * @param activate_standard_seed attiva seed standard
	 * @return Lista di n raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(long user,int n,ArrayList seed_itemset,boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;

		return this.recommend(user, n);

	}


	/**
	 * List<ScoredId> get_recommendation_list(userID,N,activate_standard_seed)
	 * Produce recommendation list di dimensione N per l’utente registrato userID, in assenza di seed esterni. Si può invocare quando userID entra nella piattaforma e non ha effettuato alcuna azione. La recommendation list che si ottiene consente di avere i primi suggerimenti per userID  
	 * @param user utente a cui si vogliono fornire raccomandazioni
	 * @param n  numero di raccomandazioni
	 * @param activate_standard_seed attiva seed standard
	 * @return lista di raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(long user,int n,boolean activate_standard_seed){

		this.activate_standard_seed=activate_standard_seed;

		return this.recommend(user, n);

	}


	/**
	 * List<ScoredId> get_recommendation_list(userID,N,activate_standard_seed)
	 * 
	 * Produce recommendation list di dimensione N per l’utente registrato userID, in assenza di seed esterni. 
	 * Si può invocare quando userID entra nella piattaforma e non ha effettuato alcuna azione.
	 * La recommendation list che si ottiene consente di avere i primi suggerimenti per userID 
	 *  
	 * @param n: raccomandazioni
	 * @param seed_itemset: insieme di seed aggiuntivi
	 * @param activate_standard_seed: attivare seed standard
	 * @return lista di raccomandazioni
	 */
	public List<ScoredId> get_recommendation_list(int n,ArrayList<Long> seed_itemset,boolean activate_standard_seed){
		this.seed_itemset=seed_itemset;
		this.activate_standard_seed=activate_standard_seed;
		long user=-1; 
		return this.recommend(user,n);

	}




	/**
	 * Servizio get_recommendation_list(n)
	 * 
	 * Produce recommendation list di dimensione N per l’utente anonimo, 
	 * analogamente al servizio per l’utente registrato con passaggio di seed
	 * 
	 * @param n NUMERO RACCOMANDAZIONI
	 * @return List<ScoredId> LISTA RACCOMANDAZIONI
	 */
	
	public List<ScoredId> get_recommendation_list(int n){
		long user=-1; 
		this.activate_standard_seed=true;
		return this.recommend(user, n);

	}


}
