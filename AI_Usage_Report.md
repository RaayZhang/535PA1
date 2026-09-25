# Generative AI Use Report — COMP535 PA1

I used OpenAI Codex as a coding and learning assistant while completing PA1. This report is intended to disclose the extent of that assistance. Codex also helped draft this report based on the project files and the recorded work process.

## How I used AI

- I asked Codex to read the PA1 requirements, the broader Link State Routing project description, and the professor's starter project, then explain what PA1 actually requires. I directed it to focus on router startup output and the `attach`, `start`, and `neighbors` commands, without claiming that later project features were complete.
- I asked for step-by-step explanations suitable for a beginner and for a separate Chinese implementation guide. I asked that the guide identify the current code locations and be updated if code positions change.
- I told Codex to preserve the professor's original Java statements. I allowed necessary additions to existing constructors and methods, as well as new fields, methods, and classes where needed. I also supplied the teaching staff's clarification that standard Java libraries are allowed and that the implementation design and build/run/test commands should be described in a README.
- Codex generated the PA1 implementation additions in `Router.java`, `RouterDescription.java`, `Link.java`, and `SOSPFPacket.java`, including socket communication, the manual `Y/N` response to `attach`, the HELLO exchange for `start`, and filtering `neighbors` by `TWO_WAY` state. It also wrote comments in the added code and drafted the separate root-level `README.md` and Chinese guide. The original `comp535_sketch_code/README.md` was kept unchanged.
- Codex compiled the Java sources and ran automated multi-process checks covering accepted and rejected attach requests, HELLO state transitions, neighbor output, and a seven-router chain. These checks were created and run as development aids, not as a substitute for the course's live demonstration.

## What the AI-generated implementation does and does not do

The submitted PA1 additions let router processes communicate through Java sockets, store accepted links in the four-port array, move attached neighbors through the HELLO states to `TWO_WAY`, and display only `TWO_WAY` neighbors. The implementation uses standard Java networking, object streams, and concurrency utilities. It does not implement Link State Database synchronization, Dijkstra shortest paths, or the later `connect`, `disconnect`, `detect`, `send`, `update`, and `quit` features described in the broader project document.

I used Codex's explanations and testing results to inspect the implementation and prepare to explain it. Responsibility for reviewing the submitted code, running the required demonstration, and answering questions about it remains with me. This report itself was drafted with generative AI assistance.
