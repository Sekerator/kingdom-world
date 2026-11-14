# The map
@icon kingdomworld:kingdom_atlas

## atlas | The atlas and the map
@icon kingdomworld:kingdom_atlas
**The kingdom map** shows the borders of every kingdom, their capitals, cities and settlements, provinces, roads, caravans, wars and much more. The ancient kingdoms of the world show on it from the first minutes of a world.

### How to open it

- The key **K** (rebind it in the controls). Pressing it again closes the map, as do the "Done" button and Esc.
- The **Kingdom Atlas** {item:kingdomworld:kingdom_atlas}: right-click with it in hand. The atlas is not used up. It is crafted shapeless from a book, a compass and an empty map.

{recipe:kingdomworld:kingdom_atlas}

### Controls

- **The mouse wheel** zooms, and the point under the pointer stays where it is. The closest zoom is 32 pixels a chunk, the farthest shows up to 1280 chunks across.
- **Dragging** moves the map.
- **Clicking land** shows the card of the kingdom in the corner: its name, the ruler (for a kingdom of the world its size and ruler), members, citizens, chunks, currency and the rate of its copper coin.
- **Clicking a province** of a state of the world shows its card of sale: name and number, the price and the "Buy" or "Buy a charter" button (see [Buying provinces and charters](buying_land)).
- **"To me"** brings the map back to you. You are the white arrow on the map, pointing where you look.
- **"Kingdoms"** opens the list of every known kingdom, the nearest first: the name, the size of a kingdom of the world, the distance in blocks and the direction (N, NE, E and so on). Hover a kingdom of the world to learn how many people and soldiers it has. A click takes the map to its capital, and the wheel scrolls the list.
- **"Army"** shows the squads of your kingdom and lets you give them orders, and the "Lands" button brings the usual map back (see [Orders on the map](map_orders), [The fog of war](fog_of_war)).

### The bottom line

On hover the bottom shows which chunk it is (and its blocks), whose land it is and which province lies there. Below it stands a hint on the controls or the answer of the server. When no capital is in sight, the hint names the nearest kingdom, its distance and direction, and while none is known yet it says: "No kingdom is known yet: kingdoms of the world lie far from the spawn of the world".

### Modes of the map

- **Looking**: the key K or the atlas.
- **Editing land**: the "Territory" button of the throne screen, titled "Land of ..." (see [Claiming and releasing land](claiming)).
- **Planning a road**: the "Plan a road" button in the Roads tab (see [Roads](roads)).
- **Founding a city**: the "Found a city" button in the Roads tab. The land of others is shaded, frames of the distance
  are drawn around cities, a click on your chunk chooses the place of the town hall, a right click resets (see [Cities](cities)).
- **Army**: the "Army" button on the map.

### How the map knows the land

The map shows the dimension you are in, and only what the server sent. On entering a world or a dimension the client gets the whole map of kingdoms of that dimension. When the dimension has more than {cfg:maps.snapshotMaxChunks} chunks of land, only the square around you arrives, with a half side of {cfg:maps.snapshotRadiusChunks} chunks, and the map asks for a new one as you move away. After that only changes arrive. The map of kingdoms is public: everybody sees all land.

## map_view | What the map shows
@icon minecraft:filled_map
The map looks like old parchment. Close up the grid of chunks shows, and darker lines every 32 chunks mark the regions.

### Land and provinces

- **The land of a kingdom** is filled with its color, with dark borders. Zoomed far out, land is drawn in squares of several chunks.
- **Provinces**: the land of a state of the world is filled with its color, borders between states are thick and between provinces thin, and **wild land** is hatched.
- **The names of provinces** show from half a pixel a chunk, the names of wild provinces paler.
- **Boundary stones** of the empty provinces of states show as grey signs: that is where such a province is held in war (see [War for provinces](province_war)).
- On hover the bottom line names the province, its kind (heart of the state, province, borderland, wild land), its holder or "of nobody", and its seat (see [Provinces](provinces)).

### Capitals, cities and settlements

- **A capital**: a gold crown with the name of the kingdom on a dark plate. The names of large kingdoms are drawn first, and a name that found no room shows on hover over its crown.
- **A city** of a kingdom: a sign with its level, one to three gold marks (village, town, large city). The crown of a capital has the marks of its level too (see [Cities](cities)).
- **A city wall**: a square ring in the color of stone or wood, dashed while it is being built, with the gates as gold dots (see [Walls and gates](walls)).
- **The settlements of states of the world** are drawn by rank: a large city (a big house with a tower) shows from 0.4 pixels a chunk, a small town (a house) from 1.5, a village (a small house) from 3, a hamlet (a dot) from 6, and their names closer. Until a settlement is raised its sign has a dashed frame. Hover a settlement to see its card: rank, people and how many of them are named, guard and militia, province (see [Living settlements](settlements)).

### Roads, caravans and robbers

