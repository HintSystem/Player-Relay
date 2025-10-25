> ⚠️ **Warning**
> 
> **This version is incompatible with versions 1.3.0 and below.**

### New Features

* **Player list**
  * Added player model rendering
  * Added dimension icons
  * Added player status overlay (AFK and death)
* Added `echest` command:
  * View another player's ender chest
  * Player must have opened their ender chest at least once in the current world
  * Lets you view your own ender chest if a player isn't specified
  * Ender chest inventory updates when you re-run the command
* Added custom player name colors
  * Colors also apply to pings from Ping Wheel
* Added Xaero's Minimap / WorldMap waypoint sharing via Player Relay
* **New config options:**
    * **Client**
        * Display Name Color *(default: `#FFFFFF`)*
        * AFK Timeout *(default: `120'000`ms)*
    * **Player List**
        * Show Dimension Icon *(default: `ON`)*
        * Player Icon Type *(default: `PLAYER_MODEL`, previously `PLAYER_HEAD`)*
    * **Xaero's Minimap / WorldMap**
        * Share Waypoints Via Player Relay *(default: `ON` previously `OFF`)*

### Changes & Fixes

* Renamed `peekinv` command to `inv`
* Fixed config not saving color types correctly
* Fixed player name and UUID being unknown until a world is loaded