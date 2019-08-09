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
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;
import TP_Final_SDyPP.UPnP.UPnPAdmin;

public class Cliente implements Runnable {
	//Atributos//
	private ConexionTCP conexionTCP;
	private int portServer;
	private int portExterno;
	private String ipExterna;
	private static Scanner scanner = new Scanner(System.in);
	private String pathJSONs;
	private UPnPAdmin admin;
	private Servidor servidor;
	private ArrayList<TrackerInfo> listaTrackers;
	private ArrayList<ThreadCliente> listaThreadsClientes;
	private ArrayList<DescargaPendiente> listaDescargasPendientes;
	public Logger logger;
	
	public int getPort() {
		return portExterno;
	}
	
	public String getIp() {
		return ipExterna;
	}
	
	public Servidor getServidor() {
		return servidor;
	}
	
	public ArrayList<ThreadCliente> getListaThreadsClientes() {
		synchronized(this) {
			return listaThreadsClientes;
		}
	}

	public ArrayList<DescargaPendiente> getListaDescargasPendientes() {
		return listaDescargasPendientes;
	}
	
	public void eliminarDescargaPendiente(int i) {
		synchronized(this) {
			this.listaDescargasPendientes.remove(i);
		}
	}
	
	public void eliminarThreadCliente(ThreadCliente tc) {
		synchronized(this) {
			this.listaDescargasPendientes.remove(tc);
		}
	}

	//Constructor//
	public Cliente(String path, int port, int portExterno, String ipExterna, UPnPAdmin admin, Servidor s, Logger logger) throws IOException {
		this.setListaDescargasPendientes();
		this.listaThreadsClientes = new ArrayList<ThreadCliente>();
		this.logger = logger;
		this.pathJSONs = path;
		this.portServer = port;
		this.portExterno = portExterno;
		this.ipExterna = ipExterna;
		this.admin = admin;
		this.servidor = s;
	}
	
	private void setListaDescargasPendientes() {
		this.listaDescargasPendientes = new ArrayList<DescargaPendiente>();
		File pathDescargasPendientes = new File("Descargas pendientes/");
		for (File file : pathDescargasPendientes.listFiles()) {
			if (file.isFile()) {
				Object obj;
				try {
					obj = new JSONParser().parse(new FileReader("Descargas pendientes/"+file.getName()));
					JSONObject jo = (JSONObject) obj; 
			        String name = (String) jo.get("name");
			        String hash = (String) jo.get("hash");
			         
					DescargaPendiente dp = new DescargaPendiente(name,hash);
					listaDescargasPendientes.add(dp);
				} catch (IOException | ParseException e) {
					e.printStackTrace();
				}
		    }
		}
	}

