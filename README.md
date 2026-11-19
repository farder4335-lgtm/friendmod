# Friend Commands (Fabric, Minecraft 26.2)

Client-side mod. Commands (type with a leading slash in chat):

- `/friend add <nick>`    - add a friend
- `/friend remove <nick>` - remove a friend (Tab completes from your list)
- `/friend list`          - show friends

Friends' nicknames are slightly green (above the head and in Tab).
You cannot hit friends (the attack is cancelled).
A friend who is 20+ blocks away is shown at the top of the screen as an arrow and the distance in metres.
A player that a friend is hitting gets a red nickname for 5 seconds.

The list is saved in `.minecraft/config/friends.json`.

## Build
Requires JDK 25.

    ./gradlew build

The mod jar is `build/libs/friendmod-1.0.0.jar`. Put it in `mods/` together with Fabric API 0.161.0+26.2 (or newer for 26.2).
