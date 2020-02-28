package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerManager;
import TP_Final_SDyPP.Peer.ParteArchivo.Estado;
import TP_Final_SDyPP.Peer.ThreadCliente.TipoError;

public class ThreadLeecher implements Runnable {

	private ThreadCliente threadCliente; 
	private ConexionTCP conexionTCP = null;
	private KeysGenerator kg;
	private String log;
	
	private Integer[] peerPartes;
	private SeedTable seed;
	
	//Del seed
	private String ip;
	private int port;
	
	private int nroParte;
	private int sizeParte;
	private String hashParte;
	private String fileName;
	
	public ThreadLeecher(ThreadCliente threadCliente, ConexionTCP conexionTCP, SeedTable seed, String ip, int port, Integer[] peerPartes) {
		this.threadCliente = threadCliente;
		this.fileName = this.threadCliente.getName();
		this.conexionTCP = conexionTCP;
		this.seed = seed;
		this.ip = ip;
		this.port = port;
		this.peerPartes = peerPartes;
		this.kg = new KeysGenerator();
	}

	@Override
	public void run() {	
		//Descargo las partes ofrecidas por el peer obtenido (que no posea ya)
		boolean fallo = this.descargarPartes();
		
		//Al salir del while le digo al peer servidor que disminuya conexiones salientes.
		if(!fallo) {					
			try {
				log = "ThreadLeecher - LOADDOWN peer "+ip+":"+port;
				this.threadCliente.logger.info(log);
				System.out.println(log);
				
				Mensaje m = new Mensaje(Mensaje.Tipo.LOADDOWN);
				m.enviarMensaje(conexionTCP, m, kg);
				
				conexionTCP.getSocket().close();
			} catch (Exception e) {
				log = "ThreadLeecher - Fallo cerrar socket contra peer servidor";
				this.threadCliente.logger.error(log);
				System.out.println(log);
				e.printStackTrace();
			}	
		}else {//Si falla envío de la parte no tengo que envíar loaddown
			this.threadCliente.RemovePeerDeSwarm(seed, ip, port);
		}

		synchronized(this.threadCliente.getCliente().getLockLeechersDisponibles()) {
			this.threadCliente.getCliente().aumentarLeechersDisponibles();
			this.threadCliente.decrementarCantidadLeechersUtilizados();
		}
	}
		
