package TP_Final_SDyPP.Peer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

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


public class ThreadServer implements Runnable {
	private ConexionTCP conexTCP;
	private Servidor servidor;
	private KeysGenerator kg;
	
	public ThreadServer(ConexionTCP c, Servidor servidor) {
		this.conexTCP = c;	
		this.servidor = servidor;
		this.kg = new KeysGenerator();
	}

	public void run() {
		try {
			Object o = conexTCP.getInObj().readObject();//Recibo mensaje de peer cliente o Tracker
			Mensaje response = null;
			if (o instanceof Mensaje)
			{
				Mensaje m = (Mensaje) o;
				
				switch(m.tipo) {
					case CHECK_AVAILABLE: 
						if(!this.enviarACK(conexTCP, m, response))
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
				
				int msgSize = 1024*1024;//1MB
		        byte[] buffer = new byte[msgSize];
		        int byteread = conexTCP.getInBuff().read(buffer, 0, msgSize);
		        //desencripto con la clave simetrica
		        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
		        byte[] msgDesencriptado = kg.desencriptarSimetrico(conexTCP.getKey(), datosEncriptados);
		        Object o = conexTCP.convertFromBytes(msgDesencriptado);
				
		        Mensaje response = null;
				if (o instanceof Mensaje)
				{
					Mensaje m = (Mensaje) o;
					
					switch(m.tipo) {
					
						case PIECES_AVAILABLE_CLOSE:
							if(!this.getPiecesAvailable(conexTCP, m, response)) 
								conexTCP.getSocket().close();
							salir = true;
							break;
					
						case PIECES_AVAILABLE:
							if(this.getPiecesAvailable(conexTCP, m, response)) {
								this.servidor.decrementarConexiones();
								conexTCP.getSocket().close();
								this.retirarmeDeSwarm(m.hash, response);//no posee el archivo con las partes disponibles. No debo pertenecer más al swarm.
								salir = true;
							}
							break;
							
						case GET_PIECE:
							if(this.getPiece(conexTCP, m, response)) {
								this.servidor.decrementarConexiones();
								conexTCP.getSocket().close();
								this.retirarmeDeSwarm(m.hash, response);//no posee la parte que decía tener. No debo pertenecer más al swarm.
								salir = true;
							}
							break;
							
						case LOADDOWN:
							this.servidor.decrementarConexiones();
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
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
	}
	
	private boolean getPiece(ConexionTCP cliente, Mensaje m, Mensaje response) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, InterruptedException {				
		return (this.servidor.enviarArchivoBuffer(m, cliente));		
	}

	private boolean getPiecesAvailable(ConexionTCP cliente, Mensaje msg, Mensaje response) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		Object obj;
		try {
			obj = new JSONParser().parse(new FileReader(msg.string));
			
			JSONArray ja = (JSONArray) obj;
			
			Integer[] misPartes = new Integer[ja.size()];
			
	    	for(int i=0; i<ja.size(); i++) {
	    		JSONObject jo = (JSONObject) ja.get(i);
	    		misPartes[i] = (jo.get("estado").equals("pendiente")) ? 0 : 1;//si tengo la parte es 1
	    	}
	    	
	    	obj = misPartes;
	    	response = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE, obj);
	    	
	    	//encripto mensaje con la clave simetrica
			byte[] datosAEncriptar = cliente.convertToBytes(response);
			byte[] mensajeEncriptado = kg.encriptarSimetrico(cliente.getKey(), datosAEncriptar);
			cliente.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			cliente.getOutBuff().flush();
	    	
	    	return false;
			
		} catch (IOException | ParseException e1) {
			this.servidor.logger.error("Falló al leer json con partes archivo en servidor.");
			e1.printStackTrace();
			response = new Mensaje(Mensaje.Tipo.ERROR);
	    	try {
	    		//encripto mensaje con la clave simetrica
				byte[] datosAEncriptar = cliente.convertToBytes(response);
				byte[] mensajeEncriptado = kg.encriptarSimetrico(cliente.getKey(), datosAEncriptar);
				cliente.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
				cliente.getOutBuff().flush();
				
				cliente.getSocket().close();
			} catch (IOException e) {
				this.servidor.logger.error("Falló enviar error desde peer servidor.");
				e.printStackTrace();
			}
	 
	    	return true;
		}
	}

	private void retirarmeDeSwarm(String hash, Mensaje response) {
		//Obtener tracker
		try {
			//obtengo trakcer
			conexTCP = this.servidor.getTracker();
    		
    		if(conexTCP!=null) {
				//enviar mi peer socket y hash del archivo
				response = new Mensaje(Mensaje.Tipo.QUIT_SWARM, this.servidor.getIpExterna(), this.servidor.getPortExterno(), hash);
				//encripto mensaje con la clave simetrica
				byte[] datosAEncriptar = conexTCP.convertToBytes(response);
				byte[] mensajeEncriptado = kg.encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
				conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
				conexTCP.getOutBuff().flush();
				conexTCP.getSocket().close();	
			}
			
		} catch (Exception e) {
			this.servidor.logger.error("Fallo al  obtener tracker por peer servidor al intentar retirarme del swarm.");
			e.printStackTrace();
		}		
	}

	//Método que envía un ACK para confirmar que está activo
	private boolean enviarACK (ConexionTCP ctcp, Mensaje m, Mensaje response) throws Exception  {
		//Creo clave simetrica, usada para encriptar mensajes entre peer servidor y peer cliente
		SecretKey key = kg.generarLlaveSimetrica();
		ctcp.setKey(key);
		if(!m.mantenerConexion) {			
			byte[] datosAEncriptar = ctcp.convertToBytes(ctcp.getKey());
			byte[] mensajeEncriptado = kg.encriptarAsimetrico(datosAEncriptar, m.kpub);
			//Agrego key al mensaje
			response = new Mensaje(Mensaje.Tipo.ACK, mensajeEncriptado);
			
			ctcp.getOutObj().writeObject(response);
			return false;
		}else if(this.servidor.getNumConexiones() < this.servidor.getNumConexionesMaximas()) {
			this.servidor.aumentarConexiones();//Suma en 1 conexiones salientes del peer
			//Creo clave simetrica, usada para encriptar mensajes entre peer servidor y peer cliente
			
			byte[] datosAEncriptar = ctcp.convertToBytes(ctcp.getKey());
			byte[] mensajeEncriptado = kg.encriptarAsimetrico(datosAEncriptar, m.kpub);
			//Agrego key al mensaje
			response = new Mensaje(Mensaje.Tipo.ACK, mensajeEncriptado);
			
			ctcp.getOutObj().writeObject(response);
			return false;
		}else {
			response = new Mensaje(Mensaje.Tipo.ERROR);
			
			//encripto mensaje con la clave pública del peer cliente
			byte[] datosAEncriptar = ctcp.convertToBytes(response);
			byte[] mensajeEncriptado = kg.encriptarAsimetrico(datosAEncriptar, m.kpub);
			
			ctcp.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			ctcp.getOutBuff().flush();
			ctcp.getSocket().close();
			return true;//salir == true
		}
	}
}
