package TP_Final_SDyPP.Peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.logging.log4j.Logger;

import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;

public class Servidor implements Runnable {
	
	private int port;
	private int portExterno;
	private String ipExterna;
	private PublicKey kPub;
	private PrivateKey kPriv;
	private KeysGenerator kg;
	private final int conexionesMaximas = 5; 
	private int conexionesSalientes = 0;
	private TrackerManager tm;
	private boolean stop;
	private String log;
	private ServerSocket socketServidor = null;
	public Logger logger;
		
	//Constructor por defecto
	public Servidor(int puertoEscucha, int portExterno, String ipExterna, PublicKey publicKey, 
			PrivateKey privateKey, Logger logger) {
		this.logger = logger;
		this.port = puertoEscucha;
		this.portExterno = portExterno;
		this.ipExterna = ipExterna;
		this.kPub = publicKey;
		this.kPriv = privateKey;
		this.tm = new TrackerManager();
		this.kg = new KeysGenerator();
		this.stop = false;
	}
	
	public void setStop(boolean estado) throws IOException {
		if(this.socketServidor!=null) {
			try {
                new Socket(socketServidor.getInetAddress(), socketServidor.getLocalPort()).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
		}

		this.stop = estado;
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
			System.err.println("Numero Conexiones: "+ this.conexionesSalientes);
			if(this.conexionesSalientes >= this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me agregue a su array de peers que no pueden ser devueltos en un swarm.
				System.err.println("Envio max_conexiones ocupadas");
				this.mensajeTracker(Mensaje.Tipo.COMPLETE);
			}
		}
	}
	
	public void decrementarConexiones() {
		synchronized(this) {
			if(this.conexionesSalientes == this.conexionesMaximas) {
				//envio mi peer ID a tracker para que me retire de su array de peers que no pueden ser devueltos en un swarm.
				System.err.println("Envio max_conexiones desocupadas");
				this.mensajeTracker(Mensaje.Tipo.FREE);
			}
			this.conexionesSalientes--;
			System.err.println("Numero Conexiones: "+ this.conexionesSalientes);
		}
	}
	
	public void mensajeTracker(Mensaje.Tipo tipoMensaje) {
		ConexionTCP c = null;
		
		try {
			c = tm.getTracker(kg, kPub, kPriv);
			
			if(c!=null) {
				Mensaje m = new Mensaje(tipoMensaje, this.ipExterna, this.portExterno);
				m.enviarMensaje(c, m, kg);
				c.getSocket().close();
			} else {
				log = "No hay trackers disponibles.";
				logger.error(log);
				System.out.println(log);
			}
			
		} catch (Exception e) {
			log = "Fallo obtención de tracker.";
			this.logger.error(log);
			System.err.println(log);
			e.printStackTrace();
		}
	}
	
	public void StartServer() {
		
		try {			
			//Abrimos un Socket en el Servidor en el puerto especificado para poder establecer conexiones con el cliente
			this.socketServidor = new ServerSocket (this.port);
			
			while (!stop) {				
				//Quedamos a la espera de conexiones por los clientes
				Socket socketClient = socketServidor.accept();
				ConexionTCP conexTCP = null;
				try {
					conexTCP = new ConexionTCP(socketClient);
				} catch (Exception e) {
					log = "Fallo al crear ConexionTCP con socket cliente en servidor";
					this.logger.error(log);
					System.err.println(log);
					e.printStackTrace();
				}

				log = "Conexion establecida con cliente: ("+socketClient.getInetAddress().getCanonicalHostName()+":"+socketClient.getPort()+")";
				this.logger.info(log);
				System.err.println(log);
				
				ThreadServer ts = new ThreadServer(conexTCP,this);
				Thread t = new Thread (ts);
				t.start();
			}
			logger.info("Servidor cerrado");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
		
	public void run() {
		this.StartServer();		
	}
	
}