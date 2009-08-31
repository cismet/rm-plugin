/*
 * Main.java
 *
 * Created on 22. November 2006, 11:09
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package de.cismet.rmplugin;

import Sirius.navigator.plugin.context.PluginContext;
import Sirius.navigator.plugin.interfaces.PluginMethod;
import Sirius.navigator.plugin.interfaces.PluginProperties;
import Sirius.navigator.plugin.interfaces.PluginSupport;
import Sirius.navigator.plugin.interfaces.PluginUI;
import Sirius.server.newuser.User;
import Sirius.server.registry.rmplugin.interfaces.RMRegistry;
import Sirius.server.registry.rmplugin.util.RMInfo;
import de.cismet.rmplugin.interfaces.RMessenger;
import java.awt.Frame;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.apache.log4j.Logger;

/*
 * RMPlugin.java
 * Copyright (C) 2005 by:
 *
 *----------------------------
 * cismet GmbH
 * Goebenstrasse 40
 * 66117 Saarbruecken
 * http://www.cismet.de
 *----------------------------
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *----------------------------
 * Author:
 * sebastian.puhl@cismet.de
 *----------------------------
 *
 * Created on 24. November 2006, 10:33
 *
 */

/**
 * RMPlugin stands for Remote Message Plugin. This piece of software ist designed
 * to enable messaging functionality in cidsNavigator. With this plugin is it you
 * have the possiblity to send messages to Navigator users. At startup the plugin
 * tries to connect to a remote registry to register the cidsNavigator user with the
 * necessary information to connect to the local plugin. With this information the
 * registry can forward(@see {@link de.cismet.rmplugin.interfaces.RMForwarder}) messages to the rmplugin.
 * @author Sebastian Puhl
 * @version 0.1
 */
public class RMPlugin implements PluginSupport,RMessenger {
    
    private Logger log = Logger.getLogger(RMPlugin.class.getName());
    private PluginContext context;
    /**
     * The default postfix for rmi binding which indicates that this object is an 
     * RMPlugin
     */
    public static final String RMI_OBJECT_NAME = "RMPlugin";
    /**
     * Constant for the textual replacing of the value -1 which indicates that a port
     * is not initialized. This presentation is a question of style and should enhance 
     * the readability of the code.
     */
    public static final int NOT_INITIALIZED = -1;
    private int defaultPort = -1;
    private int alternativePort = -1;
    private Registry reg;
    private String bindString;
    private boolean regExists=false;
    private int usedPort = -1;
    private String rmRegistryPath;
    private RMInfo rmInfo;
    private RMRegistry rmRegistry;
    private String key;
    private RMessenger rmiObject;
    private boolean initActivation = true;
    private boolean isPlugin = false;
    private Frame parentFrame;
    
