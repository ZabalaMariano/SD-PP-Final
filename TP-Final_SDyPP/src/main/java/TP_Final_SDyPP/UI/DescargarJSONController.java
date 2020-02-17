package TP_Final_SDyPP.UI;

import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Otros.DatosArchivo;
import TP_Final_SDyPP.Peer.Cliente;
import TP_Final_SDyPP.Peer.PeerMain;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class DescargarJSONController implements Observer, Initializable {
	
	//FXML descargarJSON.fxml
	@FXML private TextField nombreJSON;
	@FXML private TableView<DatosArchivo> tablaJSONs;
	@FXML private TableColumn<DatosArchivo, Integer> posicion;
	@FXML private TableColumn<DatosArchivo, String> nombre;
	@FXML private TableColumn<DatosArchivo, String> tamanio;
	@FXML private TableColumn<DatosArchivo, Integer> seeds;
	@FXML private TableColumn<DatosArchivo, Integer> leechers;
	@FXML private TableColumn<DatosArchivo, Button> descargar;
	private ObservableList<DatosArchivo> data;
	
	private Cliente cliente;
	
	
	//Contructor
	public DescargarJSONController(){
		this.cliente = PeerMain.getInstance().getCliente();
		this.cliente.addObserver(this);
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.posicion.setCellValueFactory(new PropertyValueFactory<DatosArchivo, Integer>("pos"));
		this.nombre.setCellValueFactory(new PropertyValueFactory<DatosArchivo, String>("name"));
		this.tamanio.setCellValueFactory(new PropertyValueFactory<DatosArchivo, String>("tamanio"));
		this.seeds.setCellValueFactory(new PropertyValueFactory<DatosArchivo, Integer>("cantSeeds"));
		this.leechers.setCellValueFactory(new PropertyValueFactory<DatosArchivo, Integer>("cantLeechers"));
		this.descargar.setCellValueFactory(new PropertyValueFactory<DatosArchivo, Button>("descargar"));
		
		data = FXCollections.observableArrayList();
        tablaJSONs.setItems(data);		
	}

	public void buscarJSON(ActionEvent event) throws Exception {
		Task<Void> task = new Task<Void>() {
		  @Override
		  protected Void call() throws Exception {  
			  cliente.buscarMetaArchivo(nombreJSON.getText());
			  return null;
		  }
		};
		new Thread(task).start();
	}

	@Override
	public void update(Object o, int op, String log) {
		if(o instanceof Cliente){
			Cliente cliente = (Cliente) o;
			switch (op)
			{
				case 1:
					this.mostrarMetaArchivosEncontrados();//Llega luego de "BuscarJSON", en botón "descargar JSON"
					break;
			}
		}
	}
	
	private void mostrarMetaArchivosEncontrados() {
		//Vaciar tablaJSONs, de tener algo
		this.tablaJSONs.getItems().clear();
		
		ArrayList<DatosArchivo> files = this.cliente.getMetaArchivosEncontrados();
		int i=1;
		String[] unidades = {"Bytes","KB","MB","GB","TB"};
		
		for(DatosArchivo da : files) {
			double peso = da.getSize();
			int pos = 0;
			
			while(peso >= 1024) {
				peso /= 1024;
				pos++;
			}
			
			da.setPos(i);
			da.setTamanio(String.format("%.2f " + unidades[pos],peso));
			
			Button b = new Button("Descargar");
			b.setOnAction(e -> {try {
				this.descargarJSON(da.getPos());
			} catch (Exception exp) {
				exp.printStackTrace();
			}});
			
			da.setDescargar(b);
			i++;
			
			data.add(da);
		}

	}
	
	public void descargarJSON(int pos) throws Exception {
		Task<Void> task = new Task<Void>() {
		  @Override
		  protected Void call() throws Exception {  
			  cliente.descargarJSON(pos);
			  return null;
		  }
		};
		new Thread(task).start();
	}

}
