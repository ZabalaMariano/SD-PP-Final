package TP_Final_SDyPP.UI;

import javafx.scene.control.Button;

public class DatosDescarga {

	private String hash;
	private Button startStop;
	private String nombre;
	private String tamanio;
	private String descargado;
	private String velDescarga;
	private String disponibleEnSwarm;
	private Button graficos;
	
	public DatosDescarga() {}
	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public Button getStartStop() {
		return startStop;
	}

	public void setStartStop(Button startStop) {
		this.startStop = startStop;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public String getTamanio() {
		return tamanio;
	}

	public void setTamanio(String tamanio) {
		this.tamanio = tamanio;
	}

	public String getDescargado() {
		return descargado;
	}

	public void setDescargado(String descargado) {
		this.descargado = descargado;
	}

	public String getVelDescarga() {
		return velDescarga;
	}

	public void setVelDescarga(String velDescarga) {
		this.velDescarga = velDescarga;
	}

	public String getDisponibleEnSwarm() {
		return disponibleEnSwarm;
	}

	public void setDisponibleEnSwarm(String disponibleEnSwarm) {
		this.disponibleEnSwarm = disponibleEnSwarm;
	}

	public Button getGraficos() {
		return graficos;
	}

	public void setGraficos(Button graficos) {
		this.graficos = graficos;
	}
	
}
