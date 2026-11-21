# Implementation Plan: Decentralised Payment System (BRB)

Companion to `STACK.md`, which says **what** to build. This file says **in what order, who does it,
and when it counts as done**. Deadline: **Phase 2 submission on 13 Dec 2026** (Moodle).

Team of 3: owners **A**, **B** and **C** (fill in names below). Every module has a fixed interface
from Day 0, so the three tracks can proceed in parallel and integrate weekly.

| Owner | Name | Track |
|---|---|---|
| A | ______ | `channel`, `fd`, `deploy/` (Docker, keygen/config script) |
| B | ______ | `broadcast`, `testkit` (simulated network), Byzantine modes |
| C | ______ | `app`, `storage` (ledger log + WAL), `client` (wallet) |
| all | | `common` (Day 0, together), demos, design document, README |

---

## 0. Timeline at a glance

| When | Milestone | Exit criteria |
|---|---|---|
| **Day 0** (Sat 22 Nov – Sun 23 Nov) | **M0: Skeleton** | Repo builds with `mvn verify`; `.proto` and Java interfaces merged and **frozen**; CI green |
| **Week 1** (24 – 30 Nov) | **M1: Modules in isolation** | BRB, ledger and wallet logic pass unit tests over `testkit`'s simulated network; channel passes loopback TCP tests |
| **Week 2** (1 – 7 Dec) | **M2: Integrated system** | 4 nodes in Docker Compose; wallet transfer confirmed end to end; crash demo works; WAL restart works |
| **Week 3** (8 – 11 Dec) | **M3: Faults, demos, n = 7** | All 5 demos scripted; Byzantine modes working; property tests pass for (4,1) and (7,2) |
| **12 – 13 Dec** | **M4: Submission** | Code freeze on 12 Dec; README + updated design document; submit **by 13 Dec** (aim for the 12th) |

Weekly integration: **every Sunday evening**, merge `main`, run the full test suite and the latest
demo script, and adjust next week's tasks. A 15-minute sync on Wednesdays is enough to catch
blockers.

---

## 1. Day 0: bootstrap the repository (do together, about half a day)

### 1.1 Repo setup
- [ ] `git init`, create the shared remote (GitHub/GitLab), protect `main` (PRs only, 1 approval)
- [ ] `.gitignore`: `target/`, `.idea/`, `*.iml`, `.vscode/`, `data/`, `*.keystore`, `deploy/generated/`
- [ ] Maven Wrapper (`mvn wrapper:wrapper`), so everyone and CI use the same Maven
- [ ] `.editorconfig` (4 spaces, LF, UTF-8)
- [ ] CI (GitHub Actions): `./mvnw -B verify` on every push/PR, with Temurin 21
- [ ] `docs/` holds `STACK.md`, `PLAN.md` and the design document; README stub at the root

### 1.2 Maven skeleton
Parent `pom.xml` with `<packaging>pom</packaging>`, Java 21 (`maven.compiler.release=21`), and
`dependencyManagement` + `pluginManagement` pinning all versions from STACK.md §10.

Modules and their dependencies (arrow = "depends on"):

```
common   ← (no internal deps; holds .proto, crypto, config, ALL module interfaces)
channel  → common
fd       → common
broadcast→ common
storage  → common
app      → common, channel, fd, broadcast, storage     (node main + wiring)
client   → common
testkit  → common, broadcast, app, storage            (test scope only)
```

Putting **all interfaces in `common`** means `broadcast` never depends on `channel`'s implementation.
`testkit` can then plug a simulated network in under BRB and the ledger, and the real TCP channel is
only wired in `app`.

Base package: `tfd.pay.<module>` (e.g. `tfd.pay.broadcast`).

### 1.3 Freeze the contracts (the most important Day 0 output)

**`common/src/main/protobuf/messages.proto`**: the wire contract (transfers, node frames with
epoch/linkSeq/HMAC/batching, the `Hello` handshake, client requests/responses with node signatures).
It is already in the skeleton; read it there rather than here, so there is a single source of truth.
Naming: the wire id message is `TxRef`, while the Java domain record is `tfd.pay.common.model.TxId`.
`TxStatus` is used directly as the protobuf enum.

**Java interfaces** in `common` (`tfd.pay.common.api`, from STACK.md §9): `Channel`, `Broadcast`,
`FailureDetector`, `Ledger`, `Storage`, `BrbWal`, plus the records `NodeId`, `AccountId`, `TxId`
(`tfd.pay.common.model`) and a `Quorums(n, f)` helper:

```java
public record Quorums(int n, int f) {
    public Quorums { if (n < 3 * f + 1) throw new IllegalArgumentException("n < 3f+1"); }
    public int echoToReady()    { return (n + f + 2) / 2; }  // ceil((n+f+1)/2)
    public int readyAmplify()   { return f + 1; }
    public int readyToDeliver() { return 2 * f + 1; }
    public int clientConfirm()  { return f + 1; }
}
```

