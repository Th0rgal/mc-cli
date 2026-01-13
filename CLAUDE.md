# MC-CLI Development Notes

## Minecraft Version Support

This project targets **Minecraft 1.21.11** for both Fabric and NeoForge.

- 1.21.11 is the last obfuscated Minecraft version
- Future versions (26.1+) will be unobfuscated and won't need Yarn/Parchment mappings

## Mappings

### Fabric (Yarn)
- Yarn mappings for 1.21.11: `1.21.11+build.4`
- Yarn will stop being updated after 1.21.11
- Consider migrating to Mojang mappings for future versions

### NeoForge (Parchment)
- Parchment beta versions available at: https://github.com/ParchmentMC/Parchment
- Currently disabled in build.gradle as stable 1.21.11 mappings aren't released yet
- Parchment provides parameter names and javadocs on top of Mojang mappings

## API Changes in 1.21.11

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

- Java 21+
- Gradle 9.2.1+
- Fabric Loom 1.14+ (for Fabric)
- NeoForge ModDev 2.0.134+ (for NeoForge)
