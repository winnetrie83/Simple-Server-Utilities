# SSU 1.9.0-dev3.15.2 runtime checklist

1. Run a local `./gradlew compileJava` using Java 25 / Gradle 9.2.1.
2. Start client + dedicated server with the exact same dev3.15.2 build.
3. Open the Minigame Setup Tool for a King of the Hill arena.
4. Set the hill center inside the arena Region and confirm it saves successfully.
5. Try to set the hill center outside the arena Region and confirm SSU rejects it with the existing validation message.
6. Re-open the minigame editor and confirm the saved hill center persists.
7. Smoke-test KOTH prepare/start/scoring after the setup action.
