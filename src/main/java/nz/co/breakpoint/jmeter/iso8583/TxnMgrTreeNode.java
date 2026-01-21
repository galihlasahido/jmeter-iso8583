package nz.co.breakpoint.jmeter.iso8583;

import org.apache.jmeter.testelement.AbstractTestElement;

/**
 * Represents a node in the TransactionManager configuration tree.
 * Used for serializing the hierarchical tree structure to JMeter's flat property format.
 *
 * Node types:
 * - GROUP: A named group containing participants
 * - PARTICIPANT: A transaction participant with class name and logger
 * - SCRIPT: A BeanShell script participant with inline code
 * - PROPERTY: A key/value property belonging to a participant
 *
 * The tree hierarchy is reconstructed using the nodeId and parentId fields.
 */
public class TxnMgrTreeNode extends AbstractTestElement {

    public static final String NODE_TYPE = "nodeType";
    public static final String NODE_ID = "nodeId";
    public static final String PARENT_ID = "parentId";
    public static final String CLASS_NAME = "className";
    public static final String LOGGER = "logger";
    public static final String PROP_VALUE = "propValue";
    public static final String SCRIPT_CONTENT = "scriptContent";
    public static final String SCRIPT_NAME = "scriptName";

    public enum NodeType {
        GROUP,
        PARTICIPANT,
        SCRIPT,
        PROPERTY
    }

    public TxnMgrTreeNode() {
    }

    /**
     * Create a GROUP node.
     */
    public static TxnMgrTreeNode createGroup(int nodeId, Integer parentId, String groupName) {
        TxnMgrTreeNode node = new TxnMgrTreeNode();
        node.setNodeType(NodeType.GROUP);
        node.setNodeId(nodeId);
        node.setParentId(parentId);
        node.setName(groupName);
        return node;
    }

    /**
     * Create a PARTICIPANT node.
     */
    public static TxnMgrTreeNode createParticipant(int nodeId, Integer parentId, String className, String logger) {
        TxnMgrTreeNode node = new TxnMgrTreeNode();
        node.setNodeType(NodeType.PARTICIPANT);
        node.setNodeId(nodeId);
        node.setParentId(parentId);
        node.setClassName(className);
        node.setLogger(logger);
        return node;
    }

    /**
     * Create a PROPERTY node.
     */
    public static TxnMgrTreeNode createProperty(int nodeId, int parentId, String propName, String propValue) {
        TxnMgrTreeNode node = new TxnMgrTreeNode();
        node.setNodeType(NodeType.PROPERTY);
        node.setNodeId(nodeId);
        node.setParentId(parentId);
        node.setName(propName);
        node.setPropValue(propValue);
        return node;
    }

    /**
     * Create a SCRIPT node (BeanShell participant).
     */
    public static TxnMgrTreeNode createScript(int nodeId, Integer parentId, String scriptName, String scriptContent, String logger) {
        TxnMgrTreeNode node = new TxnMgrTreeNode();
        node.setNodeType(NodeType.SCRIPT);
        node.setNodeId(nodeId);
        node.setParentId(parentId);
        node.setScriptName(scriptName);
        node.setScriptContent(scriptContent);
        node.setLogger(logger);
        return node;
    }

    // Node type
    public NodeType getNodeType() {
        String type = getPropertyAsString(NODE_TYPE);
        return type.isEmpty() ? null : NodeType.valueOf(type);
    }

    public void setNodeType(NodeType nodeType) {
        setProperty(NODE_TYPE, nodeType != null ? nodeType.name() : "");
    }

    // Node ID (unique identifier within the tree)
    public int getNodeId() {
        return getPropertyAsInt(NODE_ID, -1);
    }

    public void setNodeId(int nodeId) {
        setProperty(NODE_ID, nodeId);
    }

    // Parent ID (null/-1 for root-level items)
    public Integer getParentId() {
        int id = getPropertyAsInt(PARENT_ID, -1);
        return id < 0 ? null : id;
    }

    public void setParentId(Integer parentId) {
        setProperty(PARENT_ID, parentId != null ? parentId : -1);
    }

    // Class name (for PARTICIPANT nodes)
    public String getClassName() {
        return getPropertyAsString(CLASS_NAME);
    }

    public void setClassName(String className) {
        setProperty(CLASS_NAME, className != null ? className : "");
    }

    // Logger (for PARTICIPANT nodes)
    public String getLogger() {
        return getPropertyAsString(LOGGER);
    }

    public void setLogger(String logger) {
        setProperty(LOGGER, logger != null ? logger : "");
    }

    // Property value (for PROPERTY nodes; name is stored in getName())
    public String getPropValue() {
        return getPropertyAsString(PROP_VALUE);
    }

    public void setPropValue(String propValue) {
        setProperty(PROP_VALUE, propValue != null ? propValue : "");
    }

    // Script content (for SCRIPT nodes)
    public String getScriptContent() {
        return getPropertyAsString(SCRIPT_CONTENT);
    }

    public void setScriptContent(String scriptContent) {
        setProperty(SCRIPT_CONTENT, scriptContent != null ? scriptContent : "");
    }

    // Script name (for SCRIPT nodes)
    public String getScriptName() {
        return getPropertyAsString(SCRIPT_NAME);
    }

    public void setScriptName(String scriptName) {
        setProperty(SCRIPT_NAME, scriptName != null ? scriptName : "");
    }

    /**
     * Check if this is a root-level node (no parent).
     */
    public boolean isRoot() {
        return getParentId() == null;
    }

    @Override
    public String toString() {
        NodeType type = getNodeType();
        if (type == null) return "TxnMgrTreeNode[unknown]";

        switch (type) {
            case GROUP:
                return String.format("[G] %s", getName());
            case PARTICIPANT:
                return String.format("[P] %s (logger: %s)", getClassName(), getLogger());
            case SCRIPT:
                String name = getScriptName();
                return String.format("[S] %s (logger: %s)",
                    name.isEmpty() ? "BeanShell Script" : name, getLogger());
            case PROPERTY:
                return String.format("%s = %s", getName(), getPropValue());
            default:
                return "TxnMgrTreeNode[" + type + "]";
        }
    }
}
