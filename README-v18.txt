FailShards 18.0.0
Water and Fire use right click (slot 1) and left click (slot 2), no sneak needed. Slot 3 is disabled in code for both; there is no surface-walking logic. Other shards retain existing controls and abilities.
Water tsunami and vortex now remove selected fragile blocks, including cobwebs, plants, torches and redstone wire, and extinguish fire, within their effect area. No actual water blocks are placed. Solid building blocks and containers are excluded. BlockBreakEvent is fired for protection plugin compatibility; verify your region plugin on a test server. Set abilities.water.wash-blocks: false to disable.
Fire ability damage no longer re-enters the left-click melee handler. Existing PvP heart loss preserved. No resourcepack updates required. Cooldowns unchanged.
Build with existing GitHub Actions workflow. Not compiled or server-tested in this environment.
