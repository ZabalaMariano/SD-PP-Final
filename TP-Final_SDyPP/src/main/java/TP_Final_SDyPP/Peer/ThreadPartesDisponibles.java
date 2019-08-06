package TP_Final_SDyPP.Peer;

import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.DB4O.SeedTable;
import TP_Final_SDyPP.Otros.ConexionTCP;
import TP_Final_SDyPP.Otros.KeysGenerator;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Otros.TrackerInfo;

public class ThreadPartesDisponibles implements Runnable {

	private ThreadCliente threadCliente;
	private ConexionTCP conexTCP;
	private KeysGenerator kg;
	
	public ThreadPartesDisponibles(ThreadCliente threadCliente) {
		this.threadCliente = threadCliente;
		this.kg = new KeysGenerator();
	}

	@Override
	public void run() {
		//Mientras falte recuperar partes del archivo
		while(!this.threadCliente.getStop() && this.threadCliente.getPartesArchivo().size()>0) {
			Integer[] misPartes = new Integer[0];
			Integer[] peersPartes = new Integer[0];
			boolean exito = false;
			//Pregunto que partes tengo descargadas
			synchronized(this.threadCliente.getPathPartes()) {
				Object obj;
				try {
					obj = new JSONParser().parse(new FileReader(this.threadCliente.getPathPartes()+"/"+this.threadCliente.getName()+"Partes.json"));
					JSONArray ja = (JSONArray) obj;
					
					exito = true;
					misPartes = new Integer[ja.size()];
					peersPartes = new Integer[ja.size()];
					
			    	for(int i=0; i<ja.size(); i++) {
			    		JSONObject jo = (JSONObject) ja.get(i);
			    		misPartes[i] = (jo.get("estado").equals("pendiente")) ? 0 : 1;//si tengo la parte es 1
			    		peersPartes[i] = 0;//inicializa en 0
			    	}
			    	this.threadCliente.logger.info("ThreadPartesDisponibles - Consulta partes pendientes a descargar");
				} catch (IOException | ParseException e) {
					this.threadCliente.logger.error("ThreadPartesDisponibles - Falló lectura json con partes faltantes.");
					e.printStackTrace();
				}
			}
			
			if(exito) {//pude leer mi json con partes descargadas
				boolean cienPorciento = false;
				int index = 0;
				int tiene = 0;//contador partes que tienen los peers del swarm
				while(!cienPorciento && index<this.threadCliente.getSwarm().size()) {
					//recupera archivo del peer con las partes descargadas
					String path = this.threadCliente.getSwarm().get(index).getPath() + "/"+this.threadCliente.getName()+"Partes.json";
					Mensaje m = new Mensaje(Mensaje.Tipo.PIECES_AVAILABLE_CLOSE, path);
					String ip = this.threadCliente.getSwarm().get(index).getIpPeer();
					int port = this.threadCliente.getSwarm().get(index).getPortPeer();
					
					try {
						this.conexTCP = new ConexionTCP(ip, port);
						this.threadCliente.getSecretKey(conexTCP,false);//Mantener conexión false
						
						//encripto mensaje con la clave simetrica
						byte[] datosAEncriptar = conexTCP.convertToBytes(m);
						byte[] mensajeEncriptado = this.threadCliente.getServidor().getKG().encriptarSimetrico(conexTCP.getKey(), datosAEncriptar);
						conexTCP.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
						conexTCP.getOutBuff().flush();
						
						int msgSize = 1024*1024;//1MB
				        byte[] buffer = new byte[msgSize];
				        int byteread = conexTCP.getInBuff().read(buffer, 0, msgSize);
				        //desencripto con la clave simetrica
				        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
				        byte[] msgDesencriptado = kg.desencriptarSimetrico(conexTCP.getKey(), datosEncriptados);
				        m = (Mensaje) conexTCP.convertFromBytes(msgDesencriptado);
						
						conexTCP.getSocket().close();
						
						if(m.tipo == Mensaje.Tipo.PIECES_AVAILABLE) {
							Integer[] partesArchivo = (Integer[]) m.lista;
							for(int i=0; i<partesArchivo.length; i++) {
								if(partesArchivo[i] == 1) {
									if(peersPartes[i] == 0) {
										peersPartes[i] = 1;
										tiene++;
									}
								}
							}
						}
						
						//Si tiene==peersPartes.size -> los peers del swarm tienen todas las partes disponibles
						if(tiene == peersPartes.length) {
							cienPorciento = true;
						}
						
					} catch (Exception e) {
						this.threadCliente.logger.error("ThreadPartesDisponibles - No se pudo conectar a peer ("+ip+":"+port+")");
					}
					
					index++;
				}	
				
				//fin while por cienPorciento=true o repase todos los peers del swarm
				//calculo % total de archivo disponible en swarm
				float porcentajeDisponible = (tiene * 100) / peersPartes.length;
				this.threadCliente.logger.info("ThreadPartesDisponibles - El swarm posee un "+porcentajeDisponible+"% del total del archivo a descargar.");
				//comparo las partes que descargue yo hasta ahora con las de los peers. Si tengo el 100% de lo que ofrecen y no el 100%
				//del archivo, tengo que pedir otro swarm al tracker porque este ya no tiene nada más que ofrecerme.
				int coincidencias = 0;
				int partesDisponiblesEnPeers = 0; 
				for(int i=0; i<peersPartes.length; i++) {
					if(peersPartes[i] == 1) {
						partesDisponiblesEnPeers++;
						if(misPartes[i] == 1) {
							coincidencias++;
						}	
					}
				}
				float porcentajeDescargadoDelSwarm;
				if(partesDisponiblesEnPeers!=0)
					porcentajeDescargadoDelSwarm = (coincidencias * 100) / partesDisponiblesEnPeers;
				else
					porcentajeDescargadoDelSwarm = 0;
				this.threadCliente.logger.info("ThreadPartesDisponibles - Del "+porcentajeDisponible+"% disponible en el swarm, descargó "+porcentajeDescargadoDelSwarm+"%");
				
				if(!this.threadCliente.getStop() && porcentajeDescargadoDelSwarm == 100 && partesDisponiblesEnPeers != peersPartes.length) {//Si ya descargue todo lo que el swarm podia ofercerme y no llegue al 100%
					
					try {
						//Consigo tracker
						conexTCP = this.threadCliente.getServidor().getTracker();
						if(conexTCP!=null) {
							//Pido nuevo swarm a tracker
							Mensaje msg = new Mensaje(Mensaje.Tipo.SWARM, this.threadCliente.getIpExterna(), this.threadCliente.getPortExterno(), this.threadCliente.getHash());
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
					        
					        conexTCP.getSocket().close();
							if(msg.tipo == Mensaje.Tipo.SWARM) {
								ArrayList<SeedTable> swarm = (ArrayList<SeedTable>) response.lista;
								//asigno swarm a atributo swarm y peersdisponibles de threadcliente
								this.threadCliente.setSwarm(swarm);
								this.threadCliente.setPeersDisponibles(swarm);
							}	
						}						
						
					} catch (Exception e) {
						this.threadCliente.logger.error("ThreadPartesDisponibles - Falló comunicación con tracker al pedir nuevo swarm.");
						e.printStackTrace();
					}					
				}
			}
			
			//Duermo 10 segundos antes de preguntar devuelta por el pocentaje disponible
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				this.threadCliente.logger.error("ThreadPartesDisponibles - Falló thread sleep");
				e.printStackTrace();
			}
		}
	}

}
