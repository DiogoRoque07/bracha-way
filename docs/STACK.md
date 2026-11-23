# Recommended Stack — Decentralised Payment System (Option 1)

Aligned with the Phase 1 design document (`Project_Specification_Group6.pdf`) and updated with the
professor's Phase 1 feedback. Changes from the Phase 1 design are marked **[updated]** and must also
go into the updated design document for Phase 2.

**Conclusions agreed with the professor** **[updated]**:
1. **No failure detector.** Nodes only wait for quorums reached by the `n − f` correct nodes, so a
   detector adds nothing: either there is none, or it would have to be a **perfect detector (P)**.
   The base system has none. A P detector is an *optional* optimisation of communication, considered
   only after the base system works (§3.4).
2. **FIFO per wallet + synchronisation with versions.** Each account's transfers are applied in the
   order the wallet issued them, and versions let the wallet detect whether it, or the node it talks
   to, is out of date (§3.2).
3. **Crash recovery is an increment**, to do only after the base system is fully working (§3.3).

A digital coin system run by `n` replicated nodes with `n ≥ 3f + 1`. **`n` and `f` are read from the
config** and every threshold is computed from them **[updated]**. The demos use `n = 4, f = 1`, and
`n = 7, f = 2` is also tested to show the system is not built around `n = 4`. Clients sign
transfers with Ed25519 and submit them through a **wallet**. Transfers are disseminated with
**Bracha's Byzantine Reliable Broadcast (BRB)**, one instance per `id = (src, seq)`. There is
**no consensus and no total order**: transfers from different accounts commute, and the per-account
`seq` orders transfers from the same account (FIFO).

Guiding principle: **minimal frameworks**. The protocols (channels, BRB, replication) are written by
hand on top of the JDK.

---

## 1. System model (from the design)

- **Fully asynchronous network**: no bounds on delays, and **no failure detector** **[updated]**.
  Every guarantee holds without timing assumptions. Up to `f < n/3` Byzantine nodes (crash,
  equivocation, omission, forgery).
- **Crash-stop** in the base system: a crashed node never rejoins. A node restarted without having
  persisted its ECHO/READY counts as Byzantine (it is one of the `f`). The crash-recovery increment
  (§3.3) removes this restriction.
- Fixed set of nodes. Every node has an **Ed25519 key pair** whose public keys are known to everyone,
  plus **pairwise HMAC keys** for node-to-node links **[updated]** (see §3, `channel` and `common`).
- Clients may be malicious but cannot forge other clients' signatures. One Ed25519 key pair per account,
  used by **one wallet** (see §3.2 for what happens if two wallets share an account).
- **Correct client** **[updated]**: does not crash permanently and keeps resending its unconfirmed
  transfers until they are confirmed. Progress is guaranteed only to correct clients. A wallet that
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

## 3. Stack per module (matches Table 3 of the design, minus `fd`)

