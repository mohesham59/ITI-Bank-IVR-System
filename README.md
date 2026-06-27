# ITI Bank IVR System — Complete Lab Documentation

> A full walkthrough of building a Banking IVR (Interactive Voice Response) system using **Asterisk 20**, **Java (FastAGI)**, and **PostgreSQL** on Ubuntu 24.04.

---

## Table of Contents

1. [What Did We Build?](#what-did-we-build)
2. [How Does It All Work Together?](#how-does-it-all-work-together)
3. [Technology Stack](#technology-stack)
4. [System Architecture](#system-architecture)
5. [Step-by-Step Breakdown](#step-by-step-breakdown)
   - [Part 1 — Installing Asterisk](#part-1--installing-asterisk)
   - [Part 2 — Firewall Configuration](#part-2--firewall-configuration)
   - [Part 3 — Configuring Asterisk (PJSIP + Dialplan + AMI)](#part-3--configuring-asterisk-pjsip--dialplan--ami)
   - [Part 4 — PostgreSQL Database](#part-4--postgresql-database)
   - [Part 5 — Java Maven Project](#part-5--java-maven-project)
   - [Part 6 — Text-to-Speech Sound Files](#part-6--text-to-speech-sound-files)
   - [Part 7 — Running and Testing](#part-7--running-and-testing)
6. [Key Concepts Explained](#key-concepts-explained)
7. [Common Errors and Fixes](#common-errors-and-fixes)
8. [Port Reference](#port-reference)
9. [Project Structure](#project-structure)

---

## What Did We Build?

We built a **Banking IVR system** — exactly like what you hear when you call your bank:

> *"Welcome to ITI Bank. Press 1 for your account balance. Press 2 for money transfer. Press 3 for customer support."*

When you press a number on your phone, the system:
- Understands your input (digit press)
- Queries a real PostgreSQL database for your account balance
- Speaks the result back to you through your phone

The phone used here is a SIP softphone app called **Zoiper**, which connects over WiFi to the Asterisk server running on Ubuntu.

---

## How Does It All Work Together?

Here is the full call flow, step by step:

```
[Your Phone - Zoiper]
        |
        | (1) You dial extension 8000 over SIP protocol
        v
[Asterisk PBX Server]
        |
        | (2) Asterisk checks extensions.conf and sees:
        |     exten => 8000 → AGI(agi://127.0.0.1:4573/myivr)
        |
        | (3) Asterisk connects to the Java AGI Server on port 4573
        v
[Java FastAGI Server - App.java]
        |
        | (4) The AGI server receives the call and routes it to MyIvrScript.java
        v
[MyIvrScript.java]
        |
        | (5) Plays welcome message (iti-welcome.gsm)
        | (6) Plays menu options (press 1, press 2, press 3)
        | (7) Waits for digit input from the caller
        |
        | (8) If digit == '1':
        v
[DBHelper.java]
        |
        | (9) Connects to PostgreSQL
        | (10) SELECT balance FROM accounts WHERE phone = '1001'
        | (11) Returns: 5000.00
        v
[MyIvrScript.java]
        |
        | (12) Speaks the balance digit by digit using Asterisk sound files
        | (13) Plays goodbye message
        | (14) Hangs up
        v
[Call Ends]
```

---

## Technology Stack

| Technology | Role | Why We Use It |
|---|---|---|
| **Asterisk 20** | PBX (Phone system) | Handles SIP calls, routing, and audio playback |
| **PJSIP** | SIP channel driver | Modern replacement for the deprecated chan_sip |
| **Java 11** | Business logic | Runs the IVR script and DB queries via FastAGI |
| **asterisk-java** | Java library | Bridges Java and Asterisk over the AGI protocol |
| **PostgreSQL** | Database | Stores account information and balances |
| **Maven** | Build tool | Manages Java dependencies and compiles the project |
| **Festival TTS** | Text-to-Speech | Converts text like "Welcome to ITI Bank" into audio files |
| **SoX** | Audio processing | Converts audio files to 8000 Hz GSM format (required by Asterisk) |
| **Zoiper** | SIP softphone | The "phone" app on your mobile used to make calls |
| **UFW** | Firewall | Controls which ports are open on the Ubuntu machine |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Ubuntu 24.04                          │
│                                                             │
│  ┌──────────────┐    AGI Protocol    ┌──────────────────┐  │
│  │   Asterisk   │◄──────────────────►│  Java AGI Server │  │
│  │   (Port 5060)│    Port 4573       │  (App.java)      │  │
│  │   PJSIP      │                    │  MyIvrScript.java│  │
│  │   Dialplan   │                    │  DBHelper.java   │  │
│  └──────┬───────┘                    └────────┬─────────┘  │
│         │                                     │            │
│         │ SIP/RTP                             │ JDBC       │
│         │                             ┌───────▼────────┐  │
│         │                             │   PostgreSQL   │  │
│         │                             │   iti_bank DB  │  │
│         │                             │   accounts     │  │
│         │                             └────────────────┘  │
└─────────┼───────────────────────────────────────────────────┘
          │
          │ WiFi (192.168.1.10:5060)
          │
   ┌──────▼──────┐
   │   Zoiper    │
   │  (Mobile)   │
   │  ext: 1001  │
   └─────────────┘
```

---

## Step-by-Step Breakdown

### Part 1 — Installing Asterisk

#### What is Asterisk?

Asterisk is an open-source **PBX (Private Branch Exchange)** — essentially a software phone system. It is the same technology used by real call centers and banks. It handles:
- Registering SIP phones (like Zoiper)
- Routing calls to extensions (like 8000, 9999)
- Playing audio files to callers
- Running external scripts (our Java IVR)

#### Why compile from source?

The Ubuntu package manager (`apt`) ships an older version of Asterisk. We need **Asterisk 20 (LTS)** for modern PJSIP support, so we download and compile it ourselves.

```bash
# Download the source code tarball
cd /usr/src
sudo wget https://downloads.asterisk.org/pub/telephony/asterisk/asterisk-20-current.tar.gz
sudo tar -xzf asterisk-20-current.tar.gz
cd asterisk-20*/

# Install build dependencies automatically
sudo contrib/scripts/install_prereq install

# Configure the build (detects your system capabilities)
sudo ./configure

# Compile using all available CPU cores (faster)
sudo make -j$(nproc)

# Install the compiled binaries
sudo make install

# Install sample configuration files
sudo make samples

# Set up init scripts (so Asterisk can run as a service)
sudo make config
sudo ldconfig
```

#### Why `make -j$(nproc)`?

`$(nproc)` returns the number of CPU cores on your machine. The `-j` flag tells `make` to use that many parallel jobs, making compilation much faster.

---

### Part 2 — Firewall Configuration

We use **UFW (Uncomplicated Firewall)** to open only the ports we need.

```bash
sudo ufw allow 22/tcp        # SSH - remote terminal access
sudo ufw allow 5060/udp      # SIP signaling (UDP - default for VoIP)
sudo ufw allow 5060/tcp      # SIP signaling (TCP - fallback)
sudo ufw allow 10000:20000/udp  # RTP - the actual audio stream
sudo ufw allow 5038/tcp      # AMI - Asterisk Manager Interface
sudo ufw allow 4573/tcp      # FastAGI - our Java IVR server
sudo ufw enable
```

#### Why is RTP on ports 10000–20000?

SIP (port 5060) only handles **signaling** — things like "start call", "end call", "which codec to use". The actual **audio** travels separately over **RTP (Real-time Transport Protocol)**. Asterisk dynamically picks a port in the 10000–20000 range for each call's audio stream. If these ports are blocked, you will have a one-way call or no audio at all.

---

### Part 3 — Configuring Asterisk (PJSIP + Dialplan + AMI)

#### Why PJSIP instead of SIP?

Asterisk 20 removed the old `chan_sip` module. The modern replacement is **PJSIP**, which is more standards-compliant and supports features like WebRTC. The configuration structure is different but the concept is the same.

#### pjsip.conf — Defining Extensions (Phone Lines)

```ini
[transport-udp]
type=transport
protocol=udp
bind=0.0.0.0          ; Listen on all network interfaces

[1001]
type=endpoint
context=default        ; Which dialplan context handles calls from this extension
disallow=all           ; Start by disallowing all codecs
allow=ulaw             ; Only allow G.711 µ-law (standard phone quality)
auth=auth1001          ; Reference to the authentication block
aors=1001              ; Reference to the address-of-record block

[auth1001]
type=auth
auth_type=userpass
username=1001
password=1234

[1001]
type=aor               ; Address of Record — where to reach this extension
max_contacts=1         ; Only allow one device registered at a time
```

Each extension in PJSIP requires **three configuration blocks**: `endpoint`, `auth`, and `aor`. This is more verbose than the old SIP config but much more flexible.

#### extensions.conf — The Dialplan

The dialplan is the **brain of Asterisk**. It defines what happens when someone dials a number.

```ini
[default]

; When someone dials 1001, ring the PJSIP device registered as 1001 for 20 seconds
exten => 1001,1,Dial(PJSIP/1001,20)

; When someone dials 8000, run our Java IVR
exten => 8000,1,NoOp(Starting Java IVR)
exten => 8000,n,AGI(agi://127.0.0.1:4573/myivr)
exten => 8000,n,Hangup()

; Echo test — anything you say comes back to you (useful for audio testing)
exten => 9999,1,Answer()
exten => 9999,2,Echo()
exten => 9999,3,Hangup()
```

The syntax is: `exten => <number>, <priority>, <application>(<arguments>)`

The `n` priority means "next" — Asterisk automatically assigns the next sequential number.

`AGI(agi://127.0.0.1:4573/myivr)` tells Asterisk: "Connect to the AGI server running on this machine at port 4573, and ask it to handle a script called `myivr`."

---

### Part 4 — PostgreSQL Database

```sql
CREATE DATABASE iti_bank;
CREATE USER bankuser WITH PASSWORD 'bank123';
GRANT ALL PRIVILEGES ON DATABASE iti_bank TO bankuser;

\c iti_bank

CREATE TABLE accounts (
    id      SERIAL PRIMARY KEY,
    phone   VARCHAR(10) UNIQUE,   -- Maps to the SIP extension number
    name    VARCHAR(100),
    balance NUMERIC(10,2)
);

INSERT INTO accounts (phone, name, balance) VALUES
('1001', 'Mohamed Hesham', 5000.00),
('1002', 'Ahmed Ali',      12500.75);

-- Critical: grant table-level permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bankuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO bankuser;
```

#### Why the extra GRANT on tables?

`GRANT ALL PRIVILEGES ON DATABASE` only gives access to the database itself (the ability to connect). It does **not** automatically grant access to the tables inside it. You must separately grant privileges on the tables. This is a common PostgreSQL gotcha — the error you see is `permission denied for table accounts`.

---

### Part 5 — Java Maven Project

#### Project Structure

```
agi-project/
├── pom.xml                          ← Maven build config + dependencies
└── src/
    └── main/
        ├── java/com/myasterisk/
        │   ├── App.java             ← Starts the FastAGI server
        │   ├── MyIvrScript.java     ← IVR logic (the Banking menu)
        │   └── DBHelper.java        ← PostgreSQL connection + queries
        └── resources/
            └── fastagi-mapping.properties  ← Maps script name to Java class
```

#### App.java — The FastAGI Server

```java
public class App {
    public static void main(String[] args) throws Exception {
        AgiServer server = new DefaultAgiServer();
        System.out.println("AGI Server starting on port 4573...");
        server.startup();  // Blocks and listens for incoming connections from Asterisk
    }
}
```

`DefaultAgiServer` from the `asterisk-java` library automatically:
- Listens on port 4573
- Reads `fastagi-mapping.properties` to know which Java class handles each script name
- Spawns a new thread for each incoming call

#### fastagi-mapping.properties

```properties
myivr=com.myasterisk.MyIvrScript
```

When Asterisk calls `agi://127.0.0.1:4573/myivr`, the FastAGI server reads this file and instantiates `MyIvrScript` to handle that call. Without this file in the classpath, you get "No script configured for myivr".

#### DBHelper.java — Database Connection

```java
public class DBHelper {
    private static final String URL  = "jdbc:postgresql://localhost:5432/iti_bank";
    private static final String USER = "bankuser";
    private static final String PASS = "bank123";

    public static double getBalance(String phone) {
        String sql = "SELECT balance FROM accounts WHERE phone = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);   // Prevents SQL injection
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;  // Account not found
    }
}
```

We use a `PreparedStatement` with a `?` placeholder instead of string concatenation. This prevents **SQL injection** attacks — if someone passed `' OR '1'='1` as the phone number, the PreparedStatement treats it as literal text, not SQL code.

#### MyIvrScript.java — The IVR Logic

```java
public class MyIvrScript extends BaseAgiScript {
    public void service(AgiRequest request, AgiChannel channel) throws AgiException {
        answer();   // Pick up the call

        // Get the caller's phone number (e.g., "1001")
        String callerPhone = request.getCallerIdNumber();
        if (callerPhone == null) callerPhone = "1001";

        // Play welcome and menu audio files
        streamFile("iti-welcome");
        streamFile("iti-press1");
        streamFile("iti-press2");
        streamFile("iti-press3");

        // Wait up to 7 seconds for the caller to press a digit
        int digit = waitForDigit(7000);

        if (digit == '1') {
            double balance = DBHelper.getBalance(callerPhone);
            streamFile("iti-balance");
            if (balance >= 0) {
                // Speak each digit of the balance separately
                // e.g., 5000 → "5", "0", "0", "0"
                String balanceStr = String.valueOf((int) balance);
                for (char c : balanceStr.toCharArray()) {
                    streamFile("digits/" + c);
                }
            }
        } else if (digit == '2') {
            streamFile("iti-transfer");
        } else if (digit == '3') {
            streamFile("tt-monkeysintro");
            streamFile("tt-monkeys");
        } else {
            streamFile("demo-nogo");
        }

        streamFile("iti-goodbye");
        hangup();
    }
}
```

`waitForDigit(7000)` returns the ASCII value of the key pressed. That is why we compare `digit == '1'` — the character literal `'1'` has ASCII value 49, which matches what `waitForDigit` returns when the caller presses 1.

---

### Part 6 — Text-to-Speech Sound Files

Asterisk ships with built-in sound files (like `hello-world.gsm`, `goodbye.gsm`) but has no file that says "Welcome to ITI Bank". We use **Festival TTS** to generate custom audio.

#### Why GSM format?

Asterisk's native audio format is **GSM 6.10** at **8000 Hz, mono**. This is the same codec used in traditional phone calls. If you give Asterisk a WAV file at 16000 Hz (CD quality), it will refuse to play it with the error: `Unexpected frequency mismatch 16000 (expecting 8000)`.

#### The conversion pipeline

```bash
# Step 1: Festival generates a WAV file (at 16000 Hz by default)
festival --batch "(utt.save.wave (utt.synth (Utterance Text \"Welcome to ITI Bank\")) \"/tmp/iti-welcome.wav\" 'riff)"

# Step 2: SoX resamples to 8000 Hz mono and encodes as GSM
sox /tmp/iti-welcome.wav -r 8000 -c 1 -t gsm /var/lib/asterisk/sounds/en/iti-welcome.gsm
```

The `-t gsm` flag tells SoX to encode as GSM. Without `-r 8000 -c 1`, Asterisk will reject the file.

---

### Part 7 — Running and Testing

#### Start the AGI Server

```bash
cd ~/Desktop/ITI/IVR/agi-project
mvn exec:java -Dexec.mainClass="com.myasterisk.App"
```

You should see:
```
AGI Server starting on port 4573...
```

#### Verify everything is running

```bash
# Is Asterisk up?
sudo systemctl status asterisk

# Is the AGI server listening?
sudo ss -tlnp | grep 4573

# Are the PJSIP endpoints registered?
sudo asterisk -rx "pjsip show endpoints"

# Is the dialplan correct?
sudo asterisk -rx "dialplan show default"
```

#### Make a test call from Zoiper

| Extension | What Happens |
|---|---|
| `9999` | Echo test — you hear your own voice back |
| `8000` | Banking IVR — the full menu with DB lookup |
| `1001` → `1002` | Extension-to-extension call (need two devices) |

---

## Key Concepts Explained

### SIP (Session Initiation Protocol)

SIP is a **signaling protocol** for VoIP calls. It handles:
- Registration ("I am extension 1001, here is my IP address")
- Call setup ("Extension 1001 wants to call 8000")
- Call teardown ("The call has ended")

SIP does **not** carry audio. Audio travels separately over RTP.

### AGI (Asterisk Gateway Interface)

AGI is how Asterisk communicates with external programs. When Asterisk reaches an `AGI()` application in the dialplan, it:
1. Opens a TCP connection to the specified host and port
2. Sends call information (caller ID, channel name, etc.)
3. Listens for commands from the external program
4. Executes those commands (play audio, wait for digit, hang up)

**FastAGI** is the network-based version of AGI (using TCP on port 4573). The alternative is standard AGI, which spawns a new process for each call — much slower.

### Codec (ulaw)

A codec defines how audio is compressed for transmission. We configured `allow=ulaw` in PJSIP. **G.711 µ-law (ulaw)** is the standard North American telephone codec — 8-bit PCM at 8000 Hz. It is uncompressed and high-quality for voice.

### JDBC (Java Database Connectivity)

JDBC is Java's standard API for connecting to databases. We use:
- `DriverManager.getConnection()` to establish a connection
- `PreparedStatement` to safely run parameterized SQL queries
- `ResultSet` to read the results row by row

---

## Common Errors and Fixes

| Error | Cause | Fix |
|---|---|---|
| `No such command 'sip show peers'` | chan_sip removed in Asterisk 20 | Use PJSIP: `pjsip show endpoints` |
| `Address already in use` on port 4573 | Old AGI server still running | `sudo kill $(sudo lsof -t -i:4573)` |
| `permission denied for table accounts` | Missing table-level grants in PostgreSQL | `GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO bankuser;` |
| `Unexpected frequency mismatch 16000` | Audio file not at 8000 Hz | Re-convert with `sox -r 8000 -c 1 -t gsm` |
| `I'm afraid I can't connect to Asterisk` | AGI Server not running | Run `mvn exec:java -Dexec.mainClass="com.myasterisk.App"` |
| `No script configured for myivr` | `fastagi-mapping.properties` not in classpath | Ensure file is in `src/main/resources/` and run `mvn compile` |
| `incompatible types: int cannot be converted to String` | Wrong method signature for `waitForDigit` | Use `int digit = waitForDigit(5000);` and compare with `digit == '1'` |

---

## Port Reference

| Port | Protocol | Service | Purpose |
|---|---|---|---|
| 22 | TCP | SSH | Remote terminal access |
| 5060 | UDP + TCP | SIP | VoIP call signaling |
| 10000–20000 | UDP | RTP | Audio streaming |
| 5038 | TCP | AMI | Asterisk Manager Interface (Java control) |
| 4573 | TCP | FastAGI | Java IVR server |
| 5432 | TCP | PostgreSQL | Database (local only) |

---

## Project Structure

```
agi-project/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/myasterisk/
        │       ├── App.java             ← Entry point: starts FastAGI server on port 4573
        │       ├── MyIvrScript.java     ← IVR logic: menu, digit handling, audio playback
        │       └── DBHelper.java        ← PostgreSQL: getBalance(), getName()
        └── resources/
            └── fastagi-mapping.properties
                                         ← myivr=com.myasterisk.MyIvrScript
```

### Dependencies (pom.xml)

```xml
<!-- Asterisk-Java: FastAGI server + AGI commands -->
<dependency>
    <groupId>org.asteriskjava</groupId>
    <artifactId>asterisk-java</artifactId>
    <version>3.41.0</version>
</dependency>

<!-- PostgreSQL JDBC Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

---

*Built during ITI Java Enterprise Applications track — Asterisk Lab*
