package TP_Final_SDyPP.Tracker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.Database;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.TrackerInfo;
import TP_Final_SDyPP.UPnP.UPnPAdmin;

public class MainTracker {

	private static String pathLogs = "Logs";
	private static String pathLogsAbs;
	private static String trackersJSON = "trackers.json";
	
	public static void main(String[] args) throws Exception 
	{		
		Scanner scanner = new Scanner(System.in);
		
		//Path donde guardar JSON creados y descargados
    	System.out.print("Ingrese el path donde se guardaran los META-Archivos publicados por peers: ");
		
		String path = "";
		File sharedPath;
    	boolean pathCorrecto = false;
    	
    	do {
	    	try {
	    		path = scanner.nextLine();
	    		sharedPath = new File(path);
	    		if(sharedPath.isDirectory())
	    			pathCorrecto = true;
	    		else
	    			System.err.println("El path es invalido, debe elegir una carpeta. Pruebe nuevamente:");
	    	}catch(NumberFormatException  e){
	    		System.err.println("El path es invalido, debe elegir una carpeta. Pruebe nuevamente:");
	    	}
    	}while(!pathCorrecto);
		
		System.out.println("\n--------------INICIAR TRACKER-------------");
		System.out.print("Ingrese el Id del tracker: ");
		
		String idTracker = "";
		int idInt = -1;
    	boolean idCorrecto = false;
    	
    	do {
	    	try {
	    		idTracker = scanner.nextLine();
	    		idInt = Integer.parseInt(idTracker);
	    		
	    		if(existeID(idInt))
	    			idCorrecto = true;
	    		else
	    			System.out.println("El ID del Tracker no existe en el archivo trackers.json. Pruebe nuevamente:");
	    	}catch(NumberFormatException  e){
	    		System.out.println("El ID del Tracker no existe en el archivo trackers.json. Pruebe nuevamente:");
	    	}
    	}while(!idCorrecto);
    	
    	//Generar clave pública y privada
    	KeysGenerator kg = new KeysGenerator();
    	kg.generarLlavesAsimetricas();
    	
    	//logger
    	crearCarpetaLogs();
    	Logger logger;
    	String filename = "trackerNro"+idTracker;
		System.setProperty("logTracker", filename);
		if(System.getProperty("APP_LOG_ROOT") == null) {
			System.setProperty("APP_LOG_ROOT", pathLogsAbs);	
		}
		logger = LogManager.getLogger(Tracker.class.getName());
		logger.info("Tracker "+idTracker+" iniciado");
    	logger.info("Path donde se guardaran los META-Archivos: "+path);
		
		Tracker mp = new Tracker(idInt, path, kg.publicKey, kg.privateKey, logger);
	}
	
	private static boolean existeID(int idInt) throws IOException, ParseException {
		FileReader fileReader = new FileReader(trackersJSON);
		Object obj = new JSONParser().parse(fileReader);
		fileReader.close();		
		
		JSONArray ja = (JSONArray) obj;
		
		for(int i=0; i<ja.size(); i++){
			JSONObject jsonObject = (JSONObject) ja.get(i);
			int id = Integer.parseInt((String) jsonObject.get("id")); //id de tracker
			if(id == idInt)
				return true;
		}
		return false;
	}

	private static void crearCarpetaLogs() {
		File f = new File(pathLogs);
		if(!f.exists())
			new File(pathLogs).mkdirs();
		pathLogsAbs = f.getAbsolutePath();
	}

}
