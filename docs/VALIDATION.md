# Verification — 2026-10-20

The standalone project was created outside EchoSpeak. No EchoSpeak application code, configuration or integration was modified. The user requested public GitHub visual inspection; the public EchoFace design was used as a reference for original white-cube assets. For the requested guide, only the explicitly invoked local project-documentation skill was read from its skill folder.

## Smaller cube and animation release 0.5.0

The build passes with 29 Java tests and 22 headless Minecraft GameTests. Eight new pure state-machine tests cover idle timing, movement wake-up, working/planning, danger, task interruption, greeting conditions, curiosity and clearing transient state. Two new real-world tests verify half-size physical bounds, sleep followed by owner movement and native navigation, and sleep interrupted by actual crafting while preserving Pause and Stay.

The animation is server-selected and synchronized through tracked pose/start time. Source inspection confirms commands, owner interaction, tasks and nearby priming cues wake the cube; the renderer uses the same state to close eyes and move the body. Sleep state is intentionally not stored in NBT. Existing carry, memory, hearing, combat, placement and ownership tests continue to pass. No models or voice services were installed or invoked.

## Setup release 0.4.0

The 0.4.0 build and all 20 headless Minecraft GameTests pass. All 21 Java tests pass, including seven new setup checks: candidate-copy isolation and validation, atomic config serialization, proxy-aware check URLs, exact model-ID matching without inference, voice readiness with unloaded weights, conversation-only planner diagnostics that reject actions, and rejecting health responses without ready status. HTTP tests use local fixtures, not downloaded models.

Server setup commands require operator permission level 2 and support the console. Remote clients have no server config write packet. Source checks confirm page-specific saves, integrated-server thread handoff, bounded asynchronous diagnostics, sanitized errors, and stale inference/audio cancellation after configuration changes. Real dedicated-server operator sessions and real service credentials remain acceptance work.

The native Start, Planner, Kev, Voice and World pages were visually inspected in the development client. Title-menu entry and page navigation worked; saving the Planner and Voice pages wrote the expected JSON while all inference/audio flags remained off. Actual Minecraft F2 captures of [Planner](../artifacts/media/echo-setup-planner.png) and [Voice](../artifacts/media/echo-setup-voice.png) are included in the visual guide. No microphone was opened and no live service inference check was clicked. Integrated-server hot-save, in-world F8/client commands and dedicated-server operator interaction still need a complete live acceptance pass; their code paths compile and were inspected.

The updated visual guide passes browser checks at 1440-pixel desktop and 390-pixel mobile widths, plus desktop dark mode: all four images decoded, relative HTML links resolved, no page errors occurred and there was no document-level horizontal overflow. The setup sections were also visually inspected in all three renders. Earlier cube screenshots remain genuine 0.3.0 captures of the unchanged cube assets.

The six Python route contract tests remain prior-delivery evidence; the voice service was unchanged and was not reinstalled or run for this release. No models or inference runtimes were installed/downloaded. Actual inference, microphone/headset behavior and service-specific aliases remain unverified.

## Verified

| Check | Result | Scope |
|---|---|---|
| Java 21 + Gradle 8.10.2 `build` | Passed | Main and client compilation, JAR remapping, source JAR, JUnit tests |
| Java protocol/backend, setup and idle tests | 29 passed | Closed action schema, counts/coordinates/size limits, remote endpoint policy, mock Gemma HTTP, oversized bodies, stalled-body deadline, native Kev request/response contract, sound deduplication/expiry/budgets |
| Fabric/Minecraft 1.21.1 headless GameTest | 22 passed | Actual server world and game mechanics, carried-cube state, arrival/parking, distant hearing and dimensional recall; no real models |
| Python voice route tests | 6 passed | Direct route/auth/body-limit contracts with inference workers mocked; not live speech or a deployed HTTP service |
| Python syntax | Passed | Voice service and texture generator |
| Gradle distribution integrity | Passed | Downloaded distribution matches upstream SHA-256; wrapper checksum pinned |
| Remapped mod archive inspection | Passed | Correct metadata/version, main/client classes, texture, native Kev adapter, bounded HTTP transport; game-test classes excluded |

