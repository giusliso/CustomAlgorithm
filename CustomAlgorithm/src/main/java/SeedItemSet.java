import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemDAO;
import org.grouplens.lenskit.data.dao.PrefetchingItemEventDAO;
import org.grouplens.lenskit.data.dao.SortOrder;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.source.DataSource;

public class SeedItemSet {

	public static enum Period {LAST_WEEK, LAST_MONTH, LAST_YEAR, EVER};


	private ItemEventDAO iedao;
	private ItemDAO idao;
	private EventDAO dao;

	public SeedItemSet(DataSource dataset) {
		this.iedao = dataset.getItemEventDAO();
		this.idao = dataset.getItemDAO();
		this.dao = dataset.getEventDAO();
	}

	public SeedItemSet(EventDAO dao) {
		this.iedao = new PrefetchingItemEventDAO(dao);
		this.idao = new PrefetchingItemDAO(dao);
		this.dao = dao;
	}
	
	public Set<Long> getSeedItemSet() {
		HashSet<Long> set = new HashSet<Long>();
		set.add(getMostPopularItem(Period.EVER));
		set.add(getMostPopularItem(Period.LAST_WEEK));
		set.add(getLastPositivelyRatedItem());
		set.add(getLastItemAddedNotRated());
		return set;
	}
	
	public Long getMostPopularItem(Period period) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(getLastTimestamp()); // perchè i rating nel dataset, a quanto pare, vanno dal26/04/2000 al 28/02/2003

		Date thresholdDate = null;
		switch (period) {
		case LAST_WEEK: cal.add(Calendar.DATE, -7); thresholdDate=cal.getTime(); break;
		case LAST_MONTH:cal.add(Calendar.DATE, -30); thresholdDate=cal.getTime();  break;
		case LAST_YEAR: cal.add(Calendar.DATE, -365); thresholdDate=cal.getTime(); break;
		case EVER: break;
		}

		Long idItemMostPopular = null;
		int max=0;
		for(Long itemId : idao.getItemIds()){
			List<Event> events = iedao.getEventsForItem(itemId);
			int count = 0;
			if(thresholdDate == null)
				count = events.size();
			else
			{
				thresholdDate.setHours(0);
				thresholdDate.setMinutes(0);
				thresholdDate.setSeconds(0);
				for(Event ev : events){
					Date rateDate = new Date(ev.getTimestamp()*1000);
					if(rateDate.after(thresholdDate))
						count++;
				}
			}

			if(count > max){
				idItemMostPopular=itemId;
				max=count;
			}
		}

		return idItemMostPopular;
	}

	private Date getFirstTimestamp(){

		Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);

		for(Rating rating : events)
			return new Date(rating.getTimestamp()*1000);			

		return null;
	}

	private Date getLastTimestamp(){

		Cursor<Rating> events = dao.streamEvents(Rating.class, SortOrder.TIMESTAMP);

		long lastTimestamp=0;
		for(Rating rating : events){
			lastTimestamp = rating.getTimestamp();			
		}
		return new Date(lastTimestamp*1000);
	}




	public Long getLastPositivelyRatedItem(){
		Long lastPositivelyRatedItem = null;
		Date recentDate = getFirstTimestamp(); 

		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			int threshold = getPositiveRatingThreshold(ratings);
			Date date = new Date(0, 0, 1, 0, 0); //inizializzata al 01/01/1900

			for(Rating rating : ratings){
				Date dateR = new Date(rating.getTimestamp()*1000);
				if(rating.getValue() >= threshold && dateR.after(date)){
					date = dateR;
				}
			}

			if(date.after(recentDate))
				recentDate = date;
			lastPositivelyRatedItem = itemId;
		}

		return lastPositivelyRatedItem;
	}

	private int getPositiveRatingThreshold(List<Rating> ratings){
		int threshold=0;
		for(Rating r : ratings)
			threshold += r.getValue();
		return threshold/ratings.size();
	}


	
	public Long getLastItemAddedNotRated(){
		Long lastItemAdded = null;
		Date threshold = null;
		
		for(Long itemId : idao.getItemIds()){
			List<Rating> ratings = iedao.getEventsForItem(itemId, Rating.class);
			Date firstTimestamp=new Date(ratings.get(0).getTimestamp()*1000);
			if(threshold == null){
				threshold = firstTimestamp;
				lastItemAdded=itemId;
			}
			
			for(Rating r : ratings){
				Date timestamp = new Date(r.getTimestamp()*1000);
				if(timestamp.before(firstTimestamp))
					firstTimestamp = timestamp;
			}
			
			if(firstTimestamp.after(threshold)){
				threshold=firstTimestamp;
				lastItemAdded=itemId;
			}
		}
		

		return lastItemAdded;
	}

}
