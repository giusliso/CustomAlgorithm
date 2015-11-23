package com.thesis.evaluation;
import java.io.File;
import java.io.IOException;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.data.source.CSVDataSourceBuilder;
import org.grouplens.lenskit.data.source.DataSource;
import org.grouplens.lenskit.eval.AbstractTask;
import org.grouplens.lenskit.eval.TaskExecutionException;
import org.grouplens.lenskit.util.table.writer.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class ReduceDataset extends AbstractTask<DataSource> {
	private static final Logger logger = LoggerFactory.getLogger(ReduceDataset.class);
	
	private int minSize=0;
	private DataSource source;


	@Override
	protected DataSource perform() throws TaskExecutionException, InterruptedException {
		try { return reduce(); }
		catch (IOException e) {	throw new TaskExecutionException(e); }
	}


	@SuppressWarnings("unchecked")
	private DataSource reduce() throws IOException {
		logger.info("Reducing dataset {} considering users who rated at least {} items", source.getName(), minSize);

		String filePath = source.getName().substring(0,source.getName().lastIndexOf(File.separator)+1);
		String fileName = Files.getNameWithoutExtension(source.getName())+"_"+minSize+"."+Files.getFileExtension(source.getName());
		File output = new File(filePath+fileName);
		
		CSVWriter csv = null;
		try {
			csv = CSVWriter.open(output, null);
			Cursor<UserHistory<Rating>> histories = source.getUserEventDAO().streamEventsByUser(Rating.class);

			for (UserHistory<Rating> ratings : histories)
				if (ratings.size() >= minSize)
					for(Rating rating : ratings)
						csv.writeRow(Lists.newArrayList(rating.getUserId(), rating.getItemId(), rating.getValue(), rating.getTimestamp()));
		} 
		finally {
			if (csv != null) csv.close();
		}

		logger.info("Reduced dataset {}", source.getName());

		CSVDataSourceBuilder builder = new CSVDataSourceBuilder(source.getName());
		builder.setDomain(source.getPreferenceDomain());
		builder.setFile(output);
		return builder.build();
	}


	public ReduceDataset setSource(DataSource source) {
		this.source = source;
		return this;
	}

	public ReduceDataset setMinSize(int minSize) {
		this.minSize = minSize;
		return this;
	}

}
