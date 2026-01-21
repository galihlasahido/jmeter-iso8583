package nz.co.breakpoint.jmeter.iso8583;

/**
 * Represents a single property (name/value pair) for a TransactionManager participant.
 * Used within TxnMgrTreeNode for storing participant properties.
 */
public class TxnMgrProperty extends KeyValueConfigItem {

    public TxnMgrProperty() {
    }

    public TxnMgrProperty(String name, String value) {
        setName(name);
        setValue(value);
    }
}
