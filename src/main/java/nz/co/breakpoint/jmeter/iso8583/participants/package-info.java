/**
 * Sample TransactionParticipant implementations for jPOS TransactionManager.
 *
 * <p>These participants can be used with the ISO8583TransactionManager config element
 * to handle incoming ISO8583 requests in server mode.</p>
 *
 * <p>Usage in TransactionManager configuration:</p>
 * <pre>
 * &lt;participant class="nz.co.breakpoint.jmeter.iso8583.participants.SampleSelector" logger="Q2"&gt;
 *     &lt;property name="0800180000" value="Network"/&gt;
 *     &lt;property name="0200000000" value="Financial"/&gt;
 * &lt;/participant&gt;
 * </pre>
 */
package nz.co.breakpoint.jmeter.iso8583.participants;
