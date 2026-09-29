# MC-CLI Development Notes

## Minecraft Version Support

| Module | Minecraft | Loader | Java | Names |
|--------|-----------|--------|------|-------|
| `mod/fabric-26` | 26.3 (26.2 via `-Pmc26_version=26.2`) | Fabric Loader 0.19.5, Fabric API 0.161.0 | 25 | Mojang (unobfuscated) |
| `mod/neoforge-26` | 26.3 (26.2 via `-Pmc26_version=26.2`) | NeoForge 26.3.0.33-beta / 26.2.0.88 | 25 | Mojang (unobfuscated) |
| `mod/fabric` | 1.21.11 | Fabric 0.18.1 | 21 | Yarn |
| `mod/fabric-1.21.4` | 1.21.4 | Fabric 0.18.4 | 21 | Yarn |
| `mod/neoforge` | 1.21.11 | NeoForge 21.11 | 21 | Mojang |

- 1.21.11 is the last obfuscated Minecraft version
- 26.x is unobfuscated: no Yarn, no Parchment, no intermediary; both loaders use the Mojang names

## Minecraft 26.x layout

Because both loaders see the same (Mojang) names, all loader-independent code lives in
`mod/common-26/src/main/{java,resources}` and is added as a `srcDir` to both `fabric-26` and
`neoforge-26`. Only loader glue stays per loader:

- `fabric-26`: `dev.mccli.fabric.McCliFabric` (ClientModInitializer + Fabric API `ClientTickEvents`), `fabric.mod.json`
- `neoforge-26`: `dev.mccli.neoforge.McCliNeoForge` (`@Mod` + `ClientTickEvent.Post`), `META-INF/neoforge.mods.toml` template
- `common-26`: `McCliMod` (`init()` / `onClientTick()`), every command, TCP server, dispatcher, instance registry,
  utilities, and the mixins (`mccli.mixins.json` is used by both loaders; no refmap needed)

26.2 and 26.3 are not source-compatible in two places (26.3 moved windowing from GLFW to SDL3 and changed
`LivingEntity.swing`), so `common-26/src/mc26.2/java` and `common-26/src/mc26.3/java` hold a tiny
`dev.mccli.platform.Platform` shim plus the window-focus mixins; the build adds `src/mc<version>/java`
for the selected version. Everything else is shared. Adding a new 26.x version = add its
`fabric_api_version_<mc>` / `neo_version_<mc>` properties and a `src/mc<version>` overlay.

Add new commands to `common-26` only; they automatically ship in both loaders. Keep the pre-26 modules
(`fabric`, `fabric-1.21.4`, `neoforge`) separate: they are compiled against different names.

Jars: `mccli-<loader>-<mc>-<mod_version>.jar`. The Minecraft version is selected with
`-Pmc26_version=26.2|26.3`; per-version loader/API versions are keyed in each module's
`gradle.properties` (`fabric_api_version_<mc>`, `neo_version_<mc>`). Each jar's metadata pins its
exact Minecraft version (`~26.3` / `[26.3]`), so publish one jar per Minecraft version.

### API changes in 26.x (vs 1.21.11)

- 26.3 only: windowing is SDL3 (`org.lwjgl.sdl`), GLFW is gone: `Window.handle()` is the `SDL_Window*`;
  focus uses `SDLVideo.SDL_RaiseWindow` / `SDL_ShowWindow` (26.2 still uses GLFW). Vanilla never raises the
  window itself in either version, so the focus-suppression mixins are `require = 0` safety nets.
- Screens live on `Minecraft.gui`: `client.gui.screen()` / `client.gui.setScreen()` (see `util/ClientCompat`)
- F1 HUD toggle moved from `Options.hideGui` to `client.gui.hud.isHidden()` / `toggle()`
- Day time comes from world clocks: `Level.getOverworldClockTime()` (`/time set <ticks>` still works)
- `Minecraft.getMainRenderTarget()` -> `client.gameRenderer.mainRenderTarget()`
- `ChatComponent.addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)` (private);
  `GuiMessageTag` moved to `net.minecraft.client.multiplayer.chat`
- `ClickType` -> `ContainerInput`, `handleInventoryMouseClick` -> `handleContainerInput`
- `MultiPlayerGameMode.interactAt` removed; `interact(player, entity, hitResult, hand)` does both
- 26.3 only: `LivingEntity.swing(hand, SwingAnimation, sendToSwingingEntity)`; use `stack.getInteractAnimation()` /
  `getAttackAnimation()` (swings are client-side visuals; no serverbound swing packet). 26.2 keeps `swing(hand)`.
- `Inventory.selected` is private: use `getSelectedSlot()` / `setSelectedSlot()` (no access transformer needed)
- `StateHolder.getValues()` returns `Stream<Property.Value<?>>`
- Joining/leaving a world blocks the main thread: commands queue that work with
  `MainThreadExecutor.runNextTick` so the JSON response is sent first; disconnect the level
  (`level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE)`) before `client.disconnect(...)`
- NeoForge patches `LevelSettings` with an extra lifecycle component; use the 5-arg constructor, which exists on both loaders

## Mappings

### Minecraft 26.x
- None. 26.x ships unobfuscated; Loom and ModDevGradle use the official names directly
  (no `mappings` dependency, no Parchment, no refmap).

### Fabric (Yarn)
- Yarn mappings for 1.21.11: `1.21.11+build.4`
- Yarn will stop being updated after 1.21.11
- Consider migrating to Mojang mappings for future versions

### NeoForge (Parchment)
- Parchment beta versions available at: https://github.com/ParchmentMC/Parchment
- Currently disabled in build.gradle as stable 1.21.11 mappings aren't released yet
- Parchment provides parameter names and javadocs on top of Mojang mappings

## API Changes in 1.21.11 (pre-26 modules)

Key breaking changes from 1.21.1:

### Fabric
- `ScreenshotRecorder.takeScreenshot()` is now callback-based
- `sendCommand()` renamed to `sendChatCommand()`
- `client.disconnect()` requires Screen parameter
- `GlDebugInfo` removed - use LWJGL `GL11.glGetString(GL11.GL_RENDERER)` directly
- `Session.AccountType` removed - infer from `session.getXuid().isPresent()`
- `GameProfile.getName()/getId()` changed to `name()/id()` (record accessors)

### NeoForge
- Similar changes apply
- `Window.getWindow()` method renamed
- `ResourceKey.location()` API changed
- `Direction.getNormal()` removed
- `CustomModelData.value()` API changed

## Build Requirements

- Gradle runs on Java 25 (required by Loom 1.18; pinned in `mod/gradle/gradle-daemon-jvm.properties`,
  so a local JDK 25 must be installed; `JAVA_HOME` can still point at 21)
- 26.x modules compile with Java 25; 1.21.x modules with a Java 21 toolchain
- Java 21 (NeoForge: 21.0.6 or newer; the 21.0.4 javac fails to recompile the decompiled 1.21.11 sources with an inference error)
- Gradle 9.7.1+ (wrapper)
- Fabric Loom 1.14 (1.21.x, `fabric-loom` plugin id) and 1.18.2 (26.x, `net.fabricmc.fabric-loom` plugin id,
  no `mappings` line, plain `implementation` instead of `modImplementation`)
- NeoForge ModDev 2.0.134 (1.21.11) and 2.0.147 (26.x)
