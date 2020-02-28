package TP_Final_SDyPP.Peer;

public class ParteArchivo {

	private int parte;
	private String hash;
	private Estado estado;
	private int size;
	
	public enum Estado{
		PENDIENTE,
		DESCARGANDO
	}
	
	public ParteArchivo(int parte, String hash, Estado estado, int size){
		this.parte = parte;
		this.hash = hash;
		this.estado = estado;
		this.size = size;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
	
	public int getParte() {
		return parte;
	}

	public void setParte(int parte) {
		this.parte = parte;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public Estado getEstado() {
		return estado;
	}

	public void setEstado(Estado estado) {
		this.estado = estado;
	}
}
