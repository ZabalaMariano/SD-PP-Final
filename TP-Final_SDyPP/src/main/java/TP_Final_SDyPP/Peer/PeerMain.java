package TP_Final_SDyPP.Peer;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Tracker.Tracker;
import TP_Final_SDyPP.UPnP.UPnP;
import TP_Final_SDyPP.UPnP.UPnPAdmin;


public class PeerMain {

	public static void main(String[] args) throws Exception {
//		UPnP.closePortTCP(2000);//BORRAR
//		UPnP.closePortTCP(2001);//BORRAR
		UPnP.closePortTCP(2002);//BORRAR
		Scanner scanner = new Scanner(System.in);
		
		//Port UPnP
		UPnPAdmin admin = new UPnPAdmin();
    	System.out.print("Ingrese el puerto donde recibirá conexiones de otros peer: ");
		
		String puerto = "";
		int puertoEscucha = -1;
    	boolean puertoCorrecto = false;
    	boolean exitoUPnP = false;
    	
    	do {
	    	try {
	    		puerto = scanner.nextLine();
	    		puertoEscucha = Integer.parseInt(puerto);
	    		if(puertoEscucha>1024 && puertoEscucha<65535) {
	    			puertoCorrecto = true;
	    			exitoUPnP = admin.setPortForwarding(puertoEscucha);
	    		}else
	    			System.out.print("El puerto del peer debe ser un numero positivo entre 1024 y 65535. Intente de nuevo:");
	    	}catch(NumberFormatException  e){
	    		System.out.print("El puerto del peer debe ser un numero positivo entre 1024 y 65535. Intente de nuevo:");
	    	}
    	}while(!puertoCorrecto || !exitoUPnP);
		
    	String portRouter = admin.getPortRouter(puertoEscucha);
    	int portExterno = Integer.parseInt(portRouter);
    	String ipExterna = admin.getIPRouter();
    	System.out.println("Los demás peers se conectarán a vos utilizando el siguiente socket: (" + ipExterna + ":" + portExterno + ")\n");
    	
		//Path donde guardar JSON creados y descargados
    	System.out.print("Ingrese el path donde se guardarán los archivos con META-DATA creados y descargados: ");
		
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
	    		System.err.println("El path es inválido. Pruebe nuevamente:");
	    	}
    	}while(!pathCorrecto);
    	
    	//Generar clave pública y privada
    	KeysGenerator ppg = new KeysGenerator();
    	ppg.generarLlavesAsimetricas();
    	
    	//logger
    	Logger logger;
    	String filename = "peer-"+ipExterna+"_"+portRouter;
		System.setProperty("logFilename", filename);
		logger = LogManager.getLogger(Cliente.class);
		logger.info("Cliente "+ipExterna+":"+portExterno+" iniciado");
		
    	//Creacion peer cliente y servidor
		Servidor s = new Servidor(puertoEscucha,portExterno,ipExterna,ppg.publicKey,ppg.privateKey,logger);
		Thread tServidor = new Thread (s);
		tServidor.start();
		
		//Peer cliente		
		Cliente c = new Cliente(path,puertoEscucha,portExterno,ipExterna,admin,s,logger);		
		Thread tCliente = new Thread (c);
		tCliente.start();	
	}
}
