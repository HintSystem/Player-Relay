### ✨ New Features

* **Server support**
  * If both the **client and the server** have Player Relay installed, the mod should work normally
  * Servers with the mod installed **cannot host their own relay or connect to other relays**
    * There are currently no plans to support P2P relays on the server, but could be considered in the future
  * Players may still **manually connect** *(/connect)* to an external relay if they want to see player info from another server
  * The server must have Ping Wheel installed for other players to send and receive pings
    * Player Relay only relays Ping Wheel pings when connected to a **P2P relay**, not a server - it's Ping Wheel's job to broadcast the packets

### 🔧️ Fixes & Improvements

* Clients can now view their ender chest contents immediately on join, without needing to open an ender chest first, as long as the server has the mod installed

### 🔃 Major Code Refactors

Large portions of the codebase were restructured to support **client ↔ server communication**

This should improve how Player Relay works internally, but may also introduce new bugs

### 🐛 Found a Bug?

Please report any problems you find to [GitHub Issues](https://github.com/HintSystem/Player-Relay/issues)