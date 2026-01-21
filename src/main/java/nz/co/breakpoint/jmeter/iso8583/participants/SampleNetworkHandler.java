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
 * Sample Network Management handler participant.
 *
 * <p>Handles network management messages (MTI 0800) like echo/sign-on/sign-off.
 * Creates appropriate response and sends it back to the source.</p>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>request</b> - Context key for incoming ISOMsg (default: "REQUEST")</li>
 *   <li><b>source</b> - Context key for ISOSource (default: "SOURCE")</li>
 *   <li><b>timeout</b> - Response timeout in milliseconds (default: 30000)</li>
 * </ul>
 *
 * <h3>Example Configuration:</h3>
 * <pre>{@code
 * <group name="Network">
 *     <participant class="nz.co.breakpoint.jmeter.iso8583.participants.SampleNetworkHandler" logger="Q2">
 *         <property name="request" value="REQUEST"/>
 *         <property name="source" value="SOURCE"/>
 *         <property name="timeout" value="30000"/>
 *     </participant>
 * </group>
 * }</pre>
 *
 * <h3>Supported Network Messages:</h3>
 * <ul>
 *   <li>Echo Test (Processing Code 180000) - Returns same message with response MTI</li>
 *   <li>Sign-On (Processing Code 001) - Returns approval</li>
 *   <li>Sign-Off (Processing Code 002) - Returns approval</li>
 * </ul>
 */
public class SampleNetworkHandler implements TransactionParticipant, Configurable, LogSource {

    protected Configuration cfg;
    protected Log log;

    // Context keys
    public static final String REQUEST = "REQUEST";
    public static final String SOURCE = "SOURCE";
    public static final String RESPONSE = "RESPONSE";

    private String requestKey = REQUEST;
    private String sourceKey = SOURCE;
    private long timeout = 30000;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        this.requestKey = cfg.get("request", REQUEST);
        this.sourceKey = cfg.get("source", SOURCE);
        this.timeout = cfg.getLong("timeout", 30000);
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
            ISOMsg request = (ISOMsg) ctx.get(requestKey);
            ISOSource source = (ISOSource) ctx.get(sourceKey);

            if (request == null) {
                log.warn("No request message found in context");
                return ABORTED;
            }

            // Create response message
            ISOMsg response = (ISOMsg) request.clone();
            response.setResponseMTI();

            // Set response code (field 39) - 00 = Approved
            response.set(39, "00");

            String mti = request.getMTI();
            String processingCode = request.hasField(3) ? request.getString(3) : "";

            log.info("Processing network message: MTI=" + mti + ", PC=" + processingCode);

            // Handle different network message types
            if ("180000".equals(processingCode) || processingCode.startsWith("18")) {
                // Echo test - just return the message
                log.info("Echo test - returning response");
            } else if ("001".equals(processingCode) || "920000".equals(processingCode)) {
                // Sign-on
                log.info("Sign-on request - approved");
            } else if ("002".equals(processingCode) || "920001".equals(processingCode)) {
                // Sign-off
                log.info("Sign-off request - approved");
            } else {
                // Unknown network message - still approve
                log.info("Unknown network message type - approved");
            }

            // Send response in prepare phase while channel is still connected
            if (source != null) {
                log.info("Sending response: MTI=" + response.getMTI());
                source.send(response);
            } else {
                log.warn("Cannot send response: source is null");
            }

            // Store response in context
            ctx.put(RESPONSE, response);

            // Return PREPARED | NO_JOIN to skip commit/abort phases
            return PREPARED | NO_JOIN;

        } catch (Exception e) {
            log.error("Error processing network message", e);
            return ABORTED;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        // Response already sent in prepare phase
    }

    @Override
    public void abort(long id, Serializable context) {
        Context ctx = (Context) context;
        log.warn("Transaction aborted for id=" + id);

        // Optionally send error response
        try {
            ISOSource source = (ISOSource) ctx.get(sourceKey);
            ISOMsg request = (ISOMsg) ctx.get(requestKey);

            if (source != null && request != null) {
                ISOMsg response = (ISOMsg) request.clone();
                response.setResponseMTI();
                response.set(39, "96"); // System malfunction
                source.send(response);
            }
        } catch (Exception e) {
            log.error("Error sending abort response", e);
        }
    }
}
