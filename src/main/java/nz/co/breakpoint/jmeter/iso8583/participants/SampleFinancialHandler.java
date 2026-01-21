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
 * Sample Financial Transaction handler participant.
 *
 * <p>Handles financial messages (MTI 0200, 0400, etc.) like purchase, withdrawal, reversal.
 * This is a simple implementation that always approves transactions for testing purposes.</p>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>request</b> - Context key for incoming ISOMsg (default: "REQUEST")</li>
 *   <li><b>source</b> - Context key for ISOSource (default: "SOURCE")</li>
 *   <li><b>timeout</b> - Processing timeout in milliseconds (default: 60000)</li>
 *   <li><b>response-code</b> - Default response code (default: "00" = Approved)</li>
 * </ul>
 *
 * <h3>Example Configuration:</h3>
 * <pre>{@code
 * <group name="Financial">
 *     <participant class="nz.co.breakpoint.jmeter.iso8583.participants.SampleFinancialHandler" logger="Q2">
 *         <property name="request" value="REQUEST"/>
 *         <property name="source" value="SOURCE"/>
 *         <property name="timeout" value="60000"/>
 *         <property name="response-code" value="00"/>
 *     </participant>
 * </group>
 * }</pre>
 *
 * <h3>Processing Code Types (Field 3):</h3>
 * <ul>
 *   <li>00xxxx - Purchase/Sale</li>
 *   <li>01xxxx - Cash Withdrawal</li>
 *   <li>09xxxx - Purchase with Cashback</li>
 *   <li>20xxxx - Refund</li>
 *   <li>30xxxx - Balance Inquiry</li>
 * </ul>
 */
public class SampleFinancialHandler implements TransactionParticipant, Configurable, LogSource {

    protected Configuration cfg;
    protected Log log;

    // Context keys
    public static final String REQUEST = "REQUEST";
    public static final String SOURCE = "SOURCE";
    public static final String RESPONSE = "RESPONSE";

    private String requestKey = REQUEST;
    private String sourceKey = SOURCE;
    private long timeout = 60000;
    private String defaultResponseCode = "00";

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        this.requestKey = cfg.get("request", REQUEST);
        this.sourceKey = cfg.get("source", SOURCE);
        this.timeout = cfg.getLong("timeout", 60000);
        this.defaultResponseCode = cfg.get("response-code", "00");
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

            String mti = request.getMTI();
            String processingCode = request.hasField(3) ? request.getString(3) : "000000";
            String amount = request.hasField(4) ? request.getString(4) : "0";
            String pan = request.hasField(2) ? maskPan(request.getString(2)) : "N/A";

            log.info("Processing financial transaction: MTI=" + mti +
                    ", PC=" + processingCode +
                    ", Amount=" + amount +
                    ", PAN=" + pan);

            // Create response message
            ISOMsg response = (ISOMsg) request.clone();
            response.setResponseMTI();

            // Determine response code based on processing code
            String responseCode = determineResponseCode(processingCode, request);
            response.set(39, responseCode);

            // For balance inquiry, set balance fields
            if (processingCode.startsWith("30") || processingCode.startsWith("31")) {
                // Set available balance (field 54) - example value
                response.set(54, "0001840C000000010000"); // $100.00
                log.info("Balance inquiry - returning balance");
            }

            // Generate authorization code for approved transactions
            if ("00".equals(responseCode)) {
                response.set(38, generateAuthCode()); // Authorization code
            }

            log.info("Transaction result: Response Code=" + responseCode);

            // Send response in prepare phase while channel is still connected
            if (source != null) {
                log.info("Sending financial response: MTI=" + response.getMTI() +
                        ", RC=" + response.getString(39));
                source.send(response);
            } else {
                log.warn("Cannot send response: source is null");
            }

            // Store response in context
            ctx.put(RESPONSE, response);

            // Return PREPARED | NO_JOIN to skip commit/abort phases
            return PREPARED | NO_JOIN;

        } catch (Exception e) {
            log.error("Error processing financial transaction", e);
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
        log.warn("Financial transaction aborted for id=" + id);

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

    /**
     * Determine response code based on processing code and request content.
     * Override this method to implement custom authorization logic.
     */
    protected String determineResponseCode(String processingCode, ISOMsg request) {
        // For testing, always return configured default (usually "00" = Approved)
        // In production, this would connect to core banking, check limits, etc.
        return defaultResponseCode;
    }

    /**
     * Generate a 6-character authorization code.
     */
    protected String generateAuthCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * Mask PAN for logging (show first 6 and last 4 digits).
     */
    protected String maskPan(String pan) {
        if (pan == null || pan.length() < 13) {
            return "****";
        }
        return pan.substring(0, 6) + "****" + pan.substring(pan.length() - 4);
    }
}
