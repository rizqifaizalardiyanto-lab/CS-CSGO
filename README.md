# RF-CSGO 1.0.0

Plugin source project for Purpur/Paper 1.21.x (compiled against Paper API 1.21.1).

## Features
- `/random`: randomly balances all online players between Merah and Biru.
- `/byone <player1> <player2>`: automatically assigns the two players to Red/Blue at random, then starts a BO3 duel on the same map using the red and blue spawn points.
- `/setspawn merah|biru|duel`: saves spawn locations. The `duel` spawn is reserved for future use; current duel uses red/blue sides on the same map.
- `/startmatch`: starts a team BO3 after `/random`.
- `/score`, `/stopmatch`, `/resetmatch`.
- A round is won when all members of the opposing team are dead or have disconnected.
- Players keep their inventory during rounds; final match cleanup clears inventory, armor and offhand.

## Important limitations
- This release does NOT integrate with TacZgun_R_WhiteSun's weapon API and does not automatically grant weapons or vests. Players retain their inventory/armor across rounds (keep-inventory behavior); configure your weapon plugin separately for loadouts.
- Final cleanup intentionally clears the participating players' entire inventory and armor. Have players store personal items before starting.
- There is no automatic return-to-lobby location in this version.
- `/random` includes every online player, as requested. Run it when only intended match participants are online.
- The plugin does not isolate nonparticipants from the arena; protect the arena or manage spectators separately.

## Build
Install Java 21 and Maven, then run:
`mvn package`

The output JAR will be:
`target/RF-CSGO-1.0.0.jar`

## Install
1. Stop the server.
2. Put `RF-CSGO-1.0.0.jar` into `plugins/`.
3. Start the server.
4. As OP, stand at the desired locations and run `/setspawn merah` and `/setspawn biru`.
5. Run `/random`, then `/startmatch`.

For 1v1: set red/blue spawns, then run `/byone PlayerA PlayerB`.
