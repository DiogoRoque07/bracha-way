# Recommended Stack — Decentralised Payment System (Option 1)

Aligned with the Phase 1 design document (`Project_Specification_Group6.pdf`) and updated with the
professor's Phase 1 feedback (failure detector role, wallet retries and confirmation, storage and
recovery). Changes from the Phase 1 design are marked **[updated]** and must also go into the updated
design document for Phase 2.

A digital coin system run by `n` replicated nodes with `n ≥ 3f + 1`. **`n` and `f` are read from the
config** and every threshold is computed from them **[updated]**. The demos use `n = 4, f = 1`, and
`n = 7, f = 2` is also tested to show the system is not built around `n = 4`. Clients sign
transfers with Ed25519 and submit them through a **wallet**. Transfers are disseminated with
**Bracha's Byzantine Reliable Broadcast (BRB)**, one instance per `id = (src, seq)`. There is
**no consensus and no total order**: transfers from different accounts commute, and the per-account
`seq` orders transfers from the same account.

Guiding principle: **minimal frameworks**. The protocols (channels, BRB, failure detector,
replication) are written by hand on top of the JDK.

---

## 1. System model (from the design)

- Asynchronous network; up to `f < n/3` Byzantine nodes (crash, equivocation, omission, forgery).
- **Crash-stop with a write-ahead log** **[updated]**: a node persists every ECHO/READY it sends
  *before* sending it (§3.1). A restarted node therefore never contradicts what it already sent, so it
  is **not** counted as Byzantine. It is a correct node that has fallen behind, but catching up is not
  guaranteed (see the open question in §3.1).
- Fixed set of nodes. Every node has an **Ed25519 key pair** whose public keys are known to everyone,
  plus **pairwise HMAC keys** for node-to-node links **[updated]** (see §3, `channel` and `common`).
- Clients may be malicious but cannot forge other clients' signatures. One Ed25519 key pair per account.
- **Correct client** **[updated]**: does not crash permanently and keeps resending its pending
  transfer until it is confirmed. Progress is guaranteed only to correct clients. A wallet that
  disappears for good is a faulty client; only it holds the private key, so nothing on the node side
  can act for it. This is the same situation as lost keys, which is out of scope.
- Out of scope: dynamic membership, recovery of lost client keys, coin creation after genesis, fees.

---

## 2. Core

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 21 LTS** | Records, sealed interfaces, pattern-matching `switch`, virtual threads |
| Build | **Maven** (multi-module, one module per design module) | Familiar, simple, works in every IDE |
| Wire format | **Protocol Buffers** (`protobuf-java`) | Stated in the design |
| Client ↔ node transport | **Protobuf over TCP**, same length-delimited framing as node links **[updated: decided]** | Stated in the design; no HTTP dependency; the wallet CLI covers the demos |
| Logging | **SLF4J + Logback** | Tag every line with node ID, `id = (src, seq)`, and message type |
| Testing | **JUnit 5** (+ AssertJ, Awaitility) | Awaitility for "eventually all correct nodes deliver" assertions |
| Deployment | **Docker Compose**, `n = 4` nodes | Stated in the design |
| Version control | **git**, shared remote | Weekly integration among 3 people |

---

## 3. Stack per module (matches Table 3 of the design)

