package it.maivisto.models;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Provider;

import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.knn.item.model.ItemItemBuildContext;
import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

import it.maivisto.utility.Config;
import it.maivisto.utility.STS;
import it.maivisto.utility.Serializer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import ts.evaluation.TSinstance;
import ts.evaluation.impl.VincenteTS;

/**
 * Build a item content similarity model.
 */
@NotThreadSafe
public class ItemContentMatrixModelBuilder implements Provider<ItemItemModel> {
	private static final Logger logger = LoggerFactory.getLogger(ItemContentMatrixModelBuilder.class);

	private final ItemItemBuildContext context;
	private int threadCount=0;
	private Long2ObjectMap<ScoredItemAccumulator> rows;
	@Inject
	public ItemContentMatrixModelBuilder(@Transient ItemItemBuildContext context) {
		this.context = context;
	}
	private LongSortedSet allItems;
	private HashMap<Long,String> icMap;
	private int ndone=0;
	private int nitems=0;
	private int ntreads=5;
	/**
	 * Create the item-content matrix.
	 */
	@Override
	public ItemItemModel get() {

		Serializer serializer = new Serializer();

		ItemContentMatrixModel model = (ItemContentMatrixModel) serializer.deserialize(Config.dirSerialModel, "ItemContentMatrixModel");

		if(model==null) {
			allItems = context.getItems();
			nitems = allItems.size();

			icMap = getItemsContentMap();

			logger.info("building item-content similarity model for {} items", nitems);
			logger.info("item-content similarity model is symmetric");

			rows = makeAccumulators(allItems);

			ArrayList threads=new ArrayList();
			//divido il dataset in intervalli e li asstegno ai threads
			for(int z=0;z<ntreads;z++){
				this.threadCount+=1;
				threads.add(new itemContentThread());
			}

			//assegno le partizioni ai threads
			this.definePartition(threads);

			//faccio partire i threads
			for(int z=0;z<threads.size();z++)
				((itemContentThread) threads.get(z)).start();

			Stopwatch timer = Stopwatch.createStarted();
			ndone=1;


			//finalizzazione nel caso ci sono thread ancora in esecuzione, ne attende la fine
			
			if(threadCount!=0){
				logger.debug("waiting {} threads..",threadCount);
				while(threadCount!=0){
					Thread t=new Thread();
					try {
						t.sleep(0);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

			}


			if (logger.isDebugEnabled() && ndone % 100 == 0) 
				logger.info("computed {} of {} model rows ({}s/row)", 
						ndone, nitems, 
						String.format("%.3f", timer.elapsed(TimeUnit.MILLISECONDS) * 0.001 / ndone));

		



			timer.stop();
			logger.info("built model for {} items in {}", ndone, timer);

			model = new ItemContentMatrixModel(finishRows(rows));				
			serializer.serialize(Config.dirSerialModel,model,"ItemContentMatrixModel");
		}

		return model;
	}








	private HashMap<Long,String> getItemsContentMap(){
		HashMap<Long,String> icMap = new HashMap<Long,String>();
		for(long item : context.getItems()){
			String content = "";
			try {
				content = readItemContent(item);
			}catch (IOException e) {
				e.printStackTrace();
			}
			icMap.put(item, content);
		}
		return icMap;
	}

	private String readItemContent(long item) throws IOException{
		logger.info("reading content item {}",item);
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new FileReader(new File("data/abstract/"+item)));
		String s = "";
		while((s = br.readLine()) != null)
			sb.append(s);
		br.close();
		logger.info("read content item {}",item);
		return sb.toString();
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




	//metodo che definisce gli intervalli da assegnare ai thread
	private void definePartition(ArrayList threads){


		int totElements=nitems;
		int partition=totElements/threads.size();
		int partProduct=(partition*threads.size());
		int itemRest=totElements-partProduct;

		int iLimitRow=0;
		int fLimitRow=0;
		for(int i=0;i<threads.size();i++){
			iLimitRow=partition*i;
			fLimitRow=partition*(i+1);
			if(fLimitRow==partProduct)
				fLimitRow+=itemRest;
			itemContentThread thread=(itemContentThread) threads.get(i);
			thread.setInterval(iLimitRow, fLimitRow);
			

		}
		


	}
	public void decrementThread(){

		this.threadCount--;
		logger.debug("decremented thread {}",threadCount);
	}

	private class itemContentThread  extends Thread {
		private int iLimitRow;
		private int fLimitRow;
		@Override
		public void run() {
			
			
			STS valueSim=null;
			try {
				valueSim = new STS("lib/TextualSimilarity/config/config.properties","lib/TextualSimilarity/config/stacking.xml");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			int countRow=0;
			// TODO Auto-generated method stub
			for(LongBidirectionalIterator itI = allItems.iterator(); itI.hasNext(); ) {
				Long i = itI.next();
				countRow++;
			

				//se la riga considerata è compresa nell'intervallo del thread
				if(countRow>=iLimitRow && countRow<=fLimitRow){
					if (logger.isDebugEnabled()) 
						logger.info("computing similarities for item {} ({} of {})", i, ndone, nitems);
					for(LongBidirectionalIterator itJ = allItems.iterator(i); itJ.hasNext(); ) {
						Long j = itJ.next();

						/*String contentI = icMap.get(i);
						String contentJ = icMap.get(j);

						//					double simIJ = valueSim.computeSimilarity(contentI, contentJ).getValue("stacking"); 
						double simIJ = 0;
						try {
							simIJ = valueSim.computeSimilarities(contentI, contentJ).getFeatureSet().getValue("dsmCompSUM-ri");
							rows.get(i).put(j, simIJ);
							rows.get(j).put(i, simIJ); 
							
							logger.info("computed content similarity sim({},{}) = sim({},{}) = {}", i, j, j, i, simIJ);

						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}*/
						System.out.print(" "+i+":"+j);
						

					}
					ndone++;

				}
				
			}
			decrementThread();
		}

		//setta il numero di righe che il thread dovrà computare
		public void setInterval(int iLimitRow,int fLimitRow){
			this.fLimitRow=fLimitRow;
			this.iLimitRow=iLimitRow;
		}

	}

}
