package TP_Final_SDyPP.Peer;

public class DescargaPendiente {
	private String name;
	private boolean activo;
	
	public DescargaPendiente(String name) {
		this.name = name;
		this.activo = false;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isActivo() {
		return activo;
	}

	public void setActivo(boolean activo) {
		this.activo = activo;
	}
}
