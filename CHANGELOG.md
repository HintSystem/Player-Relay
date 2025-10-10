> ⚠️ **Warning**
> 
> **This version is incompatible with versions 1.2.1 and below.** New network features have been added that break compatibility with older versions. If you're running version 1.2.0 or above, you'll receive a warning when trying to connect to incompatible versions.

### New Features

* Show active status effects in the player list
* Display context-sensitive heart, armor, and food icons based on player status (poison, wither, frozen, etc.)
* Added `peekinv` command:
  * View another player's inventory and equipment 
  * Equipment changes update live 
  * Inventory updates when you re-run the command
* Added config options:
    * **Host**
        * UPnP Enabled - automatically configure port forwarding *(default: `ON`)*
    * **Player List**
        * Info Width - customize width of player list *(default: `86`)*
    * **Xaero's Minimap / WorldMap**
        * Show Players *(default: `ON`)*
        * Show Players From Other Servers *(default: `OFF`, previously `ON`)*
    * **Ping Wheel**
        * Show Pings From Other Servers *(default: `OFF`, previously `ON`)*

### Fixes & Improvements

* Fixed multiple player info packets not being grouped into a single update (reduces network congestion)
* Improved XP bar rendering
* Corrected player list stat alignment (left-aligned, center-aligned, right-aligned)