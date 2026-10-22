# Seepers

*You didn't fall. You seeped through.*

Horror survival mod for Minecraft 1.21.1 (NeoForge). Work in progress.

## Building

Requires JDK 21.

```
./gradlew build            # jar in build/libs/
./gradlew runClient        # dev client (Sodium and JEI are loaded for testing only)
./gradlew runServer        # dev dedicated server
./gradlew deployToInstance # copy the jar into a local test instance
```

`deployToInstance` reads the target game directory from the `seepers.instanceDir`
property in your user-level `~/.gradle/gradle.properties`.

## License

All Rights Reserved — see [LICENSE](LICENSE).
