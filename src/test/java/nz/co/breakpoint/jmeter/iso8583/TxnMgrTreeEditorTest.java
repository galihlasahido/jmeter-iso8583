package nz.co.breakpoint.jmeter.iso8583;

import java.io.*;
import java.util.*;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.property.*;
import org.apache.jorphan.collections.HashTree;
import org.junit.*;
import static org.junit.Assert.*;

/**
 * Unit tests for TxnMgrTreeEditor.
 */
public class TxnMgrTreeEditorTest extends ISO8583TestBase {

    private TxnMgrTreeEditor editor;

    @Before
    public void setUp() {
        editor = new TxnMgrTreeEditor();
    }

    @Test
    public void shouldHandleNullValue() {
        editor.setValue(null);
        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        assertTrue(((Collection<?>) value).isEmpty());
    }

    @Test
    public void shouldHandleEmptyCollection() {
        editor.setValue(new ArrayList<>());
        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        assertTrue(((Collection<?>) value).isEmpty());
    }

    @Test
    public void shouldHandleCollectionOfTxnMgrTreeNodes() {
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "key", "value"));

        editor.setValue(nodes);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(2, result.size());
    }

    @Test
    public void shouldHandleCollectionProperty() {
        // Create nodes
        List<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "key", "value"));

        // Wrap in CollectionProperty (simulating JMeter internal storage)
        CollectionProperty cp = new CollectionProperty("treeConfig", nodes);

        editor.setValue(cp);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(2, result.size());
    }

    @Test
    public void shouldHandleCollectionPropertyGetObjectValue() {
        // Create nodes
        List<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "key", "value"));

        // Wrap in CollectionProperty
        CollectionProperty cp = new CollectionProperty("treeConfig", nodes);

        // This is what TestBeanGUI might pass - the result of getObjectValue()
        Object passedValue = cp.getObjectValue();
        System.out.println("CollectionProperty.getObjectValue() returns: " + passedValue.getClass().getName());

        if (passedValue instanceof Collection) {
            Collection<?> coll = (Collection<?>) passedValue;
            for (Object item : coll) {
                System.out.println("  Item type: " + item.getClass().getName());
            }
        }

        editor.setValue(passedValue);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(2, result.size());
    }

    @Test
    public void shouldPreserveTreeStructure() {
        // Create a complex tree structure
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();

        // Root participant (Selector)
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "routing", "0800=Network"));

        // Group with participant
        nodes.add(TxnMgrTreeNode.createGroup(2, null, "Network"));
        nodes.add(TxnMgrTreeNode.createParticipant(3, 2, "com.test.NetworkHandler", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(4, 3, "timeout", "30000"));

        editor.setValue(nodes);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(5, result.size());

        // Verify structure is preserved
        Map<Integer, TxnMgrTreeNode> nodeMap = new HashMap<>();
        for (Object obj : result) {
            TxnMgrTreeNode node = (TxnMgrTreeNode) obj;
            nodeMap.put(node.getNodeId(), node);
        }

        // Verify root nodes have no parent
        assertNull(nodeMap.get(0).getParentId());
        assertNull(nodeMap.get(2).getParentId());

        // Verify child nodes have correct parents
        assertEquals(Integer.valueOf(0), nodeMap.get(1).getParentId());
        assertEquals(Integer.valueOf(2), nodeMap.get(3).getParentId());
        assertEquals(Integer.valueOf(3), nodeMap.get(4).getParentId());
    }

    @Test
    public void shouldSimulateTestBeanGUIBehavior() {
        // This test simulates what happens when TestBeanGUI loads a saved test plan:
        // 1. JMeter deserializes the JMX into TestElement with CollectionProperty
        // 2. TestBeanGUI retrieves the property and passes it to PropertyEditor.setValue()

        // Step 1: Create an ISO8583TransactionManager and set tree config
        ISO8583TransactionManager txnMgr = new ISO8583TransactionManager();
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        nodes.add(TxnMgrTreeNode.createProperty(1, 0, "key", "value"));
        txnMgr.setTreeConfig(nodes);

        // Step 2: Get the property (as TestBeanGUI would)
        JMeterProperty prop = txnMgr.getProperty("treeConfig");
        System.out.println("Property type: " + prop.getClass().getName());

        // Step 3: Get objectValue (as TestBeanGUI might pass to setValue)
        Object objectValue = prop.getObjectValue();
        System.out.println("ObjectValue type: " + objectValue.getClass().getName());

        // Step 4: Pass to editor (simulating TestBeanGUI behavior)
        // TestBeanGUI could pass either the property itself or its objectValue
        editor.setValue(objectValue);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(2, result.size());
    }

    @Test
    public void shouldHandleCollectionPropertyDirectly() {
        // TestBeanGUI might also pass the CollectionProperty directly
        ISO8583TransactionManager txnMgr = new ISO8583TransactionManager();
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        txnMgr.setTreeConfig(nodes);

        // Get the property
        JMeterProperty prop = txnMgr.getProperty("treeConfig");
        assertTrue(prop instanceof CollectionProperty);

        // Pass CollectionProperty directly to editor
        editor.setValue(prop);

        Object value = editor.getValue();
        assertTrue(value instanceof Collection);
        Collection<?> result = (Collection<?>) value;
        assertEquals(1, result.size());
    }

    // Note: SaveService round-trip test removed - requires full JMeter runtime environment
    // which is not available in unit test context due to XStream/Java module compatibility issues

    @Test
    public void shouldInspectCollectionPropertyContents() {
        // Create a TransactionManager with tree config
        ISO8583TransactionManager txnMgr = new ISO8583TransactionManager();
        Collection<TxnMgrTreeNode> nodes = new ArrayList<>();
        nodes.add(TxnMgrTreeNode.createParticipant(0, null, "com.test.Selector", "Q2"));
        txnMgr.setTreeConfig(nodes);

        // Get the property
        JMeterProperty prop = txnMgr.getProperty("treeConfig");
        System.out.println("Property class: " + prop.getClass().getName());

        if (prop instanceof CollectionProperty) {
            CollectionProperty cp = (CollectionProperty) prop;
            System.out.println("CollectionProperty size: " + cp.size());

            // Iterate and inspect
            int index = 0;
            for (JMeterProperty itemProp : cp) {
                System.out.println("Item " + index + " property class: " + itemProp.getClass().getName());
                System.out.println("Item " + index + " property name: " + itemProp.getName());

                Object objectValue = itemProp.getObjectValue();
                System.out.println("Item " + index + " objectValue class: " + objectValue.getClass().getName());

                if (objectValue instanceof TxnMgrTreeNode) {
                    TxnMgrTreeNode node = (TxnMgrTreeNode) objectValue;
                    System.out.println("Item " + index + " is TxnMgrTreeNode: nodeId=" + node.getNodeId() +
                            ", nodeType=" + node.getNodeType() + ", className=" + node.getClassName());
                }
                index++;
            }
        }
    }
}
