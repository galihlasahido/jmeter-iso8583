package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import org.apache.jmeter.testbeans.gui.TableEditor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583TransactionManager.*;

/**
 * Describes the ISO8583TransactionManager GUI.
 * Provides a tree-based UI for configuring TransactionManager participants and groups.
 */
public class ISO8583TransactionManagerBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583TransactionManagerBeanInfo() {
        super(ISO8583TransactionManager.class);

        PropertyDescriptor p;

        // ========== Transaction Manager Settings ==========
        createPropertyGroup("TransactionManager", new String[]{
            TXNMGR_NAME,
            SPACE,
            QUEUE,
            SESSIONS,
            DEBUG,
        });

        p = property(TXNMGR_NAME);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(SPACE);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "tspace:default");

        p = property(QUEUE);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "TXNQueue");

        p = property(SESSIONS);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "5");

        p = property(DEBUG);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, Boolean.FALSE);

        // ========== Tree Configuration ==========
        createPropertyGroup("Configuration", new String[]{
            TREE_CONFIG,
        });

        p = property(TREE_CONFIG);
        p.setPropertyEditorClass(TxnMgrTreeEditor.class);
        p.setValue(TableEditor.CLASSNAME, TxnMgrTreeNode.class.getName());
    }
}
