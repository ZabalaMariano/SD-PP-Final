package TP_Final_SDyPP.UPnP;

import java.net.ServerSocket;
import java.net.Socket;

public class UPnPAdmin {
    
    public UPnPAdmin() {}

    public boolean setPortForwarding(int port) {
   
    	System.out.println("CONFIGURACIÓN UPnP - Port Forwarding");           
        
        if (UPnP.isUPnPAvailable()) { //UPnP disponible?
            if (UPnP.isMappedTCP(port)) { //el puerto ya está mapeado?
                System.out.print("No se puedo configurar UPnP port forwarding: el puerto ya está mapeado. Intente con otro: ");
                return false;
            } else if (UPnP.openPortTCP(port)) { //intenta mapear port
                System.out.println("UPnP port forwarding habilitado");
            } else {
                System.err.print("Fallo al intentar mapear el puerto. Intente nuevamente:");
                return false;
            }
        } else {
            System.err.print("UPnP no está disponible. Aseguresé que este disponible en su equipo y router. Intente de nuevo:");
            return false;
        }

        return true;
    }
    
    public void closePort(int port) {
    	System.out.println("Cerrando el puerto en uso por UPnP...");
        UPnP.closePortTCP(port);
        System.out.println("Puerto cerrado.");
    }
    
    public String getPortRouter(int puertoEscucha) throws Exception {
    	return UPnP.getExternalPort(puertoEscucha);
    }
    
    public String getIPRouter() {
    	return UPnP.getExternalIP();
    }
}
