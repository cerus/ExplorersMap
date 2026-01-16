<img width="20%" height="25%" align="left" src="https://i.ibb.co/zVLbCFYG/Explorers-Map.png" alt="Minecraft map item">

<h3 align="center">Explorers Map</h3>
<p align="center">Changes the map to only show places you've been to like the true explorer you are! With multiplayer support.</p>
<p align="center"><sub><sup>Made with ♥ by <a href="https://github.com/cerus">Cerus</a></sup></sub></p>

<br>

### Installation
1. Download the mod from the releases tab
2. Put the mod into your mods folder
3. Enjoy!

### Configuration
The mod comes with the following configuration file:
`mods/Cerus_ExplorersMap/ExplorersMap.json`
```json5
{
  // The radius of chunks around your player that will be discovered.
  // Warning: Setting this any higher than 10 could make your game or server very laggy.
  "ExplorationRadius": 3,
  // When set to true, each player will each have a different map and will only see the places they've personally been to.
  // When set to false, all players share a map and will see where others have been to. Includes live updates.
  "PerPlayerMap": true,
  // The rate at which cached tile images are loaded from the disk. This value is per tick.
  "DiskLoadRate": 16,
  // The rate at which the chunks around the player are mapped into tiles. This value is per tick. Vanilla value is 20.
  "GenerationRate": 20,
  // Sets how far you can zoom out. Min value is 2.
  "MinZoom": 8.0,
  // When set to true, tiles from instances (temporary worlds) will be saved to disk.
  // Each instance type gets a dedicated folder.
  "SaveInstanceTiles": false
}
```

### Technical details
- The plugin will cache generated tiles on the disk as images. This significantly reduces the stress on the game to generate chunks.
    - The tiles are stored at `mods/Cerus_ExplorersMap/tiles`
- The information which chunks have been discovered by who is stored at `mods/Cerus_ExplorersMap/discovered`
- The mod overwrites the vanilla map rendering mechanic. This could lead to incompatibilities with other mods doing the same.

### Need help? Want to report bugs?
Feel free to open an issue. You can also [join my Discord server](https://discord.gg/xgwjQKdDgw) and talk in `#hytale` about this mod.

### Enjoying the mod?
Consider buying me a coffee! [<3](https://github.com/sponsors/cerus)