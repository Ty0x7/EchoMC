"""Update the existing companion guide for the 0.4 setup release."""
from pathlib import Path

root=Path(__file__).resolve().parents[1]
for name in ["README.md","docs/echo-guide.md","artifacts/echo-guide.html"]:
    p=root/name
    p.write_text(p.read_text(encoding="utf-8-sig").replace("0.3.0","0.4.0"),encoding="utf-8")

readme=root/"README.md"
text=readme.read_text(encoding="utf-8")
text=text.replace("Restart after configuration edits.","Use the setup screen's Save page or the operator's /echo-config reload to apply edits. New environment variables still need a process restart.")
text=text.replace("Edit generated `config/echo-server.json` and restart:","Use the in-game setup screen, or edit generated `config/echo-server.json` and run `/echo-config reload` as an operator:")
text=text.replace("Restart after configuration edits.","Save the page to apply changes immediately.")
text=text.replace("the local EchoSpeak repository was not accessed.","only the explicitly requested project-documentation skill was read locally.")
quick="""## Easier setup in 0.4.0

Open **Echo setup** from the title or pause menu, press **F8**, or use **`/echo-setup`** in game. The native screen has Start, Planner, Kev, Voice and World pages. **Save page** applies that page without restarting the mod; **Done** closes without saving additional draft changes. The screen does not install or launch inference software.

1. Start your separately installed services. In **Planner**, enter the full chat endpoint and the exact model ID your service exposes. **Check model list** is a GET request. **Test reply (inference)** checks Echo's JSON reply contract using a synthetic prompt. Turn Planner on and save.
2. In **Kev**, keep the native `/v1/systemone` URL and your server's model alias. Its runtime must load Kev-4B separately. **Test System One (inference)** checks the real native schema. Enable and save only when you want it; vanilla defense works with Kev off.
3. In **Voice**, enter the Parakeet `/transcribe` and Kokoro `/speech` routes. Check each service's health. Enable **Mic** for hold-V input and **Speech** for spoken replies, then save. These toggles are independent. Health checks never capture audio or request synthesis.
4. **World** controls automatic arrival on future joins, hearing radius and planner timeout. Player mining/build/explore permissions remain separate `/echo allow` controls.

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

"""
if "## Easier setup in 0.4.0" not in text:text=text.replace("## Play immediately, without models",quick+"## Play immediately, without models")
readme.write_text(text,encoding="utf-8")

guide=root/"docs/echo-guide.md"
text=guide.read_text(encoding="utf-8")
setup="""## In game setup and connection checks

Version 0.4.0 adds a native setup screen through the title/pause menu, F8 and `/echo-setup`. It separates Planner, Kev, Voice and World settings. Fields retain draft edits across tabs; Save page commits only the selected page. Done or Escape closes the screen without committing additional draft edits. Singleplayer server saves execute on the integrated server thread. Remote multiplayer users cannot edit server options through this screen.

The planner URL, exact model ID, environment variable names, enable flags, native Kev URL and alias, voice routes, independent microphone/speech flags, remote HTTPS permissions, automatic arrival, hearing radius and planner timeout are configurable. World changes do not grant per-companion edit permissions.

| Check | What it requests | What success establishes |
|---|---|---|
| Check model list | GET sibling `/v1/models`, preserving a proxy prefix | The configured planner alias is listed; no inference proof |
| Test reply inference | Synthetic chat completion, no world/player context | A bounded Echo JSON conversation response with no actions |
| Test System One inference | Native Kev synthetic threat-state request | Expected System One answer schema parses; no combat-quality proof |
| Parakeet or Kokoro health | GET sibling `/health` for the selected voice route | Process readiness and reported loaded flags; no audio/inference proof |

Diagnostics use a separate bounded worker queue, byte limits, deadlines and no redirects. They do not capture a microphone or execute returned actions. Inference checks are explicit; an external service may load/download weights on its own first inference. Health checks do not request inference. No model downloads or live inference were performed for this update.

Setup saves validate a whole candidate before replacing its JSON file using an atomic move where supported. Runtime configuration changes only after the write succeeds. Inference retains its original settings snapshot and its result is discarded if settings change. Voice option changes supersede old audio work. New credential environment values still require restarting the owning process.

Dedicated servers expose operator-only `/echo-config status`, `set <option> <value>`, `reload`, and `test models|planner|kev`, including console support. Settings save/apply immediately. Failed reload keeps runtime settings. Remote clients receive no server configuration editor or credential access. Native commands remain usable with all backends disabled.

For the player setup sequence and full command examples, see [Easier setup](../README.md#easier-setup-in-040). Planner/Kev endpoints are relative to the server machine; microphone/TTS endpoints are relative to each client. Loopback does not refer to a remote player's computer. Custom voice services without a compatible health route may fail the health check even when their inference route works; the message explains that distinction.

Sources: [native screen](../src/client/java/dev/echo/client/EchoSetupScreen.java), [connection checks](../src/main/java/dev/echo/setup/ConnectionChecks.java), [operator commands](../src/main/java/dev/echo/setup/SetupCommands.java), [configuration writes](../src/main/java/dev/echo/setup/ConfigFiles.java), [setup tests](../src/test/java/dev/echo/ai/SetupTest.java).

"""
if "## In game setup and connection checks" not in text:text=text.replace("## Gameplay and setup",setup+"## Gameplay and setup")
text=text.replace("Restart after editing configuration.","Use Save page or the operator reload command after editing configuration; new environment values still need a process restart.")
text=text.replace("Fourteen Java tests cover", "Twenty-one Java tests cover")
text=text.replace("mock HTTP and sound memory.","mock HTTP, sound memory and seven setup/configuration checks.")
guide.write_text(text,encoding="utf-8")