The first twenty headless Minecraft tests cover:

1. Real ordinary crafting consumes one oak log and produces four planks.
2. Vanilla block placement changes the world and consumes one owned block even for a creative-mode owner.
3. Mining physically breaks stone, yields vanilla cobblestone, and wears the correct pickaxe.
4. Denied mining preserves the block and creates no loot.
5. Inventory, ownership, permissions and remembered locations survive entity NBT serialization.
6. Echo follows an owner using actual ground navigation.
7. Echo damages a hostile targeting its owner through native melee attacks.
8. A queued gather → craft → place objective completes on server ticks with exact material accounting.
9. A partly completed crafting task saves its remaining batch count, reloads paused, and resumes without repeating the completed batch.
10. Picking up/deploying the cube preserves identity, health, inventory, permissions and remembered locations; creative deployment consumes the item.
11. Canonical carried state survives ledger NBT roundtrip; copied item tokens cannot duplicate an already deployed companion.
12. A full player inventory leaves the world companion present and unpaused.
13. Obstructed deployment preserves the item and canonical carried state.
14. First arrival creates one nearby cube; another arrival preserves that identity.
15. Disconnect parking and re-arrival preserve inventory and UUID, with parked ledger serialization.
16. Arrival never unpacks or duplicates a deliberately carried cube.
17. A missing/unresolved indexed UUID fails safely instead of creating a duplicate.
18. An actual native server sound is recorded near Echo and reaches planner context while the owner is far away.
19. A distant sound is excluded from Echo's local hearing.
20. The phrase “Echo, teleport over to me!” recalls the same cube from the Nether to the owner with inventory intact.

0.3.0 also compiles private `@echo` chat routing, text/voice unification, explicit recall cancellation, bounded source-chunk reconnection and master/voice-volume-scaled client TTS. These client/network/audio paths are not a substitute for manual multiplayer/headset validation. The six Python service contract tests are retained from the previous delivery; the Python inference service is unchanged.

The cross-dimension test exposed that vanilla UUID lookup can exclude entities in inactive chunks. The final implementation adds lifecycle-bound weak references, validates ownership/server identity/removal state, and keeps the same UUID through native transfer. Its final test passes; real cold chunk activation and slow-disk timeout behavior still need acceptance testing. Remapped sound/lifecycle mixin mappings are included and inspected in the JAR.

The custom cube model and 3D held-item model compile and are packaged. The generated design preview was visually inspected against the public face proportions/colors, and the Blockbench JSON contains one cube and embedded textures. Blockbench itself was not interactively tested.

The Fabric development client was launched in a disposable creative superflat world. Automatic arrival appeared in chat; the white cube and black eyes were visibly confirmed. `/echo carry` removed the entity and produced the owned item; inventory and third-person held textures were visibly confirmed. Using the item on clear ground restored the companion and reported its paused task; placing inside the player was safely rejected. Actual Minecraft F2 captures are [world cube](../artifacts/media/echo-in-game.png) and [held cube](../artifacts/media/echo-held.png). These original game PNGs are not procedural previews. The client exposed missing item-atlas entries; `assets/minecraft/atlases/blocks.json` now explicitly registers both item textures, and reloading resources removed that warning. First-person scale was reduced; this view does not reliably expose the face. Every animation, both hands and survival play have not been fully acceptance-tested.

The rebuilt mod passes `build` after these resource changes. The visual guide was rendered and inspected at 1440-pixel desktop width, 390-pixel mobile width and in dark mode. Images decoded, relative links resolved, no page errors were reported and the document had no horizontal overflow. The wide evidence table intentionally scrolls within its own container on mobile. QA evidence is under `build/guide-qa/`, excluded from distribution.

