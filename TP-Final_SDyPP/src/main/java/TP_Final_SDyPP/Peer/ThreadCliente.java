package TP_Final_SDyPP.Peer;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Peer.ParteArchivo.Estado;

public class ThreadCliente implements Runnable {

	private ArrayList<DescargaPendiente> listaDescargasPendientes;
	private ArrayList<SeedTable> swarm = new ArrayList<SeedTable>();
	private ArrayList<SeedTable> peersDisponibles = new ArrayList<SeedTable>();
	private String pathPartes;//Path donde almaceno partes descargadas y JSONPartes
	private ArrayList<ParteArchivo> partesArchivo = new ArrayList<ParteArchivo>();
	private String name;//del archivo a descargar
	private int cantPartes;
	private Servidor servidor;
	private String hash;
	private String ipExterna;
	private int portExterno;
	private KeysGenerator kg;
	private boolean stop;
	private boolean pausado;
	public Logger logger;
	
	//Getters & Setters//
	public boolean getStop() {
		return this.stop;
	}
	
	public String getIpExterna() {
		return ipExterna;
	}

	public void setIpExterna(String ipExterna) {
		this.ipExterna = ipExterna;
	}

	public int getPortExterno() {
		return portExterno;
	}

	public void setPortExterno(int portExterno) {
		this.portExterno = portExterno;
	}
	
	public String getHash() {
		return hash;
	}
	
	public Servidor getServidor() {
		return servidor;
	}
	
	public int getCantPartes() {
		return cantPartes;
	}

