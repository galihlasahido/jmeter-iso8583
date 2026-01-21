package nz.co.breakpoint.jmeter.iso8583;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyEditorSupport;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.tree.*;

import org.apache.jmeter.testelement.property.CollectionProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testbeans.gui.TestBeanPropertyEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom PropertyEditor for TransactionManager tree configuration.
 * Displays a JTree with groups, participants, and properties.
 * Provides toolbar buttons for adding/removing/editing nodes.
 */
public class TxnMgrTreeEditor extends PropertyEditorSupport implements TestBeanPropertyEditor {

    private static final Logger log = LoggerFactory.getLogger(TxnMgrTreeEditor.class);

    private JPanel panel;
    private JTree tree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;

    private JButton addGroupBtn;
    private JButton addSelectorBtn;
    private JButton addParticipantBtn;
    private JButton addScriptBtn;
    private JButton addPropertyBtn;
    private JButton removeBtn;
    private JButton editBtn;

    private int nextNodeId = 0;

    public TxnMgrTreeEditor() {
        initUI();
    }

    private void initUI() {
        panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Create root node
        rootNode = new DefaultMutableTreeNode(new TreeNodeData(-1, "TransactionManager", TreeNodeData.Type.ROOT));
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);

        // Configure tree
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new TxnMgrTreeCellRenderer());
        tree.setEditable(false);

        // Tree scroll pane
        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(500, 300));

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        addGroupBtn = new JButton("+ Group");
        addGroupBtn.setToolTipText("Add a new group");
        addGroupBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addGroup();
            }
        });

        addSelectorBtn = new JButton("+ Selector");
        addSelectorBtn.setToolTipText("Add a Selector participant for routing transactions");
        addSelectorBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addSelector();
            }
        });

        addParticipantBtn = new JButton("+ Participant");
        addParticipantBtn.setToolTipText("Add a new participant");
        addParticipantBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addParticipant();
            }
        });

        addScriptBtn = new JButton("+ Script");
        addScriptBtn.setToolTipText("Add a BeanShell script participant");
        addScriptBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addScript();
            }
        });

        addPropertyBtn = new JButton("+ Property");
        addPropertyBtn.setToolTipText("Add a property to selected participant");
        addPropertyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addProperty();
            }
        });

        removeBtn = new JButton("Remove");
        removeBtn.setToolTipText("Remove selected node");
        removeBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeNode();
            }
        });

        editBtn = new JButton("Edit");
        editBtn.setToolTipText("Edit selected node");
        editBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editNode();
            }
        });

        toolbar.add(addGroupBtn);
        toolbar.add(addSelectorBtn);
        toolbar.add(addParticipantBtn);
        toolbar.add(addScriptBtn);
        toolbar.add(addPropertyBtn);
        toolbar.addSeparator();
        toolbar.add(editBtn);
        toolbar.add(removeBtn);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Enable/disable buttons based on selection
        tree.addTreeSelectionListener(e -> updateButtonStates());
        updateButtonStates();
    }

    private void updateButtonStates() {
        TreePath selectionPath = tree.getSelectionPath();
        boolean hasSelection = selectionPath != null;
        boolean isRoot = false;
        boolean isGroup = false;
        boolean isParticipant = false;
        boolean isScript = false;

        if (hasSelection) {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
            TreeNodeData data = (TreeNodeData) selectedNode.getUserObject();
            isRoot = data.type == TreeNodeData.Type.ROOT;
            isGroup = data.type == TreeNodeData.Type.GROUP;
            isParticipant = data.type == TreeNodeData.Type.PARTICIPANT;
            isScript = data.type == TreeNodeData.Type.SCRIPT;
        }

        // Add Group: only when root or nothing selected
        addGroupBtn.setEnabled(!hasSelection || isRoot);

        // Add Selector: only when root or nothing selected (selectors are root-level)
        addSelectorBtn.setEnabled(!hasSelection || isRoot);

        // Add Participant: when root, group, or nothing selected
        addParticipantBtn.setEnabled(!hasSelection || isRoot || isGroup);

        // Add Script: when root, group, or nothing selected
        addScriptBtn.setEnabled(!hasSelection || isRoot || isGroup);

        // Add Property: only when participant or script selected
        addPropertyBtn.setEnabled(isParticipant || isScript);

        // Remove: when anything except root selected
        removeBtn.setEnabled(hasSelection && !isRoot);

        // Edit: when anything except root selected
        editBtn.setEnabled(hasSelection && !isRoot);
    }

    private void addGroup() {
        String groupName = JOptionPane.showInputDialog(panel, "Enter group name:", "Add Group", JOptionPane.PLAIN_MESSAGE);
        if (groupName != null && !groupName.trim().isEmpty()) {
            TreeNodeData data = new TreeNodeData(nextNodeId++, groupName.trim(), TreeNodeData.Type.GROUP);
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(data);
            rootNode.add(newNode);
            treeModel.reload(rootNode);
            tree.expandPath(new TreePath(rootNode.getPath()));
            firePropertyChange();
        }
    }

    private void addSelector() {
        JTextField classField = new JTextField("org.jpos.transaction.participant.SelectDestination", 35);
        JTextField loggerField = new JTextField("Q2", 10);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Selector Class:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(classField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Logger:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(loggerField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        inputPanel.add(new JLabel("<html><i>Tip: Add routing properties after creating the selector.</i></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Add Selector",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !classField.getText().trim().isEmpty()) {
            TreeNodeData data = new TreeNodeData(nextNodeId++, classField.getText().trim(), TreeNodeData.Type.PARTICIPANT);
            data.logger = loggerField.getText().trim();
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(data);

            // Selectors are always added at root level
            rootNode.add(newNode);
            treeModel.reload(rootNode);
            tree.expandPath(new TreePath(rootNode.getPath()));

            // Select the new node so user can easily add properties
            tree.setSelectionPath(new TreePath(newNode.getPath()));
            firePropertyChange();
        }
    }

    private void addParticipant() {
        JTextField classField = new JTextField(30);
        JTextField loggerField = new JTextField("Q2", 10);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Class:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(classField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Logger:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(loggerField, gbc);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Add Participant",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !classField.getText().trim().isEmpty()) {
            TreeNodeData data = new TreeNodeData(nextNodeId++, classField.getText().trim(), TreeNodeData.Type.PARTICIPANT);
            data.logger = loggerField.getText().trim();
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(data);

            // Determine parent node
            DefaultMutableTreeNode parentNode = rootNode;
            TreePath selectionPath = tree.getSelectionPath();
            if (selectionPath != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                TreeNodeData selectedData = (TreeNodeData) selectedNode.getUserObject();
                if (selectedData.type == TreeNodeData.Type.GROUP) {
                    parentNode = selectedNode;
                }
            }

            parentNode.add(newNode);
            treeModel.reload(parentNode);
            tree.expandPath(new TreePath(parentNode.getPath()));
            firePropertyChange();
        }
    }

    private void addScript() {
        JTextField nameField = new JTextField("MyScript", 20);
        JTextField loggerField = new JTextField("Q2", 10);
        JTextArea scriptArea = new JTextArea(20, 60);
        scriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        scriptArea.setTabSize(4);
        scriptArea.setText(getDefaultScriptTemplate());

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));

        // Top panel for name and logger
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Script Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        topPanel.add(nameField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("  Logger:"), gbc);
        gbc.gridx = 3;
        topPanel.add(loggerField, gbc);

        inputPanel.add(topPanel, BorderLayout.NORTH);

        // Script editor
        JScrollPane scrollPane = new JScrollPane(scriptArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("BeanShell Script"));
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        // Help text
        JLabel helpLabel = new JLabel("<html><i>Available: id, ctx, request, source, log, cfg, result, destination, PREPARED, ABORTED</i></html>");
        inputPanel.add(helpLabel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Add BeanShell Script",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            TreeNodeData data = new TreeNodeData(nextNodeId++, nameField.getText().trim(), TreeNodeData.Type.SCRIPT);
            data.logger = loggerField.getText().trim();
            data.scriptContent = scriptArea.getText();
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(data);

            // Determine parent node
            DefaultMutableTreeNode parentNode = rootNode;
            TreePath selectionPath = tree.getSelectionPath();
            if (selectionPath != null) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                TreeNodeData selectedData = (TreeNodeData) selectedNode.getUserObject();
                if (selectedData.type == TreeNodeData.Type.GROUP) {
                    parentNode = selectedNode;
                }
            }

            parentNode.add(newNode);
            treeModel.reload(parentNode);
            tree.expandPath(new TreePath(parentNode.getPath()));
            firePropertyChange();
        }
    }

    private String getDefaultScriptTemplate() {
        return "// BeanShell Script Participant\n" +
               "// Available variables:\n" +
               "//   id      - Transaction ID (long)\n" +
               "//   ctx     - Transaction Context\n" +
               "//   request - ISOMsg request message\n" +
               "//   source  - ISOSource for sending responses\n" +
               "//   log     - Logger\n" +
               "//   cfg     - Configuration\n" +
               "//   phase   - Current phase: \"prepare\", \"commit\", or \"abort\"\n" +
               "//\n" +
               "// Set these to control behavior:\n" +
               "//   result      - Return value (PREPARED, ABORTED, NO_JOIN, READONLY)\n" +
               "//   destination - Group name to route to (for selectors)\n" +
               "\n" +
               "import org.jpos.iso.ISOMsg;\n" +
               "import org.jpos.iso.ISOSource;\n" +
               "\n" +
               "if (phase.equals(\"prepare\")) {\n" +
               "    // Get the request message\n" +
               "    ISOMsg msg = (ISOMsg) ctx.get(\"REQUEST\");\n" +
               "    log.info(\"Processing message: MTI=\" + msg.getMTI());\n" +
               "    \n" +
               "    // Create response\n" +
               "    ISOMsg response = (ISOMsg) msg.clone();\n" +
               "    response.setResponseMTI();\n" +
               "    response.set(39, \"00\"); // Approved\n" +
               "    ctx.put(\"RESPONSE\", response);\n" +
               "    \n" +
               "    result = PREPARED;\n" +
               "}\n" +
               "\n" +
               "if (phase.equals(\"commit\")) {\n" +
               "    // Send the response\n" +
               "    ISOMsg response = (ISOMsg) ctx.get(\"RESPONSE\");\n" +
               "    ISOSource src = (ISOSource) ctx.get(\"SOURCE\");\n" +
               "    if (response != null && src != null) {\n" +
               "        src.send(response);\n" +
               "        log.info(\"Response sent: MTI=\" + response.getMTI());\n" +
               "    }\n" +
               "}\n";
    }

    private void addProperty() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) return;

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        TreeNodeData selectedData = (TreeNodeData) selectedNode.getUserObject();
        if (selectedData.type != TreeNodeData.Type.PARTICIPANT && selectedData.type != TreeNodeData.Type.SCRIPT) return;

        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(20);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Value:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(valueField, gbc);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Add Property",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            TreeNodeData data = new TreeNodeData(nextNodeId++, nameField.getText().trim(), TreeNodeData.Type.PROPERTY);
            data.propValue = valueField.getText().trim();
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(data);

            selectedNode.add(newNode);
            treeModel.reload(selectedNode);
            tree.expandPath(new TreePath(selectedNode.getPath()));
            firePropertyChange();
        }
    }

    private void removeNode() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) return;

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        TreeNodeData data = (TreeNodeData) selectedNode.getUserObject();
        if (data.type == TreeNodeData.Type.ROOT) return;

        int confirm = JOptionPane.showConfirmDialog(panel,
                "Are you sure you want to remove this node and all its children?",
                "Confirm Remove", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selectedNode.getParent();
            selectedNode.removeFromParent();
            treeModel.reload(parent);
            firePropertyChange();
        }
    }

    private void editNode() {
        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) return;

        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
        TreeNodeData data = (TreeNodeData) selectedNode.getUserObject();

        switch (data.type) {
            case GROUP:
                editGroup(selectedNode, data);
                break;
            case PARTICIPANT:
                editParticipant(selectedNode, data);
                break;
            case SCRIPT:
                editScript(selectedNode, data);
                break;
            case PROPERTY:
                editProperty(selectedNode, data);
                break;
            default:
                break;
        }
    }

    private void editGroup(DefaultMutableTreeNode node, TreeNodeData data) {
        String newName = JOptionPane.showInputDialog(panel, "Enter group name:", data.name);
        if (newName != null && !newName.trim().isEmpty()) {
            data.name = newName.trim();
            treeModel.nodeChanged(node);
            firePropertyChange();
        }
    }

    private void editParticipant(DefaultMutableTreeNode node, TreeNodeData data) {
        JTextField classField = new JTextField(data.name, 30);
        JTextField loggerField = new JTextField(data.logger, 10);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Class:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(classField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Logger:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(loggerField, gbc);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Edit Participant",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !classField.getText().trim().isEmpty()) {
            data.name = classField.getText().trim();
            data.logger = loggerField.getText().trim();
            treeModel.nodeChanged(node);
            firePropertyChange();
        }
    }

    private void editScript(DefaultMutableTreeNode node, TreeNodeData data) {
        JTextField nameField = new JTextField(data.name, 20);
        JTextField loggerField = new JTextField(data.logger, 10);
        JTextArea scriptArea = new JTextArea(20, 60);
        scriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        scriptArea.setTabSize(4);
        scriptArea.setText(data.scriptContent.isEmpty() ? getDefaultScriptTemplate() : data.scriptContent);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));

        // Top panel for name and logger
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Script Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        topPanel.add(nameField, gbc);

        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("  Logger:"), gbc);
        gbc.gridx = 3;
        topPanel.add(loggerField, gbc);

        inputPanel.add(topPanel, BorderLayout.NORTH);

        // Script editor
        JScrollPane scrollPane = new JScrollPane(scriptArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("BeanShell Script"));
        inputPanel.add(scrollPane, BorderLayout.CENTER);

        // Help text
        JLabel helpLabel = new JLabel("<html><i>Available: id, ctx, request, source, log, cfg, result, destination, PREPARED, ABORTED</i></html>");
        inputPanel.add(helpLabel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Edit BeanShell Script",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            data.name = nameField.getText().trim();
            data.logger = loggerField.getText().trim();
            data.scriptContent = scriptArea.getText();
            treeModel.nodeChanged(node);
            firePropertyChange();
        }
    }

    private void editProperty(DefaultMutableTreeNode node, TreeNodeData data) {
        JTextField nameField = new JTextField(data.name, 20);
        JTextField valueField = new JTextField(data.propValue, 20);

        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Value:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        inputPanel.add(valueField, gbc);

        int result = JOptionPane.showConfirmDialog(panel, inputPanel, "Edit Property",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && !nameField.getText().trim().isEmpty()) {
            data.name = nameField.getText().trim();
            data.propValue = valueField.getText().trim();
            treeModel.nodeChanged(node);
            firePropertyChange();
        }
    }

    // ========== PropertyEditor methods ==========

    @Override
    public Component getCustomEditor() {
        return panel;
    }

    @Override
    public boolean supportsCustomEditor() {
        return true;
    }

    @Override
    public Object getValue() {
        // Serialize tree to Collection<TxnMgrTreeNode>
        List<TxnMgrTreeNode> nodes = new ArrayList<>();
        serializeNode(rootNode, null, nodes);
        return nodes;
    }

    private void serializeNode(DefaultMutableTreeNode treeNode, Integer parentId, List<TxnMgrTreeNode> nodes) {
        TreeNodeData data = (TreeNodeData) treeNode.getUserObject();

        // Skip root node itself but process its children
        if (data.type != TreeNodeData.Type.ROOT) {
            TxnMgrTreeNode node = new TxnMgrTreeNode();
            node.setNodeId(data.nodeId);
            node.setParentId(parentId);

            switch (data.type) {
                case GROUP:
                    node.setNodeType(TxnMgrTreeNode.NodeType.GROUP);
                    node.setName(data.name);
                    break;
                case PARTICIPANT:
                    node.setNodeType(TxnMgrTreeNode.NodeType.PARTICIPANT);
                    node.setClassName(data.name);
                    node.setLogger(data.logger);
                    break;
                case SCRIPT:
                    node.setNodeType(TxnMgrTreeNode.NodeType.SCRIPT);
                    node.setScriptName(data.name);
                    node.setScriptContent(data.scriptContent);
                    node.setLogger(data.logger);
                    break;
                case PROPERTY:
                    node.setNodeType(TxnMgrTreeNode.NodeType.PROPERTY);
                    node.setName(data.name);
                    node.setPropValue(data.propValue);
                    break;
                default:
                    break;
            }

            nodes.add(node);
            parentId = data.nodeId;
        }

        // Serialize children
        for (int i = 0; i < treeNode.getChildCount(); i++) {
            serializeNode((DefaultMutableTreeNode) treeNode.getChildAt(i), parentId, nodes);
        }
    }

    @Override
    public void setValue(Object value) {
        // Clear tree
        rootNode.removeAllChildren();
        nextNodeId = 0;

        if (value == null) {
            log.info("TxnMgrTreeEditor.setValue: value is null");
            treeModel.reload();
            return;
        }

        log.info("TxnMgrTreeEditor.setValue called with type: {}", value.getClass().getName());

        // Deserialize Collection<TxnMgrTreeNode> to tree
        Collection<?> nodes;
        if (value instanceof Collection) {
            nodes = (Collection<?>) value;
            log.info("TxnMgrTreeEditor.setValue: Collection with {} items", ((Collection<?>) value).size());
        } else if (value instanceof CollectionProperty) {
            List<Object> nodeList = new ArrayList<>();
            CollectionProperty cp = (CollectionProperty) value;
            log.info("TxnMgrTreeEditor.setValue: CollectionProperty with {} items", cp.size());
            cp.iterator().forEachRemaining(p -> {
                log.info("TxnMgrTreeEditor.setValue: CollectionProperty item - JMeterProperty type: {}", p.getClass().getName());
                Object obj = p.getObjectValue();
                log.info("TxnMgrTreeEditor.setValue: CollectionProperty item objectValue type: {}", obj != null ? obj.getClass().getName() : "null");
                if (obj instanceof TxnMgrTreeNode) {
                    TxnMgrTreeNode node = (TxnMgrTreeNode) obj;
                    log.info("TxnMgrTreeEditor.setValue: TxnMgrTreeNode - nodeId={}, nodeType={}, name={}, className={}",
                        node.getNodeId(), node.getNodeType(), node.getName(), node.getClassName());
                }
                nodeList.add(obj);
            });
            nodes = nodeList;
        } else {
            log.info("TxnMgrTreeEditor.setValue: Unsupported value type: {}", value.getClass().getName());
            nodes = null;
        }

        if (nodes == null || nodes.isEmpty()) {
            log.info("TxnMgrTreeEditor.setValue: nodes is null or empty, reloading tree");
            treeModel.reload();
            return;
        }

        log.info("TxnMgrTreeEditor.setValue: Processing {} nodes", nodes.size());

        // Build node map for parent lookups
        Map<Integer, DefaultMutableTreeNode> nodeMap = new HashMap<>();
        List<TxnMgrTreeNode> sortedNodes = new ArrayList<>();

        for (Object obj : nodes) {
            log.info("TxnMgrTreeEditor.setValue: Processing node of type: {}", obj != null ? obj.getClass().getName() : "null");
            if (obj instanceof TxnMgrTreeNode) {
                TxnMgrTreeNode node = (TxnMgrTreeNode) obj;
                log.info("TxnMgrTreeEditor.setValue: Adding TxnMgrTreeNode - nodeId={}, nodeType={}, name={}, className={}",
                    node.getNodeId(), node.getNodeType(), node.getName(), node.getClassName());
                sortedNodes.add(node);
            } else if (obj instanceof org.apache.jmeter.testelement.property.TestElementProperty) {
                // Handle wrapped TestElement
                Object element = ((org.apache.jmeter.testelement.property.TestElementProperty) obj).getObjectValue();
                log.info("TxnMgrTreeEditor.setValue: TestElementProperty wrapping: {}", element != null ? element.getClass().getName() : "null");
                if (element instanceof TxnMgrTreeNode) {
                    sortedNodes.add((TxnMgrTreeNode) element);
                }
            } else {
                log.info("TxnMgrTreeEditor.setValue: Skipping unknown node type: {}", obj != null ? obj.getClass().getName() : "null");
            }
        }

        // Sort to process parents before children
        sortedNodes.sort(Comparator.comparingInt(TxnMgrTreeNode::getNodeId));

        log.info("TxnMgrTreeEditor.setValue: Sorted {} TxnMgrTreeNodes for processing", sortedNodes.size());

        for (TxnMgrTreeNode txnNode : sortedNodes) {
            log.info("TxnMgrTreeEditor.setValue: Building tree node - nodeId={}, parentId={}, nodeType={}",
                txnNode.getNodeId(), txnNode.getParentId(), txnNode.getNodeType());
            TreeNodeData data = new TreeNodeData(txnNode.getNodeId(), "", TreeNodeData.Type.ROOT);

            TxnMgrTreeNode.NodeType nodeType = txnNode.getNodeType();
            if (nodeType == null) continue;

            switch (nodeType) {
                case GROUP:
                    data.type = TreeNodeData.Type.GROUP;
                    data.name = txnNode.getName();
                    break;
                case PARTICIPANT:
                    data.type = TreeNodeData.Type.PARTICIPANT;
                    data.name = txnNode.getClassName();
                    data.logger = txnNode.getLogger();
                    break;
                case SCRIPT:
                    data.type = TreeNodeData.Type.SCRIPT;
                    data.name = txnNode.getScriptName();
                    data.scriptContent = txnNode.getScriptContent();
                    data.logger = txnNode.getLogger();
                    break;
                case PROPERTY:
                    data.type = TreeNodeData.Type.PROPERTY;
                    data.name = txnNode.getName();
                    data.propValue = txnNode.getPropValue();
                    break;
            }

            DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(data);
            nodeMap.put(txnNode.getNodeId(), treeNode);

            // Update max node ID
            if (txnNode.getNodeId() >= nextNodeId) {
                nextNodeId = txnNode.getNodeId() + 1;
            }

            // Find parent
            Integer parentId = txnNode.getParentId();
            DefaultMutableTreeNode parentNode = (parentId != null) ? nodeMap.get(parentId) : null;
            if (parentNode == null) {
                parentNode = rootNode;
            }

            parentNode.add(treeNode);
        }

        log.info("TxnMgrTreeEditor.setValue: Tree building complete, rootNode has {} children", rootNode.getChildCount());
        treeModel.reload();
        expandAllNodes();
    }

    private void expandAllNodes() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    @Override
    public String getAsText() {
        return null; // Use custom editor
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        // Not used
    }

    // ========== TestBeanPropertyEditor methods ==========

    @Override
    public void setDescriptor(java.beans.PropertyDescriptor descriptor) {
        // Not used
    }

    // ========== Internal data classes ==========

    /**
     * Internal data class for tree nodes.
     */
    static class TreeNodeData {
        enum Type { ROOT, GROUP, PARTICIPANT, SCRIPT, PROPERTY }

        int nodeId;
        String name;
        Type type;
        String logger;        // For PARTICIPANT, SCRIPT
        String propValue;     // For PROPERTY
        String scriptContent; // For SCRIPT

        TreeNodeData(int nodeId, String name, Type type) {
            this.nodeId = nodeId;
            this.name = name;
            this.type = type;
            this.logger = "";
            this.propValue = "";
            this.scriptContent = "";
        }

        @Override
        public String toString() {
            switch (type) {
                case ROOT:
                    return name;
                case GROUP:
                    return "[G] " + name;
                case PARTICIPANT:
                    return "[P] " + name + (logger.isEmpty() ? "" : " (logger: " + logger + ")");
                case SCRIPT:
                    String scriptName = name.isEmpty() ? "BeanShell Script" : name;
                    return "[S] " + scriptName + (logger.isEmpty() ? "" : " (logger: " + logger + ")");
                case PROPERTY:
                    return name + " = " + propValue;
                default:
                    return name;
            }
        }
    }

    /**
     * Custom cell renderer for tree nodes.
     */
    static class TxnMgrTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();
                if (userObject instanceof TreeNodeData) {
                    TreeNodeData data = (TreeNodeData) userObject;
                    setText(data.toString());

                    // Could set different icons based on type here
                    switch (data.type) {
                        case ROOT:
                            setIcon(UIManager.getIcon("FileView.computerIcon"));
                            break;
                        case GROUP:
                            setIcon(UIManager.getIcon("FileView.directoryIcon"));
                            break;
                        case PARTICIPANT:
                            setIcon(UIManager.getIcon("FileView.fileIcon"));
                            break;
                        case SCRIPT:
                            setIcon(UIManager.getIcon("Tree.leafIcon"));
                            break;
                        case PROPERTY:
                            setIcon(null);
                            break;
                    }
                }
            }

            return this;
        }
    }
}
