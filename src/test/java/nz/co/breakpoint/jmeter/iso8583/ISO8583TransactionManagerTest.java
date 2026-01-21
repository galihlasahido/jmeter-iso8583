package nz.co.breakpoint.jmeter.iso8583;

import java.util.*;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * Unit tests for ISO8583TransactionManager.
 */
public class ISO8583TransactionManagerTest extends ISO8583TestBase {

    private ISO8583TransactionManager instance;

    @Before
    public void setUp() {
        instance = new ISO8583TransactionManager();
        instance.setTxnMgrName("test-txnmgr");
        instance.setSpace("tspace:test");
        instance.setQueue("testQueue");
        instance.setSessions("3");
        instance.setDebug(true);
    }

    @Test
    public void shouldBuildDescriptorWithBasicProperties() {
        Element descriptor = instance.getTxnMgrDescriptor();

        assertEquals("txnmgr", descriptor.getName());
        assertEquals("test-txnmgr", descriptor.getAttributeValue("name"));
        assertEquals("org.jpos.transaction.TransactionManager", descriptor.getAttributeValue("class"));
        assertEquals("Q2", descriptor.getAttributeValue("logger"));

        // Check properties
        List<Element> properties = descriptor.getChildren("property");
        assertTrue(properties.size() >= 3); // space, queue, debug

        assertPropertyValue(descriptor, "space", "tspace:test");
        assertPropertyValue(descriptor, "queue", "testQueue");
        assertPropertyValue(descriptor, "sessions", "3");
        assertPropertyValue(descriptor, "debug", "true");
    }

