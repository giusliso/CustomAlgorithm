package it.maivisto.models;

import java.io.IOException;

import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.maivisto.utility.STS;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import ts.evaluation.FeatureSet;
import ts.evaluation.impl.VincenteTS;

public class itemContentThread extends Thread {

	private static final Logger logger = LoggerFactory.getLogger(itemContentThread.class);
	private STS valueSim;
	private String contentI;
	private String contentJ;
	private long i;
	private long j;
	private Long2ObjectMap<ScoredItemAccumulator> rows;
	private ItemContentMatrixModelBuilder matContent;

	itemContentThread(STS valueSim,String contentI,String contentJ,long i,long j,
			Long2ObjectMap<ScoredItemAccumulator> rows,ItemContentMatrixModelBuilder matContent){


		this.valueSim=valueSim;
		this.contentI=contentI;
		this.contentJ=contentJ;
		this.i=i;
		this.j=j;
		this.rows=rows;
		this.matContent=matContent;

	}
	public void run() {
		// TODO Auto-generated method stub
		try {
			logger.info("Start thread for similarity: {} : {}",i,j);
			double simIJ = valueSim.computeSimilarities(contentI, contentJ).getFeatureSet().getValue("dsmCompSUM-ri");

			rows.get(i).put(j, simIJ);
			rows.get(j).put(i, simIJ);
			
			logger.info("computed content similarity sim({},{}) = sim({},{}) = {}", i, j, j, i, simIJ);
			
			matContent.decrementThread();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}


}
