package nz.co.breakpoint.jmeter.iso8583;

import static nz.co.breakpoint.jmeter.iso8583.ISO8583Config.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.util.JMeterUtils;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.jpos.iso.GenericSSLSocketFactory;
import org.jpos.iso.ISOServer;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.q2.Q2;
import org.jpos.q2.QBeanSupport;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.QMUX;
import org.jpos.util.NameRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ISO8583 Server - A Non-Test Element that acts as an ISO8583 server.
 * <p>
 * Similar to JMeter's HTTP Mirror Server, this element starts when the test begins
 * and listens for incoming ISO8583 connections. It stays running until the test is stopped.
 * </p>
 * <p>
 * Unlike ISO8583Config in server mode, this element:
 * <ul>
 *   <li>Does not require a Thread Group to keep running</li>
 *   <li>Stays running indefinitely (like a real jPOS server)</li>
 *   <li>Can be stopped via JMeter's stop button or Ctrl+C</li>
 * </ul>
 * </p>
 */
public class ISO8583Server extends ConfigTestElement
        implements ISO8583TestElement, TestBean, Serializable, TestStateListener, NoThreadClone {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(ISO8583Server.class);

    // Shared Q2 instance with ISO8583Config
    protected static transient Q2 q2;

    // Latch to signal shutdown
    private transient CountDownLatch shutdownLatch;
    private transient volatile boolean running = false;

    static {
        org.jpos.util.Logger logger = org.jpos.util.Logger.getLogger(Q2_LOGGER);
        if (!logger.hasListeners())
            logger.addListener(new Slf4jLogListener());
    }

    // Get full class name, for instantiation
    public String getFullChannelClassName() {
        String className = getClassname();
        if (ISO8583Config.channelClasses.containsKey(className)) {
            return ISO8583Config.channelClasses.get(className);
        }
        return className;
    }

    protected Element getChannelDescriptor(String name) {
        final String channelClass = getFullChannelClassName(), packager = getPackager();
        if (channelClass == null || channelClass.isEmpty()) {
            log.error("Channel class undefined - cannot create channel");
            return null;
        }
        if (packager == null || packager.isEmpty()) {
            log.error("Packager config undefined - cannot create channel");
            return null;
        }

        Element channelDescriptor = new Element("channel")
            .setAttribute("name", name)
            .setAttribute("class", channelClass)
            .setAttribute("packager", GenericPackager.class.getName())
            .setAttribute("header", getHeader())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(new Element("property")
                .setAttribute("name", "packager-config")
                .setAttribute("value", packager))
            .addContent(new Element("property")
                .setAttribute("name", "port")
                .setAttribute("value", getPort()));

        if (JMeterUtils.getPropDefault(Q2_PACKAGER_LOGGING, false)) {
            channelDescriptor.addContent(new Element("property")
                    .setAttribute("name", "packager-logger")
                    .setAttribute("value", Q2_LOGGER))
                .addContent(new Element("property")
                    .setAttribute("name", "packager-realm")
                    .setAttribute("value", "packager"));
        }

        getChannelConfig().forEach(p ->
            channelDescriptor.addContent(new Element("property")
                .setAttribute("name", p.getName())
                .setAttribute("value", p.getValue()))
        );
        return channelDescriptor;
    }

    protected Element addSSLConfig(Element descriptor) {
        final String keystore = getKeystore();
        if (keystore == null || keystore.isEmpty()) return descriptor;

        descriptor
            .addContent(new Element("attr")
                .setAttribute("name", "socketFactory")
                .addContent(GenericSSLSocketFactory.class.getName()))
            .addContent(new Element("property")
                .setAttribute("name", "keystore")
                .setAttribute("value", keystore))
            .addContent(new Element("property")
                .setAttribute("name", "storepassword")
                .setAttribute("value", getStorePassword()))
            .addContent(new Element("property")
                .setAttribute("name", "keypassword")
                .setAttribute("value", getKeyPassword()));

        return descriptor;
    }

    protected Element addRequestListener(Element descriptor) {
        final String requestListener = getRequestListener();
        if (requestListener != null && !requestListener.isEmpty()) {
            // Check if this is a TransactionManager queue reference (format: "txnmgr:queueName" or "txnmgr:space:queueName")
            if (requestListener.startsWith("txnmgr:")) {
                String[] parts = requestListener.substring(7).split(":", 2);
                String space = parts.length > 1 ? parts[0] : "tspace:default";
                String queue = parts.length > 1 ? parts[1] : parts[0];

                Element listenerElement = new Element("request-listener")
                    .setAttribute("class", TxnMgrRequestListener.class.getName())
                    .setAttribute("logger", Q2_LOGGER)
                    .addContent(new Element("property")
                        .setAttribute("name", "space")
                        .setAttribute("value", space))
                    .addContent(new Element("property")
                        .setAttribute("name", "queue")
                        .setAttribute("value", queue));
                descriptor.addContent(listenerElement);
            } else {
                // Assume it's a Groovy script path
                descriptor.addContent(new Element("request-listener")
                    .setAttribute("class", GroovyRequestListener.class.getName())
                    .setAttribute("logger", Q2_LOGGER)
                    .addContent(new Element("property")
                        .setAttribute("name", "source")
                        .setAttribute("value", requestListener)));
            }
        }
        return descriptor;
    }

    // Registers QServer <key>-server and ISOServer server.<key>-server
    protected QBeanSupport startQServer() {
        final String key = getPropertyAsString(CONFIGKEY);
        Element channelDescriptor = getChannelDescriptor(key);
        if (channelDescriptor == null) return null;

        final String port = getPort();
        if (port == null || port.isEmpty()) {
            log.error("Port undefined, cannot start QServer");
            return null;
        }
        // Build QBean deployment descriptor in memory
        Element descriptor = new Element("qserver")
            .setAttribute("name", getQServerName())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(channelDescriptor)
            .addContent(new Element("attr")
                .setAttribute("name", "port")
                .setAttribute("type", Integer.class.getName())
                .addContent(getPort()))
            .addContent(new Element("in").addContent(key+"-send"))
            .addContent(new Element("out").addContent(key+"-receive"))
            .addContent(new Element("ready").addContent(key+".ready"));

        final String connectionSelection = getConnectionSelection();
        if (connectionSelection != null && !connectionSelection.isEmpty()) {
            descriptor.addContent(new Element("send-request")
                .addContent(ISO8583Config.ConnectionSelection.fromTag(connectionSelection).toString()));
        }
        addSSLConfig(descriptor);

        return deployAndStart(descriptor);
    }

    // Registers QMUX mux.<key>-mux and connects with <key>-receive and <key>-send Space queues
    protected QBeanSupport startMux() {
        final String key = getPropertyAsString(CONFIGKEY);

        Element descriptor = new Element("qmux")
            .setAttribute("name", getMuxName())
            .setAttribute("logger", Q2_LOGGER)
            .addContent(new Element("in").addContent(key+"-receive"))
            .addContent(new Element("out").addContent(key+"-send"))
            .addContent(new Element("unhandled").addContent(key+"-unhandled"))
            .addContent(new Element("ready").addContent(key+".ready"));

        final String mtiMapping = getMtiMapping();
        if (mtiMapping != null && !mtiMapping.isEmpty()) {
            descriptor.addContent(new Element("mtimapping").addContent(mtiMapping));
        }
        getMuxKeyConfig().forEach(c -> {
            Element muxKey = new Element("key").addContent(c.getValue());
            if (c.getName() != null && !c.getName().isEmpty()) {
                muxKey.setAttribute("mti", c.getName());
            }
            descriptor.addContent(muxKey);
        });
        addRequestListener(descriptor);

        return deployAndStart(descriptor);
    }

    protected QBeanSupport deployAndStart(Element descriptor) {
        if (log.isDebugEnabled()) {
            log.debug("Deploying {}", new XMLOutputter().outputString(descriptor));
        }
        QFactory qFactory = q2.getFactory();
        try {
            Object qbean = qFactory.instantiate(q2, descriptor);
            ObjectInstance obj = qFactory.createQBean(q2, descriptor, qbean);
            qFactory.startQBean(q2, obj.getObjectName());
            return (QBeanSupport) qbean;
        } catch (Exception e) {
            log.error("Failed to deploy {}", descriptor.getName(), e);
            return null;
        }
    }

    protected void stopQServer() {
        stopAndUndeploy(NameRegistrar.getIfExists(getQServerName()));
    }

    protected void stopMux() {
        try {
            stopAndUndeploy(getMux());
        } catch (NameRegistrar.NotFoundException ignoreBecauseItWasntRunning) {}
    }

    protected void stopAndUndeploy(QBeanSupport qbean) {
        if (qbean == null) return;
        QFactory qFactory = q2.getFactory();
        final String key = qbean.getName();
        try {
            log.debug("Undeploying {}", key);
            ObjectName objectName = new ObjectName(Q2.QBEAN_NAME+key);
            qFactory.destroyQBean(q2, objectName, qbean);
        } catch (Exception e) {
            log.error("Failed to undeploy {}", key, e);
        }
    }

    protected synchronized void startQ2() {
        q2 = Q2.getQ2();
        if (q2 == null) {
            log.debug("Creating Q2");
            q2 = new Q2(new String[]{
                "-d", JMeterUtils.getPropDefault(Q2_DEPLOY_DIR, Q2.DEFAULT_DEPLOY_DIR),
                "-no-scan", "-no-dynamic"
            });
        }
        if (!q2.running()) {
            q2.start();
            if (!q2.ready(JMeterUtils.getPropDefault(Q2_STARTUP_TIMEOUT, 2000))) {
                log.error("Q2 startup timeout exceeded");
            }
        }
    }

    protected synchronized void stopQ2() {
        if (q2 != null && q2.running()) {
            q2.stop();
        }
    }

    public QMUX getMux() throws NameRegistrar.NotFoundException {
        return (QMUX)QMUX.getMUX(getMuxName());
    }

    public String getMuxName() { return getConfigKey()+"-mux"; }

    public String getQServerName() { return getConfigKey()+"-server"; }

    // Shutdown hook thread reference
    private transient Thread shutdownHook;

    @Override
    public void testStarted() {
        if (running) {
            log.warn("Server already running");
            return;
        }

        startQ2();

        if (getConfigKey() == null || getConfigKey().isEmpty()) {
            setConfigKey(String.format("iso8583-server-%08x", hashCode()));
        }
        log.info("'{}' starting ISO8583 server on port {}", getName(), getPort());

        startQServer();
        startMux();

        running = true;
        shutdownLatch = new CountDownLatch(1);

        // Register shutdown hook for Ctrl+C
        shutdownHook = new Thread(() -> {
            log.info("Shutdown signal received");
            running = false;
            shutdownLatch.countDown();
        }, "ISO8583-Server-Shutdown-Hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        ISOServer server = null;
        try {
            server = ISOServer.getServer(getQServerName());
        } catch (NameRegistrar.NotFoundException e) {
            log.error("ISOServer not found", e);
            return;
        }

        String startupMsg = String.format("ISO8583 Server '%s' listening on port %s - press Ctrl+C to stop", getName(), getPort());
        log.info(startupMsg);
        System.out.println(startupMsg);

        // Start blocking loop in separate thread to allow other TestStateListeners to complete
        final ISOServer serverRef = server;
        Thread serverThread = new Thread(() -> {
            // Block here waiting for shutdown signal - this keeps JMeter running
            while (running) {
                try {
                    int connections = serverRef.getActiveConnections();
                    if (connections > 0) {
                        log.debug("Active connections: {}", connections);
                    }
                    // Wait with timeout to allow periodic status checks
                    if (shutdownLatch.await(10, TimeUnit.SECONDS)) {
                        break; // Shutdown signaled
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("ISO8583 Server '{}' shutting down", getName());
        }, "ISO8583-Server-Wait-" + getName());
        serverThread.setDaemon(false); // Keep JMeter running
        serverThread.start();
    }

    @Override
    public void testEnded() {
        log.debug("Stopping ISO8583 Server");
        running = false;

        // Signal shutdown
        if (shutdownLatch != null) {
            shutdownLatch.countDown();
        }

        // Remove shutdown hook if still registered (avoid calling it during normal shutdown)
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                // JVM already shutting down, ignore
            }
        }

        stopMux();
        stopQServer();
        stopQ2();
    }

    @Override
    public void testStarted(String s) { testStarted(); }

    @Override
    public void testEnded(String s) { testEnded(); }

    // Accessors for mapping to TestBean GUI elements...
    public String getConfigKey() { return getPropertyAsString(CONFIGKEY); }
    public void setConfigKey(String configKey) { setProperty(new StringProperty(CONFIGKEY, configKey)); }

    public String getClassname() { return getPropertyAsString(CLASSNAME); }
    public void setClassname(String classname) { setProperty(new StringProperty(CLASSNAME, classname)); }

    public String getPackager() { return getPropertyAsString(PACKAGER);}
    public void setPackager(String packager) { setProperty(new StringProperty(PACKAGER, packager)); }

    public String getHeader() { return getPropertyAsString(HEADER); }
    public void setHeader(String header) { setProperty(new StringProperty(HEADER, header)); }

    public String getPort() { return getPropertyAsString(PORT); }
    public void setPort(String port) { setProperty(new StringProperty(PORT, port)); }

    public String getConnectionSelection() { return getPropertyAsString(CONNECTIONSELECTION); }
    public void setConnectionSelection(String connectionSelection) { setProperty(new StringProperty(CONNECTIONSELECTION, connectionSelection)); }

    // Need Collection getter/setter for TestBean GUI
    public Collection<ChannelConfigItem> getChannelConfig() {
        Collection<ChannelConfigItem> items = new ArrayList<>();
        JMeterProperty cfg = getProperty(CHANNELCONFIG);
        if (cfg instanceof CollectionProperty) {
            ((CollectionProperty)cfg).iterator()
                .forEachRemaining(p -> items.add((ChannelConfigItem) p.getObjectValue()));
        }
        return items;
    }

    public void setChannelConfig(Collection<ChannelConfigItem> items) {
        setProperty(new CollectionProperty(CHANNELCONFIG, items));
    }

    public String getKeystore() { return getPropertyAsString(KEYSTORE); }
    public void setKeystore(String keystore) { setProperty(new StringProperty(KEYSTORE, keystore)); }

    public String getStorePassword() { return getPropertyAsString(STOREPASSWORD); }
    public void setStorePassword(String storePassword) { setProperty(new StringProperty(STOREPASSWORD, storePassword)); }

    public String getKeyPassword() { return getPropertyAsString(KEYPASSWORD); }
    public void setKeyPassword(String keyPassword) { setProperty(new StringProperty(KEYPASSWORD, keyPassword)); }

    public String getRequestListener() { return getPropertyAsString(REQUESTLISTENER); }
    public void setRequestListener(String requestListener) { setProperty(new StringProperty(REQUESTLISTENER, requestListener)); }

    public String getMtiMapping() { return getPropertyAsString(MTIMAPPING); }
    public void setMtiMapping(String mtiMapping) { setProperty(new StringProperty(MTIMAPPING, mtiMapping)); }

    // Need Collection getter/setter for TestBean GUI
    public Collection<MuxKeyConfigItem> getMuxKeyConfig() {
        Collection<MuxKeyConfigItem> items = new ArrayList<>();
        JMeterProperty cfg = getProperty(MUXKEYCONFIG);
        if (cfg instanceof CollectionProperty) {
            ((CollectionProperty)cfg).iterator()
                .forEachRemaining(p -> items.add((MuxKeyConfigItem) p.getObjectValue()));
        }
        return items;
    }

    public void setMuxKeyConfig(Collection<MuxKeyConfigItem> items) {
        setProperty(new CollectionProperty(MUXKEYCONFIG, items));
    }
}