| Module | Choice | Notes |
|---|---|---|
| **`channel`**: reliable authenticated channels | Plain TCP `java.net.Socket`, one **virtual thread** per connection; frames are length-delimited protobuf (`writeDelimitedTo` / `parseDelimitedFrom`) | Each frame carries `(senderId, epoch, linkSeq)` and an **HMAC-SHA256** with a key per node pair. ACKs, a resend buffer, deduplication, and resending after reconnect give reliable delivery between correct processes. Messages are **never dropped**, so a slow node is never treated as crashed. **ACKs bypass the resend buffer**: they are sent once and never retransmitted. `epoch` is a per-start incarnation number in the handshake; it is always `0` in the base system and is used by the crash-recovery increment (§3.3). **No TLS**: confidentiality isn't needed (the ledger is public), but authentication is, so a Byzantine node cannot impersonate others in quorums. |
| **`broadcast`**: Bracha BRB | Hand-written; interface `brbBroadcast(id, tx)` / `brbDeliver(id, tx)` with `id = (src, seq)` | **SEND**: a node that receives the client's tx sends it to all nodes. Several nodes may SEND the same `id`, because the wallet sends to `f+1` nodes and may resend; duplicate SENDs are harmless. **ECHO**: once per `id`, only for the first tx with a valid `src` signature. **READY**: on `⌈(n+f+1)/2⌉` matching ECHOs or on `f+1` matching READYs (amplification), once per `id`. **Deliver**: on `2f+1` matching READYs. All thresholds come from a `Quorums(n, f)` helper; no constants. The BRB does **not** look at the ledger: it runs an instance for any `seq`, even ahead of `next[src]`. FIFO ordering happens in `app`. **[updated]** **Garbage collection**: once an `id` is delivered, its counters and per-sender sets are dropped and only a `delivered(id)` marker is kept. By then the node has already sent its READY, and anything still unACKed is in the channel's resend buffer. After the tx is applied, `seq < next[src]` is enough to ignore late messages, so the marker is dropped too. Configurable Byzantine mode for fault injection. |
| ~~`fd`~~: **no failure detector** **[updated]** | — | Agreed with the professor: since every step waits only for quorums that the `n − f` correct nodes reach on their own, a detector contributes nothing to safety or liveness. A ◇P would also be useless here: it may be wrong for an arbitrary time, so nothing could safely be built on it. Either the system has no detector, or a **perfect** one (P), which needs a synchrony assumption. The base system has none and is fully asynchronous. P is an optional optimisation for later (§3.4). The assignment lists failure detectors among the modules it "expects in most projects", so the design document must explain this choice. |
| **`app`**: application logic **+ replication** | Validation on delivery, **FIFO per account**, pending set, balances, **versions**, node-side client API | This module also holds the **replication logic** required by the assignment. Every node keeps a full replica, and consistency comes from BRB delivering the same set of txs per `id` at every correct node, plus commutativity across accounts. On `brbDeliver(tx = ⟨src, dst, v, seq⟩)`: (1) verify the signature; (2) **FIFO**: `seq == next[src]`; if higher, **pending** until its predecessors are applied; if lower, **discard**; (3) `balance[src] ≥ v`; otherwise **pending**, re-evaluated whenever `src` is credited; (4) apply: `balance[src] -= v`, `balance[dst] += v`, `next[src]++`, `ver[src]++`, `ver[dst]++`, append to log. Never reject for insufficient funds; that would break convergence. **[updated]** Every account answer carries its **versions** (§3.2). |
| **`storage`** | Local file via `FileChannel`: append-only log of applied transfers | In the base (crash-stop) system it serves `history` and inspection, and is the base for the increment, which adds the ECHO/READY WAL (§3.3). |
| **`client`**: wallet | **picocli** CLI + local keystore file | See §3.2. |
| **`common`** | Protobuf messages, crypto, configuration | JDK `java.security`: **Ed25519**, `HmacSHA256`, `SHA-256`; no BouncyCastle. **[updated]** **Account ID = X.509-encoded public key** (`PublicKey.getEncoded()`, 44 bytes) everywhere, rebuilt with `X509EncodedKeySpec`; avoids the JDK's awkward raw-32-byte decoding. Config in YAML (`jackson-dataformat-yaml`): node IDs, addresses, node public keys, pairwise HMAC keys, `n`, `f`, genesis balances, wallet settings. Startup fails if `n < 3f + 1`. |
| **Deployment & tests** | Docker Compose with `n = 4`; JUnit unit and fault-injection tests; demo scripts | See §5 and §6. |

### 3.1 Why no failure detector works in an asynchronous system **[updated]**

In an asynchronous system no timeout can tell a crashed node from a slow one. The protocol therefore
never asks "who has failed?":
- The **BRB** waits for `⌈(n+f+1)/2⌉` ECHOs and `f+1` / `2f+1` READYs. The `n − f` correct nodes
  always supply them eventually, whatever the others do.
- The **channels** resend until ACKed and never drop. A slow node eventually receives everything, and
  a crashed node just costs some retransmissions.
- The **wallet** sends to `f+1` nodes (at least one is correct) and confirms on `f+1` matching answers.

Bracha's BRB is solvable in pure asynchrony. Consensus is not (FLP), and that is why consensus
protocols need detectors. This system avoids consensus, so it needs none.

### 3.2 Wallet: FIFO, versions and synchronisation **[updated]**

**FIFO per wallet.** The account's `seq` is the version of its outgoing stream. The wallet numbers
its transfers `0, 1, 2, …` and nodes apply them **strictly in `seq` order**, whatever order the BRB
delivers them in: `seq > next[src]` waits as pending, `seq < next[src]` is a duplicate or replay. The
wallet may therefore **pipeline** up to `maxInFlight` unconfirmed transfers (`seq = c+1 … c+W`, where
`c` is the last confirmed seq). FIFO still holds, and each one is confirmed in order.

**Versions** carried by every node answer about an account `a`:

| Version | Meaning | Used for |
|---|---|---|
| `next[a]` | number of `a`'s own transfers applied (= next expected `seq`) | FIFO; detecting an outdated wallet or node |
| `ver[a]` | number of applied transfers touching `a` (debits + credits) | monotonic balance reads |