| Module | Choice | Notes |
|---|---|---|
| **`channel`**: reliable authenticated channels | Plain TCP `java.net.Socket`, one **virtual thread** per connection; frames are length-delimited protobuf (`writeDelimitedTo` / `parseDelimitedFrom`) | Each frame carries `(senderId, epoch, linkSeq)` and an **HMAC-SHA256** with a key per node pair. ACKs, a resend buffer, deduplication, and resending after reconnect give reliable delivery between correct processes. **[updated]** `epoch` is a per-start incarnation number sent in the connection handshake: when a peer's epoch increases, its link state (dedup window, sequence numbers) is reset, so a restarted node can rejoin. **[updated]** **Heartbeats and ACKs bypass the resend buffer**: they are sent once and never retransmitted, otherwise the FD would never time out. **No TLS**: confidentiality isn't needed (the ledger is public), but authentication is, so a Byzantine node cannot impersonate others in quorums. |
| **`broadcast`**: Bracha BRB | Hand-written; interface `brbBroadcast(id, tx)` / `brbDeliver(id, tx)` with `id = (src, seq)` | **SEND**: a node that receives the client's tx sends it to all nodes. Several nodes may SEND the same `id`, because the wallet sends to `f+1` nodes and may resend; duplicate SENDs are harmless. **ECHO**: once per `id`, only for the first tx with a valid `src` signature. **READY**: on `⌈(n+f+1)/2⌉` matching ECHOs or on `f+1` matching READYs (amplification), once per `id`. **Deliver**: on `2f+1` matching READYs. All thresholds come from a `Quorums(n, f)` helper; no constants. **[updated]** **Garbage collection**: once an `id` is delivered, its counters and per-sender sets are dropped and only a `delivered(id)` marker is kept. By then the node has already sent its READY, and anything still unACKed is in the channel's resend buffer. After the tx is applied, `seq < next[src]` is enough to ignore late messages, so the marker is dropped too. Memory per account is therefore bounded by in-flight and pending txs, not by all txs ever made. **[updated]** Before sending an ECHO or READY, the node writes `(id, phase, tx)` to the WAL (`storage`). It stores the full tx, not just its hash, because it has to re-send the message after a restart; on restart it re-sends the same messages and never ECHOes/READYs a different tx for that `id`. Configurable Byzantine mode for fault injection. |
| **`fd`**: failure detector | Heartbeats on a `ScheduledExecutorService`; adaptive timeout (◇P) | **[updated]** **Liveness only; neither BRB safety nor BRB liveness depends on it.** The BRB is asynchronous and its quorums are reached by the `n − f` correct nodes whether or not anyone is suspected. **Node-side only**: (i) retransmission backoff to suspected nodes **without dropping messages**, so a node suspected by mistake still receives everything; (ii) exposing node status, suspects included, for demos. **The wallet does not use a failure detector** (§3.2); this removes use (ii) of the Phase 1 design ("the wallet picks a non-suspected node"). Suspicions are **never** used to exclude nodes from quorums; that would need partial synchrony and would make safety depend on false suspicions. |
| **`app`**: application logic **+ replication** | Validation on delivery, pending set, balances, node-side client API | **[updated]** This module also holds the **replication logic** required by the assignment. Every node keeps a full replica, and consistency comes from BRB delivering the same set of txs per `id` at every correct node, plus commutativity across accounts. State this explicitly in the design document. On `brbDeliver(tx = ⟨src, dst, v, seq⟩)`: (1) verify the signature; (2) `seq == next[src]`; if higher, **pending**; if lower, **discard**; (3) `balance[src] ≥ v`; otherwise **pending**, re-evaluated whenever `src` is credited; (4) apply: `balance[src] -= v`, `balance[dst] += v`, `next[src]++`, append to log. Never reject for insufficient funds; that would break convergence. |
| **`storage`** | Local files via `FileChannel`: **(a)** an append-only log of applied transfers, and **(b)** **[updated]** an append-only **WAL of sent ECHO/READY** records, `fsync`ed (`force(true)`) before each send | On restart the node replays (a) to rebuild balances, `next[src]` and the pending set, and loads (b) into the BRB so it never equivocates. See §3.1 for what this does and does not give. |
| **`client`**: wallet | **picocli** CLI + local keystore file | See §3.2. |
| **`common`** | Protobuf messages, crypto, configuration | JDK `java.security`: **Ed25519**, `HmacSHA256`, `SHA-256`; no BouncyCastle. **[updated]** **Account ID = X.509-encoded public key** (`PublicKey.getEncoded()`, 44 bytes) everywhere, rebuilt with `X509EncodedKeySpec`; avoids the JDK's awkward raw-32-byte decoding. Config in YAML (`jackson-dataformat-yaml`): node IDs, addresses, node public keys, pairwise HMAC keys, `n`, `f`, genesis balances, wallet `submitFanout`. Startup fails if `n < 3f + 1`. |
| **Deployment & tests** | Docker Compose with `n = 4`; JUnit unit and fault-injection tests; demo scripts | See §5 and §6. |

### 3.1 Recovery: intermediate option (write-ahead log of ECHO/READY) **[updated]**

What it gives:
- **Safety after restart.** A restarted node knows every `(id, tx)` it ECHOed or READYed and re-sends
  exactly those, so it cannot contradict itself. It no longer counts towards `f`.
- **A rebuilt local ledger.** The applied-transfer log restores balances, `next[src]` and the pending set.

