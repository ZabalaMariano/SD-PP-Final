package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.FileTable;
import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Observable.Observable;
import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.DatosArchivo;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;
import TP_Final_SDyPP.Otros.TrackerManager;
import TP_Final_SDyPP.UPnP.UPnPAdmin;
import javafx.application.Platform;

public class Cliente implements Observable{//, Runnable {
	
	private ArrayList<Observer> observadores = new ArrayList<Observer>();
	
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
		synchronized(this) {
			for (Observer o: observadores)
			{
				o.update(this, op, log);
			}
		}
	}
	
	//Atributos//
	private ConexionTCP conexionTCP;
	private int portExterno;
	private String ipExterna;
	private String pathJSONs;
	private ArrayList<TrackerInfo> listaTrackers;
	private ArrayList<ThreadCliente> listaThreadsClientes;
	private ArrayList<DescargaPendiente> listaDescargasPendientes;
	private int nroThreads = 3;
	private int leechersDisponibles;
	private String trackersJSON = "trackers.json";
	private PublicKey kPub;
	private PrivateKey kPriv;
	private KeysGenerator kg;
	private TrackerManager tm;
	private String log;
	private ArrayList<DatosArchivo> metaArchivosEncontrados;
	private String pathDescargasPendientes = "Descargas pendientes";//Para reanudar descargas
	private String pathGraficos = "Graficos";//Con información para gráficos
	private final Object lockLeechersDisponibles = new Object();
	public Logger logger;
	
	public Object getLockLeechersDisponibles() {
		return this.lockLeechersDisponibles;
	}
	
	public String getPathDescargasPendientes() {
		return this.pathDescargasPendientes;
	}
	
	public PrivateKey getKpriv() {
		return this.kPriv;
	}
	
	public PublicKey getKpub() {
		return this.kPub;
	}
	
	public KeysGenerator getKG() {
		return kg;
	}
	
	public int getNroThreads() {
		return this.nroThreads;
	}
	
	public void setNroThreads(int nroThreads) {
		this.nroThreads = nroThreads;
		this.leechersDisponibles = nroThreads;
	}
	
	public int getLeechersDisponibles() {
		return this.leechersDisponibles;
	}
	
	public void setLeechersDisponibles(int leechersDisponibles) {
		this.leechersDisponibles = leechersDisponibles;
	}
	
	public void reducirLeechersDisponibles() {
		this.leechersDisponibles--;
		System.out.println("leechers disponibles: "+leechersDisponibles);
	}
	
	public void aumentarLeechersDisponibles() {
		this.leechersDisponibles++;
		System.out.println("leechers disponibles: "+leechersDisponibles);
	}
	
	public int getPort() {
		return portExterno;
	}
	
	public String getIp() {
		return ipExterna;
	}
	
	public ArrayList<ThreadCliente> getListaThreadsClientes() {
		return listaThreadsClientes;
	}
	
	public void eliminarThreadCliente(ThreadCliente tc) {
		synchronized(this.listaThreadsClientes) {
			this.listaThreadsClientes.remove(tc);
		}
	}

	public ArrayList<DescargaPendiente> getListaDescargasPendientes() {
		synchronized(this.listaDescargasPendientes) {
			return listaDescargasPendientes;
		}
	}
	
	public void eliminarDescargaPendiente(int i) {
		synchronized(this.listaDescargasPendientes) {
			this.listaDescargasPendientes.remove(i);
		}
	}
	
	public ArrayList<DatosArchivo> getMetaArchivosEncontrados(){
		return this.metaArchivosEncontrados;
	}
	
	public String getPathGraficos() {
		return this.pathGraficos;
	}

	//Constructor//
	public Cliente(String path, int portExterno, String ipExterna, PublicKey publicKey, PrivateKey privateKey, Logger logger) throws IOException {
		this.crearCarpetaDescargasPendientes();
		this.crearCarpetaGraficos();
		
		this.setListaDescargasPendientes();
		this.listaThreadsClientes = new ArrayList<ThreadCliente>();
		this.logger = logger;
		this.pathJSONs = path;
		this.portExterno = portExterno;
		this.ipExterna = ipExterna;
		this.kPub = publicKey;
		this.kPriv = privateKey;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.leechersDisponibles = this.nroThreads;
	}
	
	private void crearCarpetaGraficos() {
		File f = new File(this.pathGraficos);
		if(!f.exists())
			new File(this.pathGraficos).mkdirs();		
	}

	private void crearCarpetaDescargasPendientes() {
		File f = new File(this.pathDescargasPendientes);
		if(!f.exists())
			new File(this.pathDescargasPendientes).mkdirs();
	}

	private void setListaDescargasPendientes() {
		this.listaDescargasPendientes = new ArrayList<DescargaPendiente>();
		File pathDescargasPendientes = new File(this.pathDescargasPendientes +"/");
		for (File file : pathDescargasPendientes.listFiles()) {
			if (file.isFile()) {
				Object obj;
				try {
					FileReader fileReader = new FileReader(this.pathDescargasPendientes+"/"+file.getName());
					obj = new JSONParser().parse(fileReader);
					fileReader.close();
					
					JSONObject jo = (JSONObject) obj; 
					String name = (String) jo.get("name");
			        String hash = (String) jo.get("hash");
			         
					DescargaPendiente dp = new DescargaPendiente(name,file.getName(),hash);
			        
					listaDescargasPendientes.add(dp);
				} catch (IOException | ParseException e) {
					e.printStackTrace();
				}
		    }
		}
	}
	
	public void pausarDescargas() {
		//pauso todas las descargas activas
		for(DescargaPendiente dp : this.listaDescargasPendientes) {
			if(dp.isActivo()) {
				String hash = dp.getHash();
				this.pausarDescarga(hash);
			}
		}
		
		logger.info("Descargas pausadas");
	}
	
	public void archivosOfrecidos() throws Exception {
		conexionTCP = null;
		conexionTCP = tm.getTracker(kg, kPub, kPriv);
		
		if(conexionTCP!=null) {
			Mensaje m = new Mensaje(Mensaje.Tipo.GET_FILES_OFFERED, this.ipExterna, this.portExterno);
			m.enviarMensaje(conexionTCP,m, kg);
			byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
			Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
	        
	        if(response.tipo == Mensaje.Tipo.GET_FILES_OFFERED) {
	        	ArrayList<FileTable> files = (ArrayList<FileTable>) response.lista;
	        	if(files.size()>0) {
	        		System.out.println("Ofrece "+files.size()+" archivo/s.");
	        		for(FileTable ti : files) {
	        			System.out.println("- "+ti.getName());
	        		}
	        	}else {
	            	log = "No esta ofreciendo ningun archivo.";
	            	this.logger.info(log);
	            	System.out.println(log);		        	
	        	}
	        }else {
	        	log = "Fallo al intentar obtener los archivos ofrecidos.";
	        	this.logger.error(log);
	        	System.out.println(log);
	        }	
		}else {
        	log = "No hay trackers disponibles en este momento.";
        	this.logger.error(log);
        	System.out.println(log);
        }
	}

	public void actualizarTrackers() throws Exception {
		this.listaTrackers = tm.readtrackerJSON(this.listaTrackers);//Lleno la listatracker antes de buscar un tracker
    	
		int i = 0;
		boolean trackerDisponible = false;
		Socket s;
		log = "tamanio lista de trackers: "+listaTrackers.size();
		this.logger.info(log);
		System.out.println(log);
		
    	while(i<listaTrackers.size() && !trackerDisponible) {
    		int port = listaTrackers.get(i).getPort();
    		String ip = listaTrackers.get(i).getIp();
    		log = "llamada a getTrackers con ip:"+ip+" y port:"+port;
    		this.logger.info(log);
    		System.out.println(log);
    		if(getTrackers(ip,port)) {//Intenta conectarse al socket del tracker y pide la lista de trackers activos
    			trackerDisponible=true;
    		}
    		i++;
    	}
    	
    	if(trackerDisponible) {
    		log = "La lista de trackers se actualizo correctamente."; 
    		this.logger.info(log);
    		System.out.println(log);
    		this.getListaTrackers();
    	}else {
    		log = "No se pudo actualizar la lista de trackers. No hay ningun tracker disponible.";
    		this.logger.error(log);
    		System.out.println(log);
    	}
	}
	
	public void getListaTrackers() throws Exception {
		tm.readtrackerJSON(this.listaTrackers);
		
		if(this.listaTrackers.size()==0) {
			log = "Lista de Trackers activos vacia";
			System.out.println(log);
		}else {
			for(int i=0; i<this.listaTrackers.size(); i++){
				if(i!=0) 
					System.out.println("");
				System.out.println("Tracker ID: "+this.listaTrackers.get(i).getId());
				System.out.println("Tracker socket: "+this.listaTrackers.get(i).getIp()+":"+this.listaTrackers.get(i).getPort());
			}
		}
	}
	
	public boolean getTrackers(String ip, int port) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible
        	ConexionTCP c = new ConexionTCP(ip,port);
        	Mensaje msj = new Mensaje(Mensaje.Tipo.GET_TRACKERS, kPub);
        	c.getOutObj().writeObject(msj);
        	Mensaje response = (Mensaje) c.getInObj().readObject();
        	
        	if (response.tipo == Mensaje.Tipo.ACK) {
        		//desencripto con la clave privada la clave simetrica
    	        byte[] msgDesencriptado = kg.desencriptarAsimetrico(response.keyEncriptada, this.getKpriv());
    	        c.setKey((SecretKey) c.convertFromBytes(msgDesencriptado));
        		
    	        //Desencripto lista de trackers con la clave simetrica
    	        byte[] trackersEnBytes = kg.desencriptarSimetrico(c.getKey(),response.datosEncriptados);
    	        this.listaTrackers = (ArrayList<TrackerInfo>) c.convertFromBytes(trackersEnBytes);
    	        
    	        //Actualizo JSON de trackers
    			tm.actualizarJSONTrackers(this.listaTrackers, this.trackersJSON, this.logger);
        		
        		return true;
        	}else {
        		log = "No recibi ack del tracker despues de get_trackers";
        		this.logger.info(log);
        		System.out.println(log);
        		c.getSocket().close();
        		return false;
        	}   
        	
        } catch (IOException | ClassNotFoundException ex) {
        	 return false;
        }
    }

	//Crear Archivo
	public void crearArchivo(String pathArchivo) throws NoSuchAlgorithmException, IOException, ParseException {
		log = "Llamada crear JSON.";
		this.logger.info(log);
		System.out.println(log);
		this.crearArchivoMeta(pathArchivo);
		
		log = "JSON creado.";
		this.logger.info(log);
		System.out.println(log);
	}

	//Subir Archivo
	public void subirArchivo(String pathJSON) throws Exception 
	{
		String hashJSON = this.hash(pathJSON);
		this.enviarArchivo(pathJSON, hashJSON);
	}

	//Crear Archivo --> Crear archivo JSON con meta data del archivo a subir
	private void crearArchivoMeta(String path) throws NoSuchAlgorithmException, IOException, ParseException 
	{
		File file = new File(path);
		String name = file.getName();
		long size = file.length(); //en Bytes
		int sizePiece = 1024 * 1024;//en Bytes = 1MB
		
		//Dividir archivo en partes, guardo el hash de cada parte en un array
		String[] piecesHashes = this.getPiecesHashes(file, size, sizePiece);
		
		//Creo JSON con meta-data
		String nameRemoveExt = name.substring(0, name.lastIndexOf('.'));
		//Creo y guardo el nuevo json, y obtengo su path
		String pathJSON = this.ConstruirArchivoMeta(name, size, sizePiece, piecesHashes, nameRemoveExt, path);
		this.ConstruirArchivoPartes(path, pathJSON);
	}

	//Crear Archivo --> CrearArchivoMeta --> generar archivo json que indica las partes que se posee del archivo
	private void ConstruirArchivoPartes(String path, String pathJSON) throws FileNotFoundException, IOException, ParseException {
     	
		FileReader fileReader = new FileReader(pathJSON);
    	Object obj = new JSONParser().parse(fileReader);
    	fileReader.close();
    	
    	JSONObject json = (JSONObject) obj;
    	String name = (String) json.get("name");
    	JSONArray hashes = (JSONArray) json.get("hashes");
    	
    	//Creo archivo que indica que partes poseo del archivo (todas al ser seed)    	
    	JSONArray partes = this.crearArchivoPartesFaltantes(json, pathJSON, hashes, true);
    	
    	try {
    		String pathDest;
    		if(path.contains("/"))
    			pathDest = path.substring(0, path.lastIndexOf('/'));
    		else
    			pathDest = path.substring(0, path.lastIndexOf('\\'));
    		File file = new File(pathDest+"/"+name+"Partes.json");
    		file.createNewFile();
			FileWriter fileW = new FileWriter(file);
			fileW.write(partes.toJSONString());
			fileW.flush();
			fileW.close();
		} catch (IOException e) {
			log = "Fallo al crear JSON de partes descargadas.";
			this.logger.error(log);
			System.out.println(log);
		}
	}

	//Crear Archivo --> CrearArchivoMeta --> generar hash de cada parte del arhivo y devolver lista para el archivo META-DATA
	private String[] getPiecesHashes(File file, long size, int sizePiece) throws NoSuchAlgorithmException, IOException 
	{
		int partes = (int) ((size / sizePiece) + 1);
		String[] piecesHashes = new String[partes];
		int index = 0;
	    byte[] buffer = new byte[sizePiece];//1 MB
	    
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream in = new BufferedInputStream(fis);
        int byteread;
         
        while ((byteread = in.read(buffer)) > 0 ) {
        	if(index+1 == partes)//Si es la ultima parte
        		buffer = Arrays.copyOfRange(buffer, 0, byteread);
        	
        	piecesHashes[index] = this.crearHash(buffer);
        	index++;
        }  	
        in.close();
        fis.close();
		return piecesHashes;
	}
	
	private String crearHash(byte[] buffer) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-1"); 
    	md.update(buffer);
    	byte[] b = md.digest();
    	StringBuffer sb = new StringBuffer();
    	
    	for(byte i : b) {
    		sb.append(Integer.toHexString(i & 0xff).toString());    		
    	}
    	
    	//devuelvo ID
    	return sb.toString();
	}
	
	//Crear Archivo --> CrearArchivoMeta --> creo JSON con nombre, tamaño, tamaño pieza, lista de hashes
	private String ConstruirArchivoMeta(String name, long size, int sizePiece, String[] piecesHashes, String nameRemoveExt, String pathArchivo) 
	{	
		JSONObject json = new JSONObject();
		json.put("name", name);
		if(pathArchivo.contains("/"))
			json.put("path", pathArchivo.substring(0, pathArchivo.lastIndexOf('/')));//Le saco el nombre. En este path esta el archivo y partes.json	
		else
			json.put("path", pathArchivo.substring(0, pathArchivo.lastIndexOf('\\')));//Le saco el nombre. En este path esta el archivo y partes.json
		json.put("fileSize", size);
		json.put("pieceSize", sizePiece);

		JSONArray array = new JSONArray();
		for(int i=0; i<piecesHashes.length; i++){
			if(i != piecesHashes.length-1) {
				JSONObject piece = new JSONObject();
				piece.put("hash", piecesHashes[i]);
				piece.put("size", sizePiece);
				array.add(piece);	
			}else {//ultima pieza
				JSONObject piece = new JSONObject();
				piece.put("hash", piecesHashes[i]);
				
				long sizeLastPiece =size % sizePiece; 
				if(sizeLastPiece == 0)
					piece.put("size", sizePiece);	
				else
					piece.put("size", sizeLastPiece);
				array.add(piece);
			}
				
		}
		
		json.put("hashes", array);

		String pathJSON = this.getNameUnico(nameRemoveExt, this.pathJSONs);
		try {
			//Escribo datos en JSON
			FileWriter fileW = new FileWriter(pathJSON);
			fileW.write(json.toJSONString());
			fileW.flush();
			fileW.close();
			return pathJSON;
		} catch (IOException e) {
			log = "Fallo creacion de JSON.";
			this.logger.error(log);
			System.out.println(log);
			return "";
		}
	}
	
	//Subir Archivo --> Hash del JSON para usarlo como ID/Nombre del mismo JSON
	public String hash(String path) throws IOException, NoSuchAlgorithmException, ParseException 
	{	
		File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream in = new BufferedInputStream(fis);
        int size = (int) file.length();
        byte[] buffer = new byte[size];
        
        in.read(buffer,0,buffer.length);
        in.close();
        fis.close();
    	return this.crearHash(buffer);
    }
	
	//Subir Archivo --> una vez creado el JSON se lo envia a uno de los tracker conocidos
	private void enviarArchivo(String pathJSON, String hashJSON) throws Exception 
	{			
		try {
			conexionTCP = null;
			conexionTCP = tm.getTracker(kg, kPub, kPriv);//Obtengo tracker activo
    		
    		if(conexionTCP!=null) {
    			Mensaje m = new Mensaje(Mensaje.Tipo.GET_PRIMARIO);
    			m.enviarMensaje(conexionTCP,m, kg);
    			byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
    			Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
        		conexionTCP.getSocket().close();
        		
        		if (response.tipo == Mensaje.Tipo.TRACKER_PRIMARIO) {
        			log = "Enviando JSON a Tracker...";
        			this.logger.info(log);
        			System.out.println(log);
        			
    	    		conexionTCP = new ConexionTCP(response.ip, response.port);//Conexion con tracker primario
    	    		
    	    		tm.getSecretKey(conexionTCP, true, this.kPub, this.kPriv, kg, this.logger);//Obtengo clave simetrica
    	    		
    	    		m = new Mensaje(Mensaje.Tipo.SEND_FILE, this.ipExterna, this.portExterno, hashJSON);//hash para identificarlo
    	    		m.enviarMensaje(conexionTCP,m, kg);//Le indico al tracker que estoy por enviar un archivo
    	    		
    	    		//Recibo ACK
			        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
			        response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
			        if(response.tipo == Mensaje.Tipo.ACK) {
	    	    		//Enviar json
	    	    		this.enviarArchivoBuffer(pathJSON);//Paso el path del JSON para que pueda encontrarlo y enviarlo
	    	    		log = "JSON enviado al Tracker.";
	    	    		this.logger.info(log);
	    	    		System.out.println(log);
			        }else {
			        	log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no respondio con un ACK. No se envio el archivo.";
	            		this.logger.error(log);
	            		System.out.println(log);
	            	}
            	}else {
            		log = "Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no pudo recuperar el tracker primario. No se envio el archivo.";
            		this.logger.error(log);
            		System.out.println(log);
            	}
        		conexionTCP.getSocket().close();
    		}else {
    			log = "No hay trackers disponibles en este momento.";
    			this.logger.error(log);
    			System.out.println(log);
    		}
		}catch(IOException ex) {
			log = "Fallo la conexion con el tracker."; 
			this.logger.error(log);
			System.out.println(log);
		}
	}
	
	//Subir Archivo --> enviarArchivo --> envio archivo JSON
	private void enviarArchivoBuffer(String pathJSON) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InterruptedException 
	{		
		try {
			Thread.sleep(100);//Necesario en pruebas locales
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

	    try {
			File archivo = new File(pathJSON);
			byte[] buffer = new byte[(int) archivo.length()];
			   
	        FileInputStream fis = new FileInputStream(archivo);
	        BufferedInputStream in = new BufferedInputStream(fis);
	        in.read(buffer,0,buffer.length);
	        
	        //encripto archivo json con la clave simetrica
			byte[] mensajeEncriptado = kg.encriptarSimetrico(conexionTCP.getKey(), buffer);
			conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexionTCP.getOutBuff().flush();
	        
	        in.close();//Cierro el buffer de lectura del JSON
	        fis.close();
	    }catch (IOException e) {
	    	log = "Fallo durante envio del JSON.";
	    	this.logger.error(log);
	    	System.out.println(log);
	    }
	}
	
	public void buscarMetaArchivo(String nombreArchivo) throws Exception {
		try {
			conexionTCP = null;
			conexionTCP = tm.getTracker(kg, kPub, kPriv);
    		
    		if(conexionTCP!=null) {
				Mensaje m = new Mensaje(Mensaje.Tipo.FIND_FILE, nombreArchivo);
				m.enviarMensaje(conexionTCP,m, kg);
				byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
				Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptados);
				conexionTCP.getSocket().close();
				
				if(response.tipo == Mensaje.Tipo.FILES_AVAILABLE) 
				{
					this.metaArchivosEncontrados = (ArrayList<DatosArchivo>) response.lista;
					this.notifyObserver(1, null);
	
				}else if(response.tipo == Mensaje.Tipo.ERROR) {
					this.logger.error(response.string);
					System.out.println(response.string);
				}
			}else {
				log = "No hay trackers disponibles en este momento.";
				this.logger.error(log);
				System.out.println(log);
	        }
		}catch(IOException ex) {
			log = "Fallo al intentar descargar META-Archivo.";
			this.logger.error(log);
			System.out.println(log);
			ex.printStackTrace();
		}	
	}
					
	public void descargarJSON(int pos) throws Exception {
		try {
			conexionTCP = null;
			conexionTCP = tm.getTracker(kg, kPub, kPriv);
    		
    		if(conexionTCP!=null) {
				Mensaje m;
				
				String hash = this.metaArchivosEncontrados.get(pos-1).getHash();
				m = new Mensaje (Mensaje.Tipo.REQUEST, hash);
				m.enviarMensaje(conexionTCP,m, kg);//Envio el hash del archivo a descargar
				String name = this.metaArchivosEncontrados.get(pos-1).getName();
				String nameSinExt = name.substring(0, name.lastIndexOf('.'));
				this.guardarArchivoBuffer(nameSinExt);
				
				log = "JSON descargado exitosamente.";
				this.logger.info(log);
				System.out.println(log);
    		}else {
				log = "No hay trackers disponibles en este momento.";
				this.logger.error(log);
				System.out.println(log);
	        }
		} catch(IOException ex) {
			log = "Fallo al intentar descargar META-Archivo.";
			this.logger.error(log);
			System.out.println(log);
			ex.printStackTrace();
		}	
	}					
	
	private boolean guardarArchivoBuffer(String name) throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ClassNotFoundException {
		int byteread;
	    int current = 0;
	
	    try {	    	
	    	//Creo archivo con nombre no utilizado
			String pathJSON = this.getNameUnico(name, this.pathJSONs);
	    	
	        File archivo = new File(pathJSON);//Al JSON le pongo como nombre su ID para evitar repetidos
	        archivo.createNewFile();//Almaceno JSON en una carpeta del cliente donde van todos los JSON
	        FileOutputStream fos = new FileOutputStream(archivo);
	        BufferedOutputStream out = new BufferedOutputStream(fos);
	        
	        Mensaje m = new Mensaje();
			byte[] datosDesencriptados = m.recibirMensaje(conexionTCP, kg);
	        out.write(datosDesencriptados, 0, datosDesencriptados.length);
	        out.flush();
	        
	        out.close();
	        fos.close();
	        conexionTCP.getSocket().close();
	        return true;
	    }catch(IOException ex) {
	    	log = "Fallo guardar JSON.";
	    	this.logger.error(log);
	    	System.out.println(log);
	    	ex.printStackTrace();
	    	return false;
	    }	
	}
	
	private String getNameUnico(String name, String ubicacion) {
		String path = "";
		boolean yaExiste = false;
		int index = 1;
		do {
			File f = null;
			if(index == 1) {
				path = ubicacion + "/" + name +".json";
				f = new File(path);
				index++;
			}else {//Ya fallo el primer nombre. Pongo un numero entre parentesis desde ahora
				path = ubicacion + "/" + name +" ("+ index +").json";
				f = new File(path);
				index++;
			}	
			yaExiste = f.exists();
		}while(yaExiste);//Mientras que el nombre exista no sale del loop
		return path;
	}

	private JSONArray crearArchivoPartesFaltantes(JSONObject json, String pathJSON, JSONArray hashes, boolean seed) throws FileNotFoundException, IOException, ParseException {
    	JSONArray partes = new JSONArray();
    	
    	for(int i=0; i<hashes.size(); i++) {
    		JSONObject hash = (JSONObject) hashes.get(i);
    		
    		String parteString = String.valueOf(i);
    		String size = String.valueOf(hash.get("size"));
    		
    		JSONObject parte = new JSONObject();
    		parte.put("parte", parteString);
    		parte.put("hash", hash.get("hash"));
    		parte.put("size", size);
    		if(seed)
    			parte.put("estado", "descargada");
    		else
    			parte.put("estado", "pendiente");
    		partes.add(parte);
    	}
    	
    	return partes;    	
	}
	
	private String crearCarpeta(String pathArchivo, String nameSinExtension) {
		int index = 1;
		String path = "";
		boolean b=false;
		do {
			if(index == 1) {
				path = pathArchivo+"/"+nameSinExtension;
				b = new File(path).mkdirs();
				index++;
			}else {//Ya fallo el primer nombre. Pongo un numero entre parentesis desde ahora
				path = pathArchivo+"/"+nameSinExtension+" ("+ index +")";
				b = new File(path).mkdirs();
				index++;
			}	
		}while(!b);
		return path;
	}
	
	//Descargar archivo
	public void descargarArchivo(String pathJSON, String pathCarpeta) {
		try {			
			FileReader fileReader = new FileReader(pathJSON);
	    	Object obj = new JSONParser().parse(fileReader);
	    	fileReader.close();
	    	
	    	JSONObject json = (JSONObject) obj;
	    	JSONArray hashes = (JSONArray) json.get("hashes");
	    	
			//Creo archivo que indica que partes faltan descargar.     	
	    	JSONArray partes = this.crearArchivoPartesFaltantes(json, pathJSON, hashes, false);
	    	
	    	//Leo hash y name del json  	
	    	String hash = (String) json.get("ID");
	    	if(hash!=null) {//Si es null esta utilizando un json invalido
	    		String name = (String) json.get("name");
	        	int cantPartes = hashes.size();//Para calcular % de archivo descargado
	        	String nameSinExtension = name.substring(0, name.lastIndexOf('.'));
	        	
	        	try {
	        		//Creo carpeta donde guardar el archivo a descargar
	        		String path = this.crearCarpeta(pathCarpeta, nameSinExtension);	        		
	        		//Creo archivo json que indica partes que faltan descargar en la carpeta
	        		File file = new File(path+"/"+name+"Partes.json");
	        		file.createNewFile();
	    			FileWriter fileW = new FileWriter(file);
	    			fileW.write(partes.toJSONString());
	    			fileW.flush();
	    			fileW.close();
	    			
	    			//Creo archivo en "Descargas Pendientes" para que pueda ser retomada la descarga en caso de detenerla
		        	String portS = String.valueOf(portExterno);
		        	String cantPartesS = String.valueOf(cantPartes);
		        	long fileSize = (long) json.get("fileSize");
		        	JSONObject datosDescarga = new JSONObject();
		        	datosDescarga.put("path", path);
		        	datosDescarga.put("hash", hash);
		        	datosDescarga.put("name", name);
		        	datosDescarga.put("ip", this.ipExterna);
		        	datosDescarga.put("port", portS);
		        	datosDescarga.put("cantPartes", cantPartesS);
		        	datosDescarga.put("time", "0");
		        	datosDescarga.put("fileSize", fileSize);
		        	datosDescarga.put("descargado", "0%");
		        	datosDescarga.put("leecher", "false");//Se vuelve true luego de enviar DOWNLOAD al tracker. Con true envia SWARM

		        	//Array de tiempos de descarga de cada parte
		        	JSONArray tiemposPartes = new JSONArray();
		        	for(int i=0; i<cantPartes; i++) {
		        		JSONObject tiempoParte = new JSONObject();
		        		tiempoParte.put("tiempo", "0");
			    		tiemposPartes.add(tiempoParte);	
		        	}
		        	datosDescarga.put("tiemposPartes", tiemposPartes);
		        	
		        	try {
		        		//Creo archivo con nombre no utilizado
		    			pathJSON = this.getNameUnico(hash,this.pathDescargasPendientes);
		        		file = new File(pathJSON);
		        		file.createNewFile();
		    			fileW = new FileWriter(file);
		    			fileW.write(datosDescarga.toJSONString());
		    			fileW.flush();
		    			fileW.close();
		    			
		    			//Agrego a lista DescargasPendientes
		    			String descargaPendienteFileName = pathJSON.substring(pathJSON.lastIndexOf('/')+1, pathJSON.length());
			        	DescargaPendiente dp = new DescargaPendiente(name,descargaPendienteFileName,hash);
			        	this.listaDescargasPendientes.add(dp);
			        	
			        	this.crearJSONgraficos(hash);//JSON de la descarga que almacenará datos para generar gráficos
			        	
			        	try {
			        		boolean nuevaDescarga = true;
			        		this.getSwarm(Mensaje.Tipo.DOWNLOAD, this.ipExterna, this.portExterno, path, name, cantPartes, hash, descargaPendienteFileName, "0%", nuevaDescarga);
			        		
			    		}catch(Exception e) {
			    			log = "Fallo la conexion con su tracker.";
			    			this.logger.error(log);
			    			System.out.println(log);
			    		}
		    		} catch (IOException e) {
		    			log = "Fallo al crear JSON de Archivo pendiente a descargar.";
		    			this.logger.error(log);
		    			System.out.println(log);
		    		}
	    		} catch (IOException e) {
	    			log = "Fallo al crear JSON de partes descargadas.";
	    			this.logger.error(log);
	    			System.out.println(log);
	    		}	
	    	} else {
	    		log = "JSON invalido. Debe utilizar un JSON descargado desde la aplicacion.";
	    		this.logger.error(log);
	    		System.out.println(log);
	    	}
		} catch(Exception e){
			log = "Archivo invalido. Debe utilizar un JSON descargado desde la aplicacion.";
			this.logger.error(log);
			System.out.println(log);
		}
	}
	
	
	private void crearJSONgraficos(String hash) throws IOException {
		JSONObject json = new JSONObject();
		JSONArray tiempoPartes = new JSONArray();
		JSONArray fallosPeers = new JSONArray();
		JSONArray descargasPeers = new JSONArray();
    	
		json.put("tiempoPartes", tiempoPartes);
		json.put("fallosPeers", fallosPeers);
		json.put("descargasPeers", descargasPeers);
		
		File file = new File(this.pathGraficos+"/"+hash+".json");
		file.createNewFile();
		FileWriter fileW = new FileWriter(file);
		fileW.write(json.toJSONString());
		fileW.flush();
		fileW.close();	
	}

	//Descargas Pendientes --> reanudarDescarga --> crea nuevo thread cliente para continuar descarga
	public void reanudarDescarga(String hashBuscado) {
		DescargaPendiente dp = this.buscarDescargaPendiente(hashBuscado);
		if(dp != null) {
			String nameFile = dp.getNameFile();
			//obtener datos json descargaPendiente
			Object obj = null;
			try {
				FileReader fileReader = new FileReader(this.pathDescargasPendientes+"/"+nameFile);
				obj = new JSONParser().parse(fileReader);
				fileReader.close();
				
				JSONObject jsonObject = (JSONObject) obj;
		        String path = (String) jsonObject.get("path");
		        String hash = (String) jsonObject.get("hash");
		        String name = (String) jsonObject.get("name");
		        String ip = (String) jsonObject.get("ip");
		        String descargado = (String) jsonObject.get("descargado");
		        String leecher = (String) jsonObject.get("leecher");
		        int port = Integer.parseInt((String) jsonObject.get("port"));
		        int cantPartes = Integer.parseInt((String) jsonObject.get("cantPartes"));
				
				try {
					boolean nuevaDescarga = false;//Ya existe en la tabla de descargas
					//Pedir swarm
					if(leecher.equals("true")) 
						this.getSwarm(Mensaje.Tipo.SWARM, ip, port, path, name, cantPartes, hash, nameFile, descargado, nuevaDescarga);
					else 
						this.getSwarm(Mensaje.Tipo.DOWNLOAD, ip, port, path, name, cantPartes, hash, nameFile, descargado, nuevaDescarga);
						
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (IOException | ParseException | ClassCastException e1) {
				e1.printStackTrace();
			}			
		} else {
			log = "No se pudo reanudar la descarga.";
			logger.info(log);
			System.out.println(log);
		}
	}

	private DescargaPendiente buscarDescargaPendiente(String hash) {
		for(DescargaPendiente dp : this.listaDescargasPendientes) {
			if(dp.getHash().equals(hash)){
				return dp; 
			}
		}
		return null;
	}

	//Descargas Pendientes --> pausarDescarga --> detiene thread cliente 
	public void pausarDescarga(String hash) {
		//Busco ThreadCliente en lista según hash
		ThreadCliente tc = null;
		synchronized(this.listaThreadsClientes) {
			for(int i=0; i<this.listaThreadsClientes.size(); i++) {
				if(hash.equals(this.listaThreadsClientes.get(i).getHash())) {
					tc = this.listaThreadsClientes.get(i);
				}
			}
		}
		
		tc.setStop(true);
	}
	
	private void getSwarm(Mensaje.Tipo tipoMensaje, String ip, int port, String path, String name, int cantPartes, String hash, String nameFile, String descargado, boolean nuevaDescarga) throws Exception {
		boolean fallo = false;
		conexionTCP = null;
		conexionTCP = tm.getTracker(kg, kPub, kPriv);
		if(conexionTCP!=null) {
			Mensaje m = null;
			switch(tipoMensaje) {
				case SWARM://Continua descarga
						m = new Mensaje(Mensaje.Tipo.SWARM, ip, port, hash);		
					break;
				case DOWNLOAD://Nueva descarga
						m = new Mensaje(Mensaje.Tipo.DOWNLOAD, hash, path, ip, port);
					break;
			}
				
			m.enviarMensaje(conexionTCP,m, kg);
			
			//Recibo swarm - cast objeto en arrayList<seedtable>
			byte[] datosDesencriptado = m.recibirMensaje(conexionTCP, kg);
	        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(datosDesencriptado);
	        
			if(response.tipo == Mensaje.Tipo.SWARM) {
				DescargaPendiente dp = this.buscarDescargaPendiente(hash);
				dp.setActivo(true);
				
				if(tipoMensaje == Mensaje.Tipo.SWARM) {//Reanuda descarga.
					this.notifyObserver(3, hash);//Cambiar boton de start a stop
				} else {//Si no, es una nueva descarga
					//Leecher = true. Soy considerado un leercher por el tracker
					Object obj;
					FileReader fileReader = new FileReader(this.pathDescargasPendientes+"/"+nameFile);
					obj = new JSONParser().parse(fileReader);
					fileReader.close();
					
					JSONObject jsonObject = (JSONObject) obj;
					String leecher = (String) jsonObject.get("leecher");
			        leecher = "true";
			        jsonObject.put("leecher", leecher);
					
					File file = new File(this.pathDescargasPendientes+"/"+nameFile);
					file.createNewFile();
					FileWriter fileW = new FileWriter(file);
					fileW.write(jsonObject.toJSONString());
					fileW.flush();
					fileW.close();
			        
					if(nuevaDescarga)
						this.notifyObserver(2, "stop");//Datos nueva descarga para agregar en tabla
					else//Si fue cargado de descargas pendientes
						this.notifyObserver(3, hash);
				}
								
				ArrayList<SeedTable> swarm = (ArrayList<SeedTable>) response.lista;
				ThreadCliente tc = new ThreadCliente(swarm, path, name, cantPartes, hash, nameFile, this, descargado);
				
				listaThreadsClientes.add(tc);
				Thread t = new Thread(tc);
				t.start();
				
				log = "ThreadCliente, para descargar archivo "+name+", iniciado";
				this.logger.info(log);
				System.out.println(log);
			}else {
				log = "Fallo al recibir Swarm. Intente descargar el archivo nuevamente.";
				this.logger.error(log);
				System.out.println(log);
				fallo = true;
			}						
		}else {
			log = "No hay trackers disponibles en este momento.";
			this.logger.error(log);
			System.out.println(log);
			fallo = true;
        }
		
		if(fallo && nuevaDescarga)
			this.notifyObserver(2, "start");//Datos nueva descarga para agregar en tabla
	}
}
		
							