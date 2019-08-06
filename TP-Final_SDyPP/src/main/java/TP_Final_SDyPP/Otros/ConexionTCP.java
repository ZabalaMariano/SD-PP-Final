package TP_Final_SDyPP.Otros;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javax.crypto.SecretKey;

public class ConexionTCP {
	private final int mb = 1024*1024;
	private ObjectInputStream inObj;
	private ObjectOutputStream outObj;
	private BufferedInputStream inBuff;
	private BufferedOutputStream outBuff;
	private Socket socket;
	private SecretKey key;
	
	//Mediante este constructor creo un nuevo socket con la ip y puerto recibidos como parámetros, y además
	//inicializo los canales de lectura y escritura
	public ConexionTCP(String ip, int port) throws Exception {
		this.setSocket(new Socket(ip,port));
		this.setOutObj(new ObjectOutputStream(this.getSocket().getOutputStream())); 
		this.setInObj(new ObjectInputStream(this.getSocket().getInputStream()));
		this.setOutBuff(new BufferedOutputStream(this.getSocket().getOutputStream(),mb));
		this.setInBuff(new BufferedInputStream(this.getSocket().getInputStream()));
	}

	//Mediante este constructor mantengo el socket recibido como parámetro e inicializo los canales de lectura
	// y escritura para ese socket
	public ConexionTCP(Socket s) throws Exception {
		this.setSocket(s); 
		this.setOutObj(new ObjectOutputStream(this.getSocket().getOutputStream())); 
		this.setInObj(new ObjectInputStream(this.getSocket().getInputStream()));
		this.setOutBuff(new BufferedOutputStream(this.getSocket().getOutputStream(),mb));
		this.setInBuff(new BufferedInputStream(this.getSocket().getInputStream()));
	}

	public SecretKey getKey() {
		return key;
	}

	public void setKey(SecretKey key) {
		this.key = key;
	}

	public BufferedInputStream getInBuff() {
		return inBuff;
	}

	public void setInBuff(BufferedInputStream inBuff) {
		this.inBuff = inBuff;
	}

	public BufferedOutputStream getOutBuff() {
		return outBuff;
	}

	public void setOutBuff(BufferedOutputStream outBuff) {
		this.outBuff = outBuff;
	}
	
	public ObjectInputStream getInObj() {
		return this.inObj;
	}

	private void setInObj(ObjectInputStream inObj) {
		this.inObj = inObj;
	}

	public ObjectOutputStream getOutObj() {
		return this.outObj;
	}

	private void setOutObj(ObjectOutputStream outObj) {
		this.outObj = outObj;
	}

	public Socket getSocket() {
		return this.socket;
	}

	private void setSocket(Socket socket) throws Exception {
		this.socket = socket;
	}	
	
	public byte[] convertToBytes(Object m) throws IOException {
	    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
	         ObjectOutput out = new ObjectOutputStream(bos)) {
	        out.writeObject(m);
	        return bos.toByteArray();
	    } 
	}
			
	public Object convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
	    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
	         ObjectInput in = new ObjectInputStream(bis)) {
	        return in.readObject();
	    } 
	}
}