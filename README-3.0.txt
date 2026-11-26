FailShards 3.0 – model visual effects (source build)
Build with Java 21 / Maven: mvn clean package. Or use existing GitHub Actions workflow.
Install target/FailShards.jar and the matching ItemsAdder 3.0 failshards content folder.
Stop server and back up config/data; do not remove player data. Merge visual-effects section from new default config into existing config if desired. Defaults apply automatically.
Start server, /iazip, publish generated pack using your existing external hosting, update ItemsAdder URL and restart. Accept the new pack on login.
Each shard has two counter-rotating floor sigils, orbiting 3D gems and floating shard icon. Slot 2 expands; slot 3 moves vertically. Crystal Armor uses two heights and hit flash; Totem rescue triggers its scene automatically. Air ultimate has a rising spiral. These are themed shared animations, not 90 independently authored spell animations. Original gameplay logic is unchanged. Vanilla particle calls disabled by default (vanilla potion/totem particles may still occur). No true wearable armor, shader bloom, or new custom audio. Existing sound effects retained.
One scene per player; cleanup on quit/death/world change/disable. Config has player cap and orbit count. If models missing, warning logged. No server test or Maven compile performed in authoring environment.