**Config format** (`deploy/config/cluster.yaml`), so everyone codes against the same shape:

```yaml
n: 4
f: 1
nodes:
  - { id: 0, host: node0, nodePort: 7000, clientPort: 8000, publicKey: "<base64 X.509>" }
  # ... one entry per node
hmacKeys:            # one entry per unordered pair i<j
  "0-1": "<base64 32 bytes>"
genesis:
  - { account: "<base64 X.509 public key>", balance: 1000 }
wallet:
  submitFanout: 2    # default f+1
  resendIntervalMs: 2000
```

Each node also gets its own `node-<id>.key` (its private Ed25519 key), which is **not** in the shared YAML.

- [ ] `messages.proto` merged, generated classes compile
- [ ] Interfaces + records + `Quorums` merged, with a unit test for `Quorums` at (4,1) and (7,2)
- [ ] `Crypto` util: Ed25519 keygen/sign/verify, X.509 encode/decode, SHA-256, HMAC-SHA256 (with tests)
- [ ] `Config` loader (YAML → records) + `n ≥ 3f+1` check
- [ ] **After this, interface changes need a PR approved by both other owners**

---

## 2. Week 1: modules in isolation (24 – 30 Nov)

### A: `channel` + `fd`
- [ ] TCP listener + outbound connector per peer, one virtual thread per connection; reconnect with backoff
- [ ] `Hello` handshake with HMAC; reject unknown senders and bad MACs
- [ ] Reliable path: `linkSeq`, resend buffer, cumulative `Ack`, dedup window, resend after reconnect
- [ ] **Epoch handling**: when a peer's epoch increases, reset that peer's link state
- [ ] Unreliable path for `Heartbeat`/`Ack`: no linkSeq, no resend buffer
- [ ] Frame batching: drain the outgoing queue into one `Frame` up to N messages / M bytes
- [ ] `fd`: heartbeat sender + adaptive-timeout ◇P (increase the timeout for a peer after each false suspicion); `onChange` callback
- [ ] Hook `fd` → `channel`: backoff on resend to suspected peers (never drop)
- **Tests**: two channels over loopback, (1) in-order exactly-once delivery; (2) kill and reopen the socket mid-stream → no loss, no duplicates; (3) a frame with a bad HMAC is dropped; (4) epoch bump resets dedup; (5) heartbeats stop → peer suspected; resume → unsuspected

### B: `testkit` + `broadcast`
- [ ] **`testkit` first (by Tue 25 Nov)**: `SimNetwork` implementing `Channel` for N in-process nodes, with a seeded random delay/reordering scheduler, plus `crash(node)`, `partition(...)`, `dropFrom(node)`, `intercept(node, fn)` for Byzantine behaviour. Deterministic given a seed.
- [ ] `Bracha` BRB per `TxId`: SEND/ECHO/READY/deliver using `Quorums`; ECHO only for the first validly signed tx per id; READY once per id; deliver once per id
- [ ] **GC**: on delivery, drop the counters and keep a `delivered(id)` marker; `ledger` tells BRB when `seq < next[src]` so the marker can go too
- [ ] **WAL hook**: call `BrbWal.record(...)` before handing ECHO/READY to the channel; on startup, `load()` and pre-mark those ids as already echoed/readied, then re-send them
- [ ] Byzantine modes, selected by flag: `omit` (sends nothing), `fake-echo` (ECHO/READY with garbage txs), `equivocate` (given 2 conflicting signed txs, sends a different one to each half)
- **Tests** (parameterised over (4,1) and (7,2), several seeds): validity, consistency, totality and integrity; `f` crashed nodes; `f` Byzantine nodes in each mode; client equivocation → at most one tx delivered per id; duplicate SENDs from several nodes → delivered once

### C: `storage` + `app` (ledger) + `client`
- [ ] `storage`: append-only applied-transfer log (`FileChannel`, length-prefixed records, `force(true)`); replay on startup. `BrbWal` implementation with group commit.
- [ ] `Ledger`: `onDeliver` with the 4-step validation; pending set keyed by `src` (waiting on seq) + re-evaluation on credit (waiting on balance); `status(id)`, `nextSeq`, `balance`, `history`
- [ ] Node-side client API server (protobuf over TCP), with signed responses
- [ ] `client` (picocli): `keygen`, `transfer`, `status`, `balance`, `history`, `next-seq`, plus the malicious flag `--double-spend`; keystore file (private key, next seq, last unconfirmed signed tx)
- [ ] Wallet submission: persist → send to `submitFanout` nodes → poll `TxStatus` incrementally → confirm on `f+1` `APPLIED`; resend the same bytes every `resendIntervalMs`
- **Tests**: ledger unit tests (out-of-order seq, insufficient funds then credit, replay discarded, conservation); storage replay round-trip; wallet confirmation logic against fake nodes (f of them lying / silent)

