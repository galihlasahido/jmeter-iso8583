package nz.co.breakpoint.jmeter.iso8583.participants;

import java.io.Serializable;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.transaction.Context;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Log;
import org.jpos.util.Logger;
import org.jpos.util.LogSource;

/**
 * Simple response sender participant.
 *
 * <p>Sends the response message from context back to the client.
 * This participant is typically placed at the end of a processing group.</p>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>source</b> - Context key for ISOSource (default: "SOURCE")</li>
 *   <li><b>response</b> - Context key for response ISOMsg (default: "RESPONSE")</li>
 * </ul>
 *
 * <h3>Example Configuration:</h3>
 * <pre>{@code
 * <group name="Financial">
 *     <participant class="...SomeProcessor" logger="Q2"/>
 *     <participant class="nz.co.breakpoint.jmeter.iso8583.participants.SampleResponseSender" logger="Q2">
 *         <property name="source" value="SOURCE"/>
 *         <property name="response" value="RESPONSE"/>
 *     </participant>
 * </group>
 * }</pre>
 */
public class SampleResponseSender implements TransactionParticipant, Configurable, LogSource {

    protected Configuration cfg;
    protected Log log;

    public static final String SOURCE = "SOURCE";
    public static final String RESPONSE = "RESPONSE";

    private String sourceKey = SOURCE;
    private String responseKey = RESPONSE;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        this.sourceKey = cfg.get("source", SOURCE);
        this.responseKey = cfg.get("response", RESPONSE);
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
        ISOSource source = (ISOSource) ctx.get(sourceKey);
        ISOMsg response = (ISOMsg) ctx.get(responseKey);

        if (source == null) {
            log.warn("No ISOSource found in context with key: " + sourceKey);
            return ABORTED;
        }
        if (response == null) {
            log.warn("No response message found in context with key: " + responseKey);
            return ABORTED;
        }

        // Send response in prepare phase while channel is still connected
        try {
            log.info("Sending response: MTI=" + response.getMTI());
            source.send(response);
            log.debug("Response sent successfully");
        } catch (Exception e) {
            log.error("Error sending response", e);
            return ABORTED;
        }

        // Return PREPARED | NO_JOIN to skip commit/abort phases
        return PREPARED | NO_JOIN;
    }

    @Override
    public void commit(long id, Serializable context) {
        // Response already sent in prepare phase
    }

    @Override
    public void abort(long id, Serializable context) {
        log.warn("Response sending aborted for transaction id=" + id);
    }
}
