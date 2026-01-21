package nz.co.breakpoint.jmeter.iso8583;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;
import org.jpos.space.Space;
import org.jpos.space.SpaceFactory;
import org.jpos.transaction.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ISORequestListener that wraps incoming ISO8583 messages in a jPOS Context
 * and puts them into a TransactionManager queue.
 *
 * <p>This listener bridges the ISO8583 server with the TransactionManager by:
 * <ul>
 *   <li>Creating a Context object for each incoming message</li>
 *   <li>Storing the ISOMsg as "REQUEST" in the context</li>
 *   <li>Storing the ISOSource as "SOURCE" in the context</li>
 *   <li>Putting the context into the TransactionManager's input queue</li>
 * </ul>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>space</b> - Space name (default: "tspace:default")</li>
 *   <li><b>queue</b> - Queue name for the TransactionManager (required)</li>
 *   <li><b>timeout</b> - Timeout in milliseconds for space.out operation (default: 5000)</li>
 * </ul>
 *
 * <h3>Example Configuration (in ISO8583Server):</h3>
 * <pre>{@code
 * requestListener=nz.co.breakpoint.jmeter.iso8583.TxnMgrRequestListener
 * requestListener.space=tspace:default
 * requestListener.queue=txnmgr-queue
 * }</pre>
 */
public class TxnMgrRequestListener implements ISORequestListener, Configurable {

    private static final Logger log = LoggerFactory.getLogger(TxnMgrRequestListener.class);

    // Context keys (same as used by participant classes)
    public static final String REQUEST = "REQUEST";
    public static final String SOURCE = "SOURCE";

    // Configuration
    private String spaceName = "tspace:default";
    private String queueName;
    private long timeout = 5000;

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.spaceName = cfg.get("space", "tspace:default");
        this.queueName = cfg.get("queue");
        this.timeout = cfg.getLong("timeout", 5000);

        if (queueName == null || queueName.isEmpty()) {
            throw new ConfigurationException("'queue' property is required for TxnMgrRequestListener");
        }

        log.info("TxnMgrRequestListener configured: space={}, queue={}, timeout={}",
            spaceName, queueName, timeout);
    }

    @Override
    public boolean process(ISOSource source, ISOMsg message) {
        try {
            // Create a context with the request and source
            Context ctx = new Context();
            ctx.put(REQUEST, message);
            ctx.put(SOURCE, source);

            // Log the incoming message
            String mti = message.getMTI();
            log.info("Received message MTI={}, putting into queue '{}'", mti, queueName);

            // Get the space and put the context into the queue
            Space<String, Object> space = SpaceFactory.getSpace(spaceName);
            space.out(queueName, ctx, timeout);

            log.debug("Context put into queue '{}' successfully", queueName);
            return true;

        } catch (Exception e) {
            log.error("Error processing message", e);
            return false;
        }
    }
}
