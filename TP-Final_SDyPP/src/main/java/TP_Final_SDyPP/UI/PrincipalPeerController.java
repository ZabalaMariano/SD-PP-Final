package TP_Final_SDyPP.UI;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import TP_Final_SDyPP.Observable.Observer;
import TP_Final_SDyPP.Otros.DatosArchivo;
import TP_Final_SDyPP.Otros.Mensaje;
import TP_Final_SDyPP.Peer.Cliente;
import TP_Final_SDyPP.Peer.DescargaPendiente;
import TP_Final_SDyPP.Peer.PeerMain;
import TP_Final_SDyPP.Peer.Servidor;

public class PrincipalPeerController implements Observer, Initializable {
	//FXML PrincipalPeer.fxml
	@FXML private TextArea loggerCliente;
	@FXML private TextArea loggerServidor;
	@FXML private Button btnSalir;
	
	@FXML private TableView<DatosDescarga> tablaDescargas;
	@FXML private TableColumn<DatosDescarga, Button> startStop;
	@FXML private TableColumn<DatosDescarga, String> nombre;
	@FXML private TableColumn<DatosDescarga, String> tamanio;
	@FXML private TableColumn<DatosDescarga, String> descargado;
	@FXML private TableColumn<DatosDescarga, String> velDescarga;
	@FXML private TableColumn<DatosDescarga, String> disponibleEnSwarm;
	@FXML private TableColumn<DatosDescarga, Button> graficos;
	private ObservableList<DatosDescarga> data;
	
	private Cliente cliente;
	
	//Contructor
	public PrincipalPeerController(){
		this.cliente = PeerMain.getInstance().getCliente();
		this.cliente.addObserver(this);
	}
		
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		
		//Update textarea peer cliente
    	Task<Void> updateLogCliente = new Task<Void>() {
            protected Void call() throws Exception {
                Console console = new Console(loggerCliente);
                PrintStream ps = new PrintStream(console, true);
                System.setOut(ps);

                return null;
            }

            protected void succeeded() {
                loggerCliente.textProperty().unbind();
            }
        };
		
        loggerCliente.textProperty().bind(updateLogCliente.messageProperty());
        new Thread(updateLogCliente).start();
		
        //Update textarea peer servidor
        Task<Void> updateLogServidor = new Task<Void>() {
            protected Void call() throws Exception {
                Console console = new Console(loggerServidor);
                PrintStream ps = new PrintStream(console, true);
                System.setErr(ps);

                return null;
            }

            protected void succeeded() {
            	loggerServidor.textProperty().unbind();
            }
        };

        loggerServidor.textProperty().bind(updateLogServidor.messageProperty());
        new Thread(updateLogServidor).start();
                
        //inicializar tabla descargas
		this.startStop.setCellValueFactory(new PropertyValueFactory<DatosDescarga, Button>("startStop"));
		this.nombre.setCellValueFactory(new PropertyValueFactory<DatosDescarga, String>("nombre"));
		this.tamanio.setCellValueFactory(new PropertyValueFactory<DatosDescarga, String>("tamanio"));
		this.descargado.setCellValueFactory(new PropertyValueFactory<DatosDescarga, String>("descargado"));
		this.velDescarga.setCellValueFactory(new PropertyValueFactory<DatosDescarga, String>("velDescarga"));
		this.disponibleEnSwarm.setCellValueFactory(new PropertyValueFactory<DatosDescarga, String>("disponibleEnSwarm"));
		this.graficos.setCellValueFactory(new PropertyValueFactory<DatosDescarga, Button>("graficos"));
		
		data = FXCollections.observableArrayList();
        tablaDescargas.setItems(data);	
        
