package TP_Final_SDyPP.UI;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;

public class PeerUI extends Application{

	public static void main(String[] args) {	
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		Parent root = FXMLLoader.load(getClass().getResource("inicioPeer.fxml"));
		Scene scene = new Scene(root);
		scene.getStylesheets().add(getClass().getResource("peer.css").toExternalForm());
		stage.setTitle("Peer");
		stage.setResizable(false);
		stage.setScene(scene);
		stage.show();
	}

}
