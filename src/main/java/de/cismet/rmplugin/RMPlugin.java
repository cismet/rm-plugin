/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
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

import org.apache.log4j.Logger;

import org.openide.util.NbBundle;

import java.awt.Frame;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.UnknownHostException;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.Iterator;
import java.util.LinkedList;

import javax.swing.JOptionPane;

import de.cismet.rmplugin.interfaces.RMessenger;

/**
 * RMPlugin stands for Remote Message Plugin. This piece of software is designed to enable messaging functionality in
 * cidsNavigator. With this plugin you have the possiblity to send messages to Navigator users. At startup the plugin
 * tries to connect to a remote registry to register the cidsNavigator user with the necessary information to connect to
 * the local plugin. With this information the registry can forward(@see
 * {@link de.cismet.rmplugin.interfaces.RMForwarder}) messages to the rmplugin.
 *
 * @author   Sebastian Puhl
 * @version  0.1
 */
public class RMPlugin implements PluginSupport, RMessenger {

    //~ Static fields/initializers ---------------------------------------------

    public static final String PARAM_ALTPORT = "AltPort";
    public static final String PARAM_DEFAULTPORT = "DefaultPort";
    public static final String PARAM_RMREGISTRYSERVER = "RMRegistryServer";
    private static final transient Logger LOG = Logger.getLogger(RMPlugin.class);

    /** The default postfix for rmi binding which indicates that this object is an RMPlugin. */
    public static final String RMI_OBJECT_NAME = "RMPlugin";                                // NOI18N
    /**
     * Constant for the textual replacing of the value -1 which indicates that a port is not initialized. This
     * presentation is a question of style and should enhance the readability of the code.
     */
    public static final int NOT_INITIALIZED = -1;

    //~ Instance fields --------------------------------------------------------

    private final PluginContext context;
    private final int defaultPort;
    private final int alternativePort;
    private final String bindString;
    private final String rmRegistryPath;
    private final String userKey;
    private final RMessenger rmiObject;
    private final Frame parentFrame;

    private Registry registry;
    private RMRegistry rmRegistry;
    private RMInfo rmInfo;
    private boolean initialised;

    //~ Constructors -----------------------------------------------------------

    /**
     * This constructor is called from the cidsNavigator. All background information such as environment, i18n,
     * userInterfache etc. are delivered via the variable context
     *
     * @param   context  cidsNavigator context settings
     *
     * @throws  IllegalArgumentException  DOCUMENT ME!
     * @throws  IllegalStateException     DOCUMENT ME!
     */
    // TODO HARMONIZE CONSTRUCTOR
    public RMPlugin(final PluginContext context) {
        if (context == null) {
            throw new IllegalArgumentException("the provided plugin context must not be null"); // NOI18N
        }

        parentFrame = null;
        this.context = context;

        if (LOG.isDebugEnabled()) {
            LOG.debug("creating new new RMPlugin from context: " + context); // NOI18N
        }

        final PluginContext.Environment env = context.getEnvironment();
        progress(0, NbBundle.getMessage(RMPlugin.class, "RMPlugin.RMPlugin(PluginContext).message")); // NOI18N

        int port = -1;
        try {
            port = Integer.parseInt(env.getParameter(PARAM_DEFAULTPORT)); // NOI18N
        } catch (final NumberFormatException e) {
            LOG.warn("no valid value for Default Port available", e);     // NOI18N
        }
        defaultPort = port;

        port = -1;
        try {
            port = Integer.parseInt(env.getParameter(PARAM_ALTPORT));           // NOI18N
        } catch (final NumberFormatException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("no valid value for alternative port available", ex); // NOI18N
            }
        }
        alternativePort = port;

        rmRegistryPath = env.getParameter(PARAM_RMREGISTRYSERVER); // NOI18N

        if (rmRegistryPath == null) {
            throw new IllegalStateException("rm registry path not provided by environment"); // NOI18N
        }

        final User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
        userKey = user.getName() + "@" + user.getUserGroup().toString(); // NOI18N

        if (LOG.isDebugEnabled()) {
            LOG.debug("defaultPort:" + defaultPort);           // NOI18N
            LOG.debug("alternativePort:" + alternativePort);   // NOI18N
            LOG.debug("path to rmRegistry:" + rmRegistryPath); // NOI18N
            LOG.debug("userkey:" + userKey);                   // NOI18N
        }

        RMessenger messenger = null;
        try {
            messenger = (RMessenger)UnicastRemoteObject.exportObject(this);
        } catch (final RemoteException ex) {
            LOG.error("error during exporting the remote object", ex); // NOI18N
        }
        rmiObject = messenger;

        String bind = null;
        try {
            bind = userKey + "_" + RMI_OBJECT_NAME + "_" + InetAddress.getLocalHost().getHostAddress(); // NOI18N
        } catch (final UnknownHostException ex) {
            LOG.error("cannot create bind string", ex);                                                 // NOI18N
        }
        bindString = bind;

