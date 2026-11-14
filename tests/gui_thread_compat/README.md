# Original standalone GUI adapter fixtures

Run `tools/check_gui_thread_compat.py` with explicit Java8, prepared Minecraft development API, existing Forge/ASM5.2/LaunchWrapper/Guava JARs, the private exact supported CustomNPCs JAR and a new private output directory. No Minecraft process or GUI is launched. No dependency is downloaded.

The source/bytecode checks run without Minecraft or NPC implementation classes on their runtime classpath. They transform the private target in memory, verify the exact source identity, reject unsupported layouts and compare every other method—including the original GUI-construction lambda—unchanged. They export no original class payload.

The API directory contains independently authored test doubles and a synthetic callback class. These prove the transformed Java8 Runnable actually defers GUI construction/display until the Minecraft owner thread drains its queue, while preserving unrelated scheduler usage. They are compiled into a separate test-only directory and must never be packaged with gameplay artifacts. No third-party implementation code is copied into these fixtures.

Expected standalone scope:33 source/bytecode assertions and26 dispatch/integration assertions. Actual runtime hook loading, modded network join, manual tabs, responsiveness, reconnect and clean shutdown remain separate acceptance gates.