	private boolean descargarPartes() {
		boolean encontre = true;//parte a descargar
		boolean fallo = false;
		boolean esperar = true;//Si estoy conectado a un leecher (peer que no tiene el archivo completo), y descargué todas
								//las partes que tenía para ofrecerme, espero 5seg antes de desconectarme en caso de que descargue más partes dicho peer
		
		//Si no encuentro una parte que me falte descargar en el peer salgo del while para buscar otro peer
		while(!this.threadCliente.getStop() && encontre && this.threadCliente.getPartesArchivo().size()>0
				&& (!this.threadCliente.getThreadClienteSinLeecher() || this.threadCliente.getLeechersUtilizados()<2)) {
			//Si no se pauso la descarga, encontre una parte que necesitaba en la última buscada en el peer,
			//todavía necesito más partes y no existe otro threadCliente que tenga 0 leechers (yo teniendo >1) 
			//--> busco otra parte en peer
			
			//Busco en el peer si tiene una parte que me falte descargar
			encontre = this.getParteFaltante();
			
			if(!this.threadCliente.getStop() && encontre) {//Pido parte a peer 
				//Si encontre, esperar vuelve a ser true
				esperar = true;
				
				//Creo mensaje que pide parte
				String pathAParte;
				Mensaje m = null;
				if(seed.isSeed()) {
					pathAParte = seed.getPath() + "/" + this.fileName;
					m = new Mensaje(Mensaje.Tipo.GET_PIECE, nroParte, pathAParte, true, sizeParte, seed.getHash());
				}else {
					pathAParte = String.format("%s.%06d", seed.getPath() + "/" + this.fileName, nroParte);
					m = new Mensaje(Mensaje.Tipo.GET_PIECE, nroParte, pathAParte, false, sizeParte, seed.getHash());
				}
				
				try {
					//medir tiempo para descargar parte
					long startTime = System.currentTimeMillis();
					m.enviarMensaje(conexionTCP, m, kg);
					
					//Recibo y almaceno la parte del archivo
					String pathNuevaParte = this.guardarArchivoBuffer(conexionTCP, nroParte, sizeParte);
					long endTime = System.currentTimeMillis();
					long time = endTime - startTime;
					
					switch(pathNuevaParte) {
						case "tiempo":
							//No pude recuperar parte.
							log = "ThreadLeecher - Fallo al recuperar parte de peer "+ip+":"+port+" por tiempo";
							this.threadCliente.logger.error(log);
							System.out.println(log);
							//vuelve estado de parte descargada en array "partesArchivo" a pendiente
							this.estadoPendiente(nroParte);
							
							//Guardar error y cuál peer es el involucrado (en archivo de carpeta Graficos)
							this.threadCliente.almacenarErrorDescargaParte(TipoError.TIEMPO, ip, port);

						break;
							
						case "error":
							//No pude recuperar parte. Ante la posibilidad de que haya eliminado las partes dejo de buscar en este peer
							//el peer se elimina del swarm.
							log = "ThreadLeecher - Fallo al recuperar parte de peer "+ip+":"+port;
							this.threadCliente.logger.error(log);
							System.out.println(log);
							conexionTCP.getSocket().close();
							encontre = false;//Dejar de buscar en este peer.
							fallo = true;
							
							//vuelve estado de parte descargada en array "partesArchivo" a pendiente
							this.estadoPendiente(nroParte);	
							
							//Guardar error y cuál peer es el involucrado (en archivo de carpeta Graficos)
							this.threadCliente.almacenarErrorDescargaParte(TipoError.CONEXION, ip, port);

						break;
							
						default:
							//genero hash de la nueva parte y lo comparo con el del json
							String hash = this.hash(pathNuevaParte);
							if(hash.equals(hashParte)) {//Descarga exitosa
								//guardo tiempo que tomo la descarga de la parte
								this.threadCliente.almacenarTiempoDescargaDeParte(time, nroParte);
								
								//Borro parte descargada del array partesArchivo
								synchronized(this.threadCliente.getPartesArchivo()) {
									int index = 0 ;
									boolean encontreParte = false;
									while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
										if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
											this.threadCliente.getPartesArchivo().remove(index);
											encontreParte = true;
											log = fileName +" - ThreadLeecher - Parte numero "+nroParte+" descargada de peer "+ip+":"+port;
											this.threadCliente.logger.info(log);
										}
										index++;
									}
								}
								
								//actualizo estado de parte descargada en json "partesPendientes" a descargado
								this.threadCliente.actualizarPartesPendientes(nroParte);
								this.threadCliente.getMisPartes()[nroParte] = 1;
								
								//mensaje que indica % descargado hasta el momento
								int faltantes = this.threadCliente.getPartesArchivo().size();
								int descargue = this.threadCliente.getCantPartes() - faltantes;
								float descargado = (float) (descargue * 100) / this.threadCliente.getCantPartes();
								String descargadoS = String.format("%.2f", descargado);
								
								log = "ThreadLeecher - "+descargadoS+"% Descargado";
								this.threadCliente.logger.info(log);
								this.threadCliente.setDescargado(descargadoS+"%");
								
								//Velocidad de descarga
								String velocidad = this.threadCliente.calcularVelocidad(time, sizeParte);
								
								//Guardar nroParte, tiempo y de que peer lo bajó (en archivo de carpeta Graficos)
								this.threadCliente.almacenarVelocidadDescargaParte(time, nroParte, ip, port);
								
								//Actualizar descargado y vel descarga en tabla
								String hashArchivo = this.threadCliente.getHash();//Para diferenciar row de table a actualizar
								this.threadCliente.getCliente().notifyObserver(4, descargadoS+"%"+"-"+velocidad+"-"+hashArchivo);								
								
							} else {
								log = "ThreadLeecher - Parte "+nroParte+" Hash invalido";
								this.threadCliente.logger.info(log);
								System.out.println(log);
								//Borro parte descargada de la carpeta de partes
								File nuevaParte = new File(pathNuevaParte);
								nuevaParte.delete();
								
								encontre = false;//Dejar de buscar en este peer.
								
								//vuelve estado de parte descargada en array "partesArchivo" a pendiente
								this.estadoPendiente(nroParte);
								
								//Guardar error y cuál peer es el involucrado (en archivo de carpeta Graficos)
								this.threadCliente.almacenarErrorDescargaParte(TipoError.HASH, ip, port);
							}
						break;
					}
				} catch (Exception e) {
					log = "ThreadLeecher - Fallo al crear conexion contra peer servidor para recibir parte "+nroParte;
					this.threadCliente.logger.error(log);
					System.out.println(log);
					e.printStackTrace();
					encontre = false;//Dejar de buscar en este peer.
					fallo = true;
					//vuelve estado de parte descargada en array "partesArchivo" a pendiente
					this.estadoPendiente(nroParte);
					
					try {
						conexionTCP.getSocket().close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}else if(!encontre){//El peer al que me conecte no tiene ninguna parte que me falte
				
				if(esperar) {
					esperar = false;
					encontre = true;
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					log = "ThreadLeecher - peer "+ip+":"+port+" No posee ninguna parte que nos falte descargar.";
					this.threadCliente.logger.info(log);
					System.out.println(log);
					
					this.threadCliente.RemovePeerDeSwarm(seed, ip, port);					
				}
			}
		}
		return fallo;
	}

