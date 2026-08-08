# SSU 1.9.0-dev3.15.1 runtime checklist

1. Run the local Gradle `compileJava`/build first.
2. Start client + dedicated server with the exact same dev3.15.1 build.
3. Open/create a King of the Hill game and verify its arena/hill center loads without errors.
4. Start KOTH and stand inside the hill; verify objective-time/stat tracking advances.
5. Leave the hill and verify KOTH objective-time no longer advances for that player.
6. Re-test one existing minigame (for example Domination or CTF) to confirm no regression in objective-time accounting.