        initRMI();
    }

    /**
     * Creates a new RMPlugin object.
     *
     * @param   parentFrame    DOCUMENT ME!
     * @param   primaryPort    DOCUMENT ME!
     * @param   secondaryPort  DOCUMENT ME!
     * @param   registryPath   DOCUMENT ME!
     * @param   userString     DOCUMENT ME!
     *
     * @throws  IllegalArgumentException  DOCUMENT ME!
     */
    public RMPlugin(final Frame parentFrame,
            final int primaryPort,
            final int secondaryPort,
            final String registryPath,
            final String userString) {
        this.context = null;
        this.parentFrame = parentFrame;
        this.defaultPort = primaryPort;
        this.alternativePort = secondaryPort;
        this.rmRegistryPath = registryPath;
        userKey = userString;

        if (LOG.isDebugEnabled()) {
            LOG.debug("defaultPort:" + defaultPort);           // NOI18N
            LOG.debug("alternativePort:" + alternativePort);   // NOI18N
            LOG.debug("path to rmRegistry:" + rmRegistryPath); // NOI18N
            LOG.debug("userkey:" + userKey);                   // NOI18N
        }

        if (rmRegistryPath == null) {
            throw new IllegalArgumentException("rm registry path must not be null"); // NOI18N
        }

        RMessenger messenger = null;
        try {
            messenger = (RMessenger)UnicastRemoteObject.exportObject(this);
        } catch (final RemoteException ex) {
            LOG.error("error during exporting the remote object", ex); // NOI18N
        }
        rmiObject = messenger;

        String bind = null;
        try {
            bind = userKey + "_" + RMI_OBJECT_NAME + "_" + InetAddress.getLocalHost().getHostAddress(); // NOI18N
        } catch (final UnknownHostException ex) {
            LOG.error("cannot create bind string", ex);                                                 // NOI18N
        }
        bindString = bind;

        initRMI();
    }

    //~ Methods ----------------------------------------------------------------

    /**
     * DOCUMENT ME!
     *
     * @param  step     DOCUMENT ME!
     * @param  message  DOCUMENT ME!
     */
    private void progress(final int step, final String message) {
        try {
            if ((context != null) && (context.getEnvironment() != null)
                        && this.context.getEnvironment().isProgressObservable()) {
                this.context.getEnvironment().getProgressObserver().setProgress(
                    step,
                    message);
            }
        } catch (final InterruptedException ex) {
            LOG.warn("could not propagate progress information", ex); // NOI18N
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @throws  IllegalStateException  DOCUMENT ME!
     */
    private void initRMI() {
        initialised = false;
        if ((defaultPort == NOT_INITIALIZED) && (alternativePort == NOT_INITIALIZED)) {
            final String message = "RMI could not be initialized because no valid port was provided"; // NOI18N
            LOG.error(message);
            throw new IllegalStateException(message);
        }

        try {
            progress(500, NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().SearchForRegistry")); // NOI18N

            final int regPort = initRMIRegistry();

            if (regPort == NOT_INITIALIZED) {
                progress(1000, NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().error")); // NOI18N
                throw new Exception("Error while initialising the Registry");                    // NOI18N
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Try to bind RMPlugin with the name " + bindString);               // NOI18N
                }

                progress(750, NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().bind")); // NOI18N

                registry.rebind(bindString, rmiObject);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("successfully bound"); ////NOI18N
                }

                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Try to add RMI Objekt " + bindString + " to RMRegistry"); // NOI18N
                    }

                    progress(900, NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().register")); // NOI18N

                    rmRegistry = (RMRegistry)Naming.lookup(rmRegistryPath);
                    final User user = Sirius.navigator.connection.SessionManager.getSession().getUser();
                    final InetAddress ip = InetAddress.getLocalHost();
                    rmInfo = new RMInfo(user.getName(),
                            user.getUserGroup().getName(),
                            user.getDomain(),
                            regPort,
                            System.currentTimeMillis(),
                            ip,
                            new URI("rmi://" + ip.getHostAddress() + ":" + regPort + "/" + bindString)); // NOI18N
                    rmRegistry.register(rmInfo);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Object " + bindString + " successfully added");                               // NOI18N
                    }
                } catch (final NotBoundException ex) {
                    LOG.error("NotBoundException while adding the object " + bindString + " to RMRegistry", ex); // NOI18N
                } catch (final RemoteException ex) {
                    LOG.error("RemoteException while adding the object " + bindString + " to RMRegistry", ex);   // NOI18N
                } catch (final MalformedURLException ex) {
                    LOG.error("MalformendURLException while adding the object " + bindString
                                + " to RMRegistry --> wrong path to the resgistry",
                        ex);                                                                                     // NOI18N
                }
                progress(1000, NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().ready"));                 // NOI18N

                initialised = true;
            }
        } catch (final Exception e) {
            LOG.error("Error while initialising the RMI connection", e);                                           // NOI18N
            try {
                if ((context != null) && (context.getEnvironment() != null)
                            && this.context.getEnvironment().isProgressObservable()) {
                    this.context.getEnvironment()
                            .getProgressObserver()
                            .setProgress(
                                1000,
                                org.openide.util.NbBundle.getMessage(RMPlugin.class, "RMPlugin.initRMI().error")); // NOI18N
                }
            } catch (InterruptedException ex) {
                LOG.error("Error while setting the progress bar", ex);                                             // NOI18N
            }
        }
    }

    /**
     * DOCUMENT ME!
     */
    private void shutdownRMI() {
        if (initialised) {
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("try to unbind " + bindString); // NOI18N
                }
                registry.unbind(bindString);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(bindString + " successfully unbound from the registry"); // NOI18N
                }

                if (rmRegistry != null) {
                    try {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Try to unbind RMI object " + bindString + " from RMRegistry"); // NOI18N
                        }

                        rmRegistry.deregister(rmInfo);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("object " + bindString + " successfully unbound");                      // NOI18N
                        }
                    } catch (final Exception ex) {
                        LOG.error("Error while unbinding the object " + bindString + " from RMRegistry", ex); // NOI18N
                    }
                } else {
                    LOG.error("No RMRegistry object existent. Unbind is not possible");                       // NOI18N
                }
            } catch (final Exception ex) {
                LOG.error("Error while unbinding the object " + bindString, ex);                              // NOI18N
            } finally {
                initialised = false;
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No RMI Objekt/Registry to unbind existent");                                       // NOI18N
            }
        }
    }

    /**
     * Tries to initialize the RMI Registry on the available ports and returns the port number where the Registry is
     * running if successful, {@link #NOT_INITIALIZED} otherwise.
     *
     * @return  DOCUMENT ME!
     */
    private int initRMIRegistry() {
        if (defaultPort == NOT_INITIALIZED) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("trying alternative port: " + alternativePort); // NOI18N
            }

            return initRMIRegistry(alternativePort) ? alternativePort : NOT_INITIALIZED;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("trying default port: " + defaultPort); // NOI18N
            }

            if (initRMIRegistry(defaultPort)) {
                return defaultPort;
            } else {
                if (alternativePort == NOT_INITIALIZED) {
                    LOG.error("cannot initialise registry"); // NOI18N

                    return NOT_INITIALIZED;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("default port unsuccessful, trying alternative port: " + alternativePort); // NOI18N
                    }

                    return initRMIRegistry(alternativePort) ? alternativePort : NOT_INITIALIZED;
                }
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   port  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private boolean initRMIRegistry(final int port) {
        try {
            registry = LocateRegistry.getRegistry(port);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Local registry on port " + port + " available");                  // NOI18N
                LOG.debug("Check if the registry contains " + RMI_OBJECT_NAME + " objects"); // NOI18N
            }

            final String[] bindedObjects = registry.list();
            boolean isRMPluginRegistry = false;
            for (final String obj : bindedObjects) {
                if (obj.contains(RMI_OBJECT_NAME)) {
                    isRMPluginRegistry = true;
                    break;
                }
            }

            return isRMPluginRegistry;
        } catch (final RemoteException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No local registry existent --> create registry"); // NOI18N
            }

            try {
                registry = LocateRegistry.createRegistry(port);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Successfully created the registry on port " + port); // NOI18N
                }

                return true;
            } catch (RemoteException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The creation of the registry on port " + port + " failed"); // NOI18N
                }

                return false;
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param   id  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public PluginMethod getMethod(final String id) {
        return null;
    }

    /**
     * DOCUMENT ME!
     *
     * @param   id  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    @Override
    public PluginUI getUI(final String id) {
        return null;
    }

    // no visible components
    @Override
    public void setVisible(final boolean visible) {
    }

    @Override
    public void setActive(final boolean active) {
        if (active && !isActive()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("RMPlugin will be activated");   // NOI18N
            }
            initRMI();
        } else if (!active && isActive()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("RMPlugin will be deactivated"); // NOI18N
            }
            shutdownRMI();
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    private boolean isActive() {
        return initialised;
    }

    @Override
    public Iterator getMethods() {
        final LinkedList ll = new LinkedList();
        return ll.iterator();
    }

    @Override
    public PluginProperties getProperties() {
        return null;
    }

    @Override
    public Iterator getUIs() {
        final LinkedList ll = new LinkedList();
        return ll.iterator();
    }

    @Override
    public void sendMessage(final String message, final String title) throws RemoteException {
        new Thread() {

                @Override
                public void run() {
                    showDialog(message, title);
                }
            }.start();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  message  DOCUMENT ME!
     * @param  title    DOCUMENT ME!
     */
    private void showDialog(final String message, final String title) {
        try {
            if (context == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("RMPlugin: message to plugin: " + message); // NOI18N
                }

                JOptionPane.showMessageDialog(context.getUserInterface().getFrame(),
                    message,
                    title,
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("RMPlugin: message to standalone"); // NOI18N
                }
                JOptionPane.showMessageDialog(parentFrame, message, title, JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            LOG.error("RMPlugin: Error while displaying the message ", ex); // NOI18N
        }
    }

    @Override
    public void test() throws RemoteException {
    }
}