	private boolean getParteFaltante() {
		boolean encontre = false;
		nroParte = 0;
		sizeParte = 0;
		hashParte = "";
		
		synchronized(this.threadCliente.getPartesArchivo()) {
			int index = 0 ;
			
			while(!this.threadCliente.getStop() && !encontre && index<this.threadCliente.getPartesArchivo().size()) {
				ParteArchivo parte = this.threadCliente.getPartesArchivo().get(index);
				if(parte.getEstado() == Estado.PENDIENTE) {//Parte que necesito y no está siendo descargada por otro threadLeecher
					nroParte = parte.getParte();
					if (peerPartes[nroParte] == 1) {//El peer tiene la parte que necesito
						encontre = true;
						hashParte = parte.getHash();
						sizeParte = parte.getSize();
						this.threadCliente.getPartesArchivo().get(index).setEstado(Estado.DESCARGANDO);
						log = "ThreadLeecher - Descargando parte numero "+nroParte+" de peer "+ip+":"+port;
						this.threadCliente.logger.info(log);
					}
				}
				index++;
			}
		}
		return encontre;
	}

	private void estadoPendiente(int nroParte) {
		synchronized(this.threadCliente.getPartesArchivo()) {
			int index = 0 ;
			boolean encontreParte = false;
			while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
				if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
					this.threadCliente.getPartesArchivo().get(index).setEstado(Estado.PENDIENTE);
					encontreParte = true;
					log = "ThreadLeecher - Parte numero "+nroParte+" vuelve a estado Pendiente.";
					this.threadCliente.logger.info(log);
				}
				index++;
			}
		}
	}
	
	public String hash (String path) throws IOException, NoSuchAlgorithmException, ParseException 
	{	
		File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream in = new BufferedInputStream(fis);
        int size = (int) file.length();
        byte[] buffer = new byte[size];
        
        in.read(buffer,0,size);
    	MessageDigest md = MessageDigest.getInstance("SHA-1"); 
    	md.update(buffer);
    	byte[] b = md.digest();
    	StringBuffer sb = new StringBuffer();
    	
    	for(byte i : b) {
    		sb.append(Integer.toHexString(i & 0xff).toString());    		
    	}
    	
    	in.close();
    	fis.close();
    	
    	//devuelvo hash
    	return sb.toString();
	}
	
	private String guardarArchivoBuffer(ConexionTCP ctcp, int nroParte, int sizeParte) throws InterruptedException {
		int byteread;
	
	    try {	   
	    	byte[] buffer = new byte[sizeParte];
	        int leido = 0;
	        int leo = 0;
	        int available;
			long esperoHasta = System.currentTimeMillis() + 15000;//15 seg
			boolean error = false;
			boolean salir = false;
			boolean tiempo = false;
			log = "ThreadLeecher - Guardando parte "+nroParte+".";
			this.threadCliente.logger.info(log);
			
			while (!error && !salir && !tiempo) {
				if(leido < sizeParte) {//Si todavía no leí todo el archivo
					if(System.currentTimeMillis() < esperoHasta) {//Si no superamos el tiempo máximo para descargar parte
						leo = java.lang.Math.min(ctcp.getInBuff().available(),sizeParte-leido);
				        available = ctcp.getInBuff().read(buffer, leido, leo);
				        if (available == -1) {
				        	log = "ThreadLeecher - Parte "+nroParte+": ERROR available igual a -1.";
				        	this.threadCliente.logger.info(log);
				        	error = true;
				        } else {
							leido += leo;
							if(available != 0)
								esperoHasta = System.currentTimeMillis() + 15000;//15 seg
				        }
					} else {
						log = "ThreadLeecher - Parte "+nroParte+": Exceso de tiempo.";
						this.threadCliente.logger.info(log);
						tiempo = true;
					}
				} else {
					log = "ThreadLeecher - Parte "+nroParte+": Descargada.";
					this.threadCliente.logger.info(log);
					salir = true;
				}
			}
			
		    if(salir) {
		    	//agrego nombre parte
				String pathParte = String.format("%s.%06d", this.threadCliente.getPathPartes() + "/" + this.fileName, nroParte);
				File archivo = new File(pathParte);	        
		        archivo.createNewFile();
		        FileOutputStream fos = new FileOutputStream(archivo);
		        BufferedOutputStream out = new BufferedOutputStream(fos);
		        out.write(buffer, 0, sizeParte);
		        
		        out.flush();
		        out.close();
		        fos.close();
		        return pathParte;
	    	} else if(tiempo){//Se pasó de tiempo
	        	return "tiempo";//No sacamos al peer del swarm
	    	} else {//Error por available
	    		return "error";//Sacamos al peer del swarm
	    	}

	    }catch(IOException ex) {
	    	log = "ThreadLeecher - Fallo guardar parte archivo en ThreadLeecher.";
	    	this.threadCliente.logger.error(log);
			System.out.println(log);
	    	return "";
	    }	
	}
	
}
