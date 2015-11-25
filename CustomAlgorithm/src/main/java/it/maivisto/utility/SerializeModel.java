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

import it.maivisto.models.ItemContentMatrixModel;
import it.maivisto.models.ItemContentMatrixModelBuilder;

public class SerializeModel {

	private static final Logger logger = LoggerFactory.getLogger(SerializeModel.class);
	
	/*nb.
	 * in seguito implementare un metodo che controlla se il dataset è uguale a quello che abbiamo 
	 * utilizzato per costruire i modelli memorizzati nella cartella
	*/
	public void serializeModel(ItemItemModel m,String matrixModel){
		try {
			File dir = new File("data/modelMatrix/");
			dir.mkdir();
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(dir+"/"+matrixModel+".dat"));
			out.writeObject(m);
			out.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public ItemItemModel deSerializeModel(String matrixModel){
		try {
			ObjectInputStream in= new ObjectInputStream(new FileInputStream("data/modelMatrix/"+matrixModel+".dat"));
			ItemItemModel m=(ItemItemModel)in.readObject();
			in.close();
			return m;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block

			logger.debug("Errore lettura: {}",e.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.debug("Errore lettura: {}",e.toString());
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			logger.debug("Errore lettura: {}",e.toString());
		}

		return null;

	}
}