Different nodes may reach the same `ver[a]` with different credits applied: credits from different
sources commute and may arrive in different orders. A version therefore only says *how far* a node
is, not *exactly which* state it has, so the read rules below always require `f+1` matching answers.

**Submission**
1. Build `⟨src, dst, amount, seq⟩` with `seq = next local seq`, and sign it.
2. **Persist the signed bytes and the new seq in the keystore *before* sending.** The keystore keeps
   the private key, the next `seq`, the last confirmed `seq`, and **all** unconfirmed signed transfers.
3. **Send to `submitFanout` nodes in parallel** (default `f+1`, configurable up to `n`). At least
   one recipient is correct, so the BRB starts without the wallet knowing who has failed.
4. If there is no confirmation after a fixed **resend interval**, **resend exactly the same signed
   bytes** to other nodes. **Never sign a different transfer with the same `seq`**: that is
   equivocation and would block the account. Resending is always safe: if no node got the tx, a node
   starts the BRB; if the BRB is running, the new SEND joins the same instance; if the tx is already
   applied, `seq < next[src]` and it is not applied twice.

**Confirmation.** Query `TxStatus(src, seq)` → `APPLIED | PENDING | UNKNOWN` (+ the node's `next[src]`).
The transfer is confirmed on **`f+1` matching `APPLIED`** answers: at least one comes from a correct
node, and by BRB totality every correct node will apply it. `UNKNOWN` means "not here yet", not
"failed". The wallet queries incrementally (first `f+1` nodes, then more as needed). Each reply is
**signed with the node's Ed25519 key**, so the wallet can tell which node gave it.

**When the connected node is outdated** (it has not yet applied transfers others have):
- The wallet detects it through the versions: a reply with `next[src] ≤` the wallet's last confirmed
  `seq`, or with `ver[a]` lower than one already confirmed, is **stale**.
- The wallet **ignores stale answers** and asks other nodes. This gives *read-your-writes* (it never
  shows a state without its own confirmed transfers) and *monotonic reads* (versions never go back).
- A stale node does **not** block submission: the BRB runs for any `seq`, and the node keeps the tx
  pending until its predecessors arrive.
- In the base (crash-stop) system the node catches up by itself: reliable channels and BRB totality
  guarantee that it eventually delivers everything. Explicit node-side synchronisation is only
  needed after a restart, which is part of the crash-recovery increment (§3.3); it reuses the same
  versions.

**When the wallet is outdated** (its local state is behind, e.g. restored from a backup):
- **Detection**: a `TransferRequest` for `seq < next[src]` whose bytes differ from the transfer
  applied at that `seq` is answered `STALE_SEQ`, with the node's `next[src]` and the transfer applied
  there. Same bytes → `ALREADY_APPLIED`.
- **Synchronisation** (`wallet sync`, run on `STALE_SEQ` and on start):
  1. Ask nodes for `AccountView(src)` and take **`v` = the (f+1)-th highest `next[src]` reported**.
     At least `f+1` nodes report `≥ v`, so at least one correct node has applied every `seq < v`.
     The wallet therefore never jumps past a transfer that does not exist. A Byzantine node cannot
     raise `v` alone, and the worst a lagging node can do is make `v` too low, which simply causes
     another `STALE_SEQ` and another sync.
  2. Fetch `History(src, from_seq = local seq)` and keep only entries **signed by the wallet's own key**.
     A Byzantine node cannot fabricate them; it can only omit them, and that is covered by asking
     `f+1` nodes.
  3. Update the keystore: next `seq = v`, last confirmed `= v − 1`. Any unconfirmed transfer whose
     `seq < v` but whose bytes differ from the applied one is marked **superseded** and reported to the user.
- Ed25519 is deterministic, so if the user remembers `dst` and `amount` of a lost unconfirmed transfer,
  the wallet can rebuild the identical signed bytes and resend them safely.

**Two wallets on the same account** (two devices, one key): `wallet sync` before each transfer narrows
the window but cannot close it. Two devices that sign different transfers for the same `seq`
concurrently are indistinguishable from client equivocation: at most one is applied, or the account
blocks from that `seq`. **Out of scope**: one account = one wallet.

### 3.3 Increment: crash recovery (after the base system works) **[updated]**

Agreed with the professor as a good increment, to be **started only once the base system is fully
functional** (demos 1–4 and the property tests passing).

1. **ECHO/READY WAL** (`storage`): before sending an ECHO or READY, append `(id, phase, tx)` and
   `fsync`. It stores the full tx, not just its hash, because the node must re-send the message after
   a restart. Group commit: the outgoing ECHO/READYs of one event-loop iteration are released after a
   single `fsync`. On restart, load the WAL so the node never ECHOes/READYs a different tx for an `id`.
   It then no longer counts towards `f`.
2. **Ledger rebuild**: replay the applied-transfer log (balances, `next[src]`, `ver[a]`, pending).
3. **Rejoin**: bump `epoch` in the channel handshake, so peers reset their link state with this node.
4. **State synchronisation using the versions** (§3.2): for each account, ask peers for transfers with
   `seq ≥` the local `next[src]`. Accept a transfer on `f+1` matching answers, or on a certificate of
   `2f+1` signed READYs. Client signatures alone are not enough: a client may have equivocated, and
   only the BRB-delivered transfer counts.

Steps 1–3 take about a day and give safety after a restart. Step 4 makes a restarted node catch up.

### 3.4 Optional optimisation: perfect failure detector (P) **[updated]**

Considered only after the base system works, and only if communication needs optimising.
- **Assumption it requires**: synchrony, i.e. known bounds on message delay and processing time
  (plausible on a LAN or in Docker). The design document must state that this assumption applies to
  this optimisation only.
- **Use**: under crash-stop, a P-suspected node really has crashed, so the channel can **stop
  retransmitting to it and free its resend buffer**. That removes the unbounded-buffer limitation (§7).
- **Never** used for BRB quorums or for the wallet. If the synchrony assumption fails, the only
  effect is that a slow but correct node misses messages. That is no worse than its being Byzantine,
  but it then counts towards `f`.
- Not compatible as-is with the crash-recovery increment: a restarted node would have been
  "permanently" suspected. Doing both needs the restart to reset the suspicion (new `epoch`).

### Signing rule (protobuf)

```proto
message Transfer       { bytes src = 1; bytes dst = 2; uint64 amount = 3; uint64 seq = 4; }
message SignedTransfer { bytes transfer = 1;   // serialized Transfer, exactly as signed
                         bytes signature = 2; } // Ed25519 over those bytes
```

Nodes verify and hash the exact `transfer` bytes and never re-serialize them, so correctness
doesn't depend on protobuf encoding being deterministic.

### Node ↔ node messages

`NodeMessage { oneof { Send, Echo, Ready, Ack } }`. Send, Echo and Ready carry `id = (src, seq)` and
the `SignedTransfer`, and go through the reliable path. Ack is sent once, with no resend buffer.

### Client ↔ node **[updated]**

Protobuf over TCP. Every reply is signed by the node and carries versions where relevant.
- `Transfer(SignedTransfer)` → `ACCEPTED | ALREADY_APPLIED | STALE_SEQ | REJECTED`, plus `next[src]`,
  plus the applied transfer at that `seq` when the answer is `STALE_SEQ`
- `TxStatus(src, seq)` → `APPLIED | PENDING | UNKNOWN`, plus `next[src]`
- `AccountView(account)` → `balance`, `next[a]`, `ver[a]`
- `History(account, from_seq)` → the account's applied transfers from `from_seq`
- `NodeStatus` → node ID and number of applied transfers (for demos)

---

## 4. Concurrency model

- **I/O on virtual threads**, one per connection.
- **Protocol logic on a single event-loop thread per node.** All incoming messages go into a
  `BlockingQueue`; one thread runs BRB and `app`.
  → No locks in protocol code, no data races, reproducible behaviour.
- **[updated]** **Signatures are verified in parallel, before the event loop.** Ed25519 verification
  is the CPU hot path, so it runs on the receiving virtual thread (or a small pool). Messages reach
  the queue already tagged as `valid` or `invalid`. Protocol logic stays single-threaded.
- **[updated]** **Batching below the protocol**: the channel may pack several queued messages into one
  frame (one HMAC). BRB indexing by `(src, seq)` is unchanged; there is no batching of txs into one
  BRB instance. (The increment's WAL adds group commit, §3.3.)

---

## 5. Fault injection & demonstrations

| Concern | Choice |
|---|---|
| Container image | `eclipse-temurin:21-jre`, fat JAR via `maven-shade-plugin`; data dir on a Docker volume |
| Cluster | Docker Compose, **`n = 4`, `f = 1`** for the demos; **[updated]** a second config/compose file for **`n = 7`, `f = 2`**, generated by the keygen/config script |
| Crash faults | `docker kill` / `docker stop` (crash-stop: the node is not restarted in the base system) |
| Byzantine node modes | `--byzantine=equivocate\|omit\|fake-echo` |
| Malicious client | Wallet flag that signs two transfers with the same `seq` and sends them to different nodes |
| **[updated]** Collusion for equivocation | `equivocate` mode is given **two conflicting txs validly signed by the malicious client** (same `seq`) and sends different ones to different nodes. Without them a Byzantine node can only send txs with invalid signatures, which everyone rejects. |

**Planned demos** (from the design):
1. A normal transfer between two clients.
2. A double-spending attempt being blocked.
3. A node crash without affecting the service.
4. A Byzantine node sending inconsistent messages without affecting final balances.
5. **[updated]** An outdated wallet (restored from an old keystore) getting `STALE_SEQ` and resynchronising.
6. *(increment)* A node restarted from its WAL that does not equivocate and catches up.

---

## 6. Properties to test

1. No negative balance.
2. Conservation: the sum of balances is constant.
3. Convergence: correct nodes end with identical balances.
4. Double-spending safety: two transfers with the same `(src, seq)` → at most one applied.
5. Progress under faults: valid transfers complete with `f` crashed or Byzantine nodes.
6. **[updated]** Wallet retry idempotence: resending the same signed tx to any number of nodes applies it exactly once.
7. **[updated]** FIFO: for every account, transfers are applied in `seq` order at every correct node, even when the BRB delivers them out of order (pipelined wallet).
8. **[updated]** Wallet sync: after `wallet sync`, the wallet's next `seq` never exceeds the true `next[src]` at any correct node, with up to `f` nodes lying.
9. *(increment)* Restart safety: a node restarted from its WAL never ECHOes/READYs a different tx for an `id` it already handled.

Run them as JUnit tests over an in-memory simulated network (fast, deterministic) and as
end-to-end checks in the Docker demo scripts. **[updated]** The simulated-network tests are
parameterised over **`(n, f) ∈ {(4, 1), (7, 2)}`**, with up to `f` faulty nodes in each run.

---

## 7. Known limitations **[updated]**

- More than `f` Byzantine nodes break the guarantees.
- Crash-stop in the base system: a restarted node counts as Byzantine (removed by the §3.3 increment).
- **Unbounded resend buffer** towards a node that crashed for good, because messages are never
  dropped. Fine at demo scale. The optional P detector (§3.4) removes it.
- **Unbounded per-account state**: a malicious client can open BRB instances or pending entries for
  arbitrarily high `seq`. Optional mitigation: a window `seq < next[src] + W`, where messages beyond it
  are **buffered, not rejected**, to keep BRB validity.
- A tx that never gets enough balance stays pending and blocks that account's later `seq`s (by design).
- One account = one wallet (§3.2).
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
common/          .proto definitions, crypto, config, all module interfaces
channel/         reliable authenticated links (TCP + HMAC + epoch + ACK/resend/dedup)
broadcast/       Bracha BRB per (src, seq), byzantine modes
app/             validation on delivery, FIFO, pending set, balances, versions, replication, client API, node main + event loop
storage/         applied-transfer log (+ ECHO/READY WAL in the increment)
client/          wallet CLI + keystore + sync
testkit/         simulated in-memory network, fault injection, property checkers (test scope)
deploy/          docker-compose.yml, Dockerfile, config/, keygen script
demo/            demo / fault-injection scripts
README.md        build, deploy and run instructions (Phase 2 deliverable)
docs/            STACK.md (this file), PLAN.md, design document
```

---

## 9. Key module interfaces

```java
interface Channel   { void send(NodeId to, NodeMessage m); void onDeliver(BiConsumer<NodeId, NodeMessage> h); }
interface Broadcast { void brbBroadcast(TxId id, SignedTransfer tx); void onBrbDeliver(BiConsumer<TxId, SignedTransfer> h);
                      void forget(TxId id); }
interface Ledger    { void onDeliver(TxId id, SignedTransfer tx);
                      AccountView view(AccountId a);                 // balance, next[a], ver[a]
                      TxStatus status(TxId id);
                      Optional<SignedTransfer> appliedAt(TxId id);   // for ALREADY_APPLIED vs STALE_SEQ
                      List<SignedTransfer> history(AccountId a, long fromSeq); }
interface Storage   { void append(SignedTransfer applied); Stream<SignedTransfer> replay(); }
interface BrbWal    { void record(TxId id, Phase phase, SignedTransfer tx); void flush(); List<Entry> load(); }
                      // increment only; the base system uses BrbWal.NONE

record TxId(AccountId src, long seq) {}
record AccountView(long balance, long nextSeq, long version) {}
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
- **Updated design document** reflecting every **[updated]** item above and the three conclusions
  agreed with the professor (no failure detector, FIFO + versions, crash recovery as an increment).
