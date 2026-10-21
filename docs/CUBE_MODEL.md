# Echo cube — 0.5.0

Echo is a **white cube**, not a humanoid character. World size and collision are 0.3125 × 0.3125 blocks, with no arms, legs, clothes, mouth, cyan eyes or dark display panel.

The visual reference is the user's public [EchoFace design](https://github.com/Ty0x7/EchoSpeak/blob/main/apps/web/src/components/EchoFace.tsx): body `#f4f4f2`, eyes `#070707`, two vertical pill eyes about 11.5% of face width and 20% of face height, with a 15% gap. These proportions and colors were recreated in original Minecraft assets. EchoSpeak's rounded 2D silhouette becomes a Minecraft cube; no application source or bitmap is bundled.

Files:

- `models/echo-cube.bbmodel`: editable Blockbench project with embedded shell/face textures.
- `src/client/java/dev/echo/client/EchoCubeModel.java`: actual Minecraft entity mesh, a single 10 × 10 × 10 UV cuboid rendered at half scale (5 × 5 × 5 model pixels).
- `src/main/resources/assets/echo/models/item/cube.json`: Minecraft 1.21.1 native 3D inventory/hand model with first-person, third-person, GUI, ground and item-frame transforms.
- `src/main/resources/assets/echo/textures/entity/echo.png`: world-entity cube UV atlas; nominal 64 × 32 layout at four-times pixel density.
- `echo_blink.png`: closed-eye variant for sleep and early waking, also used for four-tick blinks about every five seconds.
- `cube_shell.png` and `cube_face.png`: held-item textures.
- `models/echo-cube-preview.png`: orthographic design preview; **not an in-game screenshot**.

Use **sneak-right-click** on your Echo, or `/echo carry`, to pick him up. Your inventory must have a free main-inventory slot. Put the item in a hand and use it on an unoccupied supported block face to deploy him. Work pauses when picked up and remains paused after deployment. `/echo resume`, `/echo follow`, or a new instruction controls what happens next.

The item contains only the companion UUID. The save's authoritative ownership ledger stores the complete entity NBT while carried, including health, bag, memory, waypoints, remaining tasks and permissions. Deploy checks ownership, canonical state, existing loaded identity, distance, support, collision, fluids and vanilla modification rights. Even a creative-mode deployment consumes the item. Copied tokens are inactive after the one canonical companion is deployed. A lost item can be recovered with `/echo recover-cube` only while the companion is stored as carried.

Carrying can transport Echo across dimensions. Waypoints remember their dimension, and tasks from the previous dimension fail safely before performing actions there. Echo does not do gameplay work or voice inference while stored as an item. The item does not transfer ownership when traded. World backups remain advisable for crash recovery; this is not a cross-file crash-atomic transaction system.

To regenerate the original assets, install Pillow in your chosen development environment and run `python scripts/make_texture.py`. Normal mod builds use the supplied PNGs and need no Python or Blockbench. The Blockbench project has been structurally inspected but not opened interactively in Blockbench for this delivery.

## Connected animation states

`CompanionIdle` selects transient states on the server. `EchoEntity` synchronizes the pose and its world-tick start through vanilla entity data tracking. `EchoCubeModel` samples those values for body transforms, while the renderer chooses open or closed eyes. A late-tracking client receives the current state and phase; pose state does not alter saved inventory or task progress.

Safe idle lasts 600 server ticks before sleep. Owner movement, new tasks, planning, interaction, threats or unsafe terrain interrupt it. Following resumes through the existing navigation code and never enables itself when Stay/Pause was explicit. The greeting, curious and working poses derive from actual proximity, waiting and queue state. Visual hops do not move the authoritative collision box or place/destroy blocks.

The held item is a centered five-pixel cube. Both first-person transforms use rotation `[-18,145,0]`, translation `[-2,5.25,0]` and scale `[1.05,1.05,1.05]`. Minecraft mirrors yaw and horizontal translation for the left hand, so the two JSON entries intentionally match. The face pitches upward 18 degrees and turns inward 35 degrees toward the owner. Scale is 1.91 times the previous 0.55 hand scale; the world entity stays small. The resting first-person perspective faces the eye panel without exposing the underside. Native equip/swing animations still apply; a live both-hand visual check remains outstanding.

Third-person transforms keep the face upright. GUI, dropped-item and item-frame views retain their separate transforms at the smaller geometry size. The Blockbench file uses the same five-pixel element dimensions; texture UVs still cover the whole face.


Reproduce the resting geometry check with `python scripts/check_held_pose.py`. It checks both hands, underside visibility and full framing at 70-degree item FOV in 16:9. It is not a live Minecraft screenshot test.
