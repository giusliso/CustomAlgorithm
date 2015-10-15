import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class AAA {

	public static void main(String[] args) throws IOException {
		FileReader f=new FileReader("/Users/LucaNardulli/Desktop/prova.txt");

		BufferedReader b =new BufferedReader(f);

		String s;

		HashMap<String ,LinkedList<String>> map = new HashMap<>();
		
		while(true) {
			s=b.readLine();
			if(s==null)
				break;
			
			if(s.contains("Testing LenskitAlgorithm(")){
				int iI=s.indexOf("(")+1;
				int iF=s.indexOf(")");
				String alg = s.substring(iI, iF);
				
				int giI=s.indexOf("{")+1;
				int giF=s.indexOf("}");
				String dat = s.substring(giI, giF);
				
				if(!map.containsKey(dat))
					map.put(dat, new LinkedList<String>());
				else{
					LinkedList<String> list = map.get(dat);
					list.add(alg);
					map.put(dat, list);
				}
				//System.out.println(alg);
			}
			
			
		}
		
		int totale = 0;
		
		for(String k : map.keySet()){
			int local = 0;
			System.out.println("\n\n------ "+k+"\n");
			LinkedList<String> l = map.get(k);
			HashSet<String> set = new HashSet<>();
			for(String a : l){
				
				int howmany=howMany(l, a);
				
				if(!set.contains(a)){
					System.out.println(howmany + " " + a);
					totale += howmany;
					local += howmany;
				}
				set.add(a);
				
			}
			double progresso = local*100/25;
			System.out.println("\nProgresso: "+local+"/25  ("+progresso+"%)");
		}
		

		double progresso = totale*100/475;
		System.out.println("\n\n\nProgresso: "+totale+"/475  ("+progresso+"%)\n\n");
		
		FileWriter fw = new FileWriter("/Users/LucaNardulli/Desktop/progress.txt", true);
		BufferedWriter wr = new BufferedWriter(fw);
		Date date = new Date(System.currentTimeMillis());
		String orario = new SimpleDateFormat("dd/MM HH:mm:ss").format(date);
		wr.append("\n"+orario+"\tProgresso: "+totale+"/475  ("+progresso+"%)");
		wr.close();
		fw.close();
		System.out.println("FIne");
	}

	
	public static int howMany(LinkedList<String> list, String stringa){
		int count =0;
		for(String s : list){
			if(s.equals(stringa))
				count++;
		}
		return count;
	}
}
