# bracha-way

A decentralised payment system with no consensus: client transfers are disseminated among `n ≥ 3f+1`
replicas with **Bracha's Byzantine Reliable Broadcast**, one instance per `(src, seq)`. Fully
asynchronous: no failure detector, no timing assumptions.
Project for *Tolerância a Faltas Distribuída* 2026/27 (FCUL).

- **Design and stack:** [`docs/STACK.md`](docs/STACK.md)
- **Implementation plan, owners and milestones:** [`docs/PLAN.md`](docs/PLAN.md)

## Layout

| Module | Responsibility |
|---|---|
| `common` | `.proto` wire contract, crypto (Ed25519/HMAC/SHA-256), config, **all module interfaces** |
| `channel` | Reliable authenticated node-to-node links (TCP + HMAC + epoch + ACK/resend/dedup) |
| `broadcast` | Bracha BRB per `(src, seq)`, Byzantine modes |
| `storage` | Applied-transfer log (+ ECHO/READY write-ahead log in the crash-recovery increment) |
| `app` | Ledger (validation, FIFO, pending, versions, replication), client API, node main + event loop |
| `client` | Wallet CLI + keystore + sync |
| `testkit` | Deterministic simulated network and fault injection for tests |
| `deploy/` | Docker Compose, config, keygen script |
| `demo/` | Demo / fault-injection scripts |
| `docs/` | Stack, plan, design document |

## Build

Requires **JDK 21** (CI uses Temurin 21). Newer JDKs work only if they ship `lib/ct.sym`. Ubuntu's `openjdk-25` package doesn't, and fails with
`release version 21 not supported`; in that case point `JAVA_HOME` at a JDK 21:

```sh
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

```sh
./mvnw verify                 # build + tests
./mvnw -DskipTests package    # app/target/node.jar, client/target/wallet.jar
```

## Run

_TODO (Week 2): keygen, `docker compose up`, wallet usage, demos._