    /**
     * This constructor is called from the cidsNavigator. All backround information such
     * as environment, i18n, userInterfache etc. are delivered via the variable context
     * @param context cidsNavigator context settings
     */
    //TODO HARMONIZE CONSTRUCTOR    
    public RMPlugin(PluginContext context) {
        isPlugin = true;
        this.context = context;
        log.debug("new RMPlugin");
        try {            
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(0, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.initialisieren"));
        } catch (InterruptedException ex) {
            log.error("Fehler beim setzen der Progressbar",ex);
        }
        
        try {
            defaultPort = Integer.parseInt(context.getEnvironment().getParameter("DefaultPort"));
        } catch(NumberFormatException e){
            log.warn("Kein Wert oder kein g\u00FCltiger Wert f\u00FCr Default Port vorhanden",e);            
        }
        
        try {
            alternativePort = Integer.parseInt(context.getEnvironment().getParameter("AltPort"));
        } catch(NumberFormatException ex){
            log.debug("Kein Wert oder kein g\u00FCltiger Wert f\u00FCr alternativen Port vorhanden",ex);
            
        }
        
        rmRegistryPath = context.getEnvironment().getParameter("RMRegistryServer");
        User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
        key = user.getName()+"@"+user.getUserGroup().toString();
        
        log.debug("defaultPort:"+defaultPort);
        log.debug("alternativePort:"+alternativePort);
        log.debug("path to rmRegistry:"+rmRegistryPath);
        
        try {            
            rmiObject = (RMessenger)UnicastRemoteObject.exportObject(this);            
        } catch (RemoteException ex) {
            log.fatal("Schwerer Fehler beim initialiseren des RMPlugins --> Fehler beim Exportieren des Remote Objekts");
        }
        initRMI();               
    }
    
     public RMPlugin(Frame parentFrame,int primaryPort,int secondaryPort,String registryPath,String userString) {
        this.parentFrame = parentFrame;
        this.defaultPort = primaryPort;
        this.alternativePort = secondaryPort;
        this.rmRegistryPath = registryPath;        
        key = userString;
        
        log.debug("defaultPort:"+defaultPort);
        log.debug("alternativePort:"+alternativePort);
        log.debug("path to rmRegistry:"+rmRegistryPath);
        log.debug("key:"+key);
        
        try {            
            rmiObject = (RMessenger)UnicastRemoteObject.exportObject(this);            
        } catch (RemoteException ex) {
            log.fatal("Schwerer Fehler beim initialiseren des RMPlugins --> Fehler beim Exportieren des Remote Objekts");
        }
        initRMI();               
    }
    
    
            
    private void initRMI(){
        regExists = false;
        if(defaultPort==NOT_INITIALIZED && alternativePort==NOT_INITIALIZED){
            log.error("RMI konnte nicht initialisiert werden da kein Portangabe f\u00FCr die Registry vorhanden ist");
            return;
        }
        
        try {                        
            
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(500, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.registry"));
            if((regExists=initRegestry())){
                bindString = key+"_"+RMI_OBJECT_NAME+"_"+InetAddress.getLocalHost().getHostAddress();
                log.debug("Versuche RMPlugin mit dem Namen "+bindString+" zu binden");
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(750, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.bind"));
                reg.rebind(bindString,rmiObject);
                log.debug("Binden erfolgreich");
                if(rmRegistryPath != null){
                    try{
                        log.debug("Versuche RMI Objekt "+bindString+" in RMRegistry einzuf\u00FCgen");
                        if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(900, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.register"));
                        rmRegistry = (RMRegistry) Naming.lookup(rmRegistryPath);
                        User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
                        InetAddress ip = InetAddress.getLocalHost();
                        rmInfo = new RMInfo(user.getName(),user.getUserGroup().getName(),user.getDomain(),usedPort,System.currentTimeMillis(),ip,new URI("rmi://"+ip.getHostAddress()+":"+usedPort+"/"+bindString));
                        rmRegistry.register(rmInfo);
                        log.debug("Einf\u00FCgen von Objekt "+bindString+" erfolgreich");
                    } catch(NotBoundException ex){
                        log.error("NotBoundException beim einf\u00FCgen von Objekt "+bindString+" in RMRegistry",ex);
                    } catch(RemoteException ex){
                        log.error("RemoteException beim einf\u00FCgen von Objekt "+bindString+" in RMRegistry",ex);
                    }catch(MalformedURLException ex){
                        log.error("MalformendURLException beim einf\u00FCgen von Objekt "+bindString+" in RMRegistry --> falscher Pfad zur Registry",ex);
                    }
                } else {
                    if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.error"));
                    log.error("Kein Pfad zur RMRegistry vorhanden");
                }
            } else {
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.error"));
                throw new Exception("Fehler beim initalisieren der Regestry");
            }
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.ready"));
        }catch(Exception e) {
            log.error("Error beim initialisieren der RMI Verbindungen:"+e);
            bindString = null;
            try {
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, java.util.ResourceBundle.getBundle("de/cismet/rmplugin/resource/language").getString("RMPlugin.error"));
            } catch (InterruptedException ex) {
                log.error("Fehler beim setzen der Progressbar",ex);
            }
        }        
    }
    
    private void shutdownRMI(){
        if(bindString != null && regExists){
            try{
                log.debug("Versuche "+bindString+" zu unbinden");
                reg.unbind(bindString);
                log.debug(bindString+"erfolgreich aus der Registry ausgetragen");
                if(rmRegistry != null){
                    try{
                        log.debug("Versuche RMI Objekt "+bindString+" aus RMRegistry auszutragen");
                        rmRegistry.deregister(rmInfo);
                        log.debug("Austragen von Objekt "+bindString+" erfolgreich");
                    } catch(Exception ex){
                        log.error("Fehler beim austragen des Objekts "+bindString+" aus der RMRegistry",ex);
                    }
                } else {
                    log.error("Kein RMRegistry Objekt vorhanden austragen nicht m\u00F6glich");
                }
            }catch(Exception ex){
                log.error("Fehler w\u00E4hrend dem unbind des Objekts "+bindString,ex);
            }
        } else {
            log.debug("Gibt kein RMI Objekt/Registry zum unbinden");
        }
    }
    
    
    private boolean initRegestry(){
        if(defaultPort!=NOT_INITIALIZED){
            usedPort=defaultPort;
            try {
                reg = LocateRegistry.getRegistry(defaultPort);
                log.debug("Lokale Registry auf Port "+defaultPort+" vorhanden");
                log.debug("Pr\u00FCfe ob die registry "+RMI_OBJECT_NAME+" Objekte enth\u00E4lt");
                String[] bindedObjects = reg.list();
                int size = bindedObjects.length;
                int i=0;
                boolean isRMPluginRegistry=false;
                while(i < size){
                    if(bindedObjects[i].contains(RMI_OBJECT_NAME)){
                        isRMPluginRegistry = true;
                        break;
                    }
                    i++;
                }
                
                if(isRMPluginRegistry){
                    log.debug("Registry "+defaultPort+" enth\u00E4lt "+RMI_OBJECT_NAME+" Objekte");
                    return true;
                } else {
                    log.debug("Registry "+defaultPort+" enth\u00E4lt keine"+RMI_OBJECT_NAME+" Objekte --> versuche alternative Registry");
                    if (alternativePort!=NOT_INITIALIZED){
                        return initAlternativRegistry();
                    } else {
                        log.debug("Kein Port f\u00FCr Alternative Registry vorhanden");
                        return false;
                    }
                }
            }catch(RemoteException e){
                log.debug("Keine lokale Registry vorhanden --> erstelle Registry");
                try {
                    reg = LocateRegistry.createRegistry(defaultPort);
                    log.debug("Anlegen der Registry auf Port "+defaultPort+" erfolgreich");
                    return true;
                } catch(RemoteException ex){
                    log.debug("Anlegen der Registry auf Port "+defaultPort+" schlug fehl");
                    if (alternativePort!=NOT_INITIALIZED){
                        return initAlternativRegistry();
                    } else {
                        log.debug("Kein Port f\u00FCr Alternative Registry vorhanden");
                        return false;
                    }
                }
                
            }
            
        } else if(alternativePort!=NOT_INITIALIZED){
            return initAlternativRegistry();
        }
        log.debug("Fehler in initRegistry Ablauf d\u00FCrfte nicht passieren :-)");
        return false;
    }
    
    private boolean initAlternativRegistry(){
        log.debug("Initalisierung der alternativen Regestry");
        usedPort=alternativePort;
        
        try {
            reg = LocateRegistry.getRegistry(alternativePort);
            log.debug("Lokale Registry auf Port "+alternativePort+" vorhanden");
            log.debug("Pr\u00FCfe ob die registry "+RMI_OBJECT_NAME+" Objekte enth\u00E4lt");
            String[] bindedObjects = reg.list();
            int size = bindedObjects.length;
            if(size == 0){
                log.debug("Ausweich Registry leer");
                return true;
            }
            int i=0;
            boolean isRMPluginRegistry=false;
            while(i < size){
                if(bindedObjects[i].contains(RMI_OBJECT_NAME)){
                    isRMPluginRegistry = true;
                    break;
                }
                i++;
            }
            
            if(isRMPluginRegistry){
                log.debug("Ausweich Registry enth\u00E4lt Objekte vom Typ "+RMI_OBJECT_NAME);
                return true;
            } else {
                log.debug("Ausweich Registry auch nicht geeignet");
                return false;
            }
            
        }catch(RemoteException e){
            log.debug("Keine lokale Registry vorhanden oder kein Zugriff --> erstelle Registry",e);
            try {
                reg = LocateRegistry.createRegistry(alternativePort);
                log.debug("Anlegen der Registry auf Port "+alternativePort+" erfolgreich");
                return true;
            } catch(RemoteException ex){
                log.debug("Anlegen der Registry auf Port "+alternativePort+" schlug fehl");
                return false;
            }
            
        }
    }
    
    /**
     * 
     */
    public PluginMethod getMethod(String id) {
        return null;
    }
    
    /**
     *
     * @param
     * @return
     */
    public PluginUI getUI(String id) {
        return null;
    }
    
    // no visible components
    public void setVisible(boolean visible) {
    }
    
    
    //aktivieren deactivieren --> au\u00DFen vor noch denke brauche ich
    public void setActive(boolean active) {
        if(active && !initActivation ){
            log.debug("RMPlugin wird aktiviert");            
            initRMI();
            
        } else if(!active){        
            initActivation=false;
            log.debug("RMPlugin wird deaktiviert");
            shutdownRMI();
        }
    }
    
    
    
    
    public Collection getButtons() {
        return null;
    }
    
    public JComponent getComponent() {
        return null;
    }
    
    public String getId() {
        return "RMPlugin";
    }
    
    public Collection getMenus() {
        return null;
    }
    
    public Iterator getMethods() {
        LinkedList ll=new LinkedList();
        return ll.iterator();
    }
    
    public PluginProperties getProperties() {
        return null;
    }
    
    public Iterator getUIs() {
        LinkedList ll=new LinkedList();
        return ll.iterator();
    }
    
    //
    public void hidden() {
    }
    
    //
    public void moved() {
    }
    
    public void sendMessage(final String message,final String title) throws RemoteException {
        new Thread() {
            public void run() {
                showDialog(message,title);
            }
        }.start();
    }
    
    private void showDialog(String message,String title){
        try{
        if(isPlugin){
            log.debug("RMPlugin: message to plugin");
            JOptionPane.showMessageDialog(context.getUserInterface().getFrame(),message,title,JOptionPane.INFORMATION_MESSAGE);
        } else {
            log.debug("RMPlugin: message to standalone");
            JOptionPane.showMessageDialog(parentFrame,message,title,JOptionPane.INFORMATION_MESSAGE);
        }
        } catch(Exception ex){
            log.error("RMPlugin: Fehler beim anzeigen der Message ",ex);
        }
    }
    
    public void test() throws RemoteException{
    }
}
