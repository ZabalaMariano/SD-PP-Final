package TP_Final_SDyPP.Otros;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import TP_Final_SDyPP.DB4O.FileTable;
import TP_Final_SDyPP.DB4O.SeedTable;

public class Mensaje implements Serializable {

	private static final long serialVersionUID = -6185343867071731137L;

	public enum Tipo{ 
		FIND_FILE, //Enviado por el peer cuando solicita un archivo al master (puede ser un substring del nombre de un archivo)
		REQUEST,//Enviado por el peer cuando ya consiguió el nombre completo del archivo y espera por los peer servidores para iniciar la descarga
		ERROR,//Devuelto por Master o peer con un mensaje string que indica que paso
		ACK,
		SEND_FILE,//Peer servidor envia el archivo (Master no participa)
		FILES_AVAILABLE, //Lo envia el master en respuesta a una busqueda de un archivo
		FILE_UNAVAILABLE,//Enviado por peer servidor cuando no posee el archivo a descargar. Ya no es seed.
		CHECK_AVAILABLE,
		SET_PRIMARY, //Enviado por un master cuando el se define como primario
		CHANGE_PRIMARY, //Es utilizado cuando un master detecta que se cayó el primario y encuentra su reemplazo
		TRACKER_REGISTER, //Luego de que un tracker se configura, envía este mensaje al primario para registrarse y recibir actualizaciones
		TRACKER_UPDATE, //Utilizado para inicializar un tracker, o para hacer una replicación ante una actualización
		TRACKER_PRIMARIO,//Tracker responde a GET_PRIMARIO
		GET_PRIMARIO, //Peer pide primario a su tracker para enviarle JSON
		REPLICATE_FILE, //Primario envía nuevo JSON a todos los trackers
		FILE,
		GET_FILE,
		DOWNLOAD, //Pide archivo a peer
		SEND_SEED, //envia tracker a primario, luego de recibir un DOWNLOAD
		SWARM, //enviado por tracker a peer luego de DOWNLOAD. Enviado por peer al pedir nuevo swarm
		EXIT, //El cliente escribe salir
		PIECES_AVAILABLE, //pide array del peer que indica que partes del archivo descargó
		PIECES_AVAILABLE_CLOSE,
		LOADDOWN, //peer cliente le envia a peer servidor cuando no tiene más partes para descargar de él
		GET_PIECE, //indico a peer servidor parte que necesito que me envíes
		QUIT_SWARM, //Retira a peer de un swarm segun socket y hash.
		SEND_QUIT_SWARM, //enviado por tracker a primario para replicar el quitar un peer de la tabla de seeds.
		COMPLETE, //enviado por peer a tracker para no aparecer en swarms futuros
		FREE,//enviado por peer a tracker para seguir apareciendo en swarms futuros
		SEND_COMPLETE,//enviado por tracker a primario, luego de recibir "complete"
		SEND_FREE,//enviado por tracker a primario, luego de recibir "free"
		NEW_SEED,//peer a tracker cuando finaliza descarga
		SEND_NEW_SEED,//enviado por tracker a primario, luego de recibir "new_seed"
		ALIVE,
		GET_TRACKERS,
		GET_DB,
		GET_FILES_OFFERED,
	}
	
	public Tipo tipo;
	public String string;
	public String ip;
	public int port;
	public File file;
	public TrackerInfo tracker;
	public ArrayList<String> hashes;
	public String hash;
	public String path;
	public ArrayList<TrackerInfo> listaTrackers;
	public Object lista;//lista swarm(seedtable) y filesAvailable(filetable)
	public int nroParte;
	public int sizeParte;
	public String pathAParte;
	public boolean seed;
	public PublicKey kpub;
	public SecretKey key;
	public byte[] keyEncriptada;
	public byte[] datosEncriptados;
	public boolean mantenerConexion;	
	
	//Constructores
	public Mensaje() {}	
	
	public Mensaje(Tipo tipo, byte[] keyEncriptada) {//ACK DEL PEER SERVIDOR
		this.tipo = tipo;
		this.keyEncriptada = keyEncriptada;
	}
	
	public Mensaje(Tipo tipo, byte[] keyEncriptada, byte[] datosEncriptados) {//GET_TRACKERS
		this.tipo = tipo;
		this.keyEncriptada = keyEncriptada;
		this.datosEncriptados = datosEncriptados;
	}
	
	public Mensaje(Tipo tipo, String string) {//GET_FILE (string=hash) 
		this.tipo = tipo;                     //REQUEST (string=nombreArchivo)
		this.string = string;				  //ERROR (string=Mensaje error)
	}										  //FIND_FILE (string = file name)	
											  
	
	public Mensaje(Tipo tipo, String pathPartes, String hash) {//PIECES_AVAILABLE
		this.tipo = tipo;
		this.string = pathPartes;
		this.hash = hash;
	}
	
	public Mensaje(Tipo tipo) {//LOADDOWN, ALIVE, ERROR
		this.tipo = tipo;
	}
	
