## ✨ New Features

### Server support  [⚠️EXPERIMENTAL]
  * If both the **client and the server** have Player Relay installed, the mod should work between clients
  * Players may still **manually connect** *(`/connect`)* to an external relay if they want to see player info from another server
  * The server must have Ping Wheel installed for other players to send and receive pings
    * Player Relay only relays Ping Wheel pings when connected to a **P2P relay**, not a server - it's Ping Wheel's job to broadcast the packets
  
  #### Parties
* Server only feature
* Restricts player information to only members of the party
* Currently doesn't save when exiting world

### Join codes
* Encrypts whichever connection address is provided \
  *Address for connecting can be changed in `General->Host->Connection Address`*
* **Does not hide your IP address**, only obscures it from being easily visible

### New config options
* **General**
  * Use Encrypted Join Codes - turn off to replace join codes (`j:DGcvwwreF ...`) with addresses \
  *(default: `ON`)*
* **Client**
  * Use Resource Pack Icons - turn on to replace vanilla icons with the ones in your resource pack \
  *(default: `OFF`)*

## 🔧️ Fixes & Improvements

* IP addresses in logs and chat are replaced with fingerprints (`Peer<gt4dsf>`)
* Clients can now view their ender chest contents immediately on join, without needing to open an ender chest first, as long as the server has the mod installed
* Player list now updates every tick instead of every frame

## 🔃 Major Code Refactors

Large portions of the codebase were restructured to support **client ↔ server communication**

This should improve how Player Relay works internally, but may also introduce new bugs

## 🐛 Found a Bug?

Please report any problems you find to [GitHub Issues](https://github.com/HintSystem/Player-Relay/issues)