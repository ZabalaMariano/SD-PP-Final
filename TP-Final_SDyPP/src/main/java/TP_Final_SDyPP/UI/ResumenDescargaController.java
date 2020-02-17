package TP_Final_SDyPP.UI;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.ResourceBundle;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.text.Text;

public class ResumenDescargaController {

	private String pathDescargaTerminada;
	
	@FXML private Label tiempoTotal;
	@FXML private Label velocidadPromedio;
	@FXML private Label tiempoMinimo;
	@FXML private Label tiempoMaximo;
	@FXML private Label tiempoPromedio;
	@FXML private Label tiempoDesvioEstandar;
	
	@FXML private LineChart<?,?> chartTiempoDescarga;
	@FXML private BarChart<?,?> chartDescargasPorPeer;
	@FXML private BarChart<?,?> chartVelPromedioPorPeer;
	@FXML private StackedBarChart<?,?> chartErroresPorPeer;
	
	@FXML private CategoryAxis xAxisFallos;
	
	public void setPathDescargaTerminada(String path) {
		this.pathDescargaTerminada = path;
	}

	public void cargarDatos() {
		Object obj;
		FileReader fileReader;
		try {
			fileReader = new FileReader(this.pathDescargaTerminada);
			obj = new JSONParser().parse(fileReader);
			fileReader.close();
			JSONObject json = (JSONObject) obj;
			
			this.cargarTabResumen(json);
			this.cargarTabTiempoDescarga(json);
			this.cargarTabDescargasPorPeer(json);
			this.cargarTabVelPromedioPorPeer(json);
			this.cargarTabErroresPorPeer(json);
			
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}		
	}

	private void cargarTabErroresPorPeer(JSONObject json) {
		JSONArray fallosPeers = (JSONArray) json.get("fallosPeers");
		xAxisFallos.getCategories().addAll("Exceso de Tiempo", "Hash incorrecto", "Fallo conexion");
		
		XYChart.Series seriesTiempo = new XYChart.Series<>();
		seriesTiempo.setName("Exceso de Tiempo");
		
		XYChart.Series seriesHash = new XYChart.Series<>();
		seriesHash.setName("Hash incorrecto");
		
		XYChart.Series seriesConexion = new XYChart.Series<>();
		seriesConexion.setName("Fallo conexion");
		
		for(int i=0; i<fallosPeers.size(); i++) {
			JSONObject jo = (JSONObject) fallosPeers.get(i);
			String x = (String) jo.get("peer");
			long errorTiempo = (long) jo.get("errorTiempo");
			long errorHash = (long) jo.get("errorHash");
			long errorConexion = (long) jo.get("errorConexion");
			
			//seriesTiempo.getData().add(new XYChart.Data(x,errorTiempo));	
			//seriesHash.getData().add(new XYChart.Data(x,errorHash));
			//seriesConexion.getData().add(new XYChart.Data(x,errorConexion));
			
			//Tiempo
			if(errorTiempo!=0) {
				XYChart.Data dataTiempo = new XYChart.Data(x,errorTiempo);
				seriesTiempo.getData().add(dataTiempo);
				dataTiempo.nodeProperty().addListener(new ChangeListener<Node>() {
					@Override
	            	public void changed(ObservableValue<? extends Node> ov, Node oldNode, final Node node) {
	                	if (node != null) {
		                    displayLabelForData(dataTiempo);
	                	}
	            	}
				});				
			}
			
			//Hash
			if(errorHash!=0) {
				XYChart.Data dataHash = new XYChart.Data(x,errorHash);
				seriesHash.getData().add(dataHash);
				dataHash.nodeProperty().addListener(new ChangeListener<Node>() {
					@Override
	            	public void changed(ObservableValue<? extends Node> ov, Node oldNode, final Node node) {
	                	if (node != null) {
		                    displayLabelForData(dataHash);
	                	}
	            	}
				});				
			}
			
			//Conexion
			if(errorConexion!=0) {
				XYChart.Data dataConexion = new XYChart.Data(x,errorConexion);
				seriesConexion.getData().add(dataConexion);
				dataConexion.nodeProperty().addListener(new ChangeListener<Node>() {
					@Override
	            	public void changed(ObservableValue<? extends Node> ov, Node oldNode, final Node node) {
	                	if (node != null) {
		                    displayLabelForData(dataConexion);
	                	}
	            	}
				});	
			}
		}
		
		chartErroresPorPeer.getData().addAll(seriesTiempo);
		chartErroresPorPeer.getData().addAll(seriesHash);
		chartErroresPorPeer.getData().addAll(seriesConexion);
	}

