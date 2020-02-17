package TP_Final_SDyPP.UI;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;
import java.lang.ModuleLayer.Controller;

import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Peer.PeerMain;

public class InicioPeerController implements Observer {
	//FXML inicioPeer.fxml
	@FXML private TextField port;
	@FXML private TextField pathJSONs;
	@FXML private TextArea msg;
	
	private ActionEvent eventoActual;
	
	//Contructor
	public InicioPeerController(){
		PeerMain.getInstance().addObserver(this);
	}
	
	//Observer
	@Override
	public void update(Object o, int op, String log) 
	{
		if (o instanceof PeerMain)
		{
			PeerMain peerMain = (PeerMain) o;
			switch (op)
			{
				case 1:
					try {
						this.cambiarVista();
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					break;
				case 2://error
					this.error();
					break;
			}
		}
	}
	
	private void cambiarVista() throws IOException {	
		Stage stage = (Stage)((Node)eventoActual.getSource()).getScene().getWindow();
		stage.close();//Cerrar ventana
		
		FXMLLoader loader = new FXMLLoader(getClass().getResource("principalPeer.fxml"));
		Parent root = loader.load();
		PrincipalPeerController controller = loader.getController();
		Scene scene = new Scene(root);		
		
		stage.setOnHidden(e -> {
			try {
				controller.cerrarClienteYServidor();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		});
		
		stage.setTitle("Peer");
		stage.setResizable(false);
		stage.setScene(scene);
		stage.show();
	}
	
	private void error() {
		msg.setText(PeerMain.getInstance().getMensaje());
		msg.setVisible(true);						
	}
	
	//Métodos botones
	public void entrar(ActionEvent event) {
		try {
			eventoActual = event;
			PeerMain.getInstance().peerMain(port.getText() ,pathJSONs.getText());
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void elegirCarpetaJSONs(ActionEvent event) {
		DirectoryChooser directoryChooser = new DirectoryChooser();
		File selectedDirectory = directoryChooser.showDialog(null);
		
		if(selectedDirectory != null)
			Platform.runLater(()->pathJSONs.setText(selectedDirectory.getAbsolutePath()));
	}

}