	//Consola//
	public void Inicio () throws Exception {
		boolean salir = false;
		
		try {
			while(!salir) {
				System.out.println("Consola cliente\n");
				System.out.println("Elija una opcion:");
				System.out.println("1- Crear Meta Archivo"
						+ "\n2- Subir Meta Archivo\n3- Descargar Meta Archivo\n4- Descargar Archivo"
						+ "\n5- Descargas Pendientes \n6- Actualizar Trackers Conocidos\n7- Archivos ofrecidos por mí\n8- Salir");
				String opcion = scanner.nextLine();
				switch(opcion) {
				case "1":
					this.logger.info("Llamada crear archivo");
					crearArchivo();
					break;
				case "2":
					this.logger.info("Llamada subir archivo");
					subirArchivo();
					break;
				case "3":
					this.logger.info("Llamada descargar meta archivo");
					descargarMetaArchivo();
					break;
				case "4":
					this.logger.info("Llamada descargar archivo");
					descargarArchivo();
					break;
				case "5":
					this.logger.info("Llamada descargas pendientes");
					descargasPendientes();
					break;
				case "6":
					this.logger.info("Llamada actualizar trackers");
					actualizarTrackers();
					break;
				case "7":
					this.logger.info("Llamada archivos ofrecidos");
					archivosOfrecidos();
					break;
				case "8":
					salir = true;
					this.admin.closePort(portServer);
					//pauso todas las descargas activas
					int i = 0;
					for(DescargaPendiente dp : this.listaDescargasPendientes) {
						if(dp.isActivo())
							this.pausarDescarga(i);
						i++;
					}						
					logger.info("Cliente cerrado");
					break;
				default:
					System.err.println("Elija 1, 2, 3, 4, 5, 6, 7 u 8.\n");
					break;
				}
				System.out.println("-----------------------------------------------------------------------------\n");
			}			
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private void archivosOfrecidos() throws Exception {
		conexionTCP = null;
		conexionTCP = this.servidor.getTracker();
		
		if(conexionTCP!=null) {
			Mensaje m = new Mensaje(Mensaje.Tipo.GET_FILES_OFFERED, this.ipExterna, this.portExterno);
			
			//encripto mensaje con la clave simetrica
			byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
			byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
			conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexionTCP.getOutBuff().flush();
			
			int msgSize = 1024*1024;//1MB
	        byte[] buffer = new byte[msgSize];
	        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
	        //desencripto con la clave privada
	        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
	        byte[] msgDesencriptado = this.servidor.getKG().desencriptarSimetrico(conexionTCP.getKey(),datosEncriptados);
	        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
	        
	        if(response.tipo == Mensaje.Tipo.GET_FILES_OFFERED) {
	        	ArrayList<FileTable> files = (ArrayList<FileTable>) response.lista;
	        	if(files.size()>0) {
	        		System.out.println("Está ofreciendo "+files.size()+" archivo/s.");
	        		int i=1;
	        		for(FileTable ti : files) {
	        			System.out.println(i+"- "+ti.getName());
	        		}
	        	}else {
	        		System.out.println("No está ofreciendo ningún archivo.");		        	
	        	}
	        }else {
	        	this.logger.error("Fallo al intentar obtener los archivos ofrecidos.");
	        }	
		}else {
			this.logger.error("No hay trackers disponibles en este momento.");
        }
	}

	private void actualizarTrackers() throws Exception {
		readtrackerJSON();//Lleno la listatracker antes de buscar un tracker
    	
		int i = 0;
		boolean trackerDisponible = false;
		Socket s;
    	while(i<listaTrackers.size() && !trackerDisponible) {
    		int port = listaTrackers.get(i).getPort();
    		String ip = listaTrackers.get(i).getIp();
    
    		if(getTrackers(ip,port)) {//Intenta conectarse al socket del tracker.
    			trackerDisponible=true;
    		}
    		i++;
    	}
    	
    	if(trackerDisponible) {
    		this.logger.info("La lista de trackers se actualizo correctamente.");
    		this.getListaTrackers();
    	}else {
    		this.logger.error("No se pudo actualizar la lista de trackers. No hay ningún tracker disponible.");
    	}
	}
	
	public void getListaTrackers() throws Exception {
		readtrackerJSON();
		
		System.out.println("-------------------------------------------");
		if(this.listaTrackers.size()==0) {
			System.out.println("Lista de Trackers activos vacía");
		}else {
			for(int i=0; i<this.listaTrackers.size(); i++){
				if(i!=0)
					System.out.println("");
				System.out.println("Tracker ID: "+this.listaTrackers.get(i).getId());
				System.out.println("Tracker socket: "+this.listaTrackers.get(i).getIp()+":"+this.listaTrackers.get(i).getPort());
			}
		}
		System.out.println("-------------------------------------------\n");
	}
	
	public boolean getTrackers(String ip, int port) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible
        	ConexionTCP c = new ConexionTCP(ip,port);
        	Mensaje msj = new Mensaje(Mensaje.Tipo.GET_TRACKERS, this.servidor.getKpub());
        	c.getOutObj().writeObject(msj);
        	
        	Mensaje response = (Mensaje) c.getInObj().readObject();
        	
        	if (response.tipo == Mensaje.Tipo.ACK) {
        		
        		//desencripto con la clave privada la clave simetrica
    	        byte[] msgDesencriptado = this.servidor.getKG().desencriptarAsimetrico(response.keyEncriptada, this.servidor.getKpriv());
    	        c.setKey((SecretKey) c.convertFromBytes(msgDesencriptado));
        		
    	        //Desencripto lista de trackers con la clave simetrica
    	        byte[] trackersEnBytes = this.servidor.getKG().desencriptarSimetrico(c.getKey(),response.datosEncriptados);
    	        this.listaTrackers = (ArrayList<TrackerInfo>) c.convertFromBytes(trackersEnBytes);
    	        
        		this.actualizarJSONTrackers();
        		return true;
        	}else {
        		c.getSocket().close();
        		return false;
        	}   
        	
        } catch (IOException | ClassNotFoundException ex) {
        	 return false;
        }
    }
	
	public void actualizarJSONTrackers() 
	{
		File trackers = new File("trackers.json");
		trackers.delete();//Elimino el archivo de trackers existente
		
		String id = "";
		String port = "";
		
		JSONArray array = new JSONArray();
		for(TrackerInfo t : this.listaTrackers) {
			JSONObject obj = new JSONObject();
			
			id = String.valueOf(t.getId());
			port = String.valueOf(t.getPort());			
			
			obj.put("id", id);
			obj.put("ip", t.getIp());
			obj.put("puerto", port);
			array.add(obj);
		}
		
		//Escribo datos en JSON
		FileWriter file;
		try {
			file = new FileWriter("trackers.json");
			file.write(array.toJSONString());
			file.flush();
			file.close();
		} catch (IOException e) {
			this.logger.error("Falló creación de json con trackers.");
			e.printStackTrace();
		}
	}

