package it.maivisto.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.grouplens.lenskit.knn.item.model.ItemItemModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerializeModel {

	private static final Logger logger = LoggerFactory.getLogger(SerializeModel.class);
	
	/*nb.
	 * in seguito implementare un metodo che controlla se il dataset � uguale a quello che abbiamo 
	 * utilizzato per costruire i modelli memorizzati nella cartella
	*/
	public void serializeModel(ItemItemModel m,String matrixModel){
		logger.info("Serializing "+matrixModel+"...");
		try {
			File dir = new File("data/modelMatrix/");
			dir.mkdir();
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dir+"/"+matrixModel+".dat"));
			out.writeObject(m);
			out.close();
			logger.info("Serialized "+matrixModel);
		} catch (IOException e) {
			logger.error(e.getStackTrace().toString());
		}
	}
	public ItemItemModel deSerializeModel(String matrixModel){
		logger.info("Deserializing "+matrixModel+"...");
		try {
			ObjectInputStream in= new ObjectInputStream(new FileInputStream("data/modelMatrix/"+matrixModel+".dat"));
			ItemItemModel m=(ItemItemModel)in.readObject();
			in.close();
			logger.info("Deserialized "+matrixModel);
			return m;
		} catch (FileNotFoundException e) {
			logger.error(e.getStackTrace().toString());
		} catch (IOException e) {
			logger.error(e.getStackTrace().toString());
		} catch (ClassNotFoundException e) {
			logger.error(e.getStackTrace().toString());
		}

		return null;
	}
}
