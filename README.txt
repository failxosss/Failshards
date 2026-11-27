FailShards 1.2.0 — Paper 1.21.11, Java 21
This is a source project, not a compiled JAR. Compilation has not been verified.
Upload the ZIP contents to your GitHub repository (including .github). Run Build in Actions, download the FailShards artifact, extract it and place FailShards.jar in plugins. Alternatively use JDK 21 and Maven: mvn clean package (output: target/FailShards.jar).
Back up plugins/FailShards before updating. Place the failshards folder from the ItemsAdder ZIP in plugins/ItemsAdder/contents, restart the server and run /iazip according to your resource pack hosting settings.
For existing installations, update the messages and shard names in plugins/FailShards/config.yml using the English defaults in src/main/resources/config.yml. Preserve your custom settings. Existing items may need to be reissued to display updated names.
Right click with a shard in your main hand = active ability; Shift + right click = ultimate; Shift + left click = third ability. Passive effects remain unchanged. Cooldowns are shared across shard types, with separate timers for each ability. Restarting resets cooldowns.
/failshards give PLAYER air (admin), /failshards random PLAYER, /failshards reload.
The abilities section in config.yml includes disabled-worlds. Region protection is not integrated; test abilities before public deployment and disable them in protected worlds. Team protection uses scoreboard teams, not external clan plugins. Sounds are vanilla; animations use particles rather than custom 3D models. XP and upgrades are not included.
Existing configurations can add third-cooldown: 90 to each shard in abilities; the default is 90 seconds.