What it does not give:
- **Catching up.** Deliveries the node missed while down, and BRB instances whose received ECHO/READY
  counts were only in memory, are not recovered. Peers' resend buffers re-deliver what was still
  unACKed, which covers part of the gap, but not everything. The node may therefore stay behind and
  answer `TxStatus` with `unknown` for some txs. The wallet's `f+1` rule tolerates this.
- **Full crash-recovery** would also need state transfer: fetching missed deliveries from other nodes
  and accepting each on `f+1` matching answers or on a certificate of `2f+1` READYs.

Cost: about one day (WAL writer + replay on start + `epoch` in the channel handshake).

> **Open question for the professor** (to send with the Phase 2 discussion):
> *"Optámos por um modelo crash-stop com write-ahead log dos ECHO/READY, para que um nó reiniciado
> não viole a segurança. Ainda assim, não ressincroniza as transações que perdeu enquanto esteve em
> baixo. Considera que devemos implementar a extensão completa de crash-recovery (transferência de
> estado a partir de outros nós, aceitando cada transação com f+1 respostas coincidentes ou um
> certificado de 2f+1 READYs), ou o write-ahead log é suficiente para o âmbito do projeto?"*

### 3.2 Wallet: submission, retries and confirmation **[updated]**

- **`keygen` / `createAccount()`**: generates an Ed25519 key pair (public key = account ID) and writes
  a **keystore file** holding the private key, the next `seq`, and the last unconfirmed signed transfer.
  Demo keys are generated **before** the node config, because genesis balances refer to their
  public keys.
- **`transfer(dst, amount)`**:
  1. Build `⟨src, dst, amount, seq⟩` (seq starts at 0) and sign it.
  2. **Persist the signed bytes and increment `seq` in the keystore *before* sending.**
  3. **Send to `submitFanout` nodes in parallel** (default `f+1`, configurable up to `n`). At least
     one recipient is correct, so the BRB starts without the wallet knowing who has failed.
     **No failure detector** **[updated]**: correctness and progress don't depend on knowing which
     nodes are down. Optionally the wallet *prefers* nodes that answered recently, but that is an
     optimisation, not a dependency. `f+1` (rather than `n`) is the choice that scales with `n`.
  4. If there is no confirmation after a fixed **resend interval**, **resend exactly the same
     signed bytes** to other nodes. A fixed interval is not a failure detector: it says nothing
     about which node is suspected. **Never
     sign a different transfer with the same `seq`**: that is equivocation and would block the account.
     Resending is always safe: if no node got the tx, a node starts the BRB; if the BRB is running, the
     new SEND joins the same instance; if the tx is already applied, `seq < next[src]` and it is not
     applied twice.
- **Confirmation**: query `TxStatus(src, seq)` → `APPLIED | PENDING | UNKNOWN`. The transfer is
  confirmed on **`f+1` matching `APPLIED`** answers: at least one comes from a correct node, and by BRB
  totality every correct node will apply it. The wallet queries incrementally (first `f+1` nodes, then
  more as needed) rather than all `n` at once; there are `n − f ≥ 2f+1` correct nodes, so `f+1`
  `APPLIED` answers eventually arrive. `UNKNOWN` means "not here yet", not "failed"; keep
  resending and polling. Each reply is **signed with the node's Ed25519 key**, so the wallet can
  tell which node gave it.
- **Lost local state** (key kept): ask nodes for `next[src]` and accept a value reported by at least
  `f+1` nodes. Ed25519 is deterministic, so if the user remembers `dst` and `amount` for the
  unconfirmed `seq`, the wallet can rebuild the identical signed tx and resend it safely.
- **`balance(account)` / `history(account)`** (optional): answered by the queried node. Balances may
  differ between nodes while txs are in flight, so confirmation uses `TxStatus`, not balances.

### Signing rule (protobuf)

```proto
message Transfer       { bytes src = 1; bytes dst = 2; uint64 amount = 3; uint64 seq = 4; }
message SignedTransfer { bytes transfer = 1;   // serialized Transfer, exactly as signed
                         bytes signature = 2; } // Ed25519 over those bytes
```

Nodes verify and hash the exact `transfer` bytes and never re-serialize them, so correctness
doesn't depend on protobuf encoding being deterministic.

### Node ↔ node messages

`NodeMessage { oneof { Send, Echo, Ready, Heartbeat, Ack } }`. Send, Echo and Ready carry
`id = (src, seq)` and the `SignedTransfer`, and go through the reliable path. Heartbeat and Ack
take the unreliable path: sent once, no resend buffer.

