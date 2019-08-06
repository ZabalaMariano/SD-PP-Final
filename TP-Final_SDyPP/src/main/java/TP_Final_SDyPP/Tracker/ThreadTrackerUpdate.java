package TP_Final_SDyPP.Tracker;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.Mensaje;

public class ThreadTrackerUpdate implements Runnable{

	private ArrayList<String> hashes = new ArrayList<String>();
	private Tracker tracker;
	
	public ThreadTrackerUpdate(ArrayList<String> hashes, Tracker tracker) {
		this.setHashes(hashes);
		this.setTracker(tracker);
	}

	public ArrayList<String> getHashes() {
		return hashes;
	}

	public void setHashes(ArrayList<String> hashes) {
		this.hashes = hashes;
	}
	
	public Tracker getTracker() {
		return this.tracker;
	}

	private void setTracker(Tracker tracker) {
		this.tracker = tracker;
	}
	
	//---RUN---//

	@Override
	public void run() {
		boolean fallo = false;
		boolean falloPrimario = false;
		//Establezco conexion con primario y obtengo clave simetrica
		ConexionTCP conexionTCP = null;
		try {
			
			conexionTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
			this.tracker.getSecretKey(conexionTCP);
			Mensaje m = null;
			
			//Recibo archivos que me falten
			int i = 0;
			while(!hashes.isEmpty()) {
				String hash = hashes.get(i);
				m = new Mensaje(Mensaje.Tipo.GET_FILE, hash); 
				try {				
					//encripto mensaje con la clave simetrica
					byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
					byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
					conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
					conexionTCP.getOutBuff().flush();
					
					int msgSize = 1024*1024;//1MB
			        byte[] buffer = new byte[msgSize];
			        int byteread = conexionTCP.getInBuff().read(buffer, 0, msgSize);
			        //desencripto con la clave simetrica
			        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
			        byte[] msgDesencriptado = this.tracker.getKG().desencriptarSimetrico(conexionTCP.getKey(), datosEncriptados);
			        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
					
					if(response.tipo == Mensaje.Tipo.ACK) {
						String path = this.getTracker().getPath() + "/" + hash + ".json"; 
						this.getTracker().guardarArchivoBuffer(conexionTCP, path);
						hashes.remove(hash);
					}
				} catch (Exception e) {
					fallo = true;
					this.tracker.logger.error("Falló recuperación de jsons de primario.");
					e.printStackTrace();
				}
			}
			
			if(!fallo) {
				//Obtengo tuplas de seedTable faltantes
				m = new Mensaje(Mensaje.Tipo.GET_DB);
				//encripto mensaje con la clave simetrica
				byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
				byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
				conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
				conexionTCP.getOutBuff().flush();
				
	        	String path = "database"+this.tracker.getId()+".db4o";
				this.getTracker().guardarArchivoBuffer(conexionTCP, path);
				
			}
			
			//Enviar EXIT para cerrar conexión
			m = new Mensaje(Mensaje.Tipo.EXIT); 				
			//encripto mensaje con la clave simetrica
			byte[] datosAEncriptar = conexionTCP.convertToBytes(m);
			byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexionTCP.getKey(), datosAEncriptar);
			conexionTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexionTCP.getOutBuff().flush();
			conexionTCP.getSocket().close();
			
		} catch (Exception e1) {
			fallo = true;
			falloPrimario = true;
			this.tracker.logger.error("Falló conexión con primario.");
			e1.printStackTrace();
		}
		
		if(falloPrimario) {
			try {
				this.tracker.getNuevoPrimario();
			} catch (Exception e) {
				this.tracker.logger.error("Falló obtener nuevo primario.");
				e.printStackTrace();
			}
		}
		
		if(fallo) {
			this.run();
		}
	}
	
}
