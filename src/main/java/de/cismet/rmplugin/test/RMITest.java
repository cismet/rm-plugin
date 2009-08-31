package de.cismet.rmplugin.test;
/*
 * RMITest.java
 *
 * Created on 22. November 2006, 16:47
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
import Sirius.server.registry.rmplugin.interfaces.RMForwarder;
import de.cismet.rmplugin.interfaces.RMessenger;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 *
 * @author Sebastian
 */
public class RMITest {
    
    /** Creates a new instance of RMITest */
    public RMITest() {
    }
    
    public static void main(String[] args) {
        try {
            RMForwarder forwarder = (RMForwarder) Naming.lookup("rmi://192.168.100.3:1099/RMRegistryServer");
            //RMessenger m = (RMessenger) Naming.lookup("rmi://localhost:9001/admin@Administratoren@WUNDA_BLAU_RMPlugin");            
            //m.sendMessage("hello","Greeting");
            forwarder.logCurrentRegistry();
            forwarder.sendMessage("admin","WITZIG^2","Titel");
            //System.out.println(forwarder.getAllActiveUsers(forwarder.getAllActiveGroups(forwarder.getAllActiveDomains().get(0)).get(0),forwarder.getAllActiveDomains().get(0)).get(0));
            //forwarder.updateRegistry();
            //forwarder.logCurrentRegistry();
        } catch (Exception e) {
            System.err.println("Client_exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