	public Mensaje(Tipo tipo, PublicKey kpub) {//CHECK_AVAILABLE
		this.tipo = tipo;
		this.kpub = kpub;
	}
	
	public Mensaje(Tipo tipo, PublicKey kpub, boolean mantenerConexion) {//CHECK_AVAILABLE
		this.tipo = tipo;
		this.kpub = kpub;
		this.mantenerConexion = mantenerConexion;
	}
	
	public Mensaje(Tipo tipo, SecretKey key) {
		this.tipo = tipo;
		this.key = key;
	}

	public Mensaje(Tipo tipo, Object lista) {//SWARM, FILES_AVAILABLE, GET_FILES_OFFERED
		this.tipo = tipo;	
		this.lista = lista;
	}
	
	public Mensaje(Tipo tipo, ArrayList<TrackerInfo> listaTrackers, ArrayList<String> hashes) {//TRACKER_UPDATE
		this.tipo = tipo;	
		this.listaTrackers = listaTrackers;
		this.hashes = hashes; 
	}
	
	public Mensaje(Tipo tipo, TrackerInfo tracker) {//SET_PRIMARY , CHANGE_PRIMARY
		this.tipo = tipo;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo tipo, ArrayList<String> hashes, TrackerInfo tracker) {//TRACKER_REGISTER
		this.tipo = tipo;
		this.tracker = tracker;
		this.hashes = hashes;
	}
	
	public Mensaje(Tipo tipo, TrackerInfo tracker, ArrayList<TrackerInfo> listaTrackers) {//TRACKER_REPLICATE
		this.tipo = tipo;
		this.tracker = tracker;	
		this.listaTrackers = listaTrackers;
	}
	
	public Mensaje(Tipo tipo, ArrayList<TrackerInfo> listaTrackers) {//GET_TRACKERS
		this.tipo = tipo;	
		this.listaTrackers = listaTrackers;
	}
	
	public Mensaje(Tipo tipo, String ip, int port) {//TRACKER_PRIMARIO
		this.tipo = tipo;
		this.ip = ip;
		this.port = port;
	}
	
	public Mensaje(Tipo tipo, String ip, int port, String string) {
		this.tipo = tipo;										   //REPLICATE_FILE (string = nombreJSON) o sea su hashFinal
		this.string = string;										//SEND_FILE (string=hashJSON)
		this.ip = ip;												//QUIT_SWARM (string = hash)
		this.port = port;											//COMPLETE string=hash
	}																//NEW_SEED string = hash
																	//SWARM string hash
	
	public Mensaje(Tipo tipo, String hash, String path, String ip, int port) {//DOWNLOAD
		this.tipo = tipo;										   
		this.hash = hash;										
		this.path = path;
		this.ip = ip;
		this.port = port;
	}
	
	public Mensaje(Tipo tipo, String hash, String path, String ip, int port, TrackerInfo tracker) {//SEND_SEED
		this.tipo = tipo;										   
		this.hash = hash;										
		this.path = path;
		this.ip = ip;
		this.port = port;
		this.tracker = tracker;
	}

	public Mensaje(Tipo tipo, String ip, int port, String hash, TrackerInfo tracker) {//SEND_QUIT_SWARM, SEND_NEW_SEED
		this.tipo = tipo;										   
		this.hash = hash;										
		this.ip = ip;
		this.port = port;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo tipo, String ip, int port, TrackerInfo tracker) {//SEND_COMPLETE
		this.tipo = tipo;										   										
		this.ip = ip;
		this.port = port;
		this.tracker = tracker;
	}
	
	public Mensaje(Tipo getPiece, int nroParte, String pathAParte, boolean seed, int sizeParte, String hash) {//GET_PIECE
		this.tipo = getPiece;
		this.nroParte = nroParte;
		this.pathAParte = pathAParte;
		this.seed = seed;
		this.sizeParte = sizeParte;
		this.hash = hash;
	}
	
	//Envío mensajes
	public void enviarMensaje(ConexionTCP c, Mensaje m, KeysGenerator kg) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
		//encripto mensaje con la clave simetrica
		byte[] datosAEncriptar = c.convertToBytes(m);
		byte[] mensajeEncriptado = kg.encriptarSimetrico(c.getKey(), datosAEncriptar);
		c.getOutBuff().write(mensajeEncriptado,0,mensajeEncriptado.length);
		c.getOutBuff().flush();	
	}
	
	public byte[] recibirMensaje(ConexionTCP c, KeysGenerator kg) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, ClassNotFoundException {	
		int msgSize = 1024*1024;//1MB
        byte[] buffer = new byte[msgSize];
        int byteread = c.getInBuff().read(buffer, 0, msgSize);
        //desencripto con la clave simetrica
        byte[] datosEncriptados = Arrays.copyOfRange(buffer, 0, byteread);
        byte[] msgDesencriptado = kg.desencriptarSimetrico(c.getKey(),datosEncriptados);

        return msgDesencriptado;
	}


}