**M1 check (Sun 30 Nov)**: all module tests green on `main`; `testkit` can run BRB + Ledger
together for N simulated nodes and check convergence.

---

## 3. Week 2: integration (1 – 7 Dec)

- [ ] **(C, with B)** Node `main` in `app`: load config + key, build the components, single event-loop thread, and route everything through the `BlockingQueue`. Signatures are verified on the receiving threads before enqueueing.
- [ ] **(A)** `Dockerfile` (`eclipse-temurin:21-jre`, shaded JAR) + `docker-compose.yml` for n = 4 with a data volume per node
- [ ] **(A)** `deploy/keygen.sh` (or a `client keygen-cluster` subcommand): generate node keys, HMAC keys, demo wallet keystores, and `cluster.yaml` with genesis balances, **for any n, f**
- [ ] **(all)** First end-to-end run: `docker compose up` → `wallet transfer` → confirmed by `f+1` nodes → balances match on all 4
- [ ] **(B)** Byzantine flag wired through `main` (`--byzantine=...`); equivocate mode reads the conflicting txs from a file produced by `wallet --double-spend --export`
- [ ] **(C)** WAL restart path: `docker kill node2 && docker start node2` → the node reloads its WAL and does not equivocate
- [ ] **(B)** Convert the `testkit` property tests into an **integration test** that starts N real nodes in-process over real TCP (localhost)
- [ ] Logging: every line has node ID, `(src, seq)` and message type, so demos can be followed in `docker compose logs`

**M2 check (Sun 7 Dec)**: demos 1 and 3 work in Docker, with a script.

---

## 4. Week 3: faults, demos, n = 7, docs (8 – 11 Dec)

- [ ] Demo scripts in `demo/`, one per demo, each printing the expected outcome and checking it (balances on all correct nodes):
  1. `01-normal-transfer.sh`
  2. `02-double-spend.sh` (malicious wallet, same seq to different nodes → at most one applied, account blocked from that seq)
  3. `03-node-crash.sh` (`docker kill` one node mid-traffic → transfers still confirm)
  4. `04-byzantine-node.sh` (node in `equivocate` / `fake-echo` mode → final balances identical on the correct nodes)
  5. `05-wal-restart.sh` (optional)
- [ ] n = 7 compose file generated by the keygen script; run demos 1, 3 and 4 with 2 faulty nodes
- [ ] Property tests 1–7 (STACK.md §6) green for (4,1) and (7,2)
- [ ] **README.md**: prerequisites, build, keygen, `docker compose up`, wallet usage, how to run each demo, how to run the tests
- [ ] **Updated design document** (Portuguese, same template as Phase 1): every **[updated]** item in STACK.md, the answers to the professor's feedback, a scalability section (STACK.md §7.1), and the outcome of the open question (STACK.md §3.1)

**M3 check (Thu 11 Dec)**: everything above merged.

---

## 5. Submission (12 – 13 Dec)

- [ ] **Code freeze on Fri 12 Dec**; only bug fixes and docs after that
- [ ] Fresh clone on a clean machine → follow the README word for word → all demos pass
- [ ] Zip: code + README + design PDF (+ a short demo video, optional but useful)
- [ ] Submit on Moodle by **13 Dec** (aim for the 12th)

---

## 6. Working agreements

- **Branches**: `feat/<module>-<thing>`, small PRs, 1 reviewer from another track, CI green to merge.
- **Interfaces in `common`**: changes need both other owners' approval (prevents late integration pain).
- **Definition of done** for a task: code + unit tests + passing CI + logged with node/tx context.
- **Never block on another track**: code against the interface and use `testkit` fakes until the real module lands.
- **Determinism**: simulated tests take a seed; print it on failure so the run can be reproduced.

---

## 7. Risks and fallbacks

| Risk | Fallback |
|---|---|
| Channel reliability (reconnect + dedup + epoch) takes longer than planned | Integrate BRB over `testkit` and a simple TCP channel without resend first; add resend/epoch afterwards. The demos still work without injected link loss. |
| WAL restart is buggy | It is optional (demo 5). Fall back to pure crash-stop and document it. |
| Docker networking issues | Run the n nodes as local processes via a script; keep Docker for the final demo. |
| `n = 7` reveals bugs late | Parameterised simulated tests catch them from Week 1, which is why `testkit` comes first. |
| One member unavailable for a few days | Interfaces are frozen and modules are isolated; reassign that track's remaining checklist items. |

---

## 8. Open items to settle

- [ ] **Ask the professor** the crash-recovery question (STACK.md §3.1) **early in Week 1**, so the
      answer arrives in time to act on it.
- [ ] Settle team names/owners in the table at the top.
- [ ] Choose the repo host (GitHub vs GitLab FCUL) and add all members.