	private void cargarTabVelPromedioPorPeer(JSONObject json) {
		JSONArray descargasPeers = (JSONArray) json.get("descargasPeers");
		XYChart.Series series = new XYChart.Series<>();
		series.setName("Velocidad promedio por peer");
		
		for(int i=0; i<descargasPeers.size(); i++) {
			JSONObject jo = (JSONObject) descargasPeers.get(i);
			String x = (String) jo.get("peer");
			long cantidad = (long) jo.get("cantidad");
			long tiempo = (long) jo.get("tiempo");
			double segundos = (double) tiempo / 1000;//ms a seg
			segundos = (double) segundos/cantidad;
			
			int pos = 0;
			double y = (1024*1024*8) / segundos;// (*8) Bytes a Bits
			y /= 1024;//Bits a Kbits
			y = this.round(y,3);
			
			XYChart.Data data = new XYChart.Data(x,y);
			series.getData().add(data);
			data.nodeProperty().addListener(new ChangeListener<Node>() {
				@Override
            	public void changed(ObservableValue<? extends Node> ov, Node oldNode, final Node node) {
                	if (node != null) {
	                    displayLabelForData(data);
                	}
            	}
			});
		}
		
		chartVelPromedioPorPeer.getData().addAll(series);		
	}

	public double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    BigDecimal bd = BigDecimal.valueOf(value);
	    bd = bd.setScale(places, RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}

	private void cargarTabDescargasPorPeer(JSONObject json) {
		JSONArray descargasPeers = (JSONArray) json.get("descargasPeers");
		XYChart.Series series = new XYChart.Series<>();
		series.setName("Cantidad de partes descargadas por peer");
		
		for(int i=0; i<descargasPeers.size(); i++) {
			JSONObject jo = (JSONObject) descargasPeers.get(i);
			String x = (String) jo.get("peer");
			long y = (long) jo.get("cantidad");
			
			XYChart.Data data = new XYChart.Data(x,y);
			series.getData().add(data);
			data.nodeProperty().addListener(new ChangeListener<Node>() {
				@Override
            	public void changed(ObservableValue<? extends Node> ov, Node oldNode, final Node node) {
                	if (node != null) {
	                    displayLabelForData(data);
                	}
            	}
			});
		}
		
		chartDescargasPorPeer.getData().addAll(series);
	}

	private void cargarTabTiempoDescarga(JSONObject json) {
		JSONArray tiempoPartes = (JSONArray) json.get("tiempoPartes");
		XYChart.Series series = new XYChart.Series<>();
		series.setName("Orden en que las partes fueron descargadas y sus tiempos");
		
		for(int i=0; i<tiempoPartes.size(); i++) {
			JSONObject jo = (JSONObject) tiempoPartes.get(i);
			long nroParte = (long) jo.get("nroParte");
			String x = String.valueOf(nroParte);
			long y = (long) jo.get("tiempo");
			series.getData().add(new XYChart.Data(x,y));	
		}
		
		chartTiempoDescarga.getData().addAll(series);
	}

	private void cargarTabResumen(JSONObject json) {
		String tiempoFinal = (String) json.get("tiempoFinal");
		String velocidadPromedio = (String) json.get("velocidadPromedio");
		String tiempoMinimo = (String) json.get("tiempoMinimo");
		String tiempoMaximo = (String) json.get("tiempoMaximo");
		String tiempoPromedio = (String) json.get("tiempoPromedio");
		String tiempoDesvioEstandar = (String) json.get("tiempoDesvioEstandar");
		
		this.tiempoTotal.setText(tiempoFinal);
		this.velocidadPromedio.setText(velocidadPromedio);
		this.tiempoMinimo.setText(tiempoMinimo);
		this.tiempoMaximo.setText(tiempoMaximo);
		this.tiempoPromedio.setText(tiempoPromedio);
		this.tiempoDesvioEstandar.setText(tiempoDesvioEstandar);			
	}
	
	private void displayLabelForData(XYChart.Data<String, Number> data) {
        final Node node = data.getNode();
        final Text dataText = new Text(data.getYValue() + "");
        node.parentProperty().addListener(new ChangeListener<Parent>() {
            @Override
            public void changed(ObservableValue<? extends Parent> ov, Parent oldParent, Parent parent) {
                Group parentGroup = (Group) parent;
                parentGroup.getChildren().add(dataText);
            }
        });

        node.boundsInParentProperty().addListener(new ChangeListener<Bounds>() {
            @Override
            public void changed(ObservableValue<? extends Bounds> ov, Bounds oldBounds, Bounds bounds) {
                dataText.setLayoutX(
                        Math.round(
                        bounds.getMinX() + bounds.getWidth() / 2 - dataText.prefWidth(-1) / 2));
                dataText.setLayoutY(
                        Math.round(
                        bounds.getMinY() - dataText.prefHeight(-1) * (-1)));
            }
        });        	
    }
}