    @Test
    public void shouldBuildDescriptorWithRootParticipant() {
        // Add a root-level participant (no group)
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "mti", "0800"));
        nodes.add(TxnMgrTreeNode.createProperty(2, 0, "target", "Network"));
        instance.setTreeConfig(nodes);

        Element descriptor = instance.getTxnMgrDescriptor();

        // Should have one participant at root level
        List<Element> participants = descriptor.getChildren("participant");
        assertEquals(1, participants.size());

        Element participant = participants.get(0);
        assertEquals("com.test.Selector", participant.getAttributeValue("class"));
        assertEquals("Q2", participant.getAttributeValue("logger"));

        // Check participant properties
        assertPropertyValue(participant, "mti", "0800");
        assertPropertyValue(participant, "target", "Network");
    }

    @Test
    public void shouldBuildDescriptorWithGroup() {
        // Add a group with a participant
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createGroup(0, null, "Network"));
        nodes.add(TxnMgrTreeNode.createParticipant(1, 0, "com.test.NetworkHandler", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(2, 1, "timeout", "30000"));
        instance.setTreeConfig(nodes);

        Element descriptor = instance.getTxnMgrDescriptor();

        // Should have one group
        List<Element> groups = descriptor.getChildren("group");
        assertEquals(1, groups.size());

        Element group = groups.get(0);
        assertEquals("Network", group.getAttributeValue("name"));

        // Group should have one participant
        List<Element> participants = group.getChildren("participant");
        assertEquals(1, participants.size());

        Element participant = participants.get(0);
        assertEquals("com.test.NetworkHandler", participant.getAttributeValue("class"));
        assertPropertyValue(participant, "timeout", "30000");
    }

    @Test
    public void shouldBuildComplexDescriptor() {
        // Build a complex structure similar to the example XML
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();

        // Root participant (Selector)
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.ads.jpos.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "0800180000", "Network"));
        nodes.add(TxnMgrTreeNode.createProperty(2, 0, "isorequest", "REQUESTTRANS"));
        nodes.add(TxnMgrTreeNode.createProperty(3, 0, "isosource", "ISOTRANS"));

        // Network group
        nodes.add(TxnMgrTreeNode.createGroup(4, null, "Network"));
        nodes.add(TxnMgrTreeNode.createParticipant(5, 4, "com.ads.jpos.Network", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(6, 5, "isorequest", "REQUESTTRANS"));
        nodes.add(TxnMgrTreeNode.createProperty(7, 5, "isosource", "ISOTRANS"));
        nodes.add(TxnMgrTreeNode.createProperty(8, 5, "timeout", "30000"));

        instance.setTreeConfig(nodes);

        Element descriptor = instance.getTxnMgrDescriptor();

        // Print for debugging
        if (instance.isDebug()) {
            XMLOutputter xmlOutputter = new XMLOutputter(Format.getPrettyFormat());
            System.out.println(xmlOutputter.outputString(descriptor));
        }

        // Verify structure
        List<Element> rootParticipants = descriptor.getChildren("participant");
        assertEquals(1, rootParticipants.size());

        List<Element> groups = descriptor.getChildren("group");
        assertEquals(1, groups.size());

        Element networkGroup = groups.get(0);
        assertEquals("Network", networkGroup.getAttributeValue("name"));

        List<Element> groupParticipants = networkGroup.getChildren("participant");
        assertEquals(1, groupParticipants.size());
    }

    @Test
    public void shouldGenerateNameIfEmpty() {
        instance.setTxnMgrName("");
        Element descriptor = instance.getTxnMgrDescriptor();

        String name = descriptor.getAttributeValue("name");
        assertNotNull(name);
        assertTrue(name.startsWith("jmeter-txnmgr-"));
    }

    @Test
    public void treeNodeShouldCreateGroup() {
        TxnMgrTreeNode node = TxnMgrTreeNode.createGroup(1, null, "TestGroup");

        assertEquals(TxnMgrTreeNode.NodeType.GROUP, node.getNodeType());
        assertEquals(1, node.getNodeId());
        assertNull(node.getParentId());
        assertEquals("TestGroup", node.getName());
        assertTrue(node.isRoot());
    }

    @Test
    public void treeNodeShouldCreateParticipant() {
        TxnMgrTreeNode node = TxnMgrTreeNode.createParticipant(2, 1, "com.test.Handler", "Q2");

        assertEquals(TxnMgrTreeNode.NodeType.PARTICIPANT, node.getNodeType());
        assertEquals(2, node.getNodeId());
        assertEquals(Integer.valueOf(1), node.getParentId());
        assertEquals("com.test.Handler", node.getClassName());
        assertEquals("Q2", node.getLogger());
        assertFalse(node.isRoot());
    }

    @Test
    public void treeNodeShouldCreateProperty() {
        TxnMgrTreeNode node = TxnMgrTreeNode.createProperty(3, 2, "timeout", "30000");

        assertEquals(TxnMgrTreeNode.NodeType.PROPERTY, node.getNodeType());
        assertEquals(3, node.getNodeId());
        assertEquals(Integer.valueOf(2), node.getParentId());
        assertEquals("timeout", node.getName());
        assertEquals("30000", node.getPropValue());
    }

    @Test
    public void treeNodeShouldWorkWithDirectPropertySetting() {
        // This simulates how JMeter sets properties when loading from JMX
        TxnMgrTreeNode node = new TxnMgrTreeNode();

        // Set properties directly (as JMeter does when loading JMX)
        node.setProperty("nodeType", "PARTICIPANT");
        node.setProperty("nodeId", 5);
        node.setProperty("parentId", -1);
        node.setProperty("className", "com.test.TestClass");
        node.setProperty("logger", "Q2");
        node.setName("TestName"); // This sets TestElement.name

        // Verify getters work
        assertEquals(TxnMgrTreeNode.NodeType.PARTICIPANT, node.getNodeType());
        assertEquals(5, node.getNodeId());
        assertNull(node.getParentId()); // -1 should return null
        assertEquals("com.test.TestClass", node.getClassName());
        assertEquals("Q2", node.getLogger());
        assertEquals("TestName", node.getName());
    }

    @Test
    public void treeNodeShouldBeSerializableToCollection() {
        // Create nodes and add to collection
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        TxnMgrTreeNode node1 = TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2");
        TxnMgrTreeNode node2 = TxnMgrTreeNode.createProperty(1, 0, "key", "value");
        nodes.add(node1);
        nodes.add(node2);

        // Set tree config
        instance.setTreeConfig(nodes);

        // Get tree config back
        Collection<TxnMgrTreeNode> retrieved = instance.getTreeConfig();

        assertEquals(2, retrieved.size());

        // Verify first node
        TxnMgrTreeNode[] arr = retrieved.toArray(new TxnMgrTreeNode[0]);
        assertEquals(TxnMgrTreeNode.NodeType.PARTICIPANT, arr[0].getNodeType());
        assertEquals(0, arr[0].getNodeId());
        assertEquals("com.test.Selector", arr[0].getClassName());

        // Verify second node
        assertEquals(TxnMgrTreeNode.NodeType.PROPERTY, arr[1].getNodeType());
        assertEquals(1, arr[1].getNodeId());
        assertEquals("key", arr[1].getName());
        assertEquals("value", arr[1].getPropValue());
    }

    // Helper method to assert property value
    private void assertPropertyValue(Element parent, String name, String expectedValue) {
        for (Element prop : parent.getChildren("property")) {
            if (name.equals(prop.getAttributeValue("name"))) {
                assertEquals(expectedValue, prop.getAttributeValue("value"));
                return;
            }
        }
        fail("Property '" + name + "' not found");
    }
}
