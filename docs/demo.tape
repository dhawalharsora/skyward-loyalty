# docs/demo.tape — records the Skyward demo into a GIF for the README hero.
#
# PREREQS:  run `make up` FIRST (off-camera) and wait until all services are healthy.
# RECORD:   from the repo root →  vhs docs/demo.tape   →  produces docs/demo.gif
#
# Each beat reads: setup comment → command runs → takeaway comment (the "why it matters").
# TUNING: a command's Sleep must be >= its real runtime (under-shooting garbles output). Measured
#         ~1-3s each; the buffers below also leave a beat to read the result.

Output docs/demo.gif

Set Shell       "bash"
Set FontSize    16
Set Width       1200
Set Height      780
Set Theme       "Catppuccin Mocha"
Set Padding     24
Set TypingSpeed 25ms

# ── Title ───────────────────────────────────────────────────────────────────
Type "# Skyward Loyalty — three integration patterns, live:  Outbox · Saga · Strangler Fig"
Enter
Sleep 2s

# ── Beat 1: Strangler Fig ────────────────────────────────────────────────────
Type "# 1) STRANGLER FIG — one endpoint GET /members/{id}/tier, routed: legacy SOAP vs new REST"
Enter
Sleep 1700ms
Type "make demo-strangler"
Enter
Sleep 6s
Type "#    ^ same request, config-driven & sticky per member — migrate off legacy with zero downtime"
Enter
Sleep 2500ms

Type "clear"
Enter
Sleep 500ms

# ── Beat 2: Accrual via Transactional Outbox ─────────────────────────────────
Type "# 2) ACCRUAL (Outbox) — earning rules -> ledger + outbox in 1 tx -> Kafka -> balance"
Enter
Sleep 1700ms
Type "make demo-accrual"
Enter
Sleep 8s
Type "#    ^ replay returned DUPLICATE — at-least-once delivery, never double-credited"
Enter
Sleep 2500ms
