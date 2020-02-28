package TP_Final_SDyPP.Peer;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Scanner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.Observable.Observable;
import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Tracker.Tracker;
import TP_Final_SDyPP.UPnP.UPnP;
import TP_Final_SDyPP.UPnP.UPnPAdmin;

public class PeerMain implements Observable {

	private ArrayList<Observer> observadores = new ArrayList<Observer>();
	private Cliente cliente;
	private Servidor servidor;
	private String error;
	private String pathLogs = "Logs";//Con Logs de peer (cliente y servidor) y tracker
	
	private final static PeerMain instance = new PeerMain();
    public static PeerMain getInstance() {
        return instance;
    }
	
	public Cliente getCliente() {
		return this.cliente;
	}

	public Servidor getServidor() {
		return this.servidor;
	}
	
	public String getMensaje() {
		return this.error;
	}
	
	public void peerMain(String port, String pathJSONs) throws Exception {
		error = "ok";//Devuelvo error
		
		//Port UPnP
		UPnPAdmin admin = new UPnPAdmin();
    			
    	int puertoEscucha = 0;
		String portRouter = null;
		int portExterno = 0;
		String ipExterna = null;
		
		try {
    		puertoEscucha = Integer.parseInt(port);
        	if(puertoEscucha>1024 && puertoEscucha<65535) {
        		if(puertoEscucha>=2000 && puertoEscucha<3000) {//Puerto entre 2000 y 2999, pruebas locales (LAN)
    		    	portExterno = puertoEscucha;
    		    	
    		    	try(final DatagramSocket socket = new DatagramSocket()){
    		    		  socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
    		    		  ipExterna = socket.getLocalAddress().getHostAddress();
    		    	}      			
        		} else {//Prueba WAN, con UPnP.
	    			error = admin.setPortForwarding(puertoEscucha);//Si no hay error devuelve "".
	    			if(error == "ok") {
	    				portRouter = admin.getPortRouter(puertoEscucha);
	    		    	portExterno = Integer.parseInt(portRouter);
	    		    	ipExterna = admin.getIPRouter();
	    			}
        		}
    		} else
    			error = "-El puerto del peer debe ser un numero positivo entre 1024 y 65535.";
    	}catch(NumberFormatException  e){
    		error = "-El puerto del peer debe ser un numero positivo entre 1024 y 65535.";
    	}
		
		//Path donde guardar JSON creados y descargados
    	File sharedPath;
    	
    	try {
    		sharedPath = new File(pathJSONs);
    		if(!sharedPath.isDirectory()) {
    			if(error=="ok")
    				error = "-El path es invalido, debe elegir una carpeta.";
    			else
    				error += "\n-El path es invalido, debe elegir una carpeta.";
    		}
    			
    	}catch(NumberFormatException  e){
    		if(error=="ok")
				error = "-El path es invalido, debe elegir una carpeta.";
			else
				error += "\n-El path es invalido, debe elegir una carpeta.";
    	}
    	
    	if(error == "ok") {    		
        	//Generar clave pública y privada
        	KeysGenerator ppg = new KeysGenerator();
        	ppg.generarLlavesAsimetricas();
        	
        	//Ubicación logs
        	this.crearCarpetaLogs();
    		if(System.getProperty("APP_LOG_ROOT") == null) {
    			System.setProperty("APP_LOG_ROOT", pathLogs);	
    		}
    		//Nombre archivo log servidor
        	String filename = "peerServidor-"+ipExterna+"_"+portExterno;
    		System.setProperty("logServidor", filename);
    		//Nombre archivo log cliente
        	filename = "peerCliente-"+ipExterna+"_"+portExterno;
    		System.setProperty("logCliente", filename);
    		//Get loggers
    		Logger loggerCliente = LogManager.getLogger(Cliente.class.getName());
    		Logger loggerServidor = LogManager.getLogger(Servidor.class.getName());
        	
    		//Creacion peer servidor
    		this.servidor = new Servidor(puertoEscucha,portExterno,ipExterna,ppg.publicKey,ppg.privateKey,loggerServidor,admin);
    		Thread tServidor = new Thread (this.servidor);
    		tServidor.start();
    		
    		//Creacion peer cliente		
    		this.cliente = new Cliente(pathJSONs,portExterno,ipExterna,ppg.publicKey,ppg.privateKey,loggerCliente);
    		this.notifyObserver(1, null);//set cliente a PeerController
    	} else {
    		this.notifyObserver(2, null);//mensaje de error
    	}
    	
	}
	
	private void crearCarpetaLogs() {
		File f = new File(this.pathLogs);
		if(!f.exists())
			new File(this.pathLogs).mkdirs();
	}

	//Metodos Observable
	@Override
	public void addObserver(Object o) {
		if (o instanceof Observer)
		{
			Observer ob = (Observer) o;
			observadores.add(ob);
		}
	}

	@Override
	public void notifyObserver(int op, String log) {
		for (Observer o: observadores)
		{
			o.update(this, op, log);
		}
	}
	
	public void salir() throws IOException {
		this.servidor.setStop(true);
	}
}
