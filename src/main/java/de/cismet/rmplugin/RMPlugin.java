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
    public static final String RMI_OBJECT_NAME = "RMPlugin";//NOI18N
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
        log.debug("new RMPlugin");//NOI18N
        try {            
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(0, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.RMPlugin(PluginContext).message"));//NOI18N
        } catch (InterruptedException ex) {
            log.error("Error while setting the progress bar",ex);//NOI18N
        }
        
        try {
            defaultPort = Integer.parseInt(context.getEnvironment().getParameter("DefaultPort"));//NOI18N
        } catch(NumberFormatException e){
            log.warn("No value or no valid value for Default Port available",e);//NOI18N
        }
        
        try {
            alternativePort = Integer.parseInt(context.getEnvironment().getParameter("AltPort"));//NOI18N
        } catch(NumberFormatException ex){
            log.debug("No value or no valid value for alternative port available",ex);//NOI18N
            
        }
        
        rmRegistryPath = context.getEnvironment().getParameter("RMRegistryServer");//NOI18N
        User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
        key = user.getName()+"@"+user.getUserGroup().toString();//NOI18N
        
        log.debug("defaultPort:"+defaultPort);//NOI18N
        log.debug("alternativePort:"+alternativePort);//NOI18N
        log.debug("path to rmRegistry:"+rmRegistryPath);//NOI18N
        
        try {            
            rmiObject = (RMessenger)UnicastRemoteObject.exportObject(this);            
        } catch (RemoteException ex) {
            log.fatal("Fatal error while initializing the RMPlugin --> Error during exporting the remote object");//NOI18N
        }
        initRMI();               
    }
    
     public RMPlugin(Frame parentFrame,int primaryPort,int secondaryPort,String registryPath,String userString) {
        this.parentFrame = parentFrame;
        this.defaultPort = primaryPort;
        this.alternativePort = secondaryPort;
        this.rmRegistryPath = registryPath;        
        key = userString;
        
        log.debug("defaultPort:"+defaultPort);//NOI18N
        log.debug("alternativePort:"+alternativePort);//NOI18N
        log.debug("path to rmRegistry:"+rmRegistryPath);//NOI18N
        log.debug("key:"+key);//NOI18N
        
        try {            
            rmiObject = (RMessenger)UnicastRemoteObject.exportObject(this);            
        } catch (RemoteException ex) {
            log.fatal("Fatal error while initializing the RMPlugins --> Error while exporting the remote object");//NOI18N
        }
        initRMI();               
    }
    
    
            
    private void initRMI(){
        regExists = false;
        if(defaultPort==NOT_INITIALIZED && alternativePort==NOT_INITIALIZED){
            log.error("RMI could not be initialized, because because the port value of the registry is not existent.");//NOI18N
            return;
        }
        
        try {                        
            
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(500, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().SearchForRegistry"));//NOI18N
            if((regExists=initRegestry())){
                bindString = key+"_"+RMI_OBJECT_NAME+"_"+InetAddress.getLocalHost().getHostAddress();//NOI18N
                log.debug("Try to bind RMPlugin with the name " + bindString);//NOI18N
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(750, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().bind"));//NOI18N
                reg.rebind(bindString,rmiObject);
                log.debug("successfully bound");////NOI18N
                if(rmRegistryPath != null){
                    try{
                        log.debug("Try to add RMI Objekt "+bindString+" to RMRegistry");//NOI18N
                        if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(900, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().register"));//NOI18N
                        rmRegistry = (RMRegistry) Naming.lookup(rmRegistryPath);
                        User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
                        InetAddress ip = InetAddress.getLocalHost();
                        rmInfo = new RMInfo(user.getName(),user.getUserGroup().getName(),user.getDomain(),usedPort,System.currentTimeMillis(),ip,new URI("rmi://"+ip.getHostAddress()+":"+usedPort+"/"+bindString));//NOI18N
                        rmRegistry.register(rmInfo);
                        log.debug("Object "+bindString+" successfully added");//NOI18N
                    } catch(NotBoundException ex){
                        log.error("NotBoundException while adding the object "+bindString+" to RMRegistry",ex);//NOI18N
                    } catch(RemoteException ex){
                        log.error("RemoteException while adding the object "+bindString+" to RMRegistry",ex);//NOI18N
                    }catch(MalformedURLException ex){
                        log.error("MalformendURLException while adding the object "+bindString+" to RMRegistry --> wrong path to the resgistry",ex);//NOI18N
                    }
                } else {
                    if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().error"));//NOI18N
                    log.error("no path to RMRegistry available");//NOI18N
                }
            } else {
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().error"));//NOI18N
                throw new Exception("Error while initialising the Regestry");//NOI18N
            }
            if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().ready"));//NOI18N
        }catch(Exception e) {
            log.error("Error while initialising the RMI connection:"+e);//NOI18N
            bindString = null;
            try {
                if(context!=null&&context.getEnvironment()!=null&&this.context.getEnvironment().isProgressObservable())this.context.getEnvironment().getProgressObserver().setProgress(1000, org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().error"));//NOI18N
            } catch (InterruptedException ex) {
                log.error("Error while setting the progress bar",ex);//NOI18N
            }
        }        
    }
    
    private void shutdownRMI(){
        if(bindString != null && regExists){
            try{
                log.debug("try to unbind "+bindString);//NOI18N
                reg.unbind(bindString);
                log.debug(bindString+" successfully unbound from the registry");//NOI18N
                if(rmRegistry != null){
                    try{
                        log.debug("Try to unbind RMI object "+bindString+" from RMRegistry");//NOI18N
                        rmRegistry.deregister(rmInfo);
                        log.debug("object "+bindString+" successfully unbound");//NOI18N
                    } catch(Exception ex){
                        log.error("Error while unbinding the object "+bindString+" from RMRegistry",ex);//NOI18N
                    }
                } else {
                    log.error("No RMRegistry object existent. Unbind is not possible");//NOI18N
                }
            }catch(Exception ex){
                log.error("Error while unbinding the object "+bindString,ex);//NOI18N
            }
        } else {
            log.debug("No RMI Objekt/Registry to unbind existent");//NOI18N
        }
    }
    
    
    private boolean initRegestry(){
        if(defaultPort!=NOT_INITIALIZED){
            usedPort=defaultPort;
            try {
                reg = LocateRegistry.getRegistry(defaultPort);
                log.debug("Local registry on port "+defaultPort+" available");//NOI18N
                log.debug("Check if the registry contains "+RMI_OBJECT_NAME+" objects");//NOI18N
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
                    log.debug("Registry "+defaultPort+" contains "+RMI_OBJECT_NAME+" objects");//NOI18N
                    return true;
                } else {
                    log.debug("Registry "+defaultPort+" contains no "+RMI_OBJECT_NAME+" objects --> try alternative Registry");//NOI18N
                    if (alternativePort!=NOT_INITIALIZED){
                        return initAlternativRegistry();
                    } else {
                        log.debug("No port for alternative registry existent");//NOI18N
                        return false;
                    }
                }
            }catch(RemoteException e){
                log.debug("No local registry existent --> create registry");//NOI18N
                try {
                    reg = LocateRegistry.createRegistry(defaultPort);
                    log.debug("Successfully created the registry on port "+defaultPort);//NOI18N
                    return true;
                } catch(RemoteException ex){
                    log.debug("The creation of the registry on port "+defaultPort+" failed");//NOI18N
                    if (alternativePort!=NOT_INITIALIZED){
                        return initAlternativRegistry();
                    } else {
                        log.debug("No port for alternative registry available");//NOI18N
                        return false;
                    }
                }
                
            }
            
        } else if(alternativePort!=NOT_INITIALIZED){
            return initAlternativRegistry();
        }
        log.debug("Error in initRegistry. This should not happen :-)");//NOI18N
        return false;
    }
    
    private boolean initAlternativRegistry(){
        log.debug("Initialization of the alternative Regestry");//NOI18N
        usedPort=alternativePort;
        
        try {
            reg = LocateRegistry.getRegistry(alternativePort);
            log.debug("Local registry on port "+alternativePort+" available");//NOI18N
            log.debug("Check if the registry contains "+RMI_OBJECT_NAME+" objects");//NOI18N
            String[] bindedObjects = reg.list();
            int size = bindedObjects.length;
            if(size == 0){
                log.debug("Alternative Registry is empty");//NOI18N
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
                log.debug("Alternative registry contains objects of the type "+RMI_OBJECT_NAME);//NOI18N
                return true;
            } else {
                log.debug("Alternative registry is also improper");//NOI18N
                return false;
            }
            
        }catch(RemoteException e){
            log.debug("No local registry exists or no access on it --> create Registry",e);//NOI18N
            try {
                reg = LocateRegistry.createRegistry(alternativePort);
                log.debug("Creation of a registry on port "+alternativePort+" successful");//NOI18N
                return true;
            } catch(RemoteException ex){
                log.debug("Creation of a registry on port "+alternativePort+" failed");//NOI18N
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
            log.debug("RMPlugin will be activated");//NOI18N
            initRMI();
            
        } else if(!active){        
            initActivation=false;
            log.debug("RMPlugin will be deactivated");//NOI18N
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
        return "RMPlugin";//NOI18N
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
            log.debug("RMPlugin: message to plugin");//NOI18N
            JOptionPane.showMessageDialog(context.getUserInterface().getFrame(),message,title,JOptionPane.INFORMATION_MESSAGE);
        } else {
            log.debug("RMPlugin: message to standalone");//NOI18N
            JOptionPane.showMessageDialog(parentFrame,message,title,JOptionPane.INFORMATION_MESSAGE);
        }
        } catch(Exception ex){
            log.error("RMPlugin: Error while displaying the message ",ex);//NOI18N
        }
    }
    
    public void test() throws RemoteException{
    }
}
