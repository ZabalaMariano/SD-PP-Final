package TP_Final_SDyPP.Otros;

import java.io.Serializable;
import java.security.PublicKey;

//Esta clase se utiliza para almacenar los datos de los maestros (Id, Ip y Puerto)
//Cada master contiene una lista de maestros. 
public class TrackerInfo implements Serializable, Comparable {

	private static final long serialVersionUID = 4312714091653121801L;
	private int id; 
	private String ip;
	private int port;

	public TrackerInfo(int id, String ip, int port) {
		this.setId(id); //Id del maestro 
		this.setIp(ip); //Ip del maestro
		this.setPort(port); //Puerto de escucha del maestro
	}
	
	@Override
	public int compareTo(Object id) {
		int compareID=((TrackerInfo)id).getId();
        return this.id - compareID;
	}

	public int getId() {
		return this.id;
	}

	private void setId(int id) {
		this.id = id;
	}

	public String getIp() {
		return this.ip;
	}

	private void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return this.port;
	}

	private void setPort(int port) {
		this.port = port;
	}
	
}