### Client ↔ node **[updated]**

Protobuf over TCP. Every reply is signed by the node.
- `TransferRequest(SignedTransfer)` → `ACCEPTED` (valid signature/format, in progress), `ALREADY_APPLIED`, or `REJECTED`
- `TxStatusRequest(src, seq)` → `APPLIED | PENDING | UNKNOWN`
- `NextSeqRequest(src)` → `next[src]`
- `BalanceRequest(account)`, `HistoryRequest(account)`
- `NodeStatusRequest` → node ID, suspected set (for demos only; the wallet does not rely on it)

---

## 4. Concurrency model

- **I/O on virtual threads**, one per connection.
- **Protocol logic on a single event-loop thread per node.** All incoming messages go into a
  `BlockingQueue`; one thread runs BRB, the failure detector and `app`.
  → No locks in protocol code, no data races, reproducible behaviour.
- **[updated]** **Signatures are verified in parallel, before the event loop.** Ed25519 verification
  is the CPU hot path, so it runs on the receiving virtual thread (or a small pool). Messages reach
  the queue already tagged as `valid` or `invalid`. Protocol logic stays single-threaded.
- **[updated]** **Batching below the protocol**: the channel may pack several queued messages into one
  frame (one HMAC), and the WAL groups several records per `fsync` (group commit: the outgoing
  ECHO/READYs of one event-loop iteration are released after a single `fsync`). BRB indexing by
  `(src, seq)` is unchanged; there is no batching of txs into one BRB instance.

---

## 5. Fault injection & demonstrations

| Concern | Choice |
|---|---|
| Container image | `eclipse-temurin:21-jre`, fat JAR via `maven-shade-plugin`; data dir on a Docker volume so the WAL survives restarts |
| Cluster | Docker Compose, **`n = 4`, `f = 1`** for the demos; **[updated]** a second config/compose file for **`n = 7`, `f = 2`**, generated by the keygen/config script |
| Crash faults | `docker kill` / `docker stop`; optionally `docker start` to show a WAL restart |
| Byzantine node modes | `--byzantine=equivocate\|omit\|fake-echo` |
| Malicious client | Wallet flag that signs two transfers with the same `seq` and sends them to different nodes |
| **[updated]** Collusion for equivocation | `equivocate` mode is given **two conflicting txs validly signed by the malicious client** (same `seq`) and sends different ones to different nodes. Without them a Byzantine node can only send txs with invalid signatures, which everyone rejects. |

**Planned demos** (from the design):
1. A normal transfer between two clients.
2. A double-spending attempt being blocked.
3. A node crash without affecting the service.
4. A Byzantine node sending inconsistent messages without affecting final balances.
5. *(optional)* **[updated]** A node restarted from its WAL that does not equivocate.

---

## 6. Properties to test

1. No negative balance.
2. Conservation: the sum of balances is constant.
3. Convergence: correct nodes end with identical balances.
4. Double-spending safety: two transfers with the same `(src, seq)` → at most one applied.
5. Progress under faults: valid transfers complete with `f` crashed or Byzantine nodes.
6. **[updated]** Wallet retry idempotence: resending the same signed tx to any number of nodes applies it exactly once.
7. **[updated]** Restart safety: a node restarted from its WAL never ECHOes/READYs a different tx for an `id` it already handled.

Run them as JUnit tests over an in-memory simulated network (fast, deterministic) and as
end-to-end checks in the Docker demo scripts. **[updated]** The simulated-network tests are
parameterised over **`(n, f) ∈ {(4, 1), (7, 2)}`**, with up to `f` faulty nodes in each run.

---

## 7. Known limitations **[updated]**

- More than `f` Byzantine nodes break the guarantees.
- A restarted node is safe but may stay behind (no state transfer; see §3.1).
- **Unbounded resend buffer** towards a node that crashed for good, because messages are never
  dropped. Fine at demo scale.
- **Unbounded per-account state**: a malicious client can open BRB instances or pending entries for
  arbitrarily high `seq`. Optional mitigation: a window `seq < next[src] + W`, where messages beyond it
  are **buffered, not rejected**, to keep BRB validity.
- A tx that never gets enough balance stays pending and blocks that account's later `seq`s (by design).
- Bracha costs **O(n²) messages per tx** (see §7.1).

