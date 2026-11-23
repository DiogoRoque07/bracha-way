# Implementation Plan: Decentralised Payment System (BRB)

Companion to `STACK.md`, which says **what** to build. This file says **in what order, who does it,
and when it counts as done**. Deadline: **Phase 2 submission on 13 Dec 2026** (Moodle).

**Rule agreed with the professor: base system first.** The base system is fully asynchronous, with
no failure detector, crash-stop, FIFO + versions, and demos 1–5. The increments (§6: crash recovery,
then optionally a perfect failure detector) start **only after the base system is fully working**.

Team of 3: owners **A**, **B** and **C** (fill in names below). Every module has a fixed interface
from Day 0, so the three tracks can proceed in parallel and integrate weekly.

| Owner | Name | Track |
|---|---|---|
| A | ______ | `channel`, `storage` (applied-transfer log), `deploy/` (Docker, keygen/config script) |
| B | ______ | `broadcast`, `testkit` (simulated network), Byzantine modes |
| C | ______ | `app` (ledger, FIFO, versions, client API), `client` (wallet, sync) |
| all | | `common` (Day 0, together), demos, design document, README |

---

## 0. Timeline at a glance

| When | Milestone | Exit criteria |
|---|---|---|
| **Day 0** (by Sun 23 Nov) | **M0: Skeleton** | Repo builds with `mvn verify`; `.proto` and Java interfaces merged and **frozen**; CI green |
| **Week 1** (24 – 30 Nov) | **M1: Modules in isolation** | BRB, ledger (FIFO + versions) and wallet logic pass unit tests over `testkit`'s simulated network; channel passes loopback TCP tests |
| **Week 2** (1 – 7 Dec) | **M2: Integrated base system** | 4 nodes in Docker Compose; wallet transfer confirmed end to end; demos 1 and 3 scripted |
| **Week 3** (8 – 11 Dec) | **M3: Base system complete** | Demos 1–5 scripted; Byzantine modes working; property tests 1–8 pass for (4,1) and (7,2); README and design document drafted |
| *after M3* | **Increments** (§6) | Only once M3 is met. Crash recovery first, then optionally a P failure detector |
| **12 – 13 Dec** | **M4: Submission** | Code freeze on 12 Dec; README + updated design document; submit **by 13 Dec** (aim for the 12th) |

Weekly integration: **every Sunday evening**, merge `main`, run the full test suite and the latest
demo script, and adjust next week's tasks. A 15-minute sync on Wednesdays is enough to catch
blockers.

---

## 1. Day 0: bootstrap the repository

### 1.1 Repo setup
- [x] `git init`, shared remote on GitHub
- [ ] Protect `main` (PRs only, 1 approval) and add all members (manual, in GitHub settings)
- [x] `.gitignore`, `.editorconfig`, Maven Wrapper
- [x] CI (GitHub Actions): `./mvnw -B verify` on every push/PR, with Temurin 21 (check the first run)
- [x] `docs/` holds `STACK.md`, `PLAN.md` and the design document; README at the root

### 1.2 Maven skeleton
Parent `pom.xml` with `<packaging>pom</packaging>`, Java 21 (`maven.compiler.release=21`), and
`dependencyManagement` + `pluginManagement` pinning all versions from STACK.md §10.

Modules and their dependencies (arrow = "depends on"):

```
common   ← (no internal deps; holds .proto, crypto, config, ALL module interfaces)
channel  → common
broadcast→ common
storage  → common
app      → common, channel, broadcast, storage     (node main + wiring)
client   → common
testkit  → common, broadcast, app, storage        (test scope only)
```

There is no `fd` module: the base system has no failure detector (STACK.md §3.1). If the optional P
detector is done later (§6.2), it goes in a new `fd` module.

Putting **all interfaces in `common`** means `broadcast` never depends on `channel`'s implementation.
`testkit` can then plug a simulated network in under BRB and the ledger, and the real TCP channel is
only wired in `app`.

