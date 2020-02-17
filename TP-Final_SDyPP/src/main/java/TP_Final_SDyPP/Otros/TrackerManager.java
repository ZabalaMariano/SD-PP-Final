package TP_Final_SDyPP.Otros;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class TrackerManager {
	
	private ArrayList<TrackerInfo> listaTrackers;
	private String trackersJSON = "trackers.json";
	private String log;
	
	public TrackerManager(){
		this.listaTrackers = new ArrayList<TrackerInfo>();
	}
	
	public void actualizarJSONTrackers(ArrayList<TrackerInfo> listaTrackers, String trackersJSON, Logger logger) 
	{
		File trackers = new File(trackersJSON);
		trackers.delete();//Elimino el archivo de trackers existente
		
		String id = "";
		String port = "";
		
		JSONArray array = new JSONArray();
		for(TrackerInfo t : listaTrackers) {
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
			file = new FileWriter(trackersJSON);
			file.write(array.toJSONString());
			file.flush();
			file.close();
		} catch (IOException e) {
			log = "Fallo creacion de json con trackers.";
			logger.error(log);
			System.out.println(log);
			e.printStackTrace();
		}
	}
	
	public ConexionTCP nodeAvailable(String ip, int port, KeysGenerator kg, PublicKey kPub, PrivateKey kPriv) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible y me devuelve una clave simetrica
        	ConexionTCP c = new ConexionTCP(ip,port);
        	Mensaje msj = new Mensaje(Mensaje.Tipo.CHECK_AVAILABLE, kPub);
        	c.getOutObj().writeObject(msj);
        	
        	Mensaje response = (Mensaje) c.getInObj().readObject();
	    	if (response.tipo == Mensaje.Tipo.ACK) {
	    		//desencripto con la clave privada la clave simetrica
		        byte[] msgDesencriptado = kg.desencriptarAsimetrico(response.keyEncriptada, kPriv);
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

	public ArrayList<TrackerInfo> readtrackerJSON(ArrayList<TrackerInfo> listaTrackers) throws Exception  
    {  
    	String ip;
    	String port;
    	String id;
    	int idtracker;
    	int porttracker;
        Object obj = new JSONParser().parse(new FileReader(this.trackersJSON)); 
           
        JSONArray jo = (JSONArray) obj; 
          
        listaTrackers = new ArrayList<TrackerInfo>();
        TrackerInfo tracker;
        
        for(int i=0; i<jo.size(); i++){
        	
        	JSONObject jsonObject = (JSONObject) jo.get(i);
            id = (String) jsonObject.get("id");
            ip = (String) jsonObject.get("ip");
            port = (String) jsonObject.get("puerto");
      
            idtracker = Integer.parseInt(id);
            porttracker = Integer.parseInt(port);
            tracker = new TrackerInfo(idtracker, ip, porttracker);
            listaTrackers.add(tracker);
        }
        
        return listaTrackers;
    }  
	
    public ConexionTCP getTracker(KeysGenerator kg, PublicKey kPub, PrivateKey kPriv) throws Exception {
    	boolean trackerDisponible = false;
    	synchronized(this) {
    		ConexionTCP c = null;
    		listaTrackers = readtrackerJSON(listaTrackers);//Lleno la listatracker antes de buscar un tracker
			int i = 0;
	    	while(i<listaTrackers.size() && !trackerDisponible) {
	    		int port = listaTrackers.get(i).getPort();
	    		String ip = listaTrackers.get(i).getIp();
	    		c = nodeAvailable(ip,port,kg,kPub,kPriv);//Intenta conectarse al socket del tracker.
	    		if(c != null) {
	    			trackerDisponible = true;
	    		}
	    		i++;
	    	}

			return c;
    	}
	}
	
	public boolean getSecretKey(ConexionTCP conexTCP, boolean mantenerConexion, PublicKey kpub, PrivateKey kpriv, KeysGenerator kg, Logger logger) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible y le envío mi Clave Pública
        	Mensaje msg = new Mensaje(Mensaje.Tipo.CHECK_AVAILABLE, kpub, mantenerConexion);
        	conexTCP.getOutObj().writeObject(msg);
        	logger.info("Envio Check Available getsecretkey");
        	Mensaje response = (Mensaje) conexTCP.getInObj().readObject();
        	
	    	if (response.tipo == Mensaje.Tipo.ACK) {
	    		log = "Recibo ACK por Check Available getsecretkey";
	    		logger.info(log);
	    		
	    		//desencripto con la clave privada la clave simetrica
		        byte[] msgDesencriptado = kg.desencriptarAsimetrico(response.keyEncriptada, kpriv);
		        SecretKey key = (SecretKey) conexTCP.convertFromBytes(msgDesencriptado);
		        conexTCP.setKey(key);
		        return true;
	    	}
	    	conexTCP.getSocket().close();
	    	
	    	log = "Recibi Error (Max Conexiones en peer servidor) por Check Available (getSecretKey de ThreadCliente)";
    		logger.info(log);
    		System.out.println(log);
	    	return false;
        } catch (IOException | ClassNotFoundException ex) {
        	log = "Fallo getSecretKey";
        	logger.info(log);
        	System.out.println(log);
        	conexTCP.getSocket().close();
        	return false;
        }
    }


}
