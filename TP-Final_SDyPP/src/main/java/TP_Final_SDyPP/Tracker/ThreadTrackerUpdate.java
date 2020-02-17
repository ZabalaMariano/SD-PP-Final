package TP_Final_SDyPP.Tracker;

import java.util.ArrayList;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;

public class ThreadTrackerUpdate implements Runnable{

	private ArrayList<String> hashes = new ArrayList<String>();
	private Tracker tracker;
	private KeysGenerator kg;
	private TrackerManager tm;
	private String ipPrimarioActual;
	private int portPrimarioActual;
	
	public ThreadTrackerUpdate(ArrayList<String> hashes, Tracker tracker, String ipPrimarioActual, int portPrimarioActual) {
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.ipPrimarioActual = ipPrimarioActual;
		this.portPrimarioActual = portPrimarioActual;
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
			
			conexionTCP = new ConexionTCP(this.ipPrimarioActual, this.portPrimarioActual);
			tm.getSecretKey(conexionTCP, true, this.tracker.getkPub(), this.tracker.getkPriv(), kg, this.tracker.logger);//Obtengo clave simetrica
			Mensaje m = null;
			this.tracker.logger.info("ThreadTrackerUpdate conexion con primario");
			//Recibo archivos que me falten
			int i = 0;
			while(!hashes.isEmpty()) {
				this.tracker.logger.info("Cantidad jsons por recuperar: "+hashes.size());
				String hash = hashes.get(i);
				m = new Mensaje(Mensaje.Tipo.GET_FILE, hash); 
				try {				
					m.enviarMensaje(conexionTCP, m, kg);
			        byte[] msgDesencriptado = m.recibirMensaje(conexionTCP, kg);
			        Mensaje response = (Mensaje) conexionTCP.convertFromBytes(msgDesencriptado);
					
					if(response.tipo == Mensaje.Tipo.ACK) {
						String path = this.getTracker().getPath() + "/" + hash + ".json"; 
						this.getTracker().guardarArchivoBuffer(conexionTCP, path);
						hashes.remove(hash);
					}
				} catch (Exception e) {
					fallo = true;
					this.tracker.logger.error("ThreadTrackerUpdate - Falló recuperación de json de primario.");
					e.printStackTrace();
				}
			}
			this.tracker.logger.error("ThreadTrackerUpdate - Sale del while despues de fallo recuperacion de json?.");
			if(!fallo) {
				//Obtengo tuplas de seedTable faltantes
				m = new Mensaje(Mensaje.Tipo.GET_DB);
				m.enviarMensaje(conexionTCP, m, kg);
				
	        	String path = "database"+this.tracker.getId()+".db4o";
				this.getTracker().guardarArchivoBuffer(conexionTCP, path);
			}
			
			//Enviar EXIT para cerrar conexión
			m = new Mensaje(Mensaje.Tipo.EXIT); 				
			m.enviarMensaje(conexionTCP, m, kg);
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
