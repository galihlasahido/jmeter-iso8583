# ISO8583 JMeter Plugin - Example Test Plans

This directory contains complete examples demonstrating the ISO8583 JMeter plugin.

## Quick Start - Server/Client Testing

The simplest way to test ISO8583 messaging is to run the server and client test plans:

```bash
# Terminal 1: Start the ISO8583 Server
jmeter -n -t ISO8583-Server-TestPlan.jmx

# Terminal 2: Run the Client (in another terminal)
jmeter -n -t ISO8583-ClientMode-TestPlan.jmx
```

## Files

| File | Description |
|------|-------------|
| `ISO8583-Server-TestPlan.jmx` | Standalone ISO8583 server (listens on port 10000) |
| `ISO8583-ClientMode-TestPlan.jmx` | Client test plan to test against the server |
| `ISO8583-Simple-TestPlan.jmx` | Minimal test plan for GUI customization |
| `ISO8583-Complete-TestPlan.jmx` | Full-featured client test plan |
| `ISO8583-TransactionManager-TestPlan.jmx` | Advanced server with TransactionManager |
| `server-response-handler.groovy` | Groovy script for server request handling |
| `iso8583-packager.xml` | ISO8583 field packager configuration |
| `deploy/` | Q2 deployment descriptors |

## Setup Instructions

### Step 1: Build the Plugin

```bash
cd jmeter-iso8583/
mvn clean package -DskipTests
```

### Step 2: Install Plugin to JMeter

```bash
# Copy the plugin JAR to JMeter's lib/ext
cp target/jmeter-iso8583-*.jar apache-jmeter-5.6.3/lib/ext/

# Copy jPOS dependencies (if not already present)
cp target/dependency/*.jar apache-jmeter-5.6.3/lib/ext/
```

### Step 3: Copy Example Files

```bash
# Copy packager and deploy files
cp -r examples apache-jmeter-5.6.3/
```

### Step 4: Start JMeter

```bash
cd apache-jmeter-5.6.3
./bin/jmeter.sh
```

### Step 5: Open Test Plan

1. File → Open
2. Navigate to `examples/ISO8583-TransactionManager-TestPlan.jmx`
3. Click Open

## Test Plans Overview

### ISO8583-Server-TestPlan.jmx (Standalone Server)

A simple ISO8583 server that listens on port 10000 and responds to requests using a Groovy script handler.

```
Test Plan
├── ISO8583 Server              ← Listens on port 10000
│   └── requestListener: server-response-handler.groovy
└── (No thread groups - server runs continuously)
```

**Supported message types:**
- `0800`: Network Management (Echo, Sign-On, Sign-Off)
- `0100`: Authorization Request
- `0200`: Financial Transaction (Purchase, Withdrawal, Balance)
- `0400`: Reversal

### ISO8583-ClientMode-TestPlan.jmx (Test Client)

Client test plan to send ISO8583 messages to the server.

```
Test Plan
├── ISO8583 Connection Configuration  ← Connects to localhost:10000
└── Thread Group
    ├── 0800 Echo Test               ← Network echo test
    ├── 0100 Authorization           ← Authorization request
    ├── 0200 Purchase                ← Financial purchase
    ├── 0200 Balance Inquiry         ← Balance inquiry
    └── 0400 Reversal                ← Transaction reversal
```

### ISO8583-TransactionManager-TestPlan.jmx (Advanced Server)

Advanced server using jPOS TransactionManager with Java participants.

```
Test Plan
├── ISO8583 Config              ← Configures Q2, Channel, MUX
├── ISO8583 Transaction Manager ← Handles incoming requests (server mode)
├── Thread Group
│   ├── 0800 Echo Test          ← Network management echo
│   ├── 0800 Sign On            ← Network sign-on request
│   ├── 0200 Purchase           ← Financial purchase transaction
│   └── 0200 Balance Inquiry    ← Balance inquiry request
├── View Results Tree           ← See request/response details
└── Summary Report              ← Performance summary
```