	public void readtrackerJSON() throws Exception  
    {  
    	String ip;
    	String port;
    	String id;
    	int idtracker;
    	int porttracker;
        Object obj = new JSONParser().parse(new FileReader("trackers.json")); 
           
        JSONArray jo = (JSONArray) obj; 
          
        this.listaTrackers = new ArrayList<TrackerInfo>();
        TrackerInfo tracker;
        
        for(int i=0; i<jo.size(); i++){
        	
        	JSONObject jsonObject = (JSONObject) jo.get(i);
            id = (String) jsonObject.get("id");
            ip = (String) jsonObject.get("ip");
            port = (String) jsonObject.get("puerto");
      
            idtracker = Integer.parseInt(id);
            porttracker = Integer.parseInt(port);
            tracker = new TrackerInfo(idtracker, ip, porttracker);
            this.listaTrackers.add(tracker);
        }
    }

	//Crear Archivo//
	private void crearArchivo() throws NoSuchAlgorithmException, IOException, ParseException {
		//Buscar archivo en path
		System.out.print("Ingrese el path donde se encuentra el archivo a partir del cual desea crear el META-Archivo: ");
		String pathArchivo = this.buscarArchivo();
		System.out.println("Espere mientras se crea el archivo JSON con META-DATA del archivo que desea subir.");
		this.crearArchivoMeta(pathArchivo);		
		this.logger.info("JSON creado.");
	}

	//Subir Archivo//
	private void subirArchivo() throws Exception 
	{
		System.out.print("Ingrese el path donde se encuentra el META-Archivo que desea publicar: ");
		String pathJSON = this.buscarArchivo();
		this.logger.info("Enviando JSON a Tracker...");
		String hashJSON = this.hash(pathJSON);
		this.enviarArchivo(pathJSON, hashJSON);
	}

	//Subir Archivo y Crear Archivo--> Buscar archivo en path, devuelve path si es válido//
	private String buscarArchivo() 
	{		
		String path = "";
		File sharedPath;
    	boolean pathCorrecto = false;
    	
    	do {
	    	try {
	    		path = scanner.nextLine();
	    		sharedPath = new File(path);
	    		if(sharedPath.isFile())
	    			pathCorrecto = true;
	    		else
	    			System.err.println("El path es inválido. Elija un archivo. Pruebe nuevamente:");
	    	}catch(NumberFormatException  e){
	    		System.err.println("El path es inválido. Pruebe nuevamente:");
	    	}
    	}while(!pathCorrecto);
		
    	return path;
	}

