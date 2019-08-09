package TP_Final_SDyPP.Peer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.SecretKey;

import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Peer.ParteArchivo.Estado;

public class ThreadLeecher implements Runnable {

	private ThreadCliente threadCliente; 
	private ConexionTCP conexTCP = null;
	private KeysGenerator kg;
	
	public ThreadLeecher(ThreadCliente threadCliente) {
		this.threadCliente = threadCliente;
		this.kg = new KeysGenerator();
	}

	@Override
	public void run() {
		//Mientras falte recuperar partes del archivo
		while(!this.threadCliente.getStop() && this.threadCliente.getPartesArchivo().size()>0) {
			Integer[] peerPartes = new Integer[0];
			//Mientras no encuentre un peer disponible (conectado y que tenga el archivo)
			boolean peerDisponible = false;
			SeedTable seed = null;
			String ip = "";
			int port = -1;
			
			synchronized(this.threadCliente.getPeersDisponibles()) {
				int index = 0;
				while(!this.threadCliente.getStop() && !peerDisponible && index<this.threadCliente.getPeersDisponibles().size()) {
					seed = this.threadCliente.getPeersDisponibles().get(index);
					ip = seed.getIpPeer();
					port = seed.getPortPeer();
					try {
						//Pregunto si está despierto y obtengo clave simetrica
						conexTCP = new ConexionTCP(ip,port);
						if(this.threadCliente.getSecretKey(conexTCP,true)) {
							//Pregunto por su archivo con las partes que descargo
							String path = seed.getPath() + "/"+this.threadCliente.getName()+"Partes.json";
							Mensaje msg = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE, path, seed.getHash());
							
							//encripto mensaje con la clave simetrica
							byte[] datosAEncriptar = conexTCP.convertToBytes(msg);
							byte[] mensajeEncriptado = kg.encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
							conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
							conexTCP.getOutBuff().flush();
							
							int msgSize = 1024*1024;//1MB
					        byte[] buffer = new byte[msgSize];
					        int byteread = conexTCP.getInBuff().read(buffer, 0, msgSize);
					        //desencripto con la clave simetrica
					        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
					        byte[] msgDesencriptado = kg.desencriptarSimetrico(conexTCP.getKey(), datosEncriptados);
					        Mensaje response = (Mensaje) conexTCP.convertFromBytes(msgDesencriptado);
							
							if(msg.tipo == Mensaje.Tipo.PIECES_AVAILABLE) {
								peerPartes = (Integer[]) response.lista;
								peerDisponible = true;
								this.threadCliente.logger.info("ThreadLeecher - Conexión con peer disponible ("+ip+":"+port+") y obtención de archivo de partes descargadas");
							}else {
								this.threadCliente.logger.error("ThreadLeecher - No pudo obtener archivo de partes descargadas del peer disponible ("+ip+":"+port+")");
								conexTCP.getSocket().close();//Llegó ERROR
							}
						}
					} catch (Exception e) {
						this.threadCliente.logger.error("ThreadLeecher - Falló conexión con peer disponible del swarm ("+ip+":"+port+")");
					}
					this.threadCliente.logger.info("ThreadLeecher - Retirar al peer ("+ip+":"+port+") de peers disponibles");
					this.threadCliente.getPeersDisponibles().remove(index);//Lo saco del array, haya o no podido conectarme a él.
					//Aunque haya podido conectarme al peer, lo saco de la lista de peers disponibles para no utilizar un único peer.
				}
			}
			
			//Recupero partes del peer disponible
			if(peerDisponible){
				boolean encontre = true;//parte a descargar
				boolean fallo = false;//Si falla envío de la parte no tengo que envíar loaddown
				while(!this.threadCliente.getStop() && encontre && this.threadCliente.getPartesArchivo().size()>0) {//Si no encontre una parte que me falte descargar en el peer salgo del while para buscar otro peer
					encontre = false;
					int nroParte = 0;
					String hashParte = "";
					int sizeParte = 0;
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
									this.threadCliente.logger.info("ThreadLeecher - Descargando parte número "+nroParte+" de peer "+ip+":"+port);
								}
							}
							index++;
						}
					}
					
					if(!this.threadCliente.getStop() && encontre) {//Pido parte a peer 
						String pathAParte;
						Mensaje msg = null;
						if(seed.isSeed()) {
							pathAParte = seed.getPath() + "/" + this.threadCliente.getName();
							msg = new Mensaje(Mensaje.Tipo.GET_PIECE, nroParte, pathAParte, true, sizeParte, seed.getHash());
						}else {
							pathAParte = seed.getPath() + "/" + this.threadCliente.getName() + "." + nroParte;
							msg = new Mensaje(Mensaje.Tipo.GET_PIECE, nroParte, pathAParte, false, sizeParte, seed.getHash());
						}
						
						try {
							//encripto mensaje con la clave simetrica
							byte[] datosAEncriptar = conexTCP.convertToBytes(msg);
							byte[] mensajeEncriptado = kg.encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
							conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
							conexTCP.getOutBuff().flush();
														
							//Recibo y almaceno la parte del archivo
							String pathNuevaParte = this.guardarArchivoBuffer(conexTCP, nroParte, sizeParte);
							if(pathNuevaParte != "") {
								
								//genero hash de la nueva parte y lo comparo con el del json
								String hash = this.hash(pathNuevaParte);
								if(hash.equals(hashParte)) {
									//Borro parte descargada del array partesArchivo
									synchronized(this.threadCliente.getPartesArchivo()) {
										int index = 0 ;
										boolean encontreParte = false;
										while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
											if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
												this.threadCliente.getPartesArchivo().remove(index);
												encontreParte = true;
												this.threadCliente.logger.info("ThreadLeecher - Parte número "+nroParte+" descargada de peer "+ip+":"+port);
											}
											index++;
										}
									}
									
									//actualizo estado de parte descargada en json "partesPendientes" a descargado
									this.threadCliente.actualizarPartesPendientes(nroParte);
									
									//mensaje que indica % descargo hasta el momento
									int faltantes = this.threadCliente.getPartesArchivo().size();
									int descargue = this.threadCliente.getCantPartes() - faltantes;
									float descargado = (descargue * 100) / this.threadCliente.getCantPartes();
									
									this.threadCliente.logger.info("ThreadLeecher - "+descargado+"% Descargado");
									
								}else {
									//Borro parte descargada de la carpeta de partes
									File nuevaParte = new File(pathNuevaParte);
									boolean borrado = nuevaParte.delete();
									
									//vuelve estado de parte descargada en array "partesArchivo" a pendiente
									synchronized(this.threadCliente.getPartesArchivo()) {
										int index = 0 ;
										boolean encontreParte = false;
										while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
											if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
												this.threadCliente.getPartesArchivo().get(index).setEstado(Estado.PENDIENTE);
												encontreParte = true;
												this.threadCliente.logger.info("ThreadLeecher - Parte número "+nroParte+" vuelve a estado Pendiente");
											}
											index++;
										}
									}
								}
							}else {
								//No pude recuperar parte. Ante la posibilidad de que haya eliminado las partes dejo de buscar en este peer
								//el peer se elimina del swarm.
								this.threadCliente.logger.error("Falló al recuperar parte de peer "+ip+":"+port);
								conexTCP.getSocket().close();
								encontre = false;//Dejar de buscar en este peer.
								fallo = true;
								
								//vuelve estado de parte descargada en array "partesArchivo" a pendiente
								synchronized(this.threadCliente.getPartesArchivo()) {
									int index = 0 ;
									boolean encontreParte = false;
									while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
										if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
											this.threadCliente.getPartesArchivo().get(index).setEstado(Estado.PENDIENTE);
											encontreParte = true;
											this.threadCliente.logger.info("ThreadLeecher - Parte número "+nroParte+" vuelve a estado Pendiente");
										}
										index++;
									}
								}
							}
						} catch (Exception e) {
							this.threadCliente.logger.error("ThreadLeecher - Falló al crear conexión contra peer servidor para recibir parte "+nroParte);
							e.printStackTrace();
							fallo = true;
							//vuelve estado de parte descargada en array "partesArchivo" a pendiente
							synchronized(this.threadCliente.getPartesArchivo()) {
								int index = 0 ;
								boolean encontreParte = false;
								while(!encontreParte && index<this.threadCliente.getPartesArchivo().size()) {
									if(this.threadCliente.getPartesArchivo().get(index).getParte() == nroParte) {
										this.threadCliente.getPartesArchivo().get(index).setEstado(Estado.PENDIENTE);
										encontreParte = true;
										this.threadCliente.logger.info("ThreadLeecher - Parte número "+nroParte+" vuelve a estado Pendiente");
									}
									index++;
								}
							}
						}
					}
				}
				
				//Al salir del while le digo al peer servidor que disminuya conexiones salientes.
				if(!fallo) {					
					try {
						this.threadCliente.logger.info("ThreadLeecher - LOADDOWN peer "+ip+":"+port);
						Mensaje msg = new Mensaje(Mensaje.Tipo.LOADDOWN);
						//encripto mensaje con la clave simetrica
						byte[] datosAEncriptar = conexTCP.convertToBytes(msg);
						byte[] mensajeEncriptado = kg.encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
						conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
						conexTCP.getOutBuff().flush();
						
						conexTCP.getSocket().close();
					} catch (Exception e) {
						this.threadCliente.logger.error("ThreadLeecher - Falló cerrar socket contra peer servidor");
						e.printStackTrace();
					}	
				}else {
					//Saco a peer del array swarm
					synchronized(this.threadCliente.getSwarm()) {
						this.threadCliente.getSwarm().remove(seed);
						this.threadCliente.logger.error("ThreadLeecher - Retiro peer de swarm permanentemente");
					}
				}
			}else {//Renuevo peersDisponible con swarm 
				this.threadCliente.setPeersDisponibles(this.threadCliente.getSwarm());
				this.threadCliente.logger.info("ThreadLeecher - Actualización de peers disponibles con swarm");
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
    	
    	//devuelvo hash
    	return sb.toString();
	}
	
	private String guardarArchivoBuffer(ConexionTCP ctcp, int nroParte, int sizeParte) throws InterruptedException {
		Thread.sleep(100);//Espero a que el peer servidor haya escrito el archivo completo en el outputStream
		int byteread;
	
	    try {
	    	//agrego nombre parte
	    	String pathParte = this.threadCliente.getPathPartes() + "/" + this.threadCliente.getName() + "." + nroParte;
	        File archivo = new File(pathParte);
	        archivo.createNewFile();
	        FileOutputStream fos = new FileOutputStream(archivo);
	        BufferedOutputStream out = new BufferedOutputStream(fos);
	        
	        byte[] buffer = new byte[sizeParte];
	        int leido = 0;
	        boolean salir = false;
	        while(leido != sizeParte && !salir) {
	        	byteread = ctcp.getInBuff().read(buffer, 0, sizeParte);
	        	if(byteread != -1) {
	        		out.write(buffer, 0, byteread);
	        		leido+=byteread;
	        	}else
	        		salir = true;
	        }

	        out.flush();
	        
	        out.close();
	        fos.close();
	      
	        if(salir) {
	        	archivo.delete();
	        	return "";
	        }else
	        	return pathParte;
	    }catch(IOException ex) {
	    	this.threadCliente.logger.error("ThreadLeecher - Falló guardar parte archivo en ThreadLeecher.");
	    	ex.printStackTrace();
	    	return "";
	    }	
	}
}
