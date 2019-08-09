package TP_Final_SDyPP.Tracker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.db4o.ObjectSet;

import TP_Final_SDyPP.DB4O.FileTable;
import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;


public class ThreadTracker implements Runnable {

	private Socket socket;
	private Tracker tracker;
	private ConexionTCP conexTCP;
	
	public ThreadTracker (Socket socketConexion, Tracker miTracker) {
		this.setSocket(socketConexion); //Almaceno el socket que recibí como parámetro
		this.setTracker(miTracker); //Almaceno la instancia de mi tracker que recibí como parámetro
	}
	
	public Socket getSocket() {
		return this.socket;
	}

	private void setSocket(Socket socket) {
		this.socket = socket;
	}

	public Tracker getTracker() {
		return this.tracker;
	}

	private void setTracker(Tracker tracker) {
		this.tracker = tracker;
	}
	
	//Run
	public void run() {
		try {			
			conexTCP = new ConexionTCP(this.getSocket()); //Inicializo los canales de lectura y escritura del socket recibido
			Object o = conexTCP.getInObj().readObject(); //Espero por un mensaje
			Mensaje response = null;
			ArrayList<SeedTable> swarm = null;
			Object obj = new Object();
			if ( o instanceof Mensaje ) {
				Mensaje msg = (Mensaje) o;
				switch (msg.tipo) {
				
				case ALIVE:
					response = new Mensaje(Mensaje.Tipo.ALIVE);
					conexTCP.getOutObj().writeObject(response);
					conexTCP.getSocket().close(); //Cierro la conexion
					break;
				
				case CHECK_AVAILABLE: 
					this.enviarACK(conexTCP,msg,response);
					this.encriptados(conexTCP);
					break;
					
				case GET_TRACKERS:
					//genero llave simetrica
					conexTCP.setKey(this.tracker.getKG().generarLlaveSimetrica());
					//encripto lista de trackers con las llave simetrica
					ArrayList<TrackerInfo> lista = this.tracker.getListaTrackers();
					byte[] listaAEncriptar = conexTCP.convertToBytes(lista);
					byte[] listaEncriptada = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(),listaAEncriptar);
					//encripto llave simetrica con la llave pública del peer
					byte[] llaveAEncriptar = conexTCP.convertToBytes(conexTCP.getKey());
					byte[] llaveEncriptada = this.tracker.getKG().encriptarAsimetrico(llaveAEncriptar,msg.kpub);
					//creo mensaje
					response = new Mensaje(Mensaje.Tipo.ACK,llaveEncriptada,listaEncriptada);
					//envío mensaje
					conexTCP.getOutObj().writeObject(response);
					conexTCP.getOutBuff().flush();
					
					conexTCP.getSocket().close(); //Cierro la conexion
					break;
					
				default:
					this.tracker.logger.error("MENSAJE INVALIDO!");
					conexTCP.getSocket().close();
					break;
				}	
			}else {
				this.tracker.logger.error("NO ES UN MENSAJE");
				conexTCP.getSocket().close();
			}
		} catch (Exception e) {
			this.tracker.logger.error("El peer se desconecto inesperadamente.");
		}
	}
	
	public void encriptados(ConexionTCP conexTCP) {//recibo mensajes encriptados
		boolean salir = false;
		while(!salir) {
			try {
				ArrayList<SeedTable> swarm = null;
				Object obj = new Object();
				byte[] datosAEncriptar;
				byte[] mensajeEncriptado;
				TrackerInfo yo;
				
				int msgSize = 1024*1024;//1MB
		        byte[] buffer = new byte[msgSize];
		        int byteread = conexTCP.getInBuff().read(buffer, 0, msgSize);
		        //desencripto con la clave simetrica
		        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
		        byte[] msgDesencriptado = this.tracker.getKG().desencriptarSimetrico(conexTCP.getKey(), datosEncriptados);
		        Object o = conexTCP.convertFromBytes(msgDesencriptado);
				
		        Mensaje response = null;
				if (o instanceof Mensaje)
				{
					Mensaje m = (Mensaje) o;
					
					switch(m.tipo) {
					
						case EXIT:
							conexTCP.getSocket().close();
							salir = true;
							break;
							
						case GET_DB:							
							String pathDB = "database"+this.tracker.getId()+".db4o";
							this.enviarArchivoBuffer(conexTCP, pathDB);
							break;
							
						case GET_FILE:
							this.getSocketPeer(conexTCP, m.string, response);
							String pathJSON = this.getTracker().getPath() + "/" + m.string + ".json";
							this.enviarArchivoBuffer(conexTCP, pathJSON);
							break;
							
						case GET_FILES_OFFERED:
							ArrayList<FileTable> archivos = this.tracker.getArchivosOfrecidos(m.ip,m.port);
							obj = archivos;
							response = new Mensaje(Mensaje.Tipo.GET_FILES_OFFERED, obj);
							
							//encripto mensaje con la clave simetrica
							datosAEncriptar = conexTCP.convertToBytes(response);
							mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
							conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
							conexTCP.getOutBuff().flush();
							conexTCP.getSocket().close();
							salir = true;
							break;		
					
						case GET_PRIMARIO:
							this.getTracker().devolverPrimario(conexTCP);
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case SET_PRIMARY:
							this.definirNuevoPrimario(conexTCP, m);
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case CHANGE_PRIMARY://Solo llega al primario
							//Si muchos Trackers informan a este tracker que es el nuevo primario solo escucho al primero.
							//el resto esperará y al terminar el bloqueo ya no harán efecto porque ya soy el primario. evito informar
							//repetidamente que soy el primario y el replicar.
							synchronized(this.getTracker().getLock()) {
								if(this.getTracker().getId() != this.getTracker().getTrackerPrimario().getId()) {//Si no soy el primario
									yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort());
									this.getTracker().setTrackerPrimario(yo); //Me defino como primario
									this.getTracker().informarSoyPrimario(yo); //Aviso a todos los demás trackers que soy el nuevo primario
									this.getTracker().replicar(m.tracker); //replico mi información en todos los trackers activos excepto el que me eligió como primario	
								}
							}
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case TRACKER_UPDATE: 	
							this.actualizarme(conexTCP, m);
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case TRACKER_REGISTER://Solo llega al primario
							//Sincronizado con mismo objeto (lock) que SEND_FILE, donde recibo archivo para guardar (de un peer).
							//Evita aceptar nuevos archivos mientras se registra nuevo tracker. De esta forma puedo indicarle
							//exactamente que jsons le hacen falta. Luego, el tracker abre de inmediato un socket para escuchar replicas del primario
							//por nuevos json que lleguen de peers.
							synchronized(this.getTracker().getLock()) {
								this.registrarTracker (conexTCP, m, response);
							}
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case SEND_FILE://Solo llega al primario
							synchronized(this.getTracker().getLock()) {
								this.guardarArchivo(conexTCP, m);
							}
							salir = true;
							break;
							
						case FIND_FILE:
							this.buscarArchivo(conexTCP, m);//busca archivos con nombre similar al buscado
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
							
						case DOWNLOAD:
							//Almaceno el nuevo seed
							this.getTracker().almacenarSeed(m.hash, false, m.path, m.ip, m.port);
							
							//envio swarm a peer
							swarm = this.getTracker().getSwarm(m.hash,m.ip, m.port);
							obj = swarm; 
							response = new Mensaje(Mensaje.Tipo.SWARM, obj);
							
							//encripto mensaje con la clave simetrica
							datosAEncriptar = conexTCP.convertToBytes(response);
							mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
							conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
							conexTCP.getOutBuff().flush();
							conexTCP.getSocket().close();
							
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicar");
							else {
								//envio seed a primario
								yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort()); 
								conexTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
								this.tracker.getSecretKey(conexTCP);
								response = new Mensaje(Mensaje.Tipo.SEND_SEED, m.hash, m.path, m.ip, m.port, yo);
								
								//encripto mensaje con la clave simetrica
								datosAEncriptar = conexTCP.convertToBytes(response);
								mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
								conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
								conexTCP.getOutBuff().flush();							
								conexTCP.getSocket().close();	
							}
							
							salir = true;
							break;
							
						case SEND_SEED:
							conexTCP.getSocket().close();
							
							//Almaceno el nuevo seed
							this.getTracker().almacenarSeed(m.hash, false, m.path, m.ip, m.port);
							//Replico el seed al resto si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicar");
							
							salir = true;
							break;
							
						case REPLICATE_FILE:
							//Guardo json en tracker
							String path = this.getTracker().getPath()+ "/" + m.string +".json"; //msg.string = nombreJSON (hash).
							boolean exito = this.getTracker().guardarArchivoBuffer(conexTCP, path);
							
							if(exito) {					    	
								//Guardo datos json en base de datos del tracker
								this.getTracker().almacenarEnBD(path, m.ip, m.port);
							}
							
							conexTCP.getSocket().close(); //Cierro la conexion
							salir = true;
							break;
					
						case QUIT_SWARM:
							//elimino seed
							this.getTracker().eliminarSeed(m.string, m.ip, m.port);
							conexTCP.getSocket().close();
							
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarDelete");
							else {
								//Pido a primario secret key
								conexTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
								this.tracker.getSecretKey(conexTCP);
								
								//envio seed que debemos quitar a primario
								yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort()); 
								response = new Mensaje(Mensaje.Tipo.SEND_QUIT_SWARM, m.ip, m.port, m.hash, yo);
				
								//encripto mensaje con la clave simetrica
								datosAEncriptar = conexTCP.convertToBytes(response);
								mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
								conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
								conexTCP.getOutBuff().flush();
								
								conexTCP.getSocket().close();	
							}
							salir = true;
						
							break;
							
						case SEND_QUIT_SWARM://Solo llega a primario
							//elimino el seed
							this.getTracker().eliminarSeed(m.hash, m.ip, m.port);
							//Replico el seed a eliminar al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarDelete");
							
							conexTCP.getSocket().close();
							salir = true;
							break;
							
						case SWARM:
							//envio swarm a peer
							swarm = this.getTracker().getSwarm(m.string,m.ip,m.port);
							obj = swarm; 
							response = new Mensaje(Mensaje.Tipo.SWARM, obj);
							//encripto mensaje con la clave simetrica
							datosAEncriptar = conexTCP.convertToBytes(response);
							mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
							conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
							conexTCP.getOutBuff().flush();
							
							conexTCP.getSocket().close();
							salir = true;
							break;
							
						case COMPLETE:
							//hacer no disponible al peer
							this.getTracker().deshabilitarSeed(m.ip,m.port);
							conexTCP.getSocket().close();
							
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarDeshabilitar");
							else {
								//Indicar a primario que deshabilite peer.
								//Pido a primario secret key
								conexTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
								this.tracker.getSecretKey(conexTCP);
								
								//envio seed que debemos quitar a primario
								yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort()); 
								response = new Mensaje(Mensaje.Tipo.SEND_COMPLETE, m.ip, m.port, yo);
				
								//encripto mensaje con la clave simetrica
								datosAEncriptar = conexTCP.convertToBytes(response);
								mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
								conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
								conexTCP.getOutBuff().flush();
								
								conexTCP.getSocket().close();	
							}
							salir = true;
			
							break;
							
						case SEND_COMPLETE:
							//deshabilito el seed
							this.getTracker().deshabilitarSeed(m.ip, m.port);
							//Replico el seed a deshabilitar al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarDeshabilitar");
							
							conexTCP.getSocket().close();
							salir = true;
							break;
						
						case FREE:
							//hacer disponible al peer
							this.getTracker().habilitarSeed(m.ip,m.port);
							conexTCP.getSocket().close();
							
							//Replico el peer disponible al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarHabilitar");
							else {
								//Indicar a primario que habilite peer.
								//Pido a primario secret key
								conexTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
								this.tracker.getSecretKey(conexTCP);
								
								//envio seed que debemos quitar a primario
								yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort()); 
								response = new Mensaje(Mensaje.Tipo.SEND_FREE, m.ip, m.port, yo);
				
								//encripto mensaje con la clave simetrica
								datosAEncriptar = conexTCP.convertToBytes(response);
								mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
								conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
								conexTCP.getOutBuff().flush();
								
								conexTCP.getSocket().close();
							}
							salir = true;
							break;
							
						case SEND_FREE:
							//habilito el seed
							this.getTracker().habilitarSeed(m.ip, m.port);
							//Replico el seed a habilitar al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarHabilitar");
							
							conexTCP.getSocket().close();
							salir = true;
							break;
							
						case NEW_SEED:
							//hacer seed al peer para el hash dado
							this.getTracker().nuevoSeed(m.ip,m.port,m.string);//string = hash
							conexTCP.getSocket().close();
							
							//Replico el nuevo seed al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarNuevo");
							else {
								//Indicar a primario que habilite peer como seed.
								//Pido a primario secret key
								conexTCP = new ConexionTCP(this.getTracker().getTrackerPrimario().getIp(), this.getTracker().getTrackerPrimario().getPort());
								this.tracker.getSecretKey(conexTCP);
								
								//envio peer que debemos establece como seed a primario
								yo = new TrackerInfo(this.getTracker().getId(),this.getTracker().getIp(),this.getTracker().getPort()); 
								response = new Mensaje(Mensaje.Tipo.SEND_NEW_SEED, m.ip, m.port, m.string, yo);
				
								//encripto mensaje con la clave simetrica
								datosAEncriptar = conexTCP.convertToBytes(response);
								mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
								conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
								conexTCP.getOutBuff().flush();	
								conexTCP.getSocket().close();
							}
							
							salir = true;
							
							break;
							
						case SEND_NEW_SEED:
							//cambio estado de peer a seed para el hash dado
							this.getTracker().nuevoSeed(m.ip, m.port, m.hash);
							conexTCP.getSocket().close();
							//Replico el nuevo seed al resto, si soy el primario
							if(this.getTracker().getTrackerPrimario().getId() == this.getTracker().getId())
								this.getTracker().replicarSeed(m, "replicarNuevo");
							
							salir = true;
							
							break;
					}					
				}		
			} catch (Exception e) {
				System.err.println("Fallo en ThreadTracker.");
				e.printStackTrace();
			}
		}	
	}

	private void getSocketPeer(ConexionTCP conexTCP, String hash, Mensaje resp) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		SeedTable st = this.getTracker().getSocketPeer(hash);
		resp = new Mensaje (Mensaje.Tipo.ACK, st.getIpPeer(), st.getPortPeer());
		
		//encripto mensaje con la clave simetrica
		byte[] datosAEncriptar = conexTCP.convertToBytes(resp);
		byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
		conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
		conexTCP.getOutBuff().flush();
	}

	//Método que envía un ACK para confirma que está activo
	private void enviarACK (ConexionTCP ctcp, Mensaje msg, Mensaje response) throws Exception  {
		
		SecretKey key = this.tracker.getKG().generarLlaveSimetrica();
		ctcp.setKey(key);
		//encripto mensaje con la clave pública del peer cliente
		byte[] datosAEncriptar = ctcp.convertToBytes(ctcp.getKey());
		byte[] mensajeEncriptado = this.tracker.getKG().encriptarAsimetrico(datosAEncriptar, msg.kpub);
		//Agrego key al mensaje
		response = new Mensaje(Mensaje.Tipo.ACK, mensajeEncriptado);
				
		ctcp.getOutObj().writeObject(response);
	}
	
	//Método que permite registrar a un tracker secundario (le llega solo al primario)
	private void registrarTracker (ConexionTCP ctcp, Mensaje msg, Mensaje resp) throws Exception {
		TrackerInfo tracker = msg.tracker;
		this.getTracker().pingTrackers(tracker); //Compruebo cuales son los trackers activos en este momento. Si alguno no responde, lo elimino de la lista

		synchronized(this.getTracker().getListaTrackers()) {
			this.getTracker().getListaTrackers().remove(tracker);//Elimino los datos de tracker si ya existia
			this.getTracker().getListaTrackers().add(tracker); //Agrego el nuevo tracker a mi lista de trackers	
		}		
		
		ArrayList<TrackerInfo> lisTrackers = this.getTracker().getListaTrackers(); //Obtengo mi lista de trackers
		
		//Actualizo JSON de trackers
		this.tracker.actualizarJSONTrackers();
		
		String listaTrackers = "Actualización de lista de Trackers: ";
		for(int i=0;i<lisTrackers.size();i++) {
			listaTrackers += "\n- ("+lisTrackers.get(i).getId()+", "+lisTrackers.get(i).getIp()+":"+lisTrackers.get(i).getPort()+")";
		}	
		this.tracker.logger.info(listaTrackers);

		//Comparar con mis archivos cuales le faltan al nuevo tracker
		ArrayList<String> hashesFaltantes = this.getTracker().compareHashes(msg.hashes);
		if(hashesFaltantes.size()>0)
			this.getTracker().actualizarTracker(ctcp, lisTrackers, hashesFaltantes); //Actualizo el tracker agregado recientemente
		else
			this.getTracker().actualizarTracker(ctcp, lisTrackers, null); //Actualizo el tracker agregado recientemente
		
		this.getTracker().replicar(msg.tracker); //Realizo la replica en todos los trackers (excepto en el tracker agregado recientemente)
	}
	
	//Permite actualizarme con los datos enviados por el tracker primario
	private void actualizarme (ConexionTCP ctcp, Mensaje msg) throws Exception {
		this.getTracker().setListaTrackers(msg.listaTrackers); //Actualizo mi lista de trackers
		
		//Actualizo JSON de trackers
		this.tracker.actualizarJSONTrackers();
		
		//Imprimo lista de trackers luego de actualización
		String listaTrackers = "Actualización de lista de trackers: ";
		for(int i=0;i<this.getTracker().getListaTrackers().size();i++) {
			listaTrackers += "\n- ("+this.getTracker().getListaTrackers().get(i).getId()+", "+this.getTracker().getListaTrackers().get(i).getIp()+":"+this.getTracker().getListaTrackers().get(i).getPort()+")";
		}
		this.tracker.logger.info(listaTrackers);
		
	}
	
	//Permite definir mi nuevo tracker primario
	private void definirNuevoPrimario(ConexionTCP ctcp, Mensaje msg) throws Exception {
		this.getTracker().setTrackerPrimario(msg.tracker); //Seteo mi primario con el tracker que recibí por parámetro en el mensaje
		this.tracker.logger.info("Mi nuevo Tracker Primario es: TRACKER N°"+this.getTracker().getTrackerPrimario().getId());
	}
	
	//Si soy el primario me llega JSON de los peers (todos).
	//Permite que un peer envíe un archivo JSON de un archivo a compartir
	private void guardarArchivo(ConexionTCP ctcp, Mensaje msg) //msg.string = hashJSON
	{	
		String ipPeer = msg.ip; 
		int portPeer = msg.port;
		
		//Definir hash=ID=Nombre del archivo json. Veo que sea único el hash pasado por el peer.
		String hash = this.getTracker().hashUnico(msg.string);
		String pathJSON = this.getTracker().getPath()+ "/" + hash + ".json";
		
		//Leo archivo JSON en nueva conexión
		boolean exito;
		try {
			exito = this.getTracker().guardarArchivoBuffer(ctcp, pathJSON);
			ctcp.getSocket().close();
			
			if(exito) {
				//agrego al json el ID    	
		    	Object obj = new JSONParser().parse(new FileReader(pathJSON));
		    	JSONObject json = (JSONObject) obj;
		    	json.put("ID", hash);
		    	try {
		    		File file = new File(pathJSON);
					FileWriter fileW = new FileWriter(file);
					fileW.write(json.toJSONString());
					fileW.flush();
					fileW.close();
				} catch (IOException e) {
					this.tracker.logger.error("Fallo al agregar ID a JSON.");
				}
				
				this.getTracker().almacenarEnBD(pathJSON, ipPeer, portPeer);	
				this.replicarFile(ipPeer, portPeer, pathJSON, hash); //Replico el nuevo JSON, enviandolo al resto de trackers. Path = path donde se creo el nuevo JSON
			}
		} catch (Exception e1) {
			//intento borrar al archivo si llego a guardarse
			File file = new File(pathJSON);
			file.delete();
			this.tracker.logger.error("Falló al guardar archivo.");
			e1.printStackTrace();
		}
		
	}
	
	//Permite decidir como realizar la replicación en base a si soy el tracker primario o no
	private void replicarFile(String ipPeer, int portPeer, String pathJSON, String hashFinal) throws Exception {
		this.getTracker().pingTrackers(null); //Compruebo cuales son los trackers vivos en este momento. Si alguno no responde, lo elimino de la lista
		ArrayList<TrackerInfo> lisTrackers = this.getTracker().getListaTrackers();
		
		//Ordeno por ID de menor a mayor a la lista de trackers. De esta forma, si se cae el primario mientras está replicando
		//el nuevo archivo, se asegura de haber enviado el archivo primero al que sería el siguiente primario.
		Collections.sort(lisTrackers);
		
		if (lisTrackers.size()>1) { //Si hay más de un tracker en la lista (Alguien además de mí) , les replico la información			
			for (int i=0; i<lisTrackers.size(); i++) {
				if (lisTrackers.get(i).getId()!=this.getTracker().getId()) { //Debo replicar a todos menos a mi (primario)
					String ip = lisTrackers.get(i).getIp();
					int port = lisTrackers.get(i).getPort();
					
					ConexionTCP conexTCP = new ConexionTCP(ip,port); //Abro una conexion contra el tracker
					this.tracker.getSecretKey(conexTCP);
					
					Mensaje m = new Mensaje(Mensaje.Tipo.REPLICATE_FILE, ipPeer, portPeer, hashFinal);
					//encripto mensaje con la clave simetrica
					byte[] datosAEncriptar = conexTCP.convertToBytes(m);
					byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
					conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
					conexTCP.getOutBuff().flush();
					
					//Envío nuevo json
					this.enviarArchivoBuffer(conexTCP, pathJSON);//Le paso el path del JSON para que pueda encontrarlo y enviarlo
					conexTCP.getSocket().close();
					this.tracker.logger.info("JSON enviado al Tracker "+ lisTrackers.get(i).getId() +" ("+ ip +":"+ port +") desde Tracker Primario.");
				}
			}
		}
	}
	
	private void enviarArchivoBuffer(ConexionTCP conexTCP, String pathFile) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException 
	{
	    try {
			File archivo = new File(pathFile);
			   
			byte[] buffer = new byte[(int) archivo.length()];
			   
	        FileInputStream fis = new FileInputStream(archivo);
	        BufferedInputStream in = new BufferedInputStream(fis);
	        in.read(buffer,0,buffer.length);
	        
	        //encripto archivo con la clave simetrica
			byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), buffer);
	        conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
	        conexTCP.getOutBuff().flush();
	        
	        in.close();//Cierro el buffer de lectura del JSON
	     
	    }catch (IOException e) {
	    	this.tracker.logger.error("Fallo durante envío del JSON.");
	    }
	}
	
	//Busca archivos con nombre similar a  msg.string
	private void buscarArchivo(ConexionTCP conexTCP, Mensaje msg) throws Exception {
		String nombreBuscado = msg.string.toLowerCase(); //Obtengo el nombre de archivo a buscar
		ArrayList<FileTable> archivosDisponibles = new ArrayList<FileTable>(); //lista que contendrá los nombres de archivos que coincidan con la busqueda
		
		//Busco en mi BD en la tabla File_table si contiene archivos con el nombre solicitado y devuelvo una lista de los
		archivosDisponibles = this.getTracker().getFilesByName(nombreBuscado);
		
		Mensaje response = null;
		
		if (archivosDisponibles.isEmpty()) { //Si no se encontraron archivos con el nombre solicitado, devuelvo error
			response = new Mensaje(Mensaje.Tipo.ERROR, "No se encontró un archivo con el nombre solicitado");
			
			//encripto mensaje con la clave simetrica
			byte[] datosAEncriptar = conexTCP.convertToBytes(response);
			byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
			conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexTCP.getOutBuff().flush();
		
		}else { //Si hay archivos que coinciden con el nombre buscado
			Object obj = new Object();
			obj = archivosDisponibles;
			response = new Mensaje(Mensaje.Tipo.FILES_AVAILABLE, obj);
			//Envío una lista de nombres de archivos relacionados con la búsqueda
			//encripto mensaje con la clave simetrica
			byte[] datosAEncriptar = conexTCP.convertToBytes(response);
			byte[] mensajeEncriptado = this.tracker.getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
			conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
			conexTCP.getOutBuff().flush();
			
			//Aguardo respuesta del peer (hash de archivo a descargar)
			int msgSize = 1024*1024;//1MB
	        byte[] buffer = new byte[msgSize];
	        int byteread = conexTCP.getInBuff().read(buffer, 0, msgSize);
	        //desencripto con la clave simetrica
	        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
	        byte[] msgDesencriptado = this.tracker.getKG().desencriptarSimetrico(conexTCP.getKey(), datosEncriptados);
	        response = (Mensaje) conexTCP.convertFromBytes(msgDesencriptado);
	        
			if (response.tipo == Mensaje.Tipo.REQUEST) { //Si el peer envío el hash del archivo
				String hash = response.string; //hash
				String pathJSON = this.getTracker().getPath()+ "/" + hash + ".json";
				
				this.enviarArchivoBuffer(conexTCP, pathJSON);
			}
		}
	}

}