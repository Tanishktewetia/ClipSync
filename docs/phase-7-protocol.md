# Phase 7 implementation note: protocol, discovery and reconciliation

This describes actual code without silently changing architecture.md. Previous phases shipped CSP1, not the aspirational JSON wire format. Phase 7 extends the existing framing and retains basic Phase 6 TEXT compatibility.

## Wire format

Header: ASCII CSP1 (4 bytes), type (1 byte), signed big-endian payload length (4 bytes). Negative lengths and payloads over 1,048,576 bytes are rejected. Types 1–5 retain TEXT, HELLO, ACK, PING and IMAGE; images remain unimplemented.

After the existing TLS 1.3 / READY bootstrap, Android sends HELLO with the existing device-ID field set to **cs7|<latest-origin>|<replay>**. Replay is 1 or 0. The Lamport field is the latest retained version counter, or zero with an empty origin when no payload is retained. No clipboard text appears in HELLO. Windows replies with the same capability marker. Only negotiated sessions use STATE/PONG; old peers retain basic TEXT, not Phase 7 heartbeat or reconciliation guarantees.

- **STATE = 6:** positive int64 big-endian counter, uint16 big-endian origin length (1–256 bytes), UTF-8 origin, then UTF-8 text. Counters near signed overflow are rejected. Text limit is 1 MiB minus 266 bytes for bounded metadata.
- **PONG = 7:** empty. PING remains type 4.
- Shared test vector: STATE(counter 7, origin a, text hi) = 43535031060000000d00000000000000070001616869.

Message-ID delivery ACK/resend was not in the shipped Phase 6 transport and is not newly claimed here. A completed socket write is not proof that the other platform's clipboard changed. Retained version exchange repairs interrupted sessions. A failed clipboard sink invalidates that failed retained record so it remains replayable on reconnect.

## Newest known text

Each side owns a memory-only latest record and Lamport clock. Local captured updates advance the clock; observing a remote clock uses max(local, remote) + 1. Equal version counters use ordinal origin ordering. HELLO clocks are observed even if replay is off, so new live copies are not stuck behind the peer's clock.

Initial replay occurs only when the retained record is newer than the peer's advertised record and the peer permits replay. Stale/duplicate versions are ignored. Live updates continue when reconnect replay is disabled. Pause clears retained pending text; replacement pairing does not share the prior peer's retained payload. Clipboard hash echo guards remain wired in.

Phone capture is always deliberate. An enabled, paired phone can retain an explicit notification/tile read while disconnected; a copy without that tap is unknowable. PC clipboard events can be captured while disconnected. Text is never written to disk and is not restored after service/process death or reboot. Conflict order is deterministic and causal, not wall-clock last-copy ordering.

## Discovery

Android races actual Wi-Fi hotspot gateway 192.168.137.1 (only when that route exists), optional manual IPv4, last authenticated address/port, and IPv4 NSD results for _clipsync._tcp. At most 16 unique candidates per 14-second search window; probes have 2.5-second connect/read deadlines. All sockets use the Wi-Fi socket factory and existing Keystore/pin policy. Losing probes are cancelled and closed.

The winning endpoint gets a fresh ordinary authenticated session. Only the real session confirms pairing and saves trust/address. Probes can produce short-lived READY/PAIR connections in Windows logs but never save a new pin. Keystore initialization is synchronized; corrupt protected trust storage fails before probing. Discovery does not grant trust.

Windows uses native DnsServiceRegister instead of the old one-off plain multicast string. The actual computer hostname lets Windows supply resolvable address records. The native service manages DNS-SD advertisement; disposal requests deregistration. A host-side test registers, resolves IPv4+port through DnsServiceResolve, and connects to the isolated advertised listener. This does not prove Android/router/hotspot reachability.

## Lifecycle and liveness

Wi-Fi callbacks, enabled state, foreground notification, WifiLock and boot/unlock recovery remain service-owned. Backoff: 1, 2, 5, 15, 30 seconds capped, reset after success; a search's own duration is additional. Negotiated peers exchange PING/PONG every 15 seconds and retire the connection after two missed replies. Android has an independent close watchdog for stalled writes. Explicit pairing failures do not trigger silent trust replacement.

## Validation boundaries

Real TLS tests cover legacy pairing/text, newest retention, replay opt-out, observed clocks, duplicate rejection, pause, failed sinks and missed heartbeat shutdown. Both core suites share a wire-vector expectation. Native Windows DNS-SD is tested on the development host. Android NSD, OEM background behavior, real network transitions, and phone visuals still require manual acceptance.