### 7.1 Scalability and future work **[updated]**

Two kinds of scalability:

- **Correctness scales: implemented.** Everything is parametric in `n ≥ 3f + 1`, with no constants
  tied to `n = 4`, and is tested with `n = 7`.
- **Performance: no avoidable bottlenecks, but the protocol is unchanged.** Bracha's O(n²) messages
  per tx is inherent: every node sends ECHO and READY to every other node. We remove the bottlenecks
  that are not inherent: BRB garbage collection (§3), parallel signature verification and batching
  (§4), and a wallet fanout of `f+1` (§3.2). ECHO/READY carrying only the tx hash was considered and
  rejected: txs are about 150 B, and a node holding only the hash would have to fetch the tx.

Protocols that scale further, discussed in the design document but **not implemented**, because they
would replace Bracha, which is what the project is meant to demonstrate:
- **Astro** (Collins et al., DSN 2020): almost the same system, payments over BRB without consensus, plus sharding. It supports our central design decision (BRB per `(src, seq)`, no consensus).
- **FastPay** (Baudet, Danezis, Sonnino, AFT 2020): the client collects a certificate of `2f+1` signatures, giving O(n) messages per tx.
- **Probabilistic gossip-based BRB** (Guerraoui et al., "Scalable Byzantine Reliable Broadcast", DISC 2019): samples of size O(log n) instead of quorums.

---

## 8. Project layout

```
pom.xml          (parent)
common/          .proto definitions, crypto, config
channel/         reliable authenticated links (TCP + HMAC + epoch + ACK/resend/dedup)
broadcast/       Bracha BRB per (src, seq), byzantine modes
fd/              heartbeats, adaptive-timeout ◇P
app/             validation on delivery, pending set, balances, replication, client API, node main + event loop
storage/         applied-transfer log, ECHO/READY WAL, balances, next[src], pending
client/          wallet CLI + keystore
testkit/         simulated in-memory network, fault injection, property checkers (test scope)
deploy/          docker-compose.yml, Dockerfile, config/, keygen script
demo/            demo / fault-injection scripts
README.md        build, deploy and run instructions (Phase 2 deliverable)
docs/            STACK.md (this file), PLAN.md, design document
```

---

## 9. Key module interfaces

```java
interface Channel         { void send(NodeId to, NodeMessage m); void onDeliver(BiConsumer<NodeId, NodeMessage> h); }
interface Broadcast       { void brbBroadcast(TxId id, SignedTransfer tx); void onBrbDeliver(BiConsumer<TxId, SignedTransfer> h); }
interface FailureDetector { Set<NodeId> suspected(); void onChange(Consumer<Set<NodeId>> h); }
interface Ledger          { void onDeliver(TxId id, SignedTransfer tx); long balance(AccountId a);
                            List<SignedTransfer> history(AccountId a); TxStatus status(TxId id); long nextSeq(AccountId a); }
interface Storage         { void append(SignedTransfer applied); Stream<SignedTransfer> replay(); }   // ledger rebuilt by replay
interface BrbWal          { void record(TxId id, Phase phase, SignedTransfer tx); /* buffered */
                            void flush();  /* fsync; ECHO/READY recorded since last flush may be sent only after this returns */
                            List<Entry> load(); }

record TxId(AccountId src, long seq) {}
// TxStatus is the protobuf enum tfd.pay.proto.TxStatus (APPLIED | PENDING | UNKNOWN)
```

---

## 10. Dependencies

**Runtime**
- `com.google.protobuf:protobuf-java`
- `info.picocli:picocli`
- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`
- `org.slf4j:slf4j-api` + `ch.qos.logback:logback-classic`

**Test**
- `org.junit.jupiter:junit-jupiter`
- `org.assertj:assertj-core`
- `org.awaitility:awaitility`

**Build plugins**
- `io.github.ascopes:protobuf-maven-plugin` (runs `protoc` during the build)
- `org.apache.maven.plugins:maven-shade-plugin` (fat JAR)

Everything else (TCP networking, crypto, concurrency, file I/O) comes from the JDK.

---

## 11. Phase 2 deliverables (due 13 Dec)

- Code + `README.md` with build, deploy and run instructions.
- Demo scripts (§5).
- **Updated design document** reflecting every **[updated]** item above, the answers to the
  professor's feedback, and the reply to the open question in §3.1.
