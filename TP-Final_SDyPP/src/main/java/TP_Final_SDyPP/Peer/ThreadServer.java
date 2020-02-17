package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;
import javafx.application.Platform;


public class ThreadServer implements Runnable {
	private ConexionTCP conexionTCP;
	private Servidor servidor;
	private TrackerManager tm;
	private KeysGenerator kg;
	private String log;
	
	public ThreadServer(ConexionTCP c, Servidor servidor) {
		this.conexionTCP = c;	
		this.servidor = servidor;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
	}

	public void run() {
		try {
			Object o = conexionTCP.getInObj().readObject();//Recibo mensaje de peer cliente o Tracker
			Mensaje response = null;
			if (o instanceof Mensaje)
			{
				Mensaje m = (Mensaje) o;
				
				switch(m.tipo) {
					case CHECK_AVAILABLE: 
						log = "Recibi mensaje CHECK AVAILABLE";
						this.servidor.logger.info(log);
						System.err.println(log);
						if(!this.enviarACK(m, response))
							this.encriptados();
						break;
				}					
			}		
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e){
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void encriptados() {//recibo mensajes encriptados
		boolean salir = false;
		while(!salir) {
			try {
				
				Mensaje m = new Mensaje();
				byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
		        Object o = conexionTCP.convertFromBytes(msgDesencriptado);
				
		        Mensaje response = null;
				if (o instanceof Mensaje)
				{
					m = (Mensaje) o;
					
					switch(m.tipo) {
					
						case PIECES_AVAILABLE_CLOSE:
							log = "Recibi mensaje PIECES_AVAILABLE_CLOSE";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							this.getPiecesAvailable(conexionTCP, m, response); 
							conexionTCP.getSocket().close();
							salir = true;
							break;
					
						case PIECES_AVAILABLE:
							log = "Recibi mensaje PIECES_AVAILABLE";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							switch(this.getPiecesAvailable(conexionTCP, m, response)) {
							case 1://Todo ok
								break;
								
							case 2://No posee archivo, se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener archivo con piezas disponibles.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								this.retirarmeDeSwarm(m.hash, response);//no posee el archivo con las partes disponibles. No debo pertenecer más al swarm.
								salir = true;
								break;
								
							case 3://Error. No se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener archivo con piezas disponibles.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								salir = true;
								break;
							}
							break;
							
						case GET_PIECE:
							log = "Recibi mensaje GET_PIECE";
							this.servidor.logger.info(log);
							
							switch(this.getPiece(conexionTCP, m)) {
							case 1://Todo ok
								break;
								
							case 2://No posee archivo, se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "No posee el archivo con la pieza.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								this.retirarmeDeSwarm(m.hash, response);//no posee el archivo con las partes disponibles. No debo pertenecer más al swarm.
								salir = true;
								break;
								
							case 3://Error. No se retira del swarm al peer
								this.servidor.decrementarConexiones();
								conexionTCP.getSocket().close();
								
								log = "Fallo al obtener pieza.";
								this.servidor.logger.error(log);
								System.err.println(log);
								
								salir = true;
								break;
							}
							break;
							
						case LOADDOWN:
							log = "Recibi mensaje LOADDOWN";
							this.servidor.logger.info(log);
							System.err.println(log);
							
							this.servidor.decrementarConexiones();
							conexionTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
					}					
				}		
			} catch (IOException e) {
				salir = true;
				this.exception(e);
			} catch (ClassNotFoundException e){
				salir = true;
				this.exception(e);
			} catch (Exception e) {
				salir = true;
				this.exception(e);
			}	
		}	
	}
	
	private void exception(Exception e) {
		e.printStackTrace();
		this.servidor.decrementarConexiones();
		
		try {
			conexionTCP.getSocket().close();
		} catch (IOException e1) {
			log = "Fallo por intentar cerrar socket";
			this.servidor.logger.error(log);
			System.err.println(log);
		}		
	}

	private int getPiece(ConexionTCP cliente, Mensaje m) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InterruptedException, ClassNotFoundException, IOException {	
		try {
			Thread.sleep(100);//Necesario en pruebas locales
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
			
	    try {
	    	log = "m.pathAParte: nombre del archivo buscado en peer servidor:"+m.pathAParte;
	    	this.servidor.logger.info(log);
			File archivo = new File(m.pathAParte);//Busca la parte del archivo (si no es seed) o el archivo completo (si es seed)
			
			if(!archivo.exists()) {
				if(m.seed) {//Si soy seed y no encuentro el file significa que el peer servidor borró o movió el file. Dejara de ser seed
					return 2;
				} else {//Si no soy seed y no encuentra la parte que decía tener: puede ser porque este peer "no seed" terminó la
						//descarga del file, eliminando las partes y formando un file único. 
						//Pregunto si tiene el file
					String path = m.pathAParte.substring(0, m.pathAParte.lastIndexOf('.'));
					archivo = new File(m.pathAParte);
					if(!archivo.exists()) {
						return 2;
					}
					//Ahora es seed
					m.seed = true;
				}
			} 
			
			int pieceSize = 1024*1024;
			byte[] piece = new byte[pieceSize];
			int tamañoParteBuscada = m.sizeParte;//1MB, menos la última parte.
			int bytesread;
			
	        FileInputStream fis = new FileInputStream(archivo);
	        BufferedInputStream in = new BufferedInputStream(fis);
	        
	        if(m.seed) {
	        	int parte = m.nroParte;
	            while (parte>0 && (bytesread = in.read(piece,0,pieceSize)) != -1) {	    
	            	parte--;
	            }
				byte[] buffer = new byte[1024];
				int count;
				boolean continuar = true;
				while(tamañoParteBuscada>=1024 && continuar) {
					if((count = in.read(buffer)) > 0) {
						cliente.getOutBuff().write(buffer,0,count);
						tamañoParteBuscada-=count;	
					}else {
						continuar = false;
					}
				}
				count = in.read(buffer,0,tamañoParteBuscada);//Leo el resto que me falta
				cliente.getOutBuff().write(buffer,0,count);
	        }
	        else {
				byte[] buffer = new byte[1024];
				int count;
				while ((count = in.read(buffer)) > 0)
				{
					cliente.getOutBuff().write(buffer,0,count);
				}
	        }
			
	        cliente.getOutBuff().flush();
	        
	        in.close();//Cierro el buffer de lectura del JSON
	        fis.close();
	        
	        return 1;
	    }catch (IOException e) {
	    	return 3;
	    }
	}

	private int getPiecesAvailable(ConexionTCP cliente, Mensaje msg, Mensaje response) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		Object obj;
		try {
			log = "Nombre del archivo buscado en peer servidor:"+msg.string;
			this.servidor.logger.info(log);
			System.err.println(log);
			
			File file = new File(msg.string);
			if(!file.exists()) {
				response = new Mensaje(Mensaje.Tipo.FILE_UNAVAILABLE);
		    	response.enviarMensaje(cliente, response, kg);
				
				return 2;
			}
			
			FileReader fileReader = new FileReader(msg.string);
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			
			JSONArray ja = (JSONArray) obj;
			
			Integer[] misPartes = new Integer[ja.size()];
			
	    	for(int i=0; i<ja.size(); i++) {
	    		JSONObject jo = (JSONObject) ja.get(i);
	    		misPartes[i] = (jo.get("estado").equals("pendiente")) ? 0 : 1;//si tengo la parte es 1
	    	}
	    	
	    	obj = misPartes;
	    	response = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE, obj);
	    	response.enviarMensaje(cliente, response, kg);
	    	return 1;
			
		} catch (IOException | ParseException e1) {
			log = "Fallo al leer json con partes archivo en servidor.";
			this.servidor.logger.error(log);
			System.err.println(log);
			e1.printStackTrace();
			
			response = new Mensaje(Mensaje.Tipo.ERROR);
	    	try {
	    		response.enviarMensaje(cliente, response, kg);
			} catch (IOException e) {
				log = "Fallo enviar error desde peer servidor.";
				this.servidor.logger.error(log);
				System.err.println(log);
				e.printStackTrace();
			}
			
	    	return 3;
		}
	}

	private void retirarmeDeSwarm(String hash, Mensaje response) {
		//Obtener tracker
		try {
			//obtengo trakcer
			conexionTCP = tm.getTracker(kg, this.servidor.getKpub(), this.servidor.getKpriv());
    		
    		if(conexionTCP!=null) {
				//enviar mi peer socket y hash del archivo
				response = new Mensaje(Mensaje.Tipo.QUIT_SWARM, this.servidor.getIpExterna(), this.servidor.getPortExterno(), hash);
				response.enviarMensaje(conexionTCP, response, kg);
				conexionTCP.getSocket().close();
				
				log = "Peer servidor eliminado del swarm.";
				this.servidor.logger.error(log);
				System.err.println(log);
			}
			
		} catch (Exception e) {
			log = "Fallo al obtener tracker por peer servidor al intentar retirarme del swarm.";
			this.servidor.logger.error(log);
			System.err.println(log);
			e.printStackTrace();
		}		
	}

	//Método que envía un ACK para confirmar que está activo
	private boolean enviarACK (Mensaje m, Mensaje response) throws Exception  {
		//Creo clave simetrica, usada para encriptar mensajes entre peer servidor y peer cliente
		SecretKey key = kg.generarLlaveSimetrica();
		conexionTCP.setKey(key);
		if(!m.mantenerConexion) {
			this.enviarMensaje(Mensaje.Tipo.ACK,m,response);
			return false;
		}else if(this.servidor.getNumConexiones() < this.servidor.getNumConexionesMaximas()) {
			this.servidor.aumentarConexiones();//Suma en 1 conexiones salientes del peer
			this.enviarMensaje(Mensaje.Tipo.ACK,m,response);
			return false;
		}else {
			this.enviarMensaje(Mensaje.Tipo.ERROR,m,response);
			return true;//salir == true
		}
	}
	
	private void enviarMensaje(Mensaje.Tipo tipoMensaje, Mensaje m, Mensaje response) throws IOException {
		switch(tipoMensaje) {
			case ACK:				
				byte[] datosAEncriptar = conexionTCP.convertToBytes(conexionTCP.getKey());
				byte[] mensajeEncriptado = kg.encriptarAsimetrico(datosAEncriptar, m.kpub);
				//Agrego key al mensaje
				response = new Mensaje(Mensaje.Tipo.ACK, mensajeEncriptado);
				conexionTCP.getOutObj().writeObject(response);
				log = "Respondi a mensaje CHECK AVAILABLE con ACK";
				this.servidor.logger.info(log);				
				break;
				
			case ERROR:
				response = new Mensaje(Mensaje.Tipo.ERROR);
				conexionTCP.getOutObj().writeObject(response);
				conexionTCP.getSocket().close();
				log = "Respondio a mensaje CHECK AVAILABLE con ERROR (peer servidor supera conexiones maximas)";
				this.servidor.logger.info(log);
				break;
		}
	}

}
