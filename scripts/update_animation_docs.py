"""Refresh existing guides for the smaller cube and connected idle behavior."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
p=root/'README.md';s=p.read_text(encoding='utf-8-sig').replace('**0.4.0**','**0.5.0**').replace('echo-minecraft-0.4.0','echo-minecraft-0.5.0')
section='''## Smaller cube and connected animations in 0.5.0

Echo is now **0.3125 blocks wide and tall**, half his previous dimensions. The entity mesh, collision box, shadow, held item and editable Blockbench geometry agree. In first person the held cube is raised and turned toward the player in either hand so its eyes are visible.

After **600 safe idle server ticks** (about 30 seconds at 20 TPS), Echo closes his eyes, leans over and breathes slowly. The owner must be nearby and still, with Echo on dry ground, navigation stopped, no task/planning and no threat. Moving wakes him and normal following resumes if Follow was already enabled. Chat, commands and owner interaction also wake him. Stay remains Stay; Pause does not get silently cancelled.

Three additional connected animations are included:

- **Reunion greeting:** a small visual hop/wiggle after being at least eight blocks apart and settling close together again in Follow mode.
- **Curious glance:** a short tilt/look around during safe idle, also used while waiting for a planner reply.
- **Working rhythm:** a small nod/bounce while stationary mining, crafting or placing, driven by the actual task queue.

Movement keeps its gait bob; nearby threats trigger an alert posture. These are lightweight server-selected visual states, not extra model calls. Tasks, threats, water, fire and damage prevent sleep. Sleep is transient and is not persisted as a gameplay task, so carrying, rejoining and recalling keep their existing ownership/inventory behavior. Carried Echo remains an inactive item.

'''
if '## Smaller cube and connected animations in 0.5.0' not in s:s=s.replace('## Easier setup in 0.4.0',section+'## Easier setup in 0.4.0')
s=s.replace('Animation uses a custom single-cube model with blinking, looking, movement bob and tilt.','Animation uses a custom half-size cube with blinking, gait, sleep/wake, reunion greeting, curiosity, work and alert poses.')
p.write_text(s,encoding='utf-8')

p=root/'docs/CUBE_MODEL.md';s=p.read_text(encoding='utf-8').replace('# Echo cube — 0.2.0','# Echo cube — 0.5.0').replace('0.625 × 0.625','0.3125 × 0.3125').replace('a single 10 × 10 × 10 model-pixel cuboid.','a single 10 × 10 × 10 UV cuboid rendered at half scale (5 × 5 × 5 model pixels).')
s=s.replace('- `echo_blink.png`: blink variant, used for four ticks about every five seconds, offset per entity.','- `echo_blink.png`: closed-eye variant for sleep and early waking, also used for four-tick blinks about every five seconds.')
details='''
## Connected animation states

`CompanionIdle` selects transient states on the server. `EchoEntity` synchronizes the pose and its world-tick start through vanilla entity data tracking. `EchoCubeModel` samples those values for body transforms, while the renderer chooses open or closed eyes. A late-tracking client receives the current state and phase; pose state does not alter saved inventory or task progress.

Safe idle lasts 600 server ticks before sleep. Owner movement, new tasks, planning, interaction, threats or unsafe terrain interrupt it. Following resumes through the existing navigation code and never enables itself when Stay/Pause was explicit. The greeting, curious and working poses derive from actual proximity, waiting and queue state. Visual hops do not move the authoritative collision box or place/destroy blocks.

The held item is a centered five-pixel cube. Its two first-person transforms rotate 180 degrees and raise it into view, and both third-person transforms keep the face upright. GUI, dropped-item and item-frame views retain native Minecraft transforms at the smaller geometry size. The Blockbench file uses the same five-pixel element dimensions; texture UVs still cover the whole face.

'''
if '## Connected animation states' not in s:s+=details
p.write_text(s,encoding='utf-8')

p=root/'docs/echo-guide.md';s=p.read_text(encoding='utf-8').replace('Echo 0.4.0 is','Echo 0.5.0 is').replace('0.625 blocks','0.3125 blocks').replace('echo-minecraft-0.4.0','echo-minecraft-0.5.0')
details='''## Half size and connected idle behavior

Version 0.5.0 halves the physical width/height from 0.625 to 0.3125 blocks, halves the rendered entity and shadow, and changes the native held-item and Blockbench geometry from ten pixels to five. The first-person transforms face the player and raise the cube so its eyes fit in view. Existing companion identities, bags, permissions and memory are preserved.

The server selects a pose and tracks its world-tick start with `DataTracker`. Clients receive the state through normal entity synchronization; model transforms and the closed-eye texture use the same state/phase. The state machine itself performs no inference or additional world edits.

```mermaid
stateDiagram-v2
    Idle --> Curious: safe idle glance or planner pending
    Curious --> Idle: glance ends
    Idle --> Sleep: 600 safe still ticks near owner
    Sleep --> Wake: owner moves or interacts
    Wake --> Follow: existing Follow enabled
    Wake --> Idle: existing Stay enabled
    Sleep --> Work: accepted active task
    Sleep --> Alert: threat or hazard
    Follow --> Greet: far then close and settled
    Greet --> Idle: greeting finishes
    Work --> Idle: task completes
    Alert --> Idle: danger clears
```

Sleep requires dry supported ground, nearby still owner, idle navigation and no queued work, planning or threat. It is interrupted by movement, interaction, tasks and danger. Explicit Pause remains paused and explicit Stay does not silently become Follow. Carried/stored entities have no running state machine, and pose timers are intentionally absent from persistent NBT.

The three extra animations are a reunion hop/wiggle after separation, a curious glance while idle or thinking, and rhythmic motion while stationary mining/crafting/placement. Walking retains a small gait bob, and threats take an alert pose. Hops are visual, so they do not change reach, collisions or material accounting.

Sources: [idle state machine](../src/main/java/dev/echo/entity/CompanionIdle.java), [entity synchronization and control](../src/main/java/dev/echo/entity/EchoEntity.java), [client poses](../src/client/java/dev/echo/client/EchoCubeModel.java), [held geometry](../src/main/resources/assets/echo/models/item/cube.json), [state tests](../src/test/java/dev/echo/ai/CompanionIdleTest.java).

'''
if '## Half size and connected idle behavior' not in s:s=s.replace('## Communication architecture',details+'## Communication architecture')
s=s.replace('Twenty [Minecraft GameTests]','Twenty-two [Minecraft GameTests]').replace('Twenty-one Java tests','Twenty-nine Java tests')
p.write_text(s,encoding='utf-8')

p=root/'artifacts/echo-guide.html';s=p.read_text(encoding='utf-8')
s=s.replace('Version 0.4.0','Version 0.5.0').replace('Companion guide · 0.4.0','Companion guide · 0.5.0').replace('echo-minecraft-0.4.0.jar','echo-minecraft-0.5.0.jar').replace('0.625 blocks','0.3125 blocks')
s=s.replace('20 GameTests passed','22 GameTests passed').replace('21 tests passed','29 tests passed')
s=s.replace('aspect-ratio:16/9;object-fit:cover','height:auto;object-fit:contain').replace('.hero img{aspect-ratio:4/3}','.hero img{height:auto}')
s=s.replace('media/echo-in-game.png','media/echo-tiny-in-game.png').replace('media/echo-held.png','media/echo-held-face.png')
s=s.replace('Actual 0.3.0 Fabric development client, disposable test world; cube assets retained in 0.4.0.','Actual 0.5.0 Fabric development client, disposable test world.').replace('18 October 2026','20 October 2026')
s=s.replace('Actual client rendering of the cube item.','Actual first-person view: the smaller cube faces its owner.').replace('A slight movement bob and tilt','Sleep, wake, greeting, curiosity and work poses')
s=s.replace('Blinking, looking, a slight movement bob and tilt give it expression.','Blinking, gait, sleep/wake, greeting, curiosity and work poses give it expression.')
s=s.replace('The screenshot verifies appearance, not every animation or interaction.','The cube is half its previous size. Static captures are appearance evidence; server tests cover sleep/wake and task transitions.')
section='''<section id="animation"><div class="section-head"><span class="index">01a /</span><h2>A little more<br>alive.</h2><p>New in 0.5.0: safe idle becomes a nap. Movement, interaction and work wake Echo; explicit Stay and Pause still matter.</p></div>
<div class="grid2"><figure class="photo"><a href="media/echo-asleep.png"><img src="media/echo-asleep.png" alt="Actual Minecraft capture of small Echo asleep with closed eyes and a leaning pose"></a><figcaption><span><b class="label">In game</b> · Sleep pose after safe idle; no model calls.</span></figcaption></figure><figure class="photo"><a href="media/echo-held-left.png"><img src="media/echo-held-left.png" alt="Actual first person Minecraft view showing Echo facing the player from the off hand"></a><figcaption><span><b class="label">Other hand</b> · Face remains visible in the off hand too.</span></figcaption></figure></div>
<div class="grid3"><article class="feature"><h3>Reunion greeting</h3><p>After being apart, Echo gives a small hop and wiggle when you settle nearby in Follow mode.</p></article><article class="feature"><h3>Curious glance</h3><p>A short tilt and look around during safe idle, also used while a planner reply is pending.</p></article><article class="feature"><h3>Working rhythm</h3><p>A light nod and bounce while stationary mining, crafting or placing. Active tasks prevent sleep.</p></article></div>
<div class="dark"><p style="margin:0"><strong>Safe idle for 30 seconds → Sleep → Move or interact → Wake → Follow if already enabled</strong></p><p style="margin:14px 0 0;font-size:13px">Danger and tasks take priority. Sleep cannot cancel work, enable a denied permission, override Stay/Pause, or change saved ownership. State and phase synchronize through the server.</p></div></section>
'''
if 'id="animation"' not in s:s=s.replace('<section id="communication">',section+'<section id="communication">')
p.write_text(s,encoding='utf-8')

p=root/'docs/VALIDATION.md';s=p.read_text(encoding='utf-8').replace('# Verification — 2026-10-19','# Verification — 2026-10-20')
section='''## Smaller cube and animation release 0.5.0

The build passes with 29 Java tests and 22 headless Minecraft GameTests. Eight new pure state-machine tests cover idle timing, movement wake-up, working/planning, danger, task interruption, greeting conditions, curiosity and clearing transient state. Two new real-world tests verify half-size physical bounds, sleep followed by owner movement and native navigation, and sleep interrupted by actual crafting while preserving Pause and Stay.

The animation is server-selected and synchronized through tracked pose/start time. Source inspection confirms commands, owner interaction, tasks and nearby priming cues wake the cube; the renderer uses the same state to close eyes and move the body. Sleep state is intentionally not stored in NBT. Existing carry, memory, hearing, combat, placement and ownership tests continue to pass. No models or voice services were installed or invoked.

'''
if '## Smaller cube and animation release 0.5.0' not in s:s=s.replace('## Setup release 0.4.0',section+'## Setup release 0.4.0')
s=s.replace('| Java protocol/backend and setup tests | 21 passed |','| Java protocol/backend, setup and idle tests | 29 passed |').replace('| Fabric/Minecraft 1.21.1 headless GameTest | 20 passed |','| Fabric/Minecraft 1.21.1 headless GameTest | 22 passed |')
p.write_text(s,encoding='utf-8')

guide=(root/'docs/echo-guide.md').read_text(encoding='utf-8').replace('](COMMUNICATION.md)','](../docs/COMMUNICATION.md)').replace('](VALIDATION.md)','](../docs/VALIDATION.md)').replace('](test-results/','](../docs/test-results/')
(root/'artifacts/echo-guide.md').write_text(guide,encoding='utf-8')