html=root/"artifacts/echo-guide.html"
text=html.read_text(encoding="utf-8")
text=text.replace("14 tests passed","21 tests passed").replace("mock Gemma/Kev HTTP and sound budgets.","mock Gemma/Kev HTTP, sound budgets and setup diagnostics.")
text=text.replace("Restart after edits.","Use Save page to apply changes. New credential environment values need a process restart.")
section="""<section id="setup"><div class="section-head"><span class="index">05a /</span><h2>Connections,<br>inside the game.</h2><p>New in 0.4.0: open Echo setup from the title or pause menu, press F8, or use <code>/echo-setup</code>. Save each page when ready.</p></div>
<div class="grid2"><figure class="photo"><a href="media/echo-setup-planner.png"><img src="media/echo-setup-planner.png" alt="Actual Minecraft Echo setup Planner page with endpoint, model ID and connection tests"></a><figcaption><span><b class="label">Actual client</b> · Planner setup in the Fabric development client.</span></figcaption></figure><figure class="photo"><a href="media/echo-setup-voice.png"><img src="media/echo-setup-voice.png" alt="Actual Minecraft Echo Voice setup page with Parakeet, Kokoro and independent microphone and speech options"></a><figcaption><span><b class="label">Actual client</b> · Separate voice input and speech output controls.</span></figcaption></figure></div>
<div class="grid3"><article class="feature"><h3>1 · Match your running service</h3><p>Enter the full URL and exact model ID. Check model list first; Test reply verifies the planner JSON contract. Kev uses its native System One endpoint.</p></article><article class="feature"><h3>2 · Turn on what you want</h3><p>Planner, Kev, microphone and speech are separate choices. Health checks do not record audio or run inference. Enable each feature and Save page.</p></article><article class="feature"><h3>3 · Know which machine</h3><p>Planner/Kev belong to the server. Voice belongs to your client. Remote server operators use <code>/echo-config</code>; your client cannot change their settings.</p></article></div>
<p class="note">Model-list success is not inference proof. Voice readiness is not loaded-weight or headset proof. Explicit inference tests can trigger model loading/downloads in your separately managed service. No weights were downloaded for this update. Put only environment variable names in key fields; restart the process after changing their values.</p></section>
"""
if 'id="setup"' not in text:text=text.replace('<section id="evidence">',section+'<section id="evidence">')
text=text.replace('>Install</a></nav>','>Install</a><a href="#setup">Setup</a></nav>')
text=text.replace("Actual Fabric development client, disposable test world.","Actual 0.3.0 Fabric development client, disposable test world; cube assets retained in 0.4.0.")
text=text.replace("Actual 0.4.0 Fabric development client, disposable test world; cube assets retained in 0.4.0.","Actual 0.3.0 Fabric development client, disposable test world; cube assets retained in 0.4.0.")
html.write_text(text,encoding="utf-8")

validation=root/"docs/VALIDATION.md"
text=validation.read_text(encoding="utf-8").replace("# Verification — 2026-10-18","# Verification — 2026-10-19")
text=text.replace("| Java protocol/backend tests | 14 passed |","| Java protocol/backend and setup tests | 21 passed |")
release="""## Setup release 0.4.0

The 0.4.0 build and all 20 headless Minecraft GameTests pass. All 21 Java tests pass, including seven new setup checks: candidate-copy isolation and validation, atomic config serialization, proxy-aware check URLs, exact model-ID matching without inference, voice readiness with unloaded weights, conversation-only planner diagnostics that reject actions, and rejecting health responses without ready status. HTTP tests use local fixtures, not downloaded models.

Server setup commands require operator permission level 2 and support the console. Remote clients have no server config write packet. Source checks confirm page-specific saves, integrated-server thread handoff, bounded asynchronous diagnostics, sanitized errors, and stale inference/audio cancellation after configuration changes. Real dedicated-server operator sessions and real service credentials remain acceptance work.

The six Python route contract tests remain prior-delivery evidence; the voice service was unchanged and was not reinstalled or run for this release. No models or inference runtimes were installed/downloaded. Actual inference, microphone/headset behavior and service-specific aliases remain unverified.

"""
if "## Setup release 0.4.0" not in text:text=text.replace("## Verified",release+"## Verified",1)
validation.write_text(text,encoding="utf-8")

copy=guide.read_text(encoding="utf-8").replace("](COMMUNICATION.md)","](../docs/COMMUNICATION.md)").replace("](VALIDATION.md)","](../docs/VALIDATION.md)").replace("](test-results/","](../docs/test-results/")
(root/"artifacts/echo-guide.md").write_text(copy,encoding="utf-8")
