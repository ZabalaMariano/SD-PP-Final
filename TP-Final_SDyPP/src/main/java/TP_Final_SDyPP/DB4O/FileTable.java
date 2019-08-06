package TP_Final_SDyPP.DB4O;

import java.io.Serializable;

public class FileTable implements Serializable {

	private static final long serialVersionUID = 8684600198991918026L;
	private String name;
	private String hash; 
	private long size;
	
	public FileTable() {
		
	}
	
	public FileTable(String name, String hash, long size) {
		this.name = name;
		this.hash = hash;
		this.size = size;
	}
	
	public FileTable(String hash) {
		this.hash = hash;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
}
