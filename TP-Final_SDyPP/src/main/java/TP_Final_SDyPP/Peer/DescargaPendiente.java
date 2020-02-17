package TP_Final_SDyPP.Peer;

public class DescargaPendiente {
	private String hash;
	private String name;
	private String nameFile;
	private boolean activo;
	
	public DescargaPendiente(String name, String nameFile, String hash) {
		this.hash = hash;
		this.name = name;
		this.nameFile = nameFile;
		this.activo = false;
	}
	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNameFile() {
		return nameFile;
	}

	public void setNameFile(String nameFile) {
		this.nameFile = nameFile;
	}
	
	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}
}
