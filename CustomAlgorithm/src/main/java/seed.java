

import java.io.File;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.SimpleFileRatingDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.source.DataSource;
import org.grouplens.lenskit.data.source.GenericDataSource;

public class seed {
	private HashMap selection = new HashMap<Long,Long>();

	seed() {


		// accedo al dataset
		EventDAO dao = new SimpleFileRatingDAO(new File(
				"/Users/LucaNardulli/Desktop/ml-1m/ratings.dat"), "::");
		LenskitConfiguration config = new LenskitConfiguration();
		config.bind(EventDAO.class).to(dao);
		DataSource testDataSource = new GenericDataSource("testdataset", dao);
		testDataSource.getItemDAO();
		ItemEventDAO iedao = testDataSource.getItemEventDAO();
		Long idItemMostPopular = null;
		int max = 0;
		Long userId = null;
		Long event = null;


		for (Long itemId : testDataSource.getItemDAO().getItemIds()) {

			getfirstvote(iedao.getEventsForItem(itemId).listIterator());

		}
		getRecentFirstVote();

	}


	//metodo che seleziona tra i primi voti il piu' recente
	private void getRecentFirstVote() {
		ArrayList max=new ArrayList<Long>();
		// TODO Auto-generated method stub
		Iterator it = selection.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry)it.next();
			max.add(pair.getValue());

		}
		Long maxTs = (Long) Collections.max(max);
		Date date = new Date(maxTs*1000);
		
		it = selection.entrySet().iterator();
		while (it.hasNext()) {

			Map.Entry pair = (Map.Entry)it.next();
			if(pair.getValue()==maxTs){


				System.out.println("i:"+pair.getKey()+" non votato se oggi fosse "+dataDecrement((Long) pair.getValue()));
			}


		}

	}


	//metodo che data una data la decrementa
	private Date dataDecrement(Long ts) {
		final Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(ts*1000);
		cal.add(Calendar.DATE, -1);
		return cal.getTime();

	}

	//metodo che seleziona il piu' vecchio voto di un item e lo aggiugne a selection
	private void getfirstvote(ListIterator<Event> listIterator) {
		ListIterator<Event> itr = listIterator;
		Rating rate = null;
		Long Item=null;
		ArrayList min=new ArrayList<Long>();
		int count = 0;

		while (listIterator.hasNext()) {
			rate=(Rating) listIterator.next();
			Item=rate.getItemId();
			min.add(rate.getTimestamp());
		}
		Long minTs = (Long) Collections.min(min);
		selection.put(Item,minTs);





	}

}
