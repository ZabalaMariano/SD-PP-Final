package TP_Final_SDyPP.Otros;

import java.io.Serializable;

import javafx.scene.control.Button;

public class DatosArchivo implements Serializable {
	private static final long serialVersionUID = 4507788224255531760L;
	private String name; 
	private long size;
	private int cantSeeds;
	private int cantLeechers;
	private String hash;
	
	//Para tableView
	private String tamanio;
	private int pos;
	private Button descargar;
	
	public DatosArchivo(String hash, String name, long size, int cantSeeds, int cantLeechers) {
		this.hash = hash;
		this.name = name;
		this.size = size;
		this.cantSeeds = cantSeeds;
		this.cantLeechers = cantLeechers;
	}
	
	public Button getDescargar() {
		return descargar;
	}
	
	public void setDescargar(Button descargar) {
		this.descargar = descargar;
	}
	
	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}
	
	public int getCantSeeds() {
		return cantSeeds;
	}

	public void setCantSeeds(int cantSeeds) {
		this.cantSeeds = cantSeeds;
	}

	public int getCantLeechers() {
		return cantLeechers;
	}

	public void setCantLeechers(int cantLeechers) {
		this.cantLeechers = cantLeechers;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}
	
	public String getTamanio() {
		return tamanio;
	}

	public void setTamanio(String tamanio) {
		this.tamanio = tamanio;
	}
	
	public int getPos() {
		return pos;
	}

	public void setPos(int pos) {
		this.pos = pos;
	}
}