## TransactionManager Configuration

The test plan includes a pre-configured TransactionManager with:

### Selector (Routes transactions)
- `0800` → Network group
- `0800180000` → Network group
- `0200` → Financial group
- `default` → Reject group

### Groups
| Group | Handler | Description |
|-------|---------|-------------|
| Network | SampleNetworkHandler | Handles echo, sign-on/off |
| Financial | SampleFinancialHandler | Handles purchase, balance inquiry |
| Reject | SampleResponseSender | Rejects unknown messages |

## Running the Test

### Option A: GUI Mode (for development/debugging)
1. Open JMeter GUI
2. Load the test plan
3. Click the green "Start" button

### Option B: Command Line Mode (for actual testing)
```bash
./bin/jmeter -n -t examples/ISO8583-TransactionManager-TestPlan.jmx -l results.jtl
```

## Customization

### Modify Server Port
Edit variables in Test Plan:
- `HOST` - Server IP (default: 127.0.0.1)
- `PORT` - Server port (default: 10000)

### Add New Message Types
1. Right-click on Thread Group
2. Add → Sampler → ISO8583 Sampler
3. Configure fields

### Modify Transaction Routing
1. Select "ISO8583 Transaction Manager"
2. Use the tree UI to add/edit participants and groups

## Sample Participant Classes

The plugin includes these sample participants:

| Class | Purpose |
|-------|---------|
| `SampleSelector` | Routes by MTI + Processing Code |
| `SampleNetworkHandler` | Handles 0800 messages |
| `SampleFinancialHandler` | Handles 0200/0400 messages |
| `SampleResponseSender` | Sends responses back |

Full package: `nz.co.breakpoint.jmeter.iso8583.participants.*`

## Troubleshooting

### Java Version Compatibility

The Groovy script handler requires Java 11-21. If you're using Java 22+ (e.g., Java 25), you may see errors like:
```
Unsupported class file major version 69
```

**Solutions:**
1. Use Java 11-21 (recommended for production)
2. The server will still start and listen on the port, but Groovy scripts won't work
3. For Java 22+, use the TransactionManager approach with Java participants instead of Groovy

### "Class not found" errors
Ensure all JARs are in `lib/ext`:
```bash
ls apache-jmeter-5.6.3/lib/ext/*.jar
```

### Connection refused
Check that no other process is using port 10000:
```bash
lsof -i :10000
```

### Q2 not starting
Check JMeter logs for Q2 initialization errors:
```bash
tail -f apache-jmeter-5.6.3/bin/jmeter.log
```

### TransactionManager tree config not loading from JMX

If the tree configuration doesn't appear after loading a JMX file:

1. **Recommended: Use the GUI to configure**
   - Open `ISO8583-Simple-TestPlan.jmx`
   - Click on "ISO8583 Transaction Manager"
   - Use the toolbar buttons to add nodes:
     - `+ Selector` - Add a routing selector participant
     - `+ Group` - Add a named group
     - `+ Participant` - Add a participant to root or selected group
     - `+ Property` - Add a property to selected participant
   - Save the test plan
   - Reopen to verify persistence

2. **Check JMeter logs for debug output**
   Look for lines containing `TxnMgrTreeEditor.setValue` in the log file:
   ```bash
   grep TxnMgrTreeEditor apache-jmeter-5.6.3/bin/jmeter.log
   ```

3. **Verify JMX format**
   When saving via GUI, JMeter generates the correct XML format. Hand-crafted JMX files must use the exact property names:
   - `nodeType` - GROUP, PARTICIPANT, or PROPERTY
   - `nodeId` - unique integer ID
   - `parentId` - parent's nodeId (or -1 for root level)
   - `className` - for PARTICIPANT nodes
   - `logger` - for PARTICIPANT nodes
   - `propValue` - for PROPERTY nodes
   - `TestElement.name` - the name (group name, property name, etc.)
