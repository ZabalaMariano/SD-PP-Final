package TP_Final_SDyPP.DB4O;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.db4o.*;
import com.db4o.query.*;

public class Database {

	private Logger logger;
	private ObjectContainer db;
	private int id;
	private String filename = "database";
	
	public Database(int id) {
		this.id = id;
		System.setProperty("logFilename", filename);
		logger = LogManager.getLogger(Database.class);
	}
	
	public void open() {
		this.db = Db4oEmbedded.openFile(Db4oEmbedded.newConfiguration(),"database"+ this.id +".db4o");
	}

	public void insertarFile(String name, String hash, long size) {
		FileTable file = new FileTable(name, hash, size);
		
		this.open();
		this.db.store(file);
		this.db.commit();
		this.db.close();
		logger.info("Nuevo archivo: "+name);
	}

	public void insertarSeed(String hash, boolean isSeed, String pathArchivo, String ipPeer, int portPeer) {
		SeedTable seed = new SeedTable(hash, isSeed, pathArchivo, ipPeer, portPeer);
		
		this.open();
		this.db.store(seed);
		this.db.commit();
		this.db.close();	
		if(isSeed)
			logger.info("Nuevo Seed: ("+ipPeer+":"+portPeer+")");
		else
			logger.info("Nuevo Leecher: ("+ipPeer+":"+portPeer+")");
	}

	public void eliminarSeed(String hash, String ip, int port) {
		this.open();
		SeedTable st = new SeedTable(hash, ip, port);
		ObjectSet resultado = db.queryByExample(st);
        st = (SeedTable) resultado.next();
        db.delete(st);
		this.db.close();
		logger.info("Seed eliminado: ("+ip+":"+port+")");
	}
	
	public ArrayList<FileTable> getFilesByName(String nombreBuscado) {
		
		this.open();
		Query query = this.db.query();
        query.constrain(FileTable.class);
        query.descend("name").constrain(nombreBuscado).like();
        ObjectSet<FileTable> files = query.execute();
		
		ArrayList<FileTable> array = new ArrayList<FileTable>();
		
		for(FileTable f : files) {
			array.add((FileTable) f);
		}
		
		this.db.close();
		logger.info("Consulta archivos por nombre");
		return array;
	}
	
	public ArrayList<String> getHashes() {
		
		this.open();
		Query query = this.db.query();
        query.constrain(FileTable.class);
        ObjectSet<FileTable> files = query.execute();
		ArrayList<String> array = new ArrayList<String>();
		
		for(FileTable f : files) {
			array.add(f.getHash());
		}

		this.db.close();
		logger.info("Consulta hashes");
		return array;
	}
	
	public ArrayList<String> compareHashes(ArrayList<String> hashes) {
		
		this.open();
		Query query = this.db.query();
        query.constrain(FileTable.class);
        for (int i = 0; i < hashes.size(); i++) {  
        	query.descend("hash").constrain(hashes.get(i)).not();  
        } 
        ObjectSet<FileTable> files = query.execute();
		ArrayList<String> array = new ArrayList<String>();
		
		for(FileTable f : files) {
			array.add(f.getHash());
		}

		this.db.close();
		logger.info("Consulta comparar hashes");
		return array;
	}

	public SeedTable getSocketPeer(String hash) {
		this.open();
		SeedTable st = new SeedTable(hash);//busco solo según hash
        ObjectSet resultado = this.db.queryByExample(st);
		st = (SeedTable) resultado.next();
		this.db.close();
		logger.info("Consulta PeerSocket");
		return st;
	}

	public ArrayList<SeedTable> getSwarm(String hash, String ip, int port) {
		this.open();
		SeedTable st = new SeedTable(hash, true);//busco solo según hash y disponible=true
		ObjectSet<SeedTable> resultado = this.db.queryByExample(st);
        ArrayList<SeedTable> array = new ArrayList<SeedTable>();
       
        boolean tieneSeed = false;
        int i = 0;
        while(i<25 && i<resultado.size()) {
        	SeedTable s = (SeedTable) resultado.get(i);
        	if(!s.getIpPeer().equals(ip) || s.getPortPeer()!=port) {//Si no es el peer que pidio el swarm
        		if(s.isSeed())
            		tieneSeed = true;
            	
            	array.add(s);	
        	}
        	i++;        	
        }
        
        if(!tieneSeed) {
        	if(array.size() == 25) {//Si se lleno está la posibilidad de que haya un seed que se quedo afuera. Si no llega a 25, no hay seed. 
        		st = new SeedTable(hash, true, true);//busco solo según hash, isSeed y disponible=true
        		resultado = this.db.queryByExample(st);
        		st = (SeedTable) resultado.next();	
        		array.remove(0);
        		array.add(st);
        	}    		
        }
		
		this.db.close();
		logger.info("Consulta por Swarm");
		return array;
	}

	public void deshabilitarSeed(String ip, int port) {
		this.open();
		SeedTable st = new SeedTable(ip,port);
		ObjectSet<SeedTable> resultado = this.db.queryByExample(st);
        for(SeedTable s : resultado) {
        	s.setDisponible(false);
        	db.store(s);
        }
        db.commit();
		this.db.close();
		logger.info("Seed deshabilitado por carga máxima");
	}

	public void habilitarSeed(String ip, int port) {
		this.open();
		SeedTable st = new SeedTable(ip,port);
		ObjectSet<SeedTable> resultado = this.db.queryByExample(st);
        for(SeedTable s : resultado) {
        	s.setDisponible(true);
        	db.store(s);
        }
        db.commit();
		this.db.close();
		logger.info("Seed habilitado por carga normal");
	}

	public void nuevoSeed(String ip, int port, String hash) {
		this.open();
		SeedTable st = new SeedTable(hash,ip,port);
		ObjectSet<SeedTable> resultado = this.db.queryByExample(st);
        for(SeedTable s : resultado) {
        	s.setSeed(true);
        	db.store(s);
        }
        db.commit();
		this.db.close();
		logger.info("Nuevo Seed: ("+ip+":"+port+")");
	}

	public ArrayList<FileTable> getArchivosOfrecidos(String ip, int port) {
		this.open();
		//Obtengo files ofrecidos por mí en la tabla seed_table
		SeedTable st = new SeedTable(ip,port);
		ObjectSet<SeedTable> resultado = this.db.queryByExample(st);
		//Obtengo nombre de esos files de la tabla file_table
		ArrayList<FileTable> archivos = new ArrayList<FileTable>();
		for(SeedTable s : resultado) {
			FileTable ft = new FileTable(s.getHash());
			ObjectSet<FileTable> file = this.db.queryByExample(ft);
			archivos.add(file.next());
		}
		
		this.db.close();
		logger.info("Consulta archivos ofrecidos por: ("+ip+":"+port+")");
		return archivos;
	}

}
