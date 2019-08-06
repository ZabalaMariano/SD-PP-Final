package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;

public class Servidor implements Runnable {
	
	private ArrayList<TrackerInfo> listaTrackers;
	private int port;
	private int portExterno;
	private String ipExterna;
	private PublicKey kPub;
	private PrivateKey kPriv;
	private KeysGenerator kg;
	private final int conexionesMaximas = 10; 
	private int conexionesSalientes = 0;
	public Logger logger;
	
	//Constructor por defecto
	public Servidor(int puertoEscucha, int portExterno, String ipExterna, PublicKey publicKey, PrivateKey privateKey, Logger logger) {
		this.logger = logger;
		this.listaTrackers = new ArrayList<TrackerInfo>();
		this.port = puertoEscucha;
		this.portExterno = portExterno;
		this.ipExterna = ipExterna;
		this.kPub = publicKey;
		this.kPriv = privateKey;
		this.kg = new KeysGenerator();
	}
	
	public KeysGenerator getKG() {
		return kg;
	}
	
	public PublicKey getKpub() {
		return kPub;
	}
	
	public PrivateKey getKpriv() {
		return kPriv;
	}
	
	public int getPortExterno() {
		return portExterno;
	}

	public void setPortExterno(int portExterno) {
		this.portExterno = portExterno;
	}

	public String getIpExterna() {
		return ipExterna;
	}

	public void setIpExterna(String ipExterna) {
		this.ipExterna = ipExterna;
	}
	
	//Conexiones salientes
	public int getNumConexiones() {
		synchronized(this) {
			return this.conexionesSalientes;
		}
	}
	
	public int getNumConexionesMaximas() {
		return this.conexionesMaximas;
	}
	
	public void aumentarConexiones() {
		synchronized(this) {
			this.conexionesSalientes++;
			if(this.conexionesSalientes == this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me agregue a su array de peers que no pueden ser devueltos en un swarm.
				ConexionTCP c = null;
				
				try {
					c = this.getTracker();
					Mensaje m = new Mensaje(Mensaje.Tipo.COMPLETE, this.ipExterna, this.portExterno);
					//encripto mensaje con la clave simetrica
					byte[] datosAEncriptar = c.convertToBytes(m);
					byte[] mensajeEncriptado = kg.encriptarSimetrico(c.getKey(), datosAEncriptar);
					c.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
					c.getOutBuff().flush();
					c.getSocket().close();
					
				} catch (Exception e) {
					this.logger.error("Falló obtención de tracker.");
					e.printStackTrace();
				}
			}
		}
	}
	
	public void decrementarConexiones() {
		synchronized(this) {
			if(this.conexionesSalientes == this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me retire de su array de peers que no pueden ser devueltos en un swarm.
				ConexionTCP c = null;
				
				try {
					c = this.getTracker();
					Mensaje m = new Mensaje(Mensaje.Tipo.FREE, this.ipExterna, this.portExterno);
					//encripto mensaje con la clave simetrica
					byte[] datosAEncriptar = c.convertToBytes(m);
					byte[] mensajeEncriptado = kg.encriptarSimetrico(c.getKey(), datosAEncriptar);
					c.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
					c.getOutBuff().flush();
					c.getSocket().close();
					
				} catch (Exception e) {
					this.logger.error("Falló obtención de tracker.");
					e.printStackTrace();
				}
			}
			this.conexionesSalientes--;
		}
	}
	
	public void StartServer() {
		
		try {
			//Abrimos un Socket en el Servidor en el puerto especificado para poder establecer conexiones con el cliente
			ServerSocket socketServidor = new ServerSocket (this.port);
			
			while (true) {
				//Quedamos a la espera de conexiones por los clientes
				Socket socketClient = socketServidor.accept();
				ConexionTCP conexTCP = null;
				try {
					conexTCP = new ConexionTCP(socketClient);
				} catch (Exception e) {
					this.logger.error("Fallo al crear ConexionTCP con socket cliente en servidor");
					e.printStackTrace();
				}
				
				this.logger.info("Conexión establecida con cliente: ("+socketClient.getInetAddress().getCanonicalHostName()+":"+socketClient.getPort()+")");
				ThreadServer ts = new ThreadServer(conexTCP,this);
				Thread t = new Thread (ts);
				t.start();
			}
		} catch (IOException e) {
			
		}
		
	}
	
	public boolean enviarArchivoBuffer(Mensaje m, ConexionTCP cliente) throws InterruptedException 
	{
		synchronized(this) {
		    try {
				File archivo = new File(m.pathAParte);//Busca la parte del archivo (si no es seed) o el archivo completo (si es seed)
				int pieceSize = 1024*1024;
				int tamañoParteBuscada = m.sizeParte;//1MB menos la última parte.
				byte[] piece = new byte[pieceSize];
				int bytesread;
				
		        FileInputStream fis = new FileInputStream(archivo);
		        BufferedInputStream in = new BufferedInputStream(fis);
		        
		        if(m.seed) {
		        	int parte = m.nroParte;
		            while (parte>0 && (bytesread = in.read(piece,0,pieceSize)) != -1) {	    
		            	parte--;
		            }		    				
		        	bytesread = in.read(piece,0,pieceSize);//Si es seed, es el file completo. Leo la parte correspondiente a la pieza que necesito.
		        }
		        else {
		        	bytesread = in.read(piece,0,pieceSize);//Si no es seed, es un file de la pieza. Lo leo completo de 0 a 1MB
		        }
		        
		        cliente.getOutBuff().write(piece,0,bytesread);
		        cliente.getOutBuff().flush();
		        
		        in.close();//Cierro el buffer de lectura del JSON
		        
		        return false;
		    }catch (IOException e) {
		    	this.logger.error("Fallo durante envío de la parte del archivo.");
		    	return true;
		    }
		}
	}
	
	public ConexionTCP nodeAvailable(String ip, int port) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible
        	ConexionTCP c = new ConexionTCP(ip,port);
        	Mensaje msj = new Mensaje(Mensaje.Tipo.CHECK_AVAILABLE, this.kPub);
        	c.getOutObj().writeObject(msj);
        	
        	Mensaje response = (Mensaje) c.getInObj().readObject();
	    	if (response.tipo == Mensaje.Tipo.ACK) {
	    		//desencripto con la clave privada la clave simetrica
		        byte[] msgDesencriptado = kg.desencriptarAsimetrico(response.keyEncriptada, this.kPriv);
		        SecretKey key = (SecretKey) c.convertFromBytes(msgDesencriptado);
		        c.setKey(key);
		        return c;
        	}else {
        		c.getSocket().close();
        		return null;
        	}   
        	
        } catch (IOException | ClassNotFoundException ex) {
        	 return null;
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
    
    public ConexionTCP getTracker() throws Exception {
    	boolean trackerDisponible = false;
    	synchronized(this) {
	    	readtrackerJSON();//Lleno la listatracker antes de buscar un tracker
	    	ConexionTCP c = null;
			int i = 0;
	    	while(i<listaTrackers.size() && !trackerDisponible) {
	    		int port = listaTrackers.get(i).getPort();
	    		String ip = listaTrackers.get(i).getIp();
	    		c = nodeAvailable(ip,port);
	    		if(c != null) {//Intenta conectarse al socket del tracker.
	    			return c;
	    		}
	    		i++;
	    	}
    	}
		return null;
	}

	public void run() {
		this.StartServer();		
	}
	
}