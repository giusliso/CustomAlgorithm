package it.maivisto.evaluation;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.grouplens.lenskit.cursors.Cursors;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.grouplens.lenskit.eval.data.crossfold.CrossfoldTask;
import org.grouplens.lenskit.eval.data.crossfold.Holdout;
import org.grouplens.lenskit.util.table.writer.CSVWriter;
import org.grouplens.lenskit.util.table.writer.TableWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Closer;

import it.unimi.dsi.fastutil.longs.Long2IntMap;

public class CrossfoldColdUserTask extends CrossfoldTask {
	private static final Logger logger = LoggerFactory.getLogger(CrossfoldColdUserTask.class);

	private int coldPercent=30;

	/**
	 * Write train-test split files
	 * @throws  java.io.IOException if there is an error writing the files.
	 */
	@Override
	protected void createTTFiles() throws IOException {
		int partitionCount = getPartitionCount();
		File[] trainFiles = getFiles(getTrainPattern());
		File[] testFiles = getFiles(getTestPattern());
		TableWriter[] trainWriters = new TableWriter[partitionCount];
		TableWriter[] testWriters = new TableWriter[partitionCount];
		Closer closer = Closer.create();
		try {
			for (int i = 0; i < partitionCount; i++) {
				File train = trainFiles[i];
				File test = testFiles[i];
				trainWriters[i] = closer.register(CSVWriter.open(train, null));
				testWriters[i] = closer.register(CSVWriter.open(test, null));
			}

			writeTTFiles(trainWriters, testWriters);

		} catch (Throwable th) {
			throw closer.rethrow(th);
		} finally {
			closer.close();
		}
	}

	/**
	 * Write the split files by Users from the DAO using specified holdout method.
	 * The "coldPercent"% (default 30%) of the test users has a number of training ratings < 20 (cold start situation).
	 * @param trainWriters The tableWriter that write train files
	 * @param testWriters The tableWriter that writ test files
	 * @throws org.grouplens.lenskit.eval.TaskExecutionException
	 */
	private void writeTTFiles(TableWriter[] trainWriters, TableWriter[] testWriters) throws TaskExecutionException  {

		logger.info("splitting data source {} to {} partitions by users", getName(), getPartitionCount());

		Long2IntMap splits = splitUsers(getSource().getUserDAO()); 

		Holdout mode = this.getHoldout();
		try {
			int testUsers = getSource().getUserDAO().getUserIds().size()/getPartitionCount();
			int csUsers = testUsers*coldPercent/100;
			logger.info("cold start test users in each partition: {}%", coldPercent);

			// map whose keys are couple < partition, cold start users in it >
			HashMap<Integer,Integer> csUsersMap = new HashMap<Integer,Integer>(); 
			for(int p=0; p<getPartitionCount(); p++)
				csUsersMap.put(p,0);

			ArrayList<UserHistory<Rating>> histories = Cursors.makeList(getSource().getUserEventDAO().streamEventsByUser(Rating.class));
			Collections.shuffle(histories);

			for (UserHistory<Rating> history : histories) {
				int foldNum = splits.get(history.getUserId());

				csUsersMap.put(foldNum, csUsersMap.get(foldNum)+1);

				List<Rating> ratings = new ArrayList<Rating>(history);
				final int n = ratings.size();
				int p;// how many training ratings must have the current user

				if(csUsersMap.get(foldNum)<=csUsers) {
					// for users in cold start situation
					p = mode.partition(ratings, getProject().getRandom())%20;
					logger.info("Partition {} has {} cold start test user with {}/{} training ratings", foldNum, history.getUserId(), p,n);
				}
				else { // for users not in cold start situation
					p = mode.partition(ratings, getProject().getRandom());
					if(p<20)
						p=20+(n-20)/2; 
				}

				for (int f = 0; f < getPartitionCount(); f++) {
					if (f == foldNum) {
						for (int j = 0; j < p; j++)
							writeRating(trainWriters[f], ratings.get(j));

						for (int j = p; j < n; j++) 
							writeRating(testWriters[f], ratings.get(j));
					} 
					else 
						for (Rating rating : ratings) 
							writeRating(trainWriters[f], rating);
				}
			}
		} catch (IOException e) {
			throw new TaskExecutionException("Error writing to the train test files", e);
		}
	}

	/**
	 * Set how many test user must be in cold start situation.
	 * @param csPercentual cold start test user percentage 
	 */
	public void setColdStartCasesPercentual(int csPercentual) {
		this.coldPercent = csPercentual;
	}
}
