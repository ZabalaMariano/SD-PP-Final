package TP_Final_SDyPP.UPnP;

public class UPnPAdmin {
    
    public UPnPAdmin() {}

    public String setPortForwarding(int port) {
    	String msg = "";
    	if (UPnP.isUPnPAvailable()) { //UPnP disponible?
            if (UPnP.isMappedTCP(port)) { //el puerto ya está mapeado?
                msg = "-No se puedo configurar UPnP port forwarding:\nel puerto ya está mapeado.";
            } else if (UPnP.openPortTCP(port)) { //intenta mapear port
            	msg = "ok";
            } else {
            	msg = "-Fallo al intentar mapear el puerto.";
            }
        } else {
        	msg = "-UPnP no está disponible. Aseguresé que esté\ndisponible en su equipo (activar detección de redes) y router.";
        }

        return msg;
    }
    
    public boolean closePort(int port) {
        return UPnP.closePortTCP(port);
    }
    
    public String getPortRouter(int puertoEscucha) throws Exception {
    	return UPnP.getExternalPort(puertoEscucha);
    }
    
    public String getIPRouter() {
    	return UPnP.getExternalIP();
    }
}