	//Crear Archivo --> Crear archivo JSON con meta data del archivo a subir//
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
		String pathJSON = this.ConstruirArchivoMeta(name, size, sizePiece, piecesHashes, nameRemoveExt, path);
		this.ConstruirArchivoPartes(path, pathJSON);
	}

	//Crear Archivo --> CrearArchivoMeta --> generar archivo json que indica las partes que se posee del archivo
	private void ConstruirArchivoPartes(String path, String pathJSON) throws FileNotFoundException, IOException, ParseException {
		//Creo archivo que indica que partes poseo del archivo (todas al ser seed)     	
    	Object obj = new JSONParser().parse(new FileReader(pathJSON));
    	JSONObject json = (JSONObject) obj;
    	String name = (String) json.get("name");
    	JSONArray hashes = (JSONArray) json.get("hashes");
    	JSONArray partes = new JSONArray();
    	
    	for(int i=0; i<hashes.size(); i++) {
    		JSONObject hash = (JSONObject) hashes.get(i);
    		
    		JSONObject parte = new JSONObject();
    		parte.put("parte", i);
    		parte.put("hash", hash.get("hash"));
    		parte.put("size", hash.get("size"));
    		parte.put("estado", "descargada");
    		partes.add(parte);
    	}		
    	
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
			this.logger.error("Falló al crear JSON de partes descargadas.");
		}
	}

	//Crear Archivo --> CrearArchivoMeta --> generar hash de cada parte del arhivo y devolver lista para el archivo META-DATA//
	private String[] getPiecesHashes(File file, long size, int sizePiece) throws NoSuchAlgorithmException, IOException 
	{
		int partes = (int) ((size / sizePiece) + 1);
		String[] piecesHashes = new String[partes];
		int index = 0;
	    byte[] buffer = new byte[sizePiece];//1 MB
	    byte[] bufferFinal = new byte[0];
	    
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream in = new BufferedInputStream(fis);
        int byteread;
        
        while ((byteread = in.read(buffer,0,buffer.length)) != -1 ) {
        	if(index+1 == partes)
        		buffer = Arrays.copyOfRange(buffer, 0, byteread);
        	MessageDigest md = MessageDigest.getInstance("SHA-1"); 
        	md.update(buffer);
        	byte[] b = md.digest();
        	StringBuffer sb = new StringBuffer();
        	for(byte bl : b) {
        		sb.append(Integer.toHexString(bl & 0xff).toString());    		
        	}
        	piecesHashes[index] = sb.toString();
        	index++;
        }  	
        
		return piecesHashes;
	}
	
	//Crear Archivo --> CrearArchivoMeta --> creo JSON con nombre, tamaño, tamaño pieza, lista de hashes//
	private String ConstruirArchivoMeta(String name, long size, int sizePiece, String[] piecesHashes, String nameRemoveExt, String pathArchivo) 
	{	
		JSONObject json = new JSONObject();
		json.put("name", name);
		if(pathArchivo.contains("/"))
			json.put("path", pathArchivo.substring(0, pathArchivo.lastIndexOf('/')));//Le saco el nombre. En este path está el archivo y partes.json	
		else
			json.put("path", pathArchivo.substring(0, pathArchivo.lastIndexOf('\\')));//Le saco el nombre. En este path está el archivo y partes.json
		json.put("fileSize", size);
		json.put("pieceSize", sizePiece);

		JSONArray array = new JSONArray();
		for(int i=0; i<piecesHashes.length; i++){
			if(i != piecesHashes.length-1) {
				JSONObject piece = new JSONObject();
				piece.put("hash", piecesHashes[i]);
				piece.put("size", sizePiece);
				array.add(piece);	
			}else {//Última pieza
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
		
		try {			
			//Creo archivo con nombre no utilizado
			String pathJSON = "";
			boolean yaExiste = false;
			int index = 1;
			do {
				File f = null;
				if(index == 1) {
					pathJSON = this.pathJSONs + "/" + nameRemoveExt +".json";
					f = new File(pathJSON);
					index++;
				}else {//Ya fallo el primer nombre. Pongo un numero entre parentesis desde ahora
					pathJSON = this.pathJSONs + "/" + nameRemoveExt +" ("+ index +").json";
					f = new File(pathJSON);
					index++;
				}	
				yaExiste = f.exists();
			}while(yaExiste);//Mientras que el nombre exista no sale del loop
			
			//Escribo datos en JSON
			FileWriter file = new FileWriter(pathJSON);
			file.write(json.toJSONString());
			file.flush();
			file.close();
			return pathJSON;
		} catch (IOException e) {
			this.logger.error("Falló creación de JSON");
			return "";
		}
	}
	
	//Subir Archivo --> Hash del JSON para usarlo como ID/Nombre del mismo JSON//
	public String hash (String path) throws IOException, NoSuchAlgorithmException, ParseException 
	{	
		File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream in = new BufferedInputStream(fis);
        int size = (int) file.length();
        byte[] buffer = new byte[size];
        
        in.read(buffer,0,buffer.length);
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
	
	//Subir Archivo --> una vez creado el JSON se lo envia al tracker conocido//
	private void enviarArchivo(String pathJSON, String hashJSON) throws Exception 
	{			
		try {//En caso de que el tracker al que estoy conectado se haya caido, creo la conexion dentro de try
			conexionTCP = null;
			conexionTCP = this.servidor.getTracker();
    		
    		if(conexionTCP!=null) {
    			Mensaje m = new Mensaje(Mensaje.Tipo.GET_PRIMARIO);
        		//encripto mensaje con la clave simetrica
    			byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
    			byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
    			conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
    			conexionTCP.getOutBuff().flush();
        		
        		//Recibo primario, enviado por mi tracker
    			int msgSize = 1024*1024;//1MB
    	        byte[] buffer = new byte[msgSize];
    	        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
    	        //desencripto con la clave simetrica
    	        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
    	        byte[] msgDesencriptado = this.servidor.getKG().desencriptarSimetrico(conexionTCP.getKey(), datosEncriptados);
    	        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
        		conexionTCP.getSocket().close();
        		
        		if (response.tipo == Mensaje.Tipo.TRACKER_PRIMARIO) {
    	    		conexionTCP = new ConexionTCP(response.ip, response.port);
    	    		this.getSecretKey(conexionTCP);
    	    		
    	    		m = new Mensaje(Mensaje.Tipo.SEND_FILE, this.ipExterna, this.portExterno, hashJSON);//hash para identificarlo
    	    		//encripto mensaje con la clave simetrica
    				datosAEncriptar = conexionTCP.convertToBytes(m);
    				mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
    				conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
    				conexionTCP.getOutBuff().flush();
    	    		
    	    		//Enviar json
    	    		this.enviarArchivoBuffer(pathJSON);//Le paso el path del JSON para que pueda encontrarlo y enviarlo
    	    		
    	    		this.logger.info("JSON enviado al Tracker.");	
            	}else {
            		this.logger.error("Su tracker actual ("+conexionTCP.getSocket().getInetAddress().getCanonicalHostName()+":"+conexionTCP.getSocket().getPort()+") no pudo recuperar el tracker primario. No se subió el archivo.");
            	}
        		conexionTCP.getSocket().close();
    		}else {
    			this.logger.error("No hay trackers disponibles en este momento.");
    		}
		}catch(IOException ex) {
			this.logger.error("Falló la conexión con el tracker.");
		}
	}
	
	//Subir Archivo --> enviarArchivo --> envío archivo JSON
	private void enviarArchivoBuffer(String pathJSON) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InterruptedException 
	{
		Thread.sleep(100);
	    try {
			File archivo = new File(pathJSON);
			   
			byte[] buffer = new byte[(int) archivo.length()];
			   
	        FileInputStream fis = new FileInputStream(archivo);
	        BufferedInputStream in = new BufferedInputStream(fis);
	        in.read(buffer,0,buffer.length);
	        
	        //encripto archivo json con la clave simetrica
			byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), buffer);
			conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexionTCP.getOutBuff().flush();
	        
	        in.close();//Cierro el buffer de lectura del JSON
	    }catch (IOException e) {
	    	this.logger.error("Fallo durante envío del JSON.");
	    }
	}
	
	private void descargarMetaArchivo() throws Exception {
		try {
			System.out.println("Ingrese el nombre del archivo que desea descargar:");
			String nombreArchivo = scanner.nextLine();
					
			conexionTCP = null;
			conexionTCP = this.servidor.getTracker();
    		
    		if(conexionTCP!=null) {
				Mensaje m = new Mensaje(Mensaje.Tipo.FIND_FILE, nombreArchivo);	
				//encripto mensaje con la clave simetrica
				byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
				byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
				conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
				conexionTCP.getOutBuff().flush();
	    		
				int msgSize = 1024*1024;//1MB
		        byte[] buffer = new byte[msgSize];
		        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
		        //desencripto con la clave simetrica
		        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
		        byte[] msgDesencriptado = this.servidor.getKG().desencriptarSimetrico(conexionTCP.getKey(), datosEncriptados);
		        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
				
				if(response.tipo == Mensaje.Tipo.FILES_AVAILABLE) 
				{
					ArrayList<FileTable> files = (ArrayList<FileTable>) response.lista;
					System.out.println("Archivos disponibles:");
					int i=1;
					String[] unidades = {"Bytes","KB","MB","GB","TB"};
					
					for(FileTable f : files) { //Muestro los nombres de archivos que coincidieron con la búsqueda
						double peso = f.getSize();
						int pos = 0;
						
						while(peso >= 1024) {
							peso /= 1024;
							pos++;
						}
						String pesoString = String.format("%.2f", peso);	
						System.out.println(i+"- "+f.getName()+", " + pesoString + " " + unidades[pos]);
						i++;
					}
					
					boolean correcto = false;
					int pos = 0;
					int n = files.size();
					String archivoElegido;
					do {
						System.out.println("\nEscriba el número a la izquierda del nombre del archivo que desea descargar o 'salir' si no quiere descargar nada:");
						archivoElegido = scanner.nextLine();
						
						if(archivoElegido.equals("salir")) {
							correcto = true;
						}else {
							try {
					    		pos = Integer.parseInt(archivoElegido);
					    		if(pos>=1 && pos<=n) {
					    			correcto = true;
					    		}else {
					    			if(n>1)
										System.err.println("ERROR: Escriba un número (de 1 a "+n+") o salir");
									else
										System.err.println("ERROR: Escriba 1 o salir");
					    		}
							}catch(Exception e) {
								if(n>1)
									System.err.println("ERROR: Escriba un número (de 1 a "+n+") o salir");
								else
									System.err.println("ERROR: Escriba 1 o salir");
							}
						}
					}while(!correcto);
					
					if(!archivoElegido.equals("salir")) {
						String hash = files.get(pos-1).getHash();
						m = new Mensaje (Mensaje.Tipo.REQUEST, hash);
						//Envío el hash del archivo a descargar
						//encripto mensaje con la clave simetrica
						datosAEncriptar = conexionTCP.convertToBytes(m);
						mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
						conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
						conexionTCP.getOutBuff().flush();

						String name = files.get(pos-1).getName();
						String nameSinExt = name.substring(0, name.lastIndexOf('.'));
						this.guardarArchivoBuffer(conexionTCP, nameSinExt);	
						this.logger.info("JSON descargado exitosamente.");

					}else{
						m = new Mensaje (Mensaje.Tipo.EXIT);
						
						//encripto mensaje con la clave simetrica
						datosAEncriptar = conexionTCP.convertToBytes(m);
						mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
						conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
						conexionTCP.getOutBuff().flush();
						
						conexionTCP.getSocket().close();
					}
				}else if(response.tipo == Mensaje.Tipo.ERROR) {
					System.err.println("-------------------------------------------");
					this.logger.error(response.string);
					conexionTCP.getSocket().close();
				}
			}else {
				this.logger.error("No hay trackers disponibles en este momento.");
	        }
		}catch(IOException ex) {
			this.logger.error("Fallo al intentar descargar META-Archivo.");
			ex.printStackTrace();
		}	
			
	}
	
	private boolean guardarArchivoBuffer(ConexionTCP ctcp, String name) throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		int byteread;
	    int current = 0;
	
	    try {	    	
	    	//Creo archivo con nombre no utilizado
			String pathJSON = this.getNameUnico(name,this.pathJSONs);
	    	
	        File archivo = new File(pathJSON);//Al JSON le pongo como nombre su ID para evitar repetidos
	        archivo.createNewFile();//Almaceno JSON en una carpeta del cliente donde van todos los JSON
	        FileOutputStream fos = new FileOutputStream(archivo);
	        BufferedOutputStream out = new BufferedOutputStream(fos);
	        
	        int msgSize = 1024*1024;//1MB
	        byte[] buffer = new byte[msgSize];
	        byteread = ctcp.getInBuff().read(buffer, 0, msgSize);
	        //desencripto con la clave simetrica
	        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
	        byte[] datosDesencriptados = this.servidor.getKG().desencriptarSimetrico(ctcp.getKey(), datosEncriptados);
	        
	        out.write(datosDesencriptados, 0, datosDesencriptados.length);
	        out.flush();

	        fos.close();
	        ctcp.getSocket().close();
	        return true;
	    }catch(IOException ex) {
	    	this.logger.error("Falló guardar JSON.");
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

	//Descargar archivo//
	private void descargarArchivo() {
		System.out.println("Elija el META-Archivo del archivo que desea descargar, indicando su path:");
		String pathJSON = this.buscarArchivo();
		
		System.out.println("Elija una carpeta donde guardar el archivo a descargar, indicando su path:");
		String pathArchivo = this.buscarCarpeta();
		
		try {			
			//Creo archivo que indica que partes faltan descargar.     	
	    	Object obj = new JSONParser().parse(new FileReader(pathJSON));
	    	JSONObject json = (JSONObject) obj;
	    	JSONArray hashes = (JSONArray) json.get("hashes");
	    	JSONArray partes = new JSONArray();
	    	
	    	for(int i=0; i<hashes.size(); i++) {
	    		JSONObject hash = (JSONObject) hashes.get(i);
	    		
	    		String parteString = String.valueOf(i);
	    		String size = String.valueOf(hash.get("size"));
	    		
	    		JSONObject parte = new JSONObject();
	    		parte.put("parte", parteString);
	    		parte.put("hash", hash.get("hash"));
	    		parte.put("size", size);
	    		parte.put("estado", "pendiente");
	    		partes.add(parte);
	    	}
	    	
	    	//Leo hash y name del json  	
	    	String hash = (String) json.get("ID");
	    	if(hash!=null) {//Si es null está utilizando un json creado en su pc
	    		String name = (String) json.get("name");
	        	int cantPartes = hashes.size();//Para calcular % de archivo descargado
	        	String nameSinExtension = name.substring(0, name.lastIndexOf('.'));
	        	
	        	try {
	        		new File(pathArchivo+"/"+nameSinExtension).mkdirs();
	        		File file = new File(pathArchivo+"/"+nameSinExtension+"/"+name+"Partes.json");
	        		file.createNewFile();
	    			FileWriter fileW = new FileWriter(file);
	    			fileW.write(partes.toJSONString());
	    			fileW.flush();
	    			fileW.close();
	    		} catch (IOException e) {
	    			this.logger.error("Fallo al crear JSON de partes descargadas.");
	    		}
	        	
	        	//Creo archivo en "Descargas Pendientes" para que pueda ser retomada la descarga en caso de detenerla
	        	String ipS = String.valueOf(portExterno);
	        	String cantPartesS = String.valueOf(cantPartes);
	        	JSONObject datosDescarga = new JSONObject();
	        	datosDescarga.put("path", pathArchivo+"/"+nameSinExtension);
	        	datosDescarga.put("hash", hash);
	        	datosDescarga.put("name", name);
	        	datosDescarga.put("ip", this.ipExterna);
	        	datosDescarga.put("port", ipS);
	        	datosDescarga.put("cantPartes", cantPartesS);
	        	
	        	try {
	        		//Creo archivo con nombre no utilizado
	    			pathJSON = this.getNameUnico(name,"Descargas pendientes");
	        		File file = new File(pathJSON);
	        		file.createNewFile();
	    			FileWriter fileW = new FileWriter(file);
	    			fileW.write(datosDescarga.toJSONString());
	    			fileW.flush();
	    			fileW.close();
	    		} catch (IOException e) {
	    			this.logger.error("Fallo al crear JSON de Archivo pendiente a descargar.");
	    		}
	        	
	        	//Agrego a lista DescargasPendientes
	        	DescargaPendiente dp = new DescargaPendiente(name,hash);
	        	dp.setActivo(true);
	        	this.listaDescargasPendientes.add(dp);
	        	
	    		try {
	    			conexionTCP = null;
	    			conexionTCP = this.servidor.getTracker();
	        		
	        		if(conexionTCP!=null) {
	    				Mensaje m = new Mensaje(Mensaje.Tipo.DOWNLOAD, hash, pathArchivo+"/"+nameSinExtension, this.ipExterna, this.portExterno);	
	    				//encripto mensaje con la clave simetrica
	    				byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
	    				byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
	    				conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
	    				conexionTCP.getOutBuff().flush();
	    				
	    				//Recibo swarm - cast objeto en arrayList<seedtable>
	    				int msgSize = 1024*1024;//1MB
	    		        byte[] buffer = new byte[msgSize];
	    		        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
	    		        //desencripto con la clave simetrica
	    		        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
	    		        byte[] msgDesencriptado = this.servidor.getKG().desencriptarSimetrico(conexionTCP.getKey(), datosEncriptados);
	    		        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
	    		        
	    				if(response.tipo == Mensaje.Tipo.SWARM) {
	    					ArrayList<SeedTable> swarm = (ArrayList<SeedTable>) response.lista;
	    					ThreadCliente tc = new ThreadCliente(swarm, pathArchivo+"/"+nameSinExtension, name, cantPartes, hash, this);
	    					listaThreadsClientes.add(tc);
	    					Thread t = new Thread(tc);
	    					t.start();
	    					
	    					this.logger.info("ThreadCliente, para descargar archivo "+name+", iniciado");
	    				}else {
	    					this.logger.error("Fallo al recibir Swarm. Intente descargar el archivo nuevamente.");
	    				}	
	    			}else {
	    				this.logger.error("No hay trackers disponibles en este momento.");
	    	        }			
	    		}catch(Exception e) {
	    			this.logger.error("Falló la conexión con su tracker. Conéctese a otro.");
	    		}	
	    	} else {
	    		this.logger.error("JSON inválido. Debe utilizar un JSON descargado desde la aplicación.");
	    	}
		} catch(Exception e){
			this.logger.error("Archivo inválido. Debe utilizar un JSON descargado desde la aplicación.");
		}
	}
	
	//Descargas Pendientes --> lista los archivos pendientes a descargar. La descarga puede estar activa o pausada
	private void descargasPendientes() {
		if(this.listaDescargasPendientes.size()>0) {
			int i=1;
			for(DescargaPendiente dp : this.listaDescargasPendientes) {
				if(dp.isActivo())
					System.out.println(i+"- "+dp.getName()+" - Descargando");
				else
					System.out.println(i+"- "+dp.getName()+" - Descarga Pausada");
				i++;
			}
			
			//Elegir descarga para cambiar estado o salir
			boolean correcto = false;
			int pos = 0;
			int n = this.listaDescargasPendientes.size();
			String archivoElegido;
			do {
				System.out.println("\nEscriba el número a la izquierda del nombre del archivo que desea cambiar su estado o 'salir':");
				archivoElegido = scanner.nextLine();
				
				if(archivoElegido.equals("salir")) {
					correcto = true;
				}else {
					try {
			    		pos = Integer.parseInt(archivoElegido);
			    		if(pos>=1 && pos<=n) {
			    			correcto = true;
			    		}else {
			    			if(n>1)
								System.err.println("ERROR: Escriba un número (de 1 a "+n+") o salir");
							else
								System.err.println("ERROR: Escriba 1 o salir");
			    		}
					}catch(Exception e) {
						if(n>1)
							System.err.println("ERROR: Escriba un número (de 1 a "+n+") o salir");
						else
							System.err.println("ERROR: Escriba 1 o salir");
					}
				}
			}while(!correcto);
			
			if(!archivoElegido.equals("salir")) {
				pos--;
				boolean activo = this.listaDescargasPendientes.get(pos).isActivo();
				if(activo)
					this.pausarDescarga(pos);
				else
					this.reanudarDescarga(pos);
			}	
		}else {
			System.out.println("No hay descargas pendientes");
		}
	}
	
	//Descargas Pendientes --> reanudarDescarga --> crea nuevo thread cliente para continuar descarga
	private void reanudarDescarga(int pos) {
		String nameFile = this.listaDescargasPendientes.get(pos).getName();
		//obtener datos json descargaPendiente
		Object obj = null;
		try {
			obj = new JSONParser().parse(new FileReader("Descargas pendientes/"+nameFile+".json"));
			JSONObject jsonObject = (JSONObject) obj;
	        String path = (String) jsonObject.get("path");
	        String hash = (String) jsonObject.get("hash");
	        String name = (String) jsonObject.get("name");
	        String ip = (String) jsonObject.get("ip");
	        int port = Integer.parseInt((String) jsonObject.get("port"));
	        int cantPartes = Integer.parseInt((String) jsonObject.get("cantPartes"));
			
			//Pedir swarm
			conexionTCP = null;
			try {
				conexionTCP = this.servidor.getTracker();
				if(conexionTCP!=null) {
					Mensaje m = new Mensaje(Mensaje.Tipo.SWARM, ip, port, hash);	
					//encripto mensaje con la clave simetrica
					byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
					byte[] mensajeEncriptado = this.servidor.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
					conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
					conexionTCP.getOutBuff().flush();
					
					//Recibo swarm - cast objeto en arrayList<seedtable>
					int msgSize = 1024*1024;//1MB
			        byte[] buffer = new byte[msgSize];
			        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
			        //desencripto con la clave simetrica
			        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
			        byte[] msgDesencriptado = this.servidor.getKG().desencriptarSimetrico(conexionTCP.getKey(), datosEncriptados);
			        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
			        
					if(response.tipo == Mensaje.Tipo.SWARM) {
						ArrayList<SeedTable> swarm = (ArrayList<SeedTable>) response.lista;
						ThreadCliente tc = new ThreadCliente(swarm, path, name, cantPartes, hash, this);
						listaThreadsClientes.add(tc);
						Thread t = new Thread(tc);
						t.start();
						this.listaDescargasPendientes.get(pos).setActivo(true);
						this.logger.info("ThreadCliente, para descargar archivo "+name+", iniciado");
					}else {
						this.logger.error("Fallo al recibir Swarm. Intente descargar el archivo nuevamente.");
					}						
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (IOException | ParseException | ClassCastException e1) {
			e1.printStackTrace();
		}
	}

	//Descargas Pendientes --> pausarDescarga --> detiene thread cliente 
	private void pausarDescarga(int pos) {
		//Busco ThreadCliente en lista según nombre
		int threadCliente = 0;
		ThreadCliente tc = null;
		String hash = this.listaDescargasPendientes.get(pos).getHash();
		for(int i=0; i<this.listaThreadsClientes.size(); i++) {
			if(hash.equals(this.listaThreadsClientes.get(i).getHash()))
				tc = this.listaThreadsClientes.get(i);
		}
		logger.info("Pausando descarga "+tc.getName());
		tc.setStop(true);
	}

	//Descargar Archivo--> Elige carpeta donde almacenar el archivo a descargar//
	private String buscarCarpeta() 
	{		
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
	    			System.err.println("El path es inválido. Elija una carpeta. Pruebe nuevamente:");
	    	}catch(NumberFormatException  e){
	    		System.err.println("El path es inválido. Pruebe nuevamente:");
	    	}
    	}while(!pathCorrecto);
		
    	return path;
	}
	
	public void getSecretKey(ConexionTCP conexTCP) {
		Mensaje msj = new Mensaje(Mensaje.Tipo.CHECK_AVAILABLE, this.servidor.getKpub());
    	try {
    		conexTCP.getOutObj().writeObject(msj);
			
			Mensaje response = (Mensaje) conexTCP.getInObj().readObject();
	    	if (response.tipo == Mensaje.Tipo.ACK) {
	    		//desencripto con la clave privada la clave simetrica
		        byte[] msgDesencriptado = this.servidor.getKG().desencriptarAsimetrico(response.keyEncriptada, this.servidor.getKpriv());
		        SecretKey key = (SecretKey) conexTCP.convertFromBytes(msgDesencriptado);
		        conexTCP.setKey(key);
	    	}
		} catch (IOException | ClassNotFoundException e) {
			this.logger.error("Falló obtener clave secreta de primario");
			e.printStackTrace();
		}	
	}
    
	public void run() {
		try {
			this.Inicio();
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
}