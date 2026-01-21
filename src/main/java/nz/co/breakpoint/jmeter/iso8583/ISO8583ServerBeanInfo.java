package nz.co.breakpoint.jmeter.iso8583;

import java.beans.PropertyDescriptor;
import static nz.co.breakpoint.jmeter.iso8583.ISO8583Config.*;
import org.apache.jmeter.testbeans.gui.FileEditor;
import org.apache.jmeter.testbeans.gui.PasswordEditor;
import org.apache.jmeter.testbeans.gui.TableEditor;
import org.apache.jmeter.testbeans.gui.TypeEditor;

/**
 * Describes the ISO8583Server GUI.
 * This element acts as a Non-Test Element that starts an ISO8583 server.
 */
public class ISO8583ServerBeanInfo extends ISO8583TestElementBeanInfo {

    public ISO8583ServerBeanInfo() {
        super(ISO8583Server.class);

        PropertyDescriptor p;

        // ========== Connection Reference ==========
        createPropertyGroup("Connection", new String[]{
            CONFIGKEY,
        });

        p = property(CONFIGKEY);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        // ========== Server Settings ==========
        createPropertyGroup("Server", new String[]{
            PORT, CLASSNAME, PACKAGER, HEADER, CONNECTIONSELECTION, CHANNELCONFIG,
        });

        p = property(PORT);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "10000");

        p = property(CLASSNAME);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, getDefaultChannelClass());
        p.setValue(TAGS, getChannelClasses());

        p = property(PACKAGER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");

        p = property(HEADER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(CONNECTIONSELECTION, TypeEditor.ComboStringEditor);
        p.setValue(RESOURCE_BUNDLE, getBeanDescriptor().getValue(RESOURCE_BUNDLE));
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, getDefaultConnectionSelection());
        p.setValue(TAGS, ConnectionSelection.tags());

        p = property(CHANNELCONFIG);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, ChannelConfigItem.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults(CHANNELCONFIG +".tableHeaders",
            new String[]{"Name", "Value"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES,
            new String[]{"name", ChannelConfigItem.VALUE}); // name is a standard TestElement member

        // ========== SSL Settings ==========
        createPropertyGroup("SSL", new String[]{
             KEYSTORE, STOREPASSWORD, KEYPASSWORD,
        });

        p = property(KEYSTORE);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");

        p = property(STOREPASSWORD);
        p.setPropertyEditorClass(PasswordEditor.class);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(KEYPASSWORD);
        p.setPropertyEditorClass(PasswordEditor.class);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        // ========== Mux Settings ==========
        createPropertyGroup("Mux", new String[]{
             MTIMAPPING, MUXKEYCONFIG,
        });

        p = property(MTIMAPPING);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        p = property(MUXKEYCONFIG);
        p.setPropertyEditorClass(TableEditor.class);
        p.setValue(TableEditor.CLASSNAME, MuxKeyConfigItem.class.getName());
        p.setValue(TableEditor.HEADERS, getTableHeadersWithDefaults(MUXKEYCONFIG +".tableHeaders",
                new String[]{"MTI", "Key Fields"}));
        p.setValue(TableEditor.OBJECT_PROPERTIES,
                new String[]{"name", MuxKeyConfigItem.VALUE}); // name is a standard TestElement member

        // ========== Request Listener ==========
        createPropertyGroup("RequestListener", new String[]{
            REQUESTLISTENER,
        });

        p = property(REQUESTLISTENER);
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setPropertyEditorClass(FileEditor.class);
        p.setValue(DEFAULT, "");
    }
}
