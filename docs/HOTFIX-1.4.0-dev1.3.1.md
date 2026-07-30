# Build hotfix — SSU 1.4.0-dev1.3.1

## Symptom

`compileTestJava` failed because the standard Gradle `test` source set only had JUnit dependencies and did not receive ModDevGradle's Minecraft/NeoForge development classpath. Tests importing `MinecraftServer`, and tests touching payload records that implement `CustomPacketPayload`, therefore could not compile.

## Fix

The `neoForge.unitTest` integration is now enabled and bound to the `simpleserverutilities` mod:

```groovy
neoForge {
    // existing configuration
    unitTest {
        enable()
        testedMod = mods.simpleserverutilities
    }
}
```

The JUnit Platform launcher is also present at test runtime:

```groovy
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```

## Compatibility

This changes build configuration only. Network protocol remains 15 and all stored data formats remain unchanged.
