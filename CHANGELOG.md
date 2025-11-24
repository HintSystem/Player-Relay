* **Addded server support** (🚧Work in Progress🚧)
  * If both the **client and the server** have Player Relay installed, the mod should work normally
  * Servers with the mod installed **cannot host their own relay or connect to other relays**
    * There are currently no plans to support P2P relays, but could be considered in the future.
  * Players may still **manually connect** *(/connect)* to an external relay if they want to see player info from another server
  * The server must have Ping Wheel installed for other players to see pings
    * Player Relay only relays Ping Wheel pings when connected to a **P2P relay**, not a server - it's Ping Wheel's job to broadcast the packets.

## 🔧 Major Code Refactors

Large portions of the codebase were restructured to support **client ↔ server communication**.

This should improve how Player Relay works internally, but may also introduce new bugs.

## 🐛 Found a Bug?

Please report issues here:
* [GitHub Issues](https://github.com/HintSystem/Player-Relay/issues)
* Or message me on Discord: [**hintsystem**](https://discord.com/users/214251582093524993)