	public void setCantPartes(int cantPartes) {
		this.cantPartes = cantPartes;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	
	public ArrayList<SeedTable> getPeersDisponibles() {
		synchronized(this.peersDisponibles) {
			return peersDisponibles;
		}
	}

	public void setPeersDisponibles(ArrayList<SeedTable> swarm) {
		synchronized(this.peersDisponibles) {
			for(SeedTable s : swarm) {
				this.peersDisponibles.add(s);	
			}
		}
	}
	
	public ArrayList<SeedTable> getSwarm() {
		synchronized(this.swarm) {
			return swarm;
		}
	}

	public void setSwarm(ArrayList<SeedTable> swarm) {
		synchronized(this.swarm) {
			this.swarm = swarm;
		}
	}

	public String getPathPartes() {
		return pathPartes;
	}

	public void setPathPartes(String pathPartes) {
		this.pathPartes = pathPartes;
	}

	public ArrayList<ParteArchivo> getPartesArchivo() {
		synchronized(this.partesArchivo) {
			return partesArchivo;
		}
	}

	public void setPartesArchivo(ArrayList<ParteArchivo> partesArchivo) {
		this.partesArchivo = partesArchivo;
	}
	
	//Constructor//
	public ThreadCliente(ArrayList<SeedTable> swarm, String path, String name, int cantPartes, Servidor s, String hash, String ip, int port, ArrayList<DescargaPendiente> listaDescargasPendientes) {
		this.listaDescargasPendientes = listaDescargasPendientes;
		this.swarm = swarm;
		this.setPeersDisponibles(swarm);
		this.pathPartes = path;
		this.name = name;
		this.cantPartes = cantPartes;
		this.servidor = s;
		this.hash = hash;
		this.ipExterna = ip;
		this.portExterno = port;
		this.kg = new KeysGenerator();
		this.stop = false;
		this.pausado = false;
		
		String filename = "logDescarga-"+name;
		System.setProperty("logFilename", filename);
		logger = LogManager.getLogger(ThreadCliente.class);
	}

	private boolean inicializarSwarm() {
		Object obj;
		
		try {
			obj = new JSONParser().parse(new FileReader(this.pathPartes+"/"+name+"Partes.json"));
			JSONArray ja = (JSONArray) obj;
	    	for(int i=0; i<ja.size(); i++) {
	    		JSONObject jo = (JSONObject) ja.get(i);
	    		if(jo.get("estado").equals("pendiente")) {
	    			
	    			String parteS = (String) jo.get("parte");
	    			int parte = Integer.parseInt(parteS);
	               
		    		String hash = (String) jo.get("hash");
		    		
		    		String sizeS = (String) jo.get("size");
	    			int size = Integer.parseInt(sizeS);
		    		
		    		ParteArchivo pa = new ParteArchivo(parte,hash,Estado.PENDIENTE,size);
		    		this.partesArchivo.add(pa);	
	    		}
	    	}
	    	//mezclar partes en array
	    	Collections.shuffle(partesArchivo);
	    	return true;
		} catch (IOException | ParseException e) {
			logger.error("Falló lectura json con partes faltantes.");
			e.printStackTrace();
			return false;
		}
				
	}

	@Override
	public void run() {
		boolean exito = this.inicializarSwarm();
		if(exito) {
			logger.info("Descarga iniciada.");
			ArrayList<Thread> threads = new ArrayList<Thread>();
			
			for(int i=0; i<10; i++)	
			{
				try {
					ThreadLeecher tl = new ThreadLeecher(this);
					Thread t = new Thread(tl);
					threads.add(t);
					t.start();
				} catch (Exception e) {
					logger.error("Fallo al iniciar thread leecher.");
					e.printStackTrace();
				}	
			}
			
			//Inicio thread que pregunta cada x segundos porcentaje disponible del archivo
			ThreadPartesDisponibles tpd = new ThreadPartesDisponibles(this);
			Thread t = new Thread(tpd);
			t.start();
			threads.add(t);
			
			//El threadCliente termina cuando hayan terminado los threadLeecher de descargar el archivo completo
			for (int i=0; i<threads.size(); i++) {
			    try {
					threads.get(i).join();
				} catch (InterruptedException e) {
					logger.error("ThreadCliente: Fallo en join de leecher.");
					e.printStackTrace();
				}
			}
			
			if(!this.stop) {//Si no se pauso la descarga
				//Armar archivo
				try {
					this.armarFile(this.pathPartes + "/" + this.name + ".0",this.pathPartes + "/" + this.name);
				} catch (IOException e1) {
					logger.error("Falló armar archivo descargado.");
					e1.printStackTrace();
				}
				
				//Soy seed
				ConexionTCP c = null;
				try {
					c = this.servidor.getTracker();
					if(c!=null) {
						Mensaje m = new Mensaje(Mensaje.Tipo.NEW_SEED, this.ipExterna, this.portExterno, hash);
						//encripto mensaje con la clave simetrica
						byte[] datosAEncriptar = c.convertToBytes(m);
						byte[] mensajeEncriptado = kg.encriptarSimetrico(c.getKey(), datosAEncriptar);
						c.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
						c.getOutBuff().flush();
						c.getSocket().close();
					}else {
						logger.error("Fallo al notificar a tracker que soy un nuevo seed.");
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				logger.info("Descarga finalizada.");
				
				//Eliminar partes descargadas
				this.eliminarPartes(this.pathPartes + "/" + this.name + ".0");
				
				//Eliminar archivo de "Descargas pendientes"
				File file = new File("Descargas pendientes/"+name+".json");
				file.delete();
				//Retiro a Descarga pendiente de array
				int i=0;
				synchronized(this.listaDescargasPendientes) {
					while(i<this.listaDescargasPendientes.size()){
						if(this.listaDescargasPendientes.get(i).getName().equals(name)) {
							this.listaDescargasPendientes.remove(i);
						}
						i++;
					}	
				}	
			}else {
				this.pausado = true;
			}
		}
	}

	public void actualizarPartesPendientes(int nroParte) {
		Object obj;
		synchronized(this.getPathPartes()) {
			try {
				obj = new JSONParser().parse(new FileReader(this.pathPartes+"/"+name+"Partes.json"));
				JSONArray ja = (JSONArray) obj;
				JSONObject parte = (JSONObject) ja.get(nroParte);
				parte.put("estado", "descargada");
		    	
	    		File file = new File(this.pathPartes+"/"+name+"Partes.json");
	    		file.createNewFile();
				FileWriter fileW = new FileWriter(file);
				fileW.write(ja.toJSONString());
				fileW.flush();
				fileW.close();
	
			} catch (IOException | ParseException e) {
				logger.error("Falló actualizar parte pendiente a descargada en archivo \""+name+"Partes.json\".");
				e.printStackTrace();
			}
		}
	}
	
	public boolean getSecretKey(ConexionTCP conexTCP, boolean mantenerConexion) throws Exception {//Compruebo que el nodo destino   
        try {										   					//este disponible y le envío mi Clave Pública
        	Mensaje msg = new Mensaje(Mensaje.Tipo.CHECK_AVAILABLE, this.getServidor().getKpub(), mantenerConexion);
        	conexTCP.getOutObj().writeObject(msg);
        	
        	Mensaje response = (Mensaje) conexTCP.getInObj().readObject();
	    	if (response.tipo == Mensaje.Tipo.ACK) {
	    		//desencripto con la clave privada la clave simetrica
		        byte[] msgDesencriptado = this.servidor.getKG().desencriptarAsimetrico(response.keyEncriptada, this.servidor.getKpriv());
		        SecretKey key = (SecretKey) conexTCP.convertFromBytes(msgDesencriptado);
		        conexTCP.setKey(key);
		        return true;
	    	}  
	    	return false;
        } catch (IOException | ClassNotFoundException ex) {
        	conexTCP.getSocket().close();
        	return false;
        }
    }
	
	//Recontruir archivo descargado
	private void armarFile(String oneOfFiles, String into) throws IOException{
    	armarFile(new File(oneOfFiles), new File(into));
    }
	
	private void armarFile(File oneOfFiles, File into)
            throws IOException {
    	armarFile(listOfFilesToMerge(oneOfFiles), into);
    }

	private List<File> listOfFilesToMerge(File oneOfFiles) {
        String tmpName = oneOfFiles.getName();
        String destFileName = tmpName.substring(0, tmpName.lastIndexOf('.'));
        File[] files = oneOfFiles.getParentFile().listFiles(
                (File dir, String name) -> name.matches(destFileName + "[.]\\d+"));
        Arrays.sort(files);
        return Arrays.asList(files);
    }
	
	private void armarFile(List<File> files, File into)
	        throws IOException {
	    try (FileOutputStream fos = new FileOutputStream(into);
	         BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {
	        for (File f : files) {
	            Files.copy(f.toPath(), mergingStream);
	        }
	    }
	}
	
	private void eliminarPartes(String parte) {
		List<File> files = listOfFilesToMerge(new File(parte));
		for(File f : files) {
			f.delete();
		}
	}

	public void stop() {
		this.stop = true;
		while(!this.pausado) {}//Espero a que finalice el run correctamente, en caso que stop sea llamado durante el cierre de la app.
	}
}