- **Roads** in the color of their class: a path brown, gravel grey, paved light. The built part is solid and the rest dashed, so the roads of the plan of states not laid yet are dashed too (see [Roads](roads)).
- **Caravans**: a small cart in the color of the seller where the time of its way says it is. On hover: "Caravan ... → ..., N% of the way" (see [Deals and caravans](deals)).
- **Camps of robbers**: red crossed swords. You see the camps your kingdom knows of and those you paid an innkeeper {cfg:life.rumorPrice} to mark (see [Robbers](bandits), [Rumors](rumors)).

### Diplomacy and war

- By the capitals of other kingdoms stand a bar in the color of the relation under the crown and a sign of the bond: crossed swords (war), a shield (alliance), a seal (pact), a ring (vassalage), a white flag (truce). A blue frame means the kingdom is shielded while its players are away. On hover you see the bond and the number of the relation (see [Diplomacy](diplomacy)).
- **Land occupied by an enemy** is hatched: red when the enemy holds our chunks, green when we hold his.
- **A besieged city** wears a ring of flames with the share of its gate broken, **a province being held** a diamond with the share held, and **a taken** city or province a banner. On hover you see how far the hold has gone (see [War](war), [Sieges and the ram](sieges)).

### Festivals and the army

- A flag over a capital on a festival day and an awning over a city on a fair day (see [Festivals and fairs](festivals)).
- In the "Army" mode you see your squads, the squads of others in sight, pale marks of those seen lately, and battles going on (see [Orders on the map](map_orders), [The fog of war](fog_of_war)).
- The **Citizens** button (off by default) shows your citizens as dots, if you have the Citizens right: green where the body is, grey at the last place known of a citizen far from players, orange gone far from home or post. Hovering shows the name, the profession and what the citizen does; a click opens it in the [Workers window](workers_window). The dots come for the part of the map in view and are renewed every few seconds.

## map_mods | Map mods
@icon minecraft:compass
When you or the server have a map mod, the land of kingdoms shows on it too, and the atlas of the mod works as before. No map mod is required: a compat module starts only when its mod is installed, and the modules of client maps on the client only. Checked with these versions for Forge 1.20.1: JourneyMap 6.0.7, Xaero's World Map 1.47.0 and Xaero's Minimap 26.6.0, BlueMap 5.3 and Dynmap 3.7-beta-6.

### JourneyMap on the player's client

The land of every kingdom is drawn as polygons along its border (with holes and enclaves) in the kingdom's color with a translucent fill, the name stands over the largest part of the land, and on hover you see the kingdom, its ruler and its currency. The capital is marked with a crown, cities with small crowns and their names, and caravans with a cart that moves along its way. JourneyMap 6.0 or newer is needed: the 5.x versions have another interface for mods and show no land.

### Xaero's maps on the player's client

Xaero's World Map and Xaero's Minimap have no official interface for other mods, so the land shows through their chunk highlights, the way they show the claims of Open Parties and Claims:

- fill and borders in the kingdom's color, the capital chunk brighter, the chunk of a city's town hall too;
- on the world map, on hover, the kingdom (or its capital), ruler and currency;
- on the minimap the kingdom and ruler in the information lines;
- Xaero's maps have no icons of other mods, so caravans do not show on them.

Before it links in, the mod checks that Xaero's classes are as expected. If not, the link stays off with one line in the log, and the game goes on.

### The BlueMap web map on the server

Every map of a dimension gets the marker sets "Kingdoms" (land as shapes along the outlines with holes, the name over the land, the capital with a crown; the popup shows ruler and currency), "Caravans" (a cart and the line of its way), "Cities" and "Roads". BlueMap does not keep the markers of other mods, so they are made again whenever BlueMap starts or reloads. BlueMap 5.12 needs Java 21, while a server on Java 17 runs BlueMap 5.3.

### The Dynmap web map on the server

The sets "Kingdoms" (areas of land and capitals, the popup showing ruler and currency), "Caravans", "Cities" and "Roads". Dynmap areas cannot have holes, so a hole is joined to the outer border by a cut of no width: it shows as a thin line.

### Provinces, settlements and war

All four mods draw every province as an area of its own in the color of its holder and show the settlements of states as cities. The web maps of BlueMap and Dynmap show contested land of 3 by 3 chunks around the place of every province being held. The sets "Contested lands" and "Battle zones" appear on the web maps only when they hold something.

### Language of the labels

On JourneyMap and Xaero's maps the labels are in the player's language. The language of the web maps is set by the key maps.webMapLanguage in the server config: English by default, ru_ru for Russian.

### Settings

- In the client config of the mod: switch JourneyMap and Xaero's maps on, whether to show caravans, the opacity of the fill and the width of borders.
- In the server config: whether the web maps show land and caravans (the keys blueMap, dynmap and webMapCaravans under maps).
- The opacity of the fill on the web maps is {cfg:maps.webMapFillOpacity}: zero leaves only the borders, one gives a solid fill.
- The width of borders on the web maps is {cfg:maps.webMapBorderWidth} pixels.
- The web maps are updated at most once every {cfg:maps.webMapUpdateSeconds} s, and the changes in between are collected.
- The list disabledCompatModules in the common config of the mod switches a module off entirely.

> Land reaches the client maps the same way as the atlas: first the whole map of the dimension (or a square around you), then only changes. Kingdoms renamed or recolored, new rulers and capitals are updated every 5 seconds.
