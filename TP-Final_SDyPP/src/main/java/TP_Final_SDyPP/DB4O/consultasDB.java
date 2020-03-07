package TP_Final_SDyPP.DB4O;

import java.io.File;
import java.util.ArrayList;

import com.db4o.Db4oEmbedded;
import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Query;

public class consultasDB {

	public static void main(String[] args) {		
		//CONSULTA TODOS LOS SEED Y FILES guardados
		File file = new File("database0.db4o");
		String string = "database0.db4o";
		if(!file.exists()) {
			file = new File("database1.db4o");
			string = "database1.db4o";
			if(!file.exists()) {
				file = new File("database2.db4o");
				string = "database2.db4o";
			}
		}
			
		ObjectContainer o = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration(),string);
		Query query = o.query();
        query.constrain(FileTable.class);
        ObjectSet<FileTable> files = query.execute();
		
        System.out.println("FileTable rows: "+files.size());
		for(FileTable f : files) {
			System.out.println("Name: "+f.getName()+"\nHash: "+f.getHash()+"\nSize: "+f.getSize()+"\n");
		}
		
		Query query2 = o.query();
        query2.constrain(SeedTable.class);
        ObjectSet<SeedTable> seeds = query2.execute();

        System.out.println("SeedTable rows: "+seeds.size());
        for(SeedTable s : seeds) {
        	System.out.println("Hash: "+s.getHash()+"\nIs Seed: "+s.isSeed()+"\nDisponible: "+s.getDisponible()+
        			"\nPath: "+s.getPath()+"\nIP peer: "+s.getIpPeer()+"\nPort Peer: "+s.getPortPeer()+"\n");
		}
        
		o.close();       
	}

}
