package TP_Final_SDyPP.Tracker;

import java.io.File;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.DB4O.Database;
import TP_Final_SDyPP.Otros.KeysGenerator;

public class MainTracker {

	private static String pathLogs = "Logs";
	private static String pathLogsAbs;
	
	public static void main(String[] args) throws Exception 
	{
		Scanner scanner = new Scanner(System.in);
		
		//Path donde guardar JSON creados y descargados
    	System.out.print("Ingrese el path donde se guardarán los META-Archivos publicados por peers: ");
		
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
	    			System.err.println("El path es inválido, debe elegir una carpeta. Pruebe nuevamente:");
	    	}catch(NumberFormatException  e){
	    		System.err.println("El path es inválido, debe elegir una carpeta. Pruebe nuevamente:");
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
	    		if(idInt>=0 && idInt<=2)
	    			idCorrecto = true;
	    		else
	    			System.out.println("El ID del Tracker debe ser un numero positivo entre 0 y 1");
	    	}catch(NumberFormatException  e){
	    		System.out.println("El ID del Tracker debe ser un numero positivo entre 0 y 1");
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
    	logger.info("Path donde se guardarán los META-Archivos: "+path);
		
		Tracker mp = new Tracker(idInt, path, kg.publicKey, kg.privateKey, logger);
	}
	
	private static void crearCarpetaLogs() {
		File f = new File(pathLogs);
		if(!f.exists())
			new File(pathLogs).mkdirs();
		pathLogsAbs = f.getAbsolutePath();
	}

}
