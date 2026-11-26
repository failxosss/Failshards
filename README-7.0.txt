FailShards 7.0 — experimental keyframe scenes
Paper 1.21.11 / Java 21 / ItemsAdder. Build using existing GitHub Actions.
90 scene definitions, 627 individual display model registrations, 90 new palette textures.
The models are procedurally built from cuboids, using shared geometry and choreography families; not hand-authored skeletal animations or shader VFX.
The new scenes-v7.yml contains normalized time and separate transform tracks for each part: time,x,y,z,scaleX,scaleY,scaleZ,rotationX,rotationY,rotationZ. Angles in degrees. All tracks start at 0 and end at 1.
Visuals do not change ability gameplay. Some visual movement is illustrative rather than precisely tied to gameplay hits. Only one active scene per player; new casts replace previous scenes.
Back up existing plugin/data/ItemsAdder contents. Replace the failshards ItemsAdder folder, regenerate pack, upload pack, update URL and restart. Keep old data and config. Existing visual-profiles.yml is no longer used by this engine. To customize: scenes-v7.yml (restart to reload); config visual-effects.shards.<key>.slot-<1..3>-scale/speed/ticks/enabled. Default max-total-displays 240.
Validation: YAML, model JSON, texture references, track times and ZIP structure checked. Java compilation and live Minecraft rendering not verified. No custom sounds, shader bloom, or wearable custom armor. Existing ability logic and /changeshard unchanged.
