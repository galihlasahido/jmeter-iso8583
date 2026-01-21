package nz.co.breakpoint.jmeter.iso8583.participants;

import bsh.BshClassManager;
import bsh.EvalError;
import bsh.Interpreter;
import bsh.UtilEvalError;
import java.io.Serializable;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.transaction.Context;
import org.jpos.transaction.GroupSelector;
import org.jpos.transaction.TransactionParticipant;
import org.jpos.util.Log;
import org.jpos.util.Logger;
import org.jpos.util.LogSource;

/**
 * BeanShell script participant for the TransactionManager.
 *
 * <p>Allows writing custom transaction processing logic using BeanShell scripts.
 * The script has access to the transaction context and can process ISO messages.</p>
 *
 * <h3>Available Variables in Script:</h3>
 * <ul>
 *   <li><b>id</b> - Transaction ID (long)</li>
 *   <li><b>ctx</b> - Transaction Context</li>
 *   <li><b>request</b> - The ISO request message (ISOMsg)</li>
 *   <li><b>source</b> - The ISO source for sending responses (ISOSource)</li>
 *   <li><b>cfg</b> - Configuration object</li>
 *   <li><b>log</b> - Logger for logging messages</li>
 *   <li><b>result</b> - Set this to control the return value (PREPARED, ABORTED, etc.)</li>
 *   <li><b>destination</b> - Set this to route to a group (for GroupSelector)</li>
 * </ul>
 *
 * <h3>Return Values:</h3>
 * <ul>
 *   <li><b>PREPARED</b> (1) - Continue with transaction</li>
 *   <li><b>ABORTED</b> (0) - Abort transaction</li>
 *   <li><b>NO_JOIN</b> (64) - Don't call commit/abort</li>
 *   <li><b>READONLY</b> (32) - Read-only participant</li>
 * </ul>
 *
 * <h3>Example Script:</h3>
 * <pre>{@code
 * // Get the request message
 * ISOMsg msg = (ISOMsg) ctx.get("REQUEST");
 * String mti = msg.getMTI();
 * log.info("Processing MTI: " + mti);
 *
 * // Create response
 * ISOMsg response = (ISOMsg) msg.clone();
 * response.setResponseMTI();
 * response.set(39, "00");
 * ctx.put("RESPONSE", response);
 *
 * // Send response
 * ISOSource src = (ISOSource) ctx.get("SOURCE");
 * src.send(response);
 *
 * result = PREPARED;
 * }</pre>
 *
 * <h3>Configuration Properties:</h3>
 * <ul>
 *   <li><b>source</b> - BeanShell script code (inline)</li>
 *   <li><b>file</b> - Path to external script file (optional)</li>
 * </ul>
 */
public class BeanShellParticipant implements TransactionParticipant, GroupSelector, Configurable, LogSource {

    protected Configuration cfg;
    protected Log log;
    protected String script;
    protected String scriptFile;

    // Context keys
    public static final String REQUEST = "REQUEST";
    public static final String SOURCE = "SOURCE";
    public static final String RESPONSE = "RESPONSE";
    public static final String DESTINATION = "DESTINATION";

    @Override
    public void setConfiguration(Configuration cfg) throws ConfigurationException {
        this.cfg = cfg;
        this.script = cfg.get("source", "");
        this.scriptFile = cfg.get("file", "");

        if (script.isEmpty() && scriptFile.isEmpty()) {
            throw new ConfigurationException("Either 'source' or 'file' property is required");
        }
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

    /**
     * Create and initialize a BeanShell interpreter with common variables.
     */
    protected Interpreter createInterpreter(long id, Context ctx) throws EvalError, UtilEvalError {
        Interpreter bsh = new Interpreter();
        BshClassManager bcm = bsh.getClassManager();
        bcm.setClassLoader(Thread.currentThread().getContextClassLoader());

        // Set common variables
        bsh.set("id", id);
        bsh.set("ctx", ctx);
        bsh.set("cfg", cfg);
        bsh.set("log", log);

        // Set convenience variables
        bsh.set("request", ctx.get(REQUEST));
        bsh.set("source", ctx.get(SOURCE));

        // Set return value constants
        bsh.set("PREPARED", PREPARED);
        bsh.set("ABORTED", ABORTED);
        bsh.set("NO_JOIN", NO_JOIN);
        bsh.set("READONLY", READONLY);

        // Set default result
        bsh.set("result", PREPARED);
        bsh.set("destination", null);

        return bsh;
    }

    /**
     * Execute the script and return the result.
     */
    protected int executeScript(Interpreter bsh) throws EvalError {
        // Execute inline script or file
        if (!script.isEmpty()) {
            bsh.eval(script);
        } else if (!scriptFile.isEmpty()) {
            try {
                bsh.source(scriptFile);
            } catch (java.io.IOException e) {
                throw new EvalError("Failed to load script file: " + scriptFile + " - " + e.getMessage(), null, null);
            }
        }

        // Get result
        Object resultObj = bsh.get("result");
        if (resultObj instanceof Integer) {
            return (Integer) resultObj;
        }
        return PREPARED;
    }

    @Override
    public int prepare(long id, Serializable context) {
        Context ctx = (Context) context;

        try {
            Interpreter bsh = createInterpreter(id, ctx);
            bsh.set("phase", "prepare");

            int result = executeScript(bsh);

            // Store destination if set (for GroupSelector)
            Object dest = bsh.get("destination");
            if (dest != null) {
                ctx.put(DESTINATION, dest.toString());
            }

            return result;

        } catch (Exception e) {
            log.error("Error in BeanShell prepare", e);
            return ABORTED;
        }
    }

    @Override
    public void commit(long id, Serializable context) {
        Context ctx = (Context) context;

        try {
            Interpreter bsh = createInterpreter(id, ctx);
            bsh.set("phase", "commit");

            // Check if there's a commit script section
            if (script.contains("// commit") || script.contains("//commit") ||
                script.contains("/* commit */") || script.contains("if (phase.equals(\"commit\"))")) {
                executeScript(bsh);
            }

        } catch (Exception e) {
            log.error("Error in BeanShell commit", e);
        }
    }

    @Override
    public void abort(long id, Serializable context) {
        Context ctx = (Context) context;

        try {
            Interpreter bsh = createInterpreter(id, ctx);
            bsh.set("phase", "abort");

            // Check if there's an abort script section
            if (script.contains("// abort") || script.contains("//abort") ||
                script.contains("/* abort */") || script.contains("if (phase.equals(\"abort\"))")) {
                executeScript(bsh);
            }

        } catch (Exception e) {
            log.error("Error in BeanShell abort", e);
        }
    }

    /**
     * GroupSelector implementation - returns the destination group name.
     * Clears the destination from context after returning to prevent loops.
     */
    @Override
    public String select(long id, Serializable context) {
        Context ctx = (Context) context;
        String dest = (String) ctx.get(DESTINATION);
        // Clear destination after selecting to prevent loop when next participant is also a GroupSelector
        ctx.remove(DESTINATION);
        return dest;
    }
}