Base package: `tfd.pay.<module>` (e.g. `tfd.pay.broadcast`).

### 1.3 Freeze the contracts (the most important Day 0 output)

**`common/src/main/protobuf/messages.proto`**: the wire contract (transfers, node frames with
epoch/linkSeq/HMAC/batching, the `Hello` handshake, client requests/responses with versions and node
signatures). It is in the skeleton; read it there rather than here, so there is a single source of
truth. Naming: the wire id message is `TxRef`, while the Java domain record is
`tfd.pay.common.model.TxId`. `TxStatus` is used directly as the protobuf enum.

**Java interfaces** in `common` (`tfd.pay.common.api`, from STACK.md §9): `Channel`, `Broadcast`,
`Ledger`, `Storage`, `BrbWal` (increment only; base uses `BrbWal.NONE`), plus the records `NodeId`,
`AccountId`, `TxId`, `AccountView` (`tfd.pay.common.model`) and `Quorums(n, f)`. `Quorums` includes
`safeLowerBound(values)`, the "(f+1)-th highest" rule used by `wallet sync`.

**Config format** (`deploy/config/cluster.example.yaml`):

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
  submitFanout: 2    # in [f+1, n]; default f+1
  resendIntervalMs: 2000
  maxInFlight: 4     # pipelined unconfirmed transfers per wallet (FIFO is kept by seq)