    	this.cargarDescargasPendientes();
	}
	
    //Consola
    public static class Console extends OutputStream {

        private TextArea output;

        Console(TextArea ta) {
            this.output = ta;
        }

        public void write(int i) throws IOException {
        	Platform.runLater(()->output.appendText(String.valueOf((char) i))); 
        }
    }
	
	private void cargarDescargasPendientes() {//Llena la tabla de descargas con las descargas pendientes
		//Vaciar tablaDescargas, de tener algo
		this.tablaDescargas.getItems().clear();
		
		ArrayList<DescargaPendiente> files = this.cliente.getListaDescargasPendientes();
		boolean nuevaDescarga = false;
		for(DescargaPendiente dp : files) {
			this.agregarDescargaATabla(dp, nuevaDescarga);
		}
	}

	private void start(String hash) {
		this.cliente.reanudarDescarga(hash);
	}

	private void stop(Button b, String hash) {
		this.cliente.pausarDescarga(hash);
		b.setOnAction(e -> {try {
			this.start(hash);
		} catch (Exception exp) {
			exp.printStackTrace();
		}});		
		//Cambia texto
		Platform.runLater(()->b.setText("Start"));
	}

	//Observer
	@Override
	public void update(Object o, int op, String log) 
	{
		if(o instanceof Cliente){
			Cliente cliente = (Cliente) o;
			switch (op)
			{
				case 1://Vista Descargar JSON: lista jsons encontrados
					break;
					
				case 2://Datos nueva descarga para agregar en tabla
					this.cargarNuevaDescarga(log);
					break;
					
				case 3://Cambiar de start a stop
					this.cambiarStartAStop(log);//log = hash
					break;
					
				case 4://Actualizar % descargado y velocidad descarga
					this.actualizarVelocidadDescargaYPorcentajeDescargado(log);
					break;
					
				case 5://Actualizar % disponible en swarm
					this.actualizarPorcentajeDisponibleEnSwarm(log);
					break;
					
				case 6://Al terminar descarga quitar boton start/stop y poner listo que permite borrar la fila.
					this.setBotonBorrar(log);
					break;
					
				case 7://Al terminar descarga agrega boton ver gráficos
					this.setBotonVerGraficos(log);
					break;
			}
		}
	}
	
	private void setBotonVerGraficos(String hash) {
		synchronized(this) {
			//Busco botón
		    for (DatosDescarga dd : data) {
		    	if(dd.getHash().equals(hash)) {
		    		Button b = dd.getGraficos();
		    		b.setVisible(true);
		    		this.setFuncionalidadBotonVer(b, hash);   
		    	}
		    }
		}		
	}
	
	private void setFuncionalidadBotonVer(Button b, String hash) {
		b.setOnAction(e -> {try {
			this.verGraficos(this.cliente.getPathGraficos()+"/"+hash+".json");
			
		} catch (Exception exp) {
			exp.printStackTrace();
		}});		
	}

	private void verGraficos(String path) throws IOException {
		//Cargar scene de resumen descarga
        FXMLLoader loader = new FXMLLoader(getClass().getResource("resumen.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
         
        //Get controller de la scene resumen descarga
        ResumenDescargaController controller = loader.getController();
        //Paso el objeto de descarga terminada para que puede mostrar los datos
        controller.setPathDescargaTerminada(path);
        controller.cargarDatos();
		
		Stage stage = new Stage();
		stage.setTitle("Gráficos descarga");
		stage.setResizable(false);
		stage.setScene(scene);
		stage.show();
	}

	private void setBotonBorrar(String hash) {
		synchronized(this) {
			//Busco boton
		    for (DatosDescarga dd : data) {
		    	if(dd.getHash().equals(hash)) {
		    		/*Platform.runLater(()->{
			    		//Set Velocidad de descarga
			    		dd.setVelDescarga("-");
			    		
			    		//Set porcentaje del archivo disponible en el swarm
			    		dd.setDisponibleEnSwarm("-");
		    		});*/
		    		
		    		//Termino descarga
		    		dd.setTerminoDescarga(true);
		    		
		    		//Set Velocidad de descarga
		    		dd.setVelDescarga("-");
		    		
		    		//Set porcentaje del archivo disponible en el swarm
		    		dd.setDisponibleEnSwarm("-");
		    		
		    		data.set(data.indexOf(dd), dd);
		    		
		    		Button b = dd.getStartStop();
		    		this.setFuncionalidadBotonBorrar(b, hash, dd);
		    	}
		    }
		}
	}

	private void setFuncionalidadBotonBorrar(Button b, String hash, DatosDescarga dd) {
		b.setOnAction(e -> {try {//Al apretar botón Borrar
			//Platform.runLater(()->data.remove(dd));
			data.remove(dd);
			
			//Eliminar archivo en carpeta graficos
			File file = new File(this.cliente.getPathGraficos()+"/"+hash+".json");
			file.delete();
			
			//Eliminar archivo de "Descargas pendientes"
			file = new File(this.cliente.getPathDescargasPendientes()+"/"+hash+".json");
			file.delete();
			
			//Eliminar descargaPendiente de array de cliente
			int i=0;
			boolean eliminado = false;
			synchronized(this.cliente.getListaDescargasPendientes()) {
				while(i<this.cliente.getListaDescargasPendientes().size() && !eliminado){
					if(this.cliente.getListaDescargasPendientes().get(i).getHash().equals(hash)) {
						this.cliente.eliminarDescargaPendiente(i);
					}
					i++;
				}
			}
			
		} catch (Exception exp) {
			exp.printStackTrace();
		}});
		//Cambia texto
		Platform.runLater(()->b.setText("Borrar"));
	}

	private void cambiarStartAStop(String hash) {
		synchronized(this) {
			//Busco boton
		    for (DatosDescarga dd : data) {
		    	if(dd.getHash().equals(hash)) {
		    		Button b = dd.getStartStop();
		    		b.setOnAction(e -> {try {
						this.stop(b, hash);
					} catch (Exception exp) {
						exp.printStackTrace();
					}});
					//Cambia texto
					Platform.runLater(()->b.setText("Stop"));
		    	}
		    }
		}
	}

	private void actualizarPorcentajeDisponibleEnSwarm(String log) {
		String[] tokens = log.split("-");
		String porcentajeDisponible = tokens[0];
		String hash = tokens[1];

		synchronized(this) {
		    for (DatosDescarga dd : data) {
		    	if(dd.getHash().equals(hash)) {
		    		/*Platform.runLater(()->{
				        dd.setDisponibleEnSwarm(porcentajeDisponible);
				        data.set(data.indexOf(dd), dd);
		    		});*/
		    		
			        dd.setDisponibleEnSwarm(porcentajeDisponible);
			        data.set(data.indexOf(dd), dd);

		    	}
		    }		
		}
	}

	private void actualizarVelocidadDescargaYPorcentajeDescargado(String log) {
		String[] tokens = log.split("-");
		String descargado = tokens[0];
		String velDescarga = tokens[1];
		String hash = tokens[2];

		synchronized(this) {
		    for (DatosDescarga dd : data) {
		    	if(dd.getHash().equals(hash)) {
		    		/*Platform.runLater(()->{
				        dd.setDescargado(descargado);
				        dd.setVelDescarga(velDescarga);
				        data.set(data.indexOf(dd), dd);		    		
		    		});*/
		    		
		    		dd.setDescargado(descargado);
			        dd.setVelDescarga(velDescarga);
			        data.set(data.indexOf(dd), dd);
		    	}
		    }		
		}
	}
	
	private void cargarNuevaDescarga(String boton) {//Llena la tabla de descargas con las descargas pendientes
		boolean descargaEmpezo = true;
		if(boton.equals("start"))
			descargaEmpezo = false;
		
		int index = this.cliente.getListaDescargasPendientes().size();
		DescargaPendiente dp = this.cliente.getListaDescargasPendientes().get(index-1);
		this.agregarDescargaATabla(dp, descargaEmpezo);		
	}
	
	private void agregarDescargaATabla(DescargaPendiente dp, boolean nuevaDescarga) {
		long fileSize = 0;
		String descargado = "-";
		
		//obtener datos json descargaPendiente
		Object obj = null;
		try {
			FileReader fileReader = new FileReader(this.cliente.getPathDescargasPendientes()+"/"+dp.getHash()+".json");
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			
			JSONObject jsonObject = (JSONObject) obj;
	        fileSize = (long) jsonObject.get("fileSize");
	        descargado = (String) jsonObject.get("descargado");
		} catch (IOException | ParseException | ClassCastException e1) {
			e1.printStackTrace();
		}
		
		DatosDescarga dd = new DatosDescarga();
		
		//Set Hash
		String hash = dp.getHash();
		dd.setHash(hash);
		
		//Set Nombre
		dd.setNombre(dp.getName());
		
		//Set Tamaño
		String[] unidades = {"Bytes","KB","MB","GB","TB"};
		int pos = 0;
		double peso = fileSize;
		while(peso >= 1024) {
			peso /= 1024;
			pos++;
		}
		dd.setTamanio(String.format("%.2f " + unidades[pos],peso));
		
		//Set Porcentaje descargado
		dd.setDescargado(descargado);
		
		//Set Velocidad de descarga
		dd.setVelDescarga("-");
		
		//Set porcentaje del archivo disponible en el swarm
		dd.setDisponibleEnSwarm("-");
		
		//Set botones start/stop o Borrar y gráficos
		if(descargado.equals("100%")) {//Si ya se descargó, pongo botón Borrar y Ver de gráficos
			dd.setTerminoDescarga(true);
			
			Button b = new Button();
			this.setFuncionalidadBotonBorrar(b, hash, dd);
			dd.setStartStop(b);
			
			Button graficos = new Button("Ver");
			this.setFuncionalidadBotonVer(graficos, hash);
			dd.setGraficos(graficos);			
		} else {
			dd.setTerminoDescarga(false);
			
			//Si no se descargó el 100% pongo botón de Start/Stop.
			if(nuevaDescarga) {
				Button stop = new Button("Stop");
				stop.setOnAction(e -> {try {
					this.stop(stop, hash);
				} catch (Exception exp) {
					exp.printStackTrace();
				}});
				dd.setStartStop(stop);	
			} else {
				Button start = new Button("Start");
				start.setOnAction(e -> {try {
					this.start(hash);
				} catch (Exception exp) {
					exp.printStackTrace();
				}});
				dd.setStartStop(start);
			}			
			
			Button graficos = new Button("Ver");
			graficos.setVisible(false);
			dd.setGraficos(graficos);
		}
		
		//Platform.runLater(()->data.add(dd));		
		data.add(dd);
	}

	//Métodos botones
	public void crearJSON(ActionEvent event) throws IOException, NoSuchAlgorithmException, ParseException {
		FileChooser fileChooser = new FileChooser();
		File selectedFile = fileChooser.showOpenDialog(null);
		
		if(selectedFile != null) {
			Task<Void> task = new Task<Void>() {
			  @Override
			  protected Void call() throws Exception {  
				  cliente.crearArchivo(selectedFile.getAbsolutePath());
				  return null;
			  }
			};
			new Thread(task).start();
		}

	}
	
	public void publicarJSON(ActionEvent event) throws Exception {
		FileChooser fileChooser = new FileChooser();
		File selectedFile = fileChooser.showOpenDialog(null);
		
		if(selectedFile != null) {
			Task<Void> task = new Task<Void>() {
			  @Override
			  protected Void call() throws Exception {  
				  cliente.subirArchivo(selectedFile.getAbsolutePath());
				  return null;
			  }
			};
			new Thread(task).start();
		}
	}
	
	public void archivosPublicados(ActionEvent event) throws Exception {
		Task<Void> task = new Task<Void>() {
		  @Override
		  protected Void call() throws Exception {  
			  cliente.archivosOfrecidos();
			  return null;
		  }
		};
		new Thread(task).start();
	}
	
	public void descargarJSON(ActionEvent event) throws Exception {
		Parent root = FXMLLoader.load(getClass().getResource("descargarJSON.fxml"));
		Scene scene = new Scene(root);

		Stage stage = new Stage();
		stage.setTitle("Descargar JSON");
		stage.setResizable(false);
		stage.setScene(scene);
		stage.show();
	}
	
	public void descargarArchivo(ActionEvent event) throws Exception {
		FileChooser JSONChooser = new FileChooser();
		File selectedJSON = JSONChooser.showOpenDialog(null);//JSON del archivo a descargar
				
		if(selectedJSON != null) {
			System.out.println("JSON elegido para descargar archivo: "+selectedJSON.getAbsolutePath());
			DirectoryChooser directoryChooser = new DirectoryChooser();
			File selectedDirectory = directoryChooser.showDialog(null);//Path donde guardar archivo descargado

			if(selectedDirectory != null) {
				System.out.println("Carpeta elegida para guardar archivo a descargar: "+selectedDirectory.getAbsolutePath());
				
				Task<Void> task = new Task<Void>() {
				  @Override
				  protected Void call() throws Exception {  
					  cliente.descargarArchivo(selectedJSON.getAbsolutePath(), selectedDirectory.getAbsolutePath());
					  return null;
				  }
				};
				new Thread(task).start();
				
			} else {
				System.out.println("No se eligio la carpeta donde guardar la descarga.");
			}
		} else {
			System.out.println("No se eligio el JSON para iniciar la descarga.");
		}	
	}
	
	public void actualizarTrackers(ActionEvent event) throws Exception {
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {  
				cliente.actualizarTrackers();
				return null;
			}
		};
		new Thread(task).start();
	}
	
	public void cambiarNroThreads(ActionEvent event) throws IOException {
		int nroThreads = this.cliente.getNroThreads();
		
		//Cargar scene de cambiarNroThreads
        FXMLLoader loader = new FXMLLoader(getClass().getResource("cambiarNroThreads.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);
         
        //Get controller de la scene cambiarNroThreads
        CambiarNroThreadsController controller = loader.getController();
        //Paso el nro de threads que posee el cliente
        controller.setTextNroThreads(nroThreads);
        controller.setCliente(this.cliente);
        controller.setBotones(this.data);
        
		Stage stage = new Stage();
		stage.setTitle("Cambiar Nro. Threads");
		stage.setResizable(false);
		stage.setScene(scene);
		stage.show();
	}	
	
	public void salir(ActionEvent event) throws Exception {
		Stage stage = (Stage) btnSalir.getScene().getWindow();
	    stage.close();//Cerrar ventana
	}	
	
	public void cerrarClienteYServidor() throws Exception {
		PeerMain.getInstance().salir();//Detiene thread servidor
		cliente.pausarDescargas();
	}

}
