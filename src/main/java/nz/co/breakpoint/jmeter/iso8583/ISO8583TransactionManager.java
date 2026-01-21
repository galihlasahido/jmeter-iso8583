package nz.co.breakpoint.jmeter.iso8583;

import java.io.Serializable;
import java.util.*;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.apache.jmeter.config.ConfigTestElement;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.jdom2.Element;
import org.jdom2.output.XMLOutputter;
import org.jpos.q2.Q2;
import org.jpos.q2.QBeanSupport;
import org.jpos.q2.QFactory;
import org.jpos.transaction.TransactionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMeter Config Element for jPOS TransactionManager QBean.
 * <p>
 * Manages the lifecycle of a TransactionManager with configurable participants and groups.
 * Designed for server mode - handling incoming ISO8583 requests.
 * </p>
 * <p>
 * This element shares the Q2 instance from {@link ISO8583Config} or {@link ISO8583Server}.
 * Ensure either an ISO8583Config or ISO8583Server element is placed before this in the test plan.
 * </p>
 */
public class ISO8583TransactionManager extends ConfigTestElement
        implements ISO8583TestElement, TestBean, Serializable, TestStateListener, NoThreadClone {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(ISO8583TransactionManager.class);

    static {
        log.debug("ISO8583TransactionManager class loaded");
    }

    // Property names (appear in script files, don't change)
    public static final String
        TXNMGR_NAME = "txnMgrName",
        SPACE = "space",
        QUEUE = "queue",
        SESSIONS = "sessions",
        DEBUG = "debug",
        TREE_CONFIG = "treeConfig";

    protected static final String DEFAULT_LOGGER = "Q2";

    // Cached TransactionManager QBean instance
    protected transient QBeanSupport txnMgr;

    public ISO8583TransactionManager() {
        setProperty(new CollectionProperty(TREE_CONFIG, new ArrayList<>()));
    }

    // ========== JDOM Element Builder ==========

    /**
     * Build the TransactionManager deployment descriptor as a JDOM Element.
     */
    protected Element getTxnMgrDescriptor() {
        String name = getTxnMgrName();
        if (name == null || name.isEmpty()) {
            name = "jmeter-txnmgr-" + String.format("%08x", hashCode());
        }

        Element descriptor = new Element("txnmgr")
            .setAttribute("name", name)
            .setAttribute("class", TransactionManager.class.getName())
            .setAttribute("logger", DEFAULT_LOGGER)
            .setAttribute("realm", name);

        // Core properties
        addPropertyElement(descriptor, "space", getSpace());
        addPropertyElement(descriptor, "queue", getQueue());

        String sessions = getSessions();
        if (sessions != null && !sessions.isEmpty()) {
            addPropertyElement(descriptor, "sessions", sessions);
        }

        if (isDebug()) {
            addPropertyElement(descriptor, "debug", "true");
        }

        // Build participants and groups from tree config
        buildParticipantsAndGroups(descriptor);

        return descriptor;
    }

    /**
     * Build participant and group elements from the tree configuration.
     */
    protected void buildParticipantsAndGroups(Element descriptor) {
        Collection<TxnMgrTreeNode> nodes = getTreeConfig();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        // Build lookup maps
        Map<Integer, TxnMgrTreeNode> nodeMap = new HashMap<>();
        Map<Integer, List<TxnMgrTreeNode>> childrenMap = new HashMap<>();

        for (TxnMgrTreeNode node : nodes) {
            int nodeId = node.getNodeId();
            nodeMap.put(nodeId, node);

            Integer parentId = node.getParentId();
            if (parentId == null) {
                // Root-level node
                childrenMap.computeIfAbsent(-1, k -> new ArrayList<>()).add(node);
            } else {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }

        // Process root-level nodes
        List<TxnMgrTreeNode> rootNodes = childrenMap.getOrDefault(-1, Collections.emptyList());
        for (TxnMgrTreeNode rootNode : rootNodes) {
            TxnMgrTreeNode.NodeType type = rootNode.getNodeType();
            if (type == null) continue;

            switch (type) {
                case GROUP:
                    descriptor.addContent(buildGroupElement(rootNode, childrenMap));
                    break;
                case PARTICIPANT:
                    descriptor.addContent(buildParticipantElement(rootNode, childrenMap));
                    break;
                case SCRIPT:
                    descriptor.addContent(buildScriptElement(rootNode, childrenMap));
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Build a group element with its participants.
     */
    protected Element buildGroupElement(TxnMgrTreeNode groupNode, Map<Integer, List<TxnMgrTreeNode>> childrenMap) {
        Element groupElement = new Element("group")
            .setAttribute("name", groupNode.getName());

        List<TxnMgrTreeNode> children = childrenMap.getOrDefault(groupNode.getNodeId(), Collections.emptyList());
        for (TxnMgrTreeNode child : children) {
            TxnMgrTreeNode.NodeType childType = child.getNodeType();
            if (childType == TxnMgrTreeNode.NodeType.PARTICIPANT) {
                groupElement.addContent(buildParticipantElement(child, childrenMap));
            } else if (childType == TxnMgrTreeNode.NodeType.SCRIPT) {
                groupElement.addContent(buildScriptElement(child, childrenMap));
            }
        }

        return groupElement;
    }

    /**
     * Build a participant element with its properties.
     */
    protected Element buildParticipantElement(TxnMgrTreeNode participantNode, Map<Integer, List<TxnMgrTreeNode>> childrenMap) {
        Element participantElement = new Element("participant")
            .setAttribute("class", participantNode.getClassName());

        String logger = participantNode.getLogger();
        if (logger != null && !logger.isEmpty()) {
            participantElement.setAttribute("logger", logger);
        }

        // Add properties
        List<TxnMgrTreeNode> children = childrenMap.getOrDefault(participantNode.getNodeId(), Collections.emptyList());
        for (TxnMgrTreeNode child : children) {
            if (child.getNodeType() == TxnMgrTreeNode.NodeType.PROPERTY) {
                addPropertyElement(participantElement, child.getName(), child.getPropValue());
            }
        }

        return participantElement;
    }

    /**
     * Build a BeanShell script participant element.
     * Uses the BeanShellParticipant class and includes the script as a 'source' property.
     */
    protected Element buildScriptElement(TxnMgrTreeNode scriptNode, Map<Integer, List<TxnMgrTreeNode>> childrenMap) {
        Element participantElement = new Element("participant")
            .setAttribute("class", "nz.co.breakpoint.jmeter.iso8583.participants.BeanShellParticipant");

        String logger = scriptNode.getLogger();
        if (logger != null && !logger.isEmpty()) {
            participantElement.setAttribute("logger", logger);
        }

        // Add script content as 'source' property
        String scriptContent = scriptNode.getScriptContent();
        if (scriptContent != null && !scriptContent.isEmpty()) {
            addPropertyElement(participantElement, "source", scriptContent);
        }

        // Add any additional properties
        List<TxnMgrTreeNode> children = childrenMap.getOrDefault(scriptNode.getNodeId(), Collections.emptyList());
        for (TxnMgrTreeNode child : children) {
            if (child.getNodeType() == TxnMgrTreeNode.NodeType.PROPERTY) {
                addPropertyElement(participantElement, child.getName(), child.getPropValue());
            }
        }

        return participantElement;
    }

    protected void addPropertyElement(Element parent, String name, String value) {
        if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
            parent.addContent(new Element("property")
                .setAttribute("name", name)
                .setAttribute("value", value));
        }
    }

    // ========== Q2 Lifecycle Management ==========

    /**
     * Deploy and start the TransactionManager.
     * Reuses the Q2 instance from ISO8583Config or ISO8583Server.
     */
    protected QBeanSupport startTxnMgr() {
        // Try ISO8583Config's Q2 first, then fall back to ISO8583Server's Q2
        Q2 q2 = ISO8583Config.q2;
        if (q2 == null || !q2.running()) {
            q2 = ISO8583Server.q2;
        }
        if (q2 == null || !q2.running()) {
            log.error("Q2 is not running. Ensure ISO8583Config or ISO8583Server is configured and started first.");
            return null;
        }

        Element descriptor = getTxnMgrDescriptor();
        if (log.isDebugEnabled()) {
            log.debug("Deploying TransactionManager: {}", new XMLOutputter().outputString(descriptor));
        }

        QFactory qFactory = q2.getFactory();
        try {
            Object qbean = qFactory.instantiate(q2, descriptor);
            ObjectInstance obj = qFactory.createQBean(q2, descriptor, qbean);
            qFactory.startQBean(q2, obj.getObjectName());
            log.info("TransactionManager '{}' started successfully", getTxnMgrName());
            return (QBeanSupport) qbean;
        } catch (Exception e) {
            log.error("Failed to deploy TransactionManager", e);
            return null;
        }
    }

    /**
     * Stop and undeploy the TransactionManager.
     */
    protected void stopTxnMgr() {
        if (txnMgr == null) return;

        // Try ISO8583Config's Q2 first, then fall back to ISO8583Server's Q2
        Q2 q2 = ISO8583Config.q2;
        if (q2 == null) {
            q2 = ISO8583Server.q2;
        }
        if (q2 == null) return;

        QFactory qFactory = q2.getFactory();
        String name = txnMgr.getName();
        try {
            log.debug("Undeploying TransactionManager '{}'", name);
            ObjectName objectName = new ObjectName(Q2.QBEAN_NAME + name);
            qFactory.destroyQBean(q2, objectName, txnMgr);
        } catch (Exception e) {
            log.error("Failed to undeploy TransactionManager '{}'", name, e);
        }
    }

    // ========== TestStateListener Implementation ==========

    @Override
    public void testStarted() {
        log.info("ISO8583TransactionManager.testStarted() called for '{}'", getName());
        txnMgr = startTxnMgr();
    }

    @Override
    public void testEnded() {
        log.debug("'{}' stopping TransactionManager", getName());
        stopTxnMgr();
        txnMgr = null;
    }

    @Override
    public void testStarted(String host) {
        testStarted();
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    // ========== Property Accessors ==========

    public String getTxnMgrName() {
        return getPropertyAsString(TXNMGR_NAME);
    }

    public void setTxnMgrName(String name) {
        setProperty(new StringProperty(TXNMGR_NAME, name));
    }

    public String getSpace() {
        return getPropertyAsString(SPACE);
    }

    public void setSpace(String space) {
        setProperty(new StringProperty(SPACE, space));
    }

    public String getQueue() {
        return getPropertyAsString(QUEUE);
    }

    public void setQueue(String queue) {
        setProperty(new StringProperty(QUEUE, queue));
    }

    public String getSessions() {
        return getPropertyAsString(SESSIONS);
    }

    public void setSessions(String sessions) {
        setProperty(new StringProperty(SESSIONS, sessions));
    }

    public boolean isDebug() {
        return getPropertyAsBoolean(DEBUG);
    }

    public void setDebug(boolean debug) {
        setProperty(new BooleanProperty(DEBUG, debug));
    }

    // Tree configuration
    public Collection<TxnMgrTreeNode> getTreeConfig() {
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        JMeterProperty prop = getProperty(TREE_CONFIG);
        if (prop instanceof CollectionProperty) {
            ((CollectionProperty) prop).iterator()
                .forEachRemaining(p -> {
                    Object obj = p.getObjectValue();
                    if (obj instanceof TxnMgrTreeNode) {
                        nodes.add((TxnMgrTreeNode) obj);
                    }
                });
        }
        return nodes;
    }

    public void setTreeConfig(Collection<TxnMgrTreeNode> nodes) {
        setProperty(new CollectionProperty(TREE_CONFIG, nodes));
    }
}