```

Each node also gets its own `node-<id>.key` (its private Ed25519 key), which is **not** in the shared YAML.

- [x] `messages.proto` merged, generated classes compile
- [x] Interfaces + records + `Quorums` merged, with unit tests at (4,1) and (7,2)
- [x] `Crypto` util: Ed25519 keygen/sign/verify, X.509 encode/decode, SHA-256, HMAC-SHA256 (with tests)
- [x] `Config` loader (YAML → records) + `n ≥ 3f+1` check
- [ ] Whole team reviews the contracts → **after this, interface changes need a PR approved by both other owners**

---

## 2. Week 1: modules in isolation (24 – 30 Nov)

### A: `channel` + `storage`
- [ ] TCP listener + outbound connector per peer, one virtual thread per connection; reconnect with backoff
- [ ] `Hello` handshake with HMAC; reject unknown senders and bad MACs
- [ ] Reliable path: `linkSeq`, resend buffer, cumulative `Ack`, dedup window, resend after reconnect; **never drop**
- [ ] `Ack` sent once (no linkSeq, no resend buffer)
- [ ] Frame batching: drain the outgoing queue into one `Frame` up to N messages / M bytes
- [ ] `storage`: append-only applied-transfer log (`FileChannel`, length-delimited records, `force(true)`); `replay()`
- **Tests**: two channels over loopback, (1) in-order exactly-once delivery; (2) kill and reopen the socket mid-stream → no loss, no duplicates; (3) a frame with a bad HMAC is dropped; (4) a peer that is down for a while receives everything once it is back. Storage: append/replay round-trip, including a torn last record.

### B: `testkit` + `broadcast`
- [ ] **`testkit` first (by Tue 25 Nov)**: `SimNetwork` implementing `Channel` for N in-process nodes, with a seeded random delay/reordering scheduler, plus `crash(node)`, `partition(...)`, `dropFrom(node)`, `intercept(node, fn)` for Byzantine behaviour. Deterministic given a seed.
- [ ] `Bracha` BRB per `TxId`: SEND/ECHO/READY/deliver using `Quorums`; ECHO only for the first validly signed tx per id; READY once per id; deliver once per id; runs for any `seq` (no ledger checks)
- [ ] **GC**: on delivery, drop the counters and keep a `delivered(id)` marker; the ledger calls `forget(id)` once `seq < next[src]`
- [ ] Byzantine modes, selected by flag: `omit` (sends nothing), `fake-echo` (ECHO/READY with garbage txs), `equivocate` (given 2 conflicting signed txs, sends a different one to each half)
- **Tests** (parameterised over (4,1) and (7,2), several seeds): validity, consistency, totality and integrity; `f` crashed nodes; `f` Byzantine nodes in each mode; client equivocation → at most one tx delivered per id; duplicate SENDs from several nodes → delivered once

### C: `app` (ledger) + `client`
- [ ] `Ledger`: `onDeliver` with the 4-step validation; **FIFO** pending by seq + pending by balance (re-evaluated on credit); versions `next[a]`, `ver[a]`; `view`, `status`, `appliedAt`, `history(a, fromSeq)`
- [ ] Node-side client API server (protobuf over TCP), signed responses carrying versions; `TransferReply` with `ALREADY_APPLIED` vs `STALE_SEQ`
- [ ] `client` (picocli): `keygen`, `transfer`, `status`, `balance`, `history`, `sync`, plus the malicious flag `--double-spend`; keystore (private key, next seq, last confirmed seq, all unconfirmed signed txs)
- [ ] Wallet submission: persist → send to `submitFanout` nodes → poll `TxStatus` incrementally → confirm on `f+1` `APPLIED`; resend the same bytes every `resendIntervalMs`; pipelining up to `maxInFlight`
- [ ] Wallet freshness: ignore stale answers (read-your-writes, monotonic reads via versions)
- [ ] `wallet sync`: `Quorums.safeLowerBound` over `next[src]`, history filtered by own signature, superseded unconfirmed txs reported
- **Tests**: ledger unit tests (out-of-order seq → FIFO, insufficient funds then credit, replay discarded, conservation, versions); wallet logic against fake nodes (f of them lying / silent / stale); sync with a stale keystore

**M1 check (Sun 30 Nov)**: all module tests green on `main`; `testkit` can run BRB + Ledger
together for N simulated nodes and check convergence and FIFO.

---

## 3. Week 2: integration of the base system (1 – 7 Dec)

- [ ] **(C, with B)** Node `main` in `app`: load config + key, build the components, single event-loop thread, and route everything through the `BlockingQueue`. Signatures are verified on the receiving threads before enqueueing.
- [ ] **(A)** `Dockerfile` (`eclipse-temurin:21-jre`, shaded JAR) + `docker-compose.yml` for n = 4 with a data volume per node
- [ ] **(A)** `deploy/keygen.sh` (or a `client keygen-cluster` subcommand): generate node keys, HMAC keys, demo wallet keystores, and `cluster.yaml` with genesis balances, **for any n, f**
- [ ] **(all)** First end-to-end run: `docker compose up` → `wallet transfer` → confirmed by `f+1` nodes → balances match on all 4
- [ ] **(B)** Byzantine flag wired through `main` (`--byzantine=...`); equivocate mode reads the conflicting txs from a file produced by `wallet --double-spend --export`
- [ ] **(B)** Convert the `testkit` property tests into an **integration test** that starts N real nodes in-process over real TCP (localhost)
- [ ] Logging: every line has node ID, `(src, seq)` and message type, so demos can be followed in `docker compose logs`

**M2 check (Sun 7 Dec)**: demos 1 and 3 work in Docker, with a script.

---

## 4. Week 3: base system complete (8 – 11 Dec)

- [ ] Demo scripts in `demo/`, one per demo, each printing the expected outcome and checking it (balances on all correct nodes):
  1. `01-normal-transfer.sh`
  2. `02-double-spend.sh` (malicious wallet, same seq to different nodes → at most one applied, account blocked from that seq)
  3. `03-node-crash.sh` (`docker kill` one node mid-traffic → transfers still confirm)
  4. `04-byzantine-node.sh` (node in `equivocate` / `fake-echo` mode → final balances identical on the correct nodes)
  5. `05-stale-wallet.sh` (restore an old keystore → `STALE_SEQ` → `wallet sync` → transfers continue)
- [ ] n = 7 compose file generated by the keygen script; run demos 1, 3 and 4 with 2 faulty nodes
- [ ] Property tests 1–8 (STACK.md §6) green for (4,1) and (7,2)
- [ ] **README.md**: prerequisites, build, keygen, `docker compose up`, wallet usage, how to run each demo, how to run the tests
- [ ] **Updated design document** (Portuguese, same template as Phase 1): every **[updated]** item in STACK.md and the three conclusions agreed with the professor. Explain why there is no failure detector (STACK.md §3.1), FIFO + versions (§3.2), and the increments (§3.3, §3.4) as done or as future work. Include a scalability section (§7.1).

**M3 check (Thu 11 Dec)**: base system complete. **Only now do the increments start.**

---

## 5. Submission (12 – 13 Dec)

- [ ] **Code freeze on Fri 12 Dec**; only bug fixes and docs after that
- [ ] Fresh clone on a clean machine → follow the README word for word → all demos pass
- [ ] Zip: code + README + design PDF (+ a short demo video, optional but useful)
- [ ] Submit on Moodle by **13 Dec** (aim for the 12th)

---

## 6. Increments (only after M3)

Each increment goes on its own branch and is merged only if it is complete and all base tests still
pass. Anything not finished goes in the design document as future work.

### 6.1 Crash recovery (first; agreed with the professor as a good increment). See STACK.md §3.3.
- [ ] `storage`: `FileBrbWal` (record + group-commit `flush`, `load`)
- [ ] `broadcast`: record before sending ECHO/READY; on start, load and re-send, never contradicting
- [ ] `app`: rebuild the ledger by replaying the log; `epoch` from a persisted counter, incremented on each start
- [ ] `channel`: on a peer's higher epoch, reset the link state
- [ ] State sync with versions: ask peers for txs with `seq ≥ next[src]`; accept on `f+1` matching answers or `2f+1` READYs
- [ ] Demo `06-node-restart.sh` + property test 9

### 6.2 Perfect failure detector (P), optional; only if communication needs optimising. See STACK.md §3.4.
- [ ] New `fd` module: heartbeats with a **fixed** timeout derived from the assumed synchrony bound
- [ ] `channel`: stop retransmitting to P-suspected nodes and free their resend buffer
- [ ] State the synchrony assumption explicitly in the design document; never use the detector for quorums or the wallet

---

## 7. Working agreements

- **Branches**: `feat/<module>-<thing>`, small PRs, 1 reviewer from another track, CI green to merge.
- **Interfaces in `common`**: changes need both other owners' approval (prevents late integration pain).
- **Definition of done** for a task: code + unit tests + passing CI + logged with node/tx context.
- **Never block on another track**: code against the interface and use `testkit` fakes until the real module lands.
- **Determinism**: simulated tests take a seed; print it on failure so the run can be reproduced.

---

## 8. Risks and fallbacks

| Risk | Fallback |
|---|---|
| Channel reliability (reconnect + dedup) takes longer than planned | Integrate BRB over `testkit` and a simple TCP channel without resend first; add resend afterwards. The demos still work without injected link loss. |
| Wallet sync / versions take longer than planned | Demo 5 is the last base demo; the core (FIFO in the ledger + `f+1` confirmation) comes first. |
| Docker networking issues | Run the n nodes as local processes via a script; keep Docker for the final demo. |
| `n = 7` reveals bugs late | Parameterised simulated tests catch them from Week 1, which is why `testkit` comes first. |
| No time for the increments | Expected and acceptable: they are documented as future work (STACK.md §3.3, §3.4). |
| One member unavailable for a few days | Interfaces are frozen and modules are isolated; reassign that track's remaining checklist items. |

---

## 9. Open items

- [ ] Settle team names/owners in the table at the top.
- [ ] Protect `main` and add all members to the GitHub repo.
