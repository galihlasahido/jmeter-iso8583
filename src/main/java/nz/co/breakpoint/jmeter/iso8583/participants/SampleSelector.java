package nz.co.breakpoint.jmeter.iso8583.participants;

import java.io.Serializable;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.transaction.Context;
import org.jpos.transaction.GroupSelector;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Log;
import org.jpos.util.Logger;
import org.jpos.util.LogSource;

/**
 * Sample Selector participant that routes transactions based on MTI and processing code.
 *
 * <p>This participant reads the incoming ISO message, extracts the MTI (field 0) and
 * optionally the processing code (field 3), then looks up the destination group
 * from the configuration properties.</p>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>request</b> - Context key for incoming ISOMsg (default: "REQUEST")</li>
 *   <li><b>source</b> - Context key for ISOSource (default: "SOURCE")</li>
 *   <li><b>default</b> - Default destination if no match found</li>
 *   <li><b>[MTI]</b> - Maps MTI to destination group (e.g., "0800" = "Network")</li>
 *   <li><b>[MTI+ProcessingCode]</b> - Maps MTI+PC to destination (e.g., "0800180000" = "Network")</li>
 * </ul>
 *
 * <h3>Example Configuration:</h3>
 * <pre>{@code
 * <participant class="nz.co.breakpoint.jmeter.iso8583.participants.SampleSelector" logger="Q2">
 *     <property name="request" value="REQUEST"/>
 *     <property name="0800" value="Network"/>
 *     <property name="0800180000" value="Network"/>
 *     <property name="0200" value="Financial"/>
 *     <property name="default" value="Reject"/>
 * </participant>
 * }</pre>
 *
 * <h3>How Routing Works:</h3>
 * <ol>
 *   <li>Extract MTI (e.g., "0800") and Processing Code (e.g., "180000") from message</li>
 *   <li>Try to match MTI+ProcessingCode (e.g., "0800180000")</li>
 *   <li>If no match, try to match just MTI (e.g., "0800")</li>
 *   <li>If still no match, use "default" destination</li>
 *   <li>Set the destination in context as "DESTINATION" for TransactionManager</li>
 * </ol>
 */
public class SampleSelector implements TransactionParticipant, GroupSelector, Configurable, LogSource {

    protected Configuration cfg;
    protected Log log;

    // Context keys
    public static final String REQUEST = "REQUEST";
    public static final String SOURCE = "SOURCE";
    public static final String DESTINATION = "DESTINATION";

    private String requestKey = REQUEST;
    private String sourceKey = SOURCE;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        this.requestKey = cfg.get("request", REQUEST);
        this.sourceKey = cfg.get("source", SOURCE);
    }

    @Override
    public void setLogger(Logger logger, String realm) {
        this.log = new Log(logger, realm);
    }

    @Override
    public Logger getLogger() {
        return log != null ? log.getLogger() : null;
    }

    @Override
    public String getRealm() {
        return log != null ? log.getRealm() : null;
    }

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;

        try {
            ISOMsg msg = (ISOMsg) ctx.get(requestKey);
            if (msg == null) {
                log.warn("No ISO message found in context with key: " + requestKey);
                return ABORTED;
            }

            String mti = msg.getMTI();
            String processingCode = msg.hasField(3) ? msg.getString(3) : "";
            String fullKey = mti + processingCode;

            // Try full key first (MTI + Processing Code)
            String destination = cfg.get(fullKey, null);

            // Fall back to just MTI
            if (destination == null) {
                destination = cfg.get(mti, null);
            }

            // Fall back to default
            if (destination == null) {
                destination = cfg.get("default", null);
            }

            if (destination == null) {
                log.warn("No destination found for MTI=" + mti + ", PC=" + processingCode);
                return ABORTED;
            }

            log.info("Routing MTI=" + mti + " PC=" + processingCode + " -> " + destination);
            ctx.put(DESTINATION, destination);

            return PREPARED;

        } catch (Exception e) {
            log.error("Error in selector", e);
            return ABORTED;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        // Nothing to commit
    }

    @Override
    public void abort(long id, Serializable context) {
        // Nothing to abort
    }

    /**
     * GroupSelector implementation - returns the destination group name.
     * This is called by TransactionManager after prepare() to determine
     * which group(s) to execute next.
     * Clears the destination from context after returning to prevent loops.
     */
    @Override
    public String select(long id, Serializable context) {
        Context ctx = (Context) context;
        String destination = (String) ctx.get(DESTINATION);
        // Clear destination after selecting to prevent loop when next participant is also a GroupSelector
        ctx.remove(DESTINATION);
        log.info("select() returning destination: " + destination);
        return destination;
    }
}