Test evidence is copied under `docs/test-results/`. The Minecraft tests use mock server players in a real headless test world, not real remote players. The mod JAR was compiled/remapped and inspected; it has not been launched in a packaged production client/server installation.

Initial compile/test issues were corrected before these results: a Yarn protected-method access, an explicit Gson test dependency, and a placement fixture that put its mock player inside the target block. The Python sandbox's Windows async event loop stalled; the final route tests ran successfully outside that sandbox with no model imports or downloads.

The test server emits Minecraft's `No data fixer registered for echo:companion` log during entity registration. NBT persistence is exercised; 0.3.0 includes a narrow legacy last-location-to-anchor conversion, rather than a comprehensive historical migration system. Minecraft also reports a deprecated mock-player helper in test compilation. Neither prevented the test server or tests from passing.

## 0.5.0 held pose and animation release

The cube remains 0.3125 blocks wide and tall. The native first-person item transform now uses pitch -18 degrees, yaw 145 degrees, vertical offset 5.25 pixels and inward offset 2 pixels and scale 1.05 in both hands. Minecraft mirrors the left-hand transform; its entry intentionally matches the right. Compared with hand scale 0.55, the linear size is 1.91 times larger. Resting-pose camera-space checks use Minecraft 1.21.1 equip offsets and mirrored item transforms to verify that the textured face points at the camera and the bottom face is hidden in both hands. These checks do not substitute for live rendering or cover swing/equip transitions.

The additional two GameTests exercise safe-idle sleep followed by owner movement and real following, and task interruption with crafting progress. Eight CompanionIdle unit cases cover timing, safety, Stay/Pause behavior and connected pose priorities. Existing screenshots are historical development-client captures; there are no new 0.5.0 gameplay screenshots. New model inference, voice and multiplayer acceptance remain outstanding.

## Not verified live

- Gemma 4 12B inference, actual backend alias/JSON compatibility and planning quality.
- Kev-4B inference, latency and decision accuracy. The native System One schema is verified using an HTTP fixture.
- Parakeet/Kokoro dependency installation, model loading, transcription/synthesis, GPU compatibility or performance. No models were downloaded or installed.
- A real microphone/headset, native audio permission behavior, TTS playback and latency.
- Complete animation coverage, both-hand acceptance, survival play and a packaged production client installation. Development-client entity and held-item rendering were checked as described above.
- Multiple real connected players, third-party claim mods, dedicated production deployment, long-distance terrain, sustained performance or long-running saves.

## Manual acceptance pass

1. In a new disposable 1.21.1 Fabric instance, install Echo and the pinned Fabric API. Verify Echo renders as a white cube with two black pill eyes, blinks, looks, moves and swims. Sneak-right-click to pick up, hold in both hands, and use on solid ground to deploy; verify inventory/health/memory and pause/resume. Check cross-dimension carrying and lost-item recovery.
2. Give tools/materials, grant mining/building, run the README's gather/craft/build sequence and inspect inventory/blocks/durability. Try obstacles, full inventories, missing tables/tools and permission revocation.
3. Pause/cancel while moving/mining, during a slow planner request and while fighting. Save/restart, resume remaining work, test unloaded-chunk ownership and death drops.
4. Connect two actual players, summon one Echo each, verify owner-only control and responses. Test your claim/protection mod before granting edits.
5. Only when you choose to obtain models, enable each configured backend separately. Exercise unavailable, slow and malformed responses; verify the companion's fallback behavior stays responsive.
6. Opt in to microphone/TTS, hold/release V, disconnect during capture, and test device/service failures. Verify typed controls still work.

Capability limits are listed in the README. No claims of unrestricted autonomy, production readiness, live inference quality or finished custom animation are made by these test results.


The final release guide was rendered at 1440-pixel light/dark and 390-pixel mobile widths. All four historical images decoded, links resolved and no page errors or page-wide overflow were detected. Test XML preserves case names/counts while omitting machine hostname and process logs for publication.
