# Echo — Minecraft Java 1.21.1 / Fabric

An independent, original companion mod. Echo is a small off-white cube with two black pill-shaped eyes, no mouth and no humanoid limbs. He has a practical personality, blinking/look/bob animations, a real inventory, and server-controlled actions. His face follows the public EchoSpeak design; the Minecraft assets and model are original. No EchoSpeak application code or integration is used. Verity JE is behavioral inspiration only; no assets or source were copied.

This is **0.5.0**, an implemented foundation with bounded gameplay skills, not unrestricted human-level Minecraft automation. Read the capability limits below.

Download the mod JAR and complete source archive from [GitHub Releases](https://github.com/Ty0x7/EchoMC/releases).

## Install

1. Use Minecraft **Java 1.21.1**, **Java 21**, Fabric Loader **0.16.14 or newer**, and [Fabric API 0.116.6+1.21.1](https://modrinth.com/mod/fabric-api/version/0.116.6+1.21.1).
2. Put `echo-minecraft-0.5.0.jar` in the instance's `mods` directory. On multiplayer, install Echo and Fabric API on **both the server and each client**. It is not a vanilla-client mod.
   When upgrading, remove the old Echo JAR first. Existing companions keep the same entity ID and become cubes; back up your world before upgrading.
3. Start the game/server once to generate `config/echo-server.json`. Client launch also generates `config/echo-client.json`.
4. Join the world: Echo automatically appears beside you once safe nearby ground is available. Each player has one companion per world save. Existing Echo is reused; a carried cube stays carried. Use `/echo here` to retry arrival or recall him. `autoSpawnOnJoin: false` disables automatic arrival. `/echo summon` remains a manual first-creation fallback.
5. Hold a tool or material stack and use `/echo give` within four blocks. The actual stack transfers into Echo's 27-slot inventory. `/echo equip minecraft:iron_sword` equips an owned item; mining chooses an appropriate tool automatically.
6. Grant only the capabilities you want: `/echo allow mine true`, `/echo allow build true`, `/echo allow explore true`. Combat defense starts enabled; `/echo allow combat false` disables it.

Echo functions without any model server. AI and voice are **off by default**. This project does not contain model weights, install inference runtimes, execute shell commands, or automatically download models. The optional voice service downloads weights only if you separately install/start it and call its inference endpoints.

## Smaller cube and connected animations in 0.5.0

Echo is now **0.3125 blocks wide and tall**, half his previous dimensions. The entity mesh, collision box, shadow, held item and editable Blockbench geometry agree. In first person the held cube tilts upward toward the player and turns inward in either hand. Its hand scale is now 1.05 instead of 0.55 (1.91 times larger), with the resting pose designed to show the face without the underside. The world cube remains small. Final live both-hand acceptance is still pending.

After **600 safe idle server ticks** (about 30 seconds at 20 TPS), Echo closes his eyes, leans over and breathes slowly. The owner must be nearby and still, with Echo on dry ground, navigation stopped, no task/planning and no threat. Moving wakes him and normal following resumes if Follow was already enabled. Chat, commands and owner interaction also wake him. Stay remains Stay; Pause does not get silently cancelled.

Three additional connected animations are included:

- **Reunion greeting:** a small visual hop/wiggle after being at least eight blocks apart and settling close together again in Follow mode.
- **Curious glance:** a short tilt/look around during safe idle, also used while waiting for a planner reply.
- **Working rhythm:** a small nod/bounce while stationary mining, crafting or placing, driven by the actual task queue.

Movement keeps its gait bob; nearby threats trigger an alert posture. These are lightweight server-selected visual states, not extra model calls. Tasks, threats, water, fire and damage prevent sleep. Sleep is transient and is not persisted as a gameplay task, so carrying, rejoining and recalling keep their existing ownership/inventory behavior. Carried Echo remains an inactive item.

## Easier setup in 0.4.0

Open **Echo setup** from the title or pause menu, press **F8**, or use **`/echo-setup`** in game. The native screen has Start, Planner, Kev, Voice and World pages. **Save page** applies that page without restarting the mod; **Done** closes without saving additional draft changes. The screen does not install or launch inference software.

1. Start your separately installed services. In **Planner**, enter the full chat endpoint and the exact model ID your service exposes. **Check model list** is a GET request. **Test reply (inference)** checks Echo's JSON reply contract using a synthetic prompt. Turn Planner on and save.
2. In **Kev**, keep the native `/v1/systemone` URL and your server's model alias. Its runtime must load Kev-4B separately. **Test System One (inference)** checks the real native schema. Enable and save only when you want it; vanilla defense works with Kev off.
3. In **Voice**, enter the Parakeet `/transcribe` and Kokoro `/speech` routes. Check each service's health. Enable **Mic** for hold-V input and **Speech** for spoken replies, then save. These toggles are independent. Health checks never capture audio or request synthesis.
4. **World** controls automatic arrival on future joins, hearing radius and planner timeout. These settings apply across the instance/server; companion memory remains world-specific. Player mining/build/explore permissions remain separate `/echo allow` controls.

Planner/Kev run from the Minecraft **server machine**. Voice services run from **each player's client machine**. `127.0.0.1` means that machine, not the other end of a multiplayer connection. Singleplayer hosts edit their local server settings directly. A remote multiplayer client can edit only its voice options; server operators use **`/echo-config`** from their console or operator chat.

The model-list check does not prove inference works. Voice health can be ready while weights remain unloaded. Inference tests are explicit and may make an external service load/download its configured weights; leave those buttons alone until you intend to run inference. No models were installed or downloaded during this update.

API-key fields accept **environment variable names**, never the actual key. A service without authentication may leave those variables unset. If a key is needed, set its value before launching Minecraft/server. Changing the environment requires restarting that process; editing URLs, aliases and toggles does not.

Dedicated-server examples (operator permission level 2, or console):

```text
/echo-config status
/echo-config set planner-url http://127.0.0.1:8080/v1/chat/completions
/echo-config set planner-model your-exact-loaded-model-id
/echo-config test models
/echo-config test planner
/echo-config set planner-enabled true
/echo-config set kev-url http://127.0.0.1:8009/v1/systemone
/echo-config set kev-model kev-latest
/echo-config test kev
/echo-config set kev-enabled true
/echo-config set auto-arrival true
/echo-config set hearing-radius 32
/echo-config reload
```

`set` validates, saves and applies immediately. `reload` rejects malformed files and keeps the current runtime configuration. Pending inference from an older configuration is discarded. Connection errors give a short next step without printing keys or response bodies. GUI checks test the draft; they do not enable a feature or save it automatically.

## Play immediately, without models

```text
/echo here
/echo chat Hello Echo
/echo senses
/echo follow
/echo stay
/echo mine minecraft:oak_log 3
/echo craft minecraft:oak_planks 3
/echo place minecraft:oak_planks ~2 ~ ~
/echo platform minecraft:oak_planks ~2 ~ ~ 3 3
/echo deliver
/echo goto ~8 ~ ~
/echo allow explore true
/echo explore
/echo remember camp
/echo recall camp
/echo pause
/echo resume
/echo stop
/echo status
/echo locate
```

`mine` count means blocks broken. `craft` count means recipe executions (three log-to-plank recipes produce twelve planks). Place at empty cells adjacent to a solid face, within three blocks of Echo; the platform command queues up to 32 placements. Relative coordinates resolve against the player when the command is issued. Echo walks toward each target and stops with a reason if obstructed or out of materials. Position the platform where Echo can stand beside it; it does not fly, scaffold itself, or place inside entities.

Only exposed blocks in a local 17×9×17 search area are mined. Tool requirements, hardness, durability, loot tables, enchantment-sensitive drops and XP hooks apply. Echo will not mine block entities, unbreakable blocks, fluids, or the block directly beneath itself/the owner. Mining permission also enables nearby dropped-item gathering. Mining can damage player builds: grant it intentionally.

Crafting uses the server's ordinary shaped/shapeless recipe system and consumes ingredients; larger recipes require a visible crafting table within three blocks. Recipe remainders are returned. Overflow drops into the world. Furnace/smithing/stonecutting and special dynamic recipes are not implemented. Ingredient selection is greedy and may reject ambiguous recipes that could work with a different ingredient assignment.

Combat uses native melee attacks and navigation against hostile mobs targeting Echo or the owner. It never targets players, passive mobs, or every hostile just because it is nearby. Low health triggers retreat. Give Echo a weapon and equip it before combat; it does not have food/healing, ranged attacks, armor management, or advanced combat tactics.

Right-click Echo for status. **Sneak-right-click** him (or use `/echo carry`) to pick him up as a non-stackable 3D cube item. Hold him in either hand, then **use the item on solid ground** to put him down. Inventory, health, permissions, memory and identity are preserved; tasks remain paused until `/echo resume` or a new instruction. If the carried item is lost, `/echo recover-cube` restores a token for the same stored companion. Copied tokens cannot create extra companions. The full state stays in the world save; trading the item does not transfer ownership. Carried Echo does not perform gameplay or voice tasks until placed down. `/echo plan <JSON>` executes the same validated schema used by the planner, useful for deterministic multi-step instructions:

```text
/echo plan {"reply":"Gathering, crafting, then bringing it back.","steps":[{"action":"mine","item":"minecraft:oak_log","count":2},{"action":"craft","item":"minecraft:oak_planks","count":2},{"action":"deliver"}]}
```

## Chat, hearing and automatic arrival

Type `@echo Hello` in normal Minecraft chat, use `/echo chat Hello`, or hold V with microphone support enabled. Explicit `@echo` messages are routed privately to your own companion; ordinary player chat is not sent to AI. The same message handler supports text and voice. `teleport to me` and `/echo here` safely recall the same owned cube, even across dimensions, without consuming an AI call. If an old chunk is unloaded, remote chat queues one message while attempting a bounded lookup; it does not silently teleport Echo.

Echo now records nearby server-emitted sound cues and block-break effects, with sound IDs, coordinates and ages. `/echo senses` or `what did you hear?` reports them even without a planner. Recent cues enter Gemma/Kev context; nearby priming/explosion cues can trigger a warning, and priming can trigger a short retreat when combat is permitted. These are Echo's local ears, not an omniscient feed of the player's headphones. Client-only music, menus, sound-pack waveforms, arbitrary desktop audio and other voice-chat mods are not captured.

On disconnect or normal server shutdown, loaded Echo is parked into world-persistent state. On rejoin, the same identity, bag and memory are restored beside the owner; previous work is paused. A cube deliberately carried as an item is never automatically unpacked. Arrival retries for ten seconds if no safe space is available, then gives a clear retry message.

See [how communication works](docs/COMMUNICATION.md) for the complete microphone → intelligence → gameplay → TTS explanation.

## Gemma 4 12B planner

Use a separately managed inference server that supports Gemma 4 12B and an OpenAI-compatible `POST /v1/chat/completions` endpoint with JSON responses. Echo does not select/install a particular inference engine. Set the model field to the exact identifier exposed by your server; the default `gemma-4-12b` is configurable, not a guarantee of a provider's alias. [Google's model documentation](https://ai.google.dev/gemma/docs/get_started) describes the family.

Use the in-game setup screen, or edit generated `config/echo-server.json` and run `/echo-config reload` as an operator:

```json
{
  "aiEnabled": true,
  "plannerUrl": "http://127.0.0.1:8080/v1/chat/completions",
  "plannerModel": "gemma-4-12b",
  "plannerKeyEnv": "ECHO_PLANNER_KEY",
  "kevEnabled": false,
  "kevUrl": "http://127.0.0.1:8009/v1/systemone",
  "kevModel": "kev-latest",
  "kevKeyEnv": "ECHO_KEV_KEY",
  "allowRemoteBackends": false,
  "timeoutSeconds": 45
}
```

Then `/echo say Gather two nearby oak logs, craft planks, and bring them to me`. Echo sends a snapshot of local blocks/mobs, biome, coordinates, weather/daytime, both inventories, permissions, active task and bounded memories. The returned plan is validated in full before replacing a task and revalidated while executing. Pure conversation leaves ongoing work intact. The model receives outcomes in later snapshots; there is no continuous autonomous replanning loop after failures. Unavailable or malformed planner responses leave existing basic behavior active.

Snapshots contain player/world information. Loopback HTTP is permitted by default. Remote backends require `allowRemoteBackends: true` and HTTPS. Keys come from environment variables of the game/server process, never from model output or client packets. HTTP redirects are disabled. Credentials and backend response bodies are not logged.

## Kev-4B native System One integration

Targets [Jared Palmer's Kev](https://github.com/jaredpalmer/kev). Configure your separately managed Kev server to load **Kev-4B**. The default native endpoint is `http://127.0.0.1:8009/v1/systemone`; `kev-latest` is the request alias used by upstream examples, while the server's startup configuration chooses the actual checkpoint. Set `kevEnabled: true` when that server is ready. No Kev model or runtime is downloaded by Echo.

Echo posts a short combat snapshot as `state`, plus a `choice` question with `engage`, `defend`, `retreat` criteria and an independent `noul` danger question. It reads `answers.reaction.choice`, its selected probability, and `answers.danger.noul`. A selected probability below 0.65 falls back to vanilla logic; danger probability at least 0.8 requests retreat. These thresholds are initial heuristics, not measured Minecraft accuracy. Model output cannot choose entities, permissions, commands or arbitrary gameplay actions.

Only active threats trigger inference, at most once per two seconds per companion, with a two-second deadline, a separate worker queue, and short-lived decisions. Low-health retreat, ownership and combat permissions override model decisions. Failed, slow or invalid Kev responses use vanilla reactions. Cold-started remote inference will often miss this deadline; keep your runtime warm if you enable it.

## Optional Parakeet + Kokoro voice

The client uses **hold V**, up to 15 seconds, to capture 16 kHz mono PCM WAV. It sends that WAV directly to your configured STT service. Only the resulting bounded text is sent to the Minecraft server. Echo's owner-only response packet drives local TTS. Audio is not relayed to other players or recorded to world saves. Typed chat and commands remain available.

`backend/voice_service.py` implements the endpoints using NVIDIA NeMo Parakeet and Kokoro directly, with lazy model loading. Dependencies are listed in `backend/requirements.txt`. For a future manual setup, use a separate Python environment (Python 3.11 is a practical starting point), compatible PyTorch/audio dependencies and the platform prerequisites of NeMo/Kokoro. NeMo is generally easier in Linux/WSL than native Windows; Kokoro may require eSpeak NG. **These dependencies and real models have not been installed or exercised for this delivery.** The mod also works with an independently provided compatible voice service.

After you choose to install dependencies and obtain weights separately, a manually operated service can be started with:

```text
cd backend
python -m uvicorn voice_service:app --host 127.0.0.1 --port 8090
```

Do not run it yet if you do not want model downloads on the first inference request. Optional service variables: `ECHO_PARAKEET_MODEL` (default `nvidia/parakeet-tdt-0.6b-v2`), `ECHO_KOKORO_VOICE` (default `af_heart`), `ECHO_VOICE_KEY` (optional bearer token; set the same value for the client). The service serializes inference and rejects concurrent requests with 503. `/health` reports process readiness and whether weights have loaded; readiness does not prove inference works.

Client configuration, on each player's machine:

```json
{
  "voiceEnabled": true,
  "speechEnabled": true,
  "sttUrl": "http://127.0.0.1:8090/transcribe",
  "ttsUrl": "http://127.0.0.1:8090/speech",
  "keyEnv": "ECHO_VOICE_KEY",
  "allowRemoteBackends": false
}
```

Use the Voice page's Save page to apply client edits; its Start page also offers Reload saved voice options for manual JSON edits. Server operators use `/echo-config reload` for server JSON. New environment variables still need a process restart. STT contract: `POST /transcribe`, `Content-Type: audio/wav`, response `{"text":"..."}`. TTS contract: `POST /speech`, JSON `{"text":"..."}`, response PCM WAV. Microphone permission/device errors and model failures show a message instead of blocking the game. Playback is local, not spatialized in-world speech. No wake-word or open-mic mode is implemented. Replies are a private client voice connection, including when Echo is far away; PCM playback follows Minecraft master and voice-category volume settings. This is not positional world audio audible to other players.

## Persistence and multiplayer

Entity NBT stores owner UUID, inventory, queued task remainder, permissions, conversations (last 40 entries), discoveries, and up to 32 named locations. The save's overworld `PersistentState` indexes ownership and last known dimension/coordinates, including unloaded companions. Restarted tasks load paused; `/echo resume` continues the remaining batches. Entity and ownership data are scoped to that world save.

Echo physically follows a present owner in the same dimension. Private chat and voice replies can reach the owner at a distance and across dimensions. Normal gameplay uses loaded terrain; reconnecting or recalling an unloaded Echo may asynchronously request the one chunk at his saved location, with a bounded timeout. You can carry Echo through a portal and place him down in another dimension. Remembered waypoints retain their original dimension; old tasks cannot execute against coordinates in the new dimension. Send a new instruction after such a move. If left behind, use `/echo here`, or say/type `teleport to me`. `/echo locate` reports his last location. If the origin cannot be resolved, Echo reports the failure and never creates a duplicate. Echo can die, dropping its inventory and freeing its ownership entry; its entity memories are lost on death. Back up worlds for durable history. Other mods deleting the entity without death can leave a stale index; `/echo reset-index` is operator-only recovery after verifying the old entity is permanently gone.

Mining honors vanilla modification checks and Fabric block-break callbacks; placement uses the vanilla BlockItem placement context with the owner and Fabric use-block callback. This is not universal protection-mod compatibility: protection mods that require a real player interaction manager or inspect the owner's actual held item need a dedicated adapter. Validate your server's claim system before granting world edits. No arbitrary commands, OS process execution, scripts, file paths or NBT payloads are accepted as actions.

## Build and verify

Install Java 21 and set `JAVA_HOME`. The included Gradle 8.10.2 wrapper resolves all pinned Minecraft/Fabric dependencies:

```powershell
.\gradlew.bat build
.\gradlew.bat runGametest
# Optional manual visual testing:
.\gradlew.bat runClient
```

Outputs: `build/libs/echo-minecraft-0.5.0.jar`, matching sources JAR, JUnit reports under `build/reports/tests/test`, headless integration report `build/gametest-results.xml`. The game tests are a separate development mod, excluded from the shipped JAR. The asset generator is `python scripts/make_texture.py` (requires Pillow). The editable Blockbench model is `models/echo-cube.bbmodel`; the native held-item JSON is `src/main/resources/assets/echo/models/item/cube.json`. See [cube model notes](docs/CUBE_MODEL.md).

See the [visual companion guide](artifacts/echo-guide.html), [technical companion document](docs/echo-guide.md), [architecture](docs/ARCHITECTURE.md) and [validation](docs/VALIDATION.md) for communication diagrams, actual development-client screenshots, verification state and remaining checks.

## Current boundaries

- Natural instructions produce at most 32 validated sequential actions. Complex builds need smaller plans; there is no blueprint importer, learned navigation, general survival planner, recursive recipe dependency resolver, or automatic recovery/replanning after a blocked task.
- Navigation uses vanilla ground paths; no digging tunnels to navigate, terrain modification for pathfinding, ladders/scaffolding construction, autonomous portal travel or unloaded-chunk exploration.
- Construction supports ordinary solid cubes, rejecting block entities, explosives, fire and falling blocks. Mining cannot handle containers or fluid blocks. No container logistics or machine interaction.
- Environment awareness is a bounded local snapshot plus threat/daytime reactions, not full visual scene understanding or every game event.
- Animation uses a custom half-size cube with blinking, gait, sleep/wake, reunion greeting, curiosity, work and alert poses. Tools are used from internal inventory without humanoid arms. Voice playback is client-local.
- Real model inference, headset behavior, full animation/survival acceptance and real multiplayer/claim compatibility still require manual validation. Development-client entity and held-item rendering were checked and captured. See validation report; protocol mocks are not live model proof.
