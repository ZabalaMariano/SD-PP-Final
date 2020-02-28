package TP_Final_SDyPP.UI;

import TP_Final_SDyPP.Peer.Cliente;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class CambiarNroThreadsController {

	//FXML cambiarNroThreads.fxml
	@FXML private TextField nroThreads;
	@FXML private Button btnCancelar;
	@FXML private Button btnAceptar;
	
	private Cliente cliente;
	private ObservableList<DatosDescarga> data;
	private int nuevoNroThreads = 0;
	
	public void setTextNroThreads(int nroThreads) {
		this.nroThreads.setText(""+nroThreads);
	}
	
	public void setCliente(Cliente cliente) {
		this.cliente = cliente;
	}
	
	public void cancelar() {
		this.cerrar();
	}
	
	public void aceptar() {
		boolean numeroValido = true;
		try {
			nuevoNroThreads = Integer.parseInt(nroThreads.getText());
			if(nuevoNroThreads<1)
				numeroValido = false;
		}catch(NumberFormatException  e){
    		numeroValido = false;
    	}
		
		if(numeroValido) {
			
			Task<Void> task = new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					//Pauso todas las descargas y cambio leechersdisponibles
					cliente.pausarDescargas();
					
				    for (DatosDescarga dd : data) {
			    		Button b = dd.getStartStop();
			    		b.setOnAction(e -> {try {
							cliente.reanudarDescarga(dd.getHash());
						} catch (Exception exp) {
							exp.printStackTrace();
						}});
						//Cambia texto
						Platform.runLater(()->b.setText("Start"));
				    }
				    
					cliente.setNroThreads(nuevoNroThreads);					
					return null;
				}
			};
			new Thread(task).start();
			cerrar();
		}
	}
	
	private void cerrar() {
		Stage stage = (Stage) btnCancelar.getScene().getWindow();
	    stage.close();//Cerrar ventana		
	}

	public void setBotones(ObservableList<DatosDescarga> data) {
		this.data = data;
	}
}
