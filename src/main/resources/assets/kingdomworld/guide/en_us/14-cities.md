# Cities and roads
@icon kingdomworld:town_hall

## cities | Cities
@icon kingdomworld:town_hall
A **city** is a place where a kingdom lives, with a level, a wall, gates, guards and roads of its own. The capital
(where the throne stands) always counts as a city. New cities are founded with a **Town Hall**
{item:kingdomworld:town_hall}.

{recipe:kingdomworld:town_hall}

Crafting: wool, a bell and wool on top; stone bricks, a gold ingot and stone bricks in the middle; three stone
bricks at the bottom. Wool of any color will do.

### How to found a city

1. Give the city a name: rename the town hall on an anvil (up to 32 characters). Without a name the city is
  called after the kingdom with a number: the first city after the capital gets number 2.
2. Place the town hall on the land of your kingdom: on a claimed chunk or in a province it holds.
3. The king or a member with the **Construction** right must place it.
4. The kingdom hears "City ... founded at ...". If no city can be founded there, the town hall goes straight
  back into your inventory with the same name, and the chat tells why.

### Founding from the map

There is no need to go there: the "Found a city" button in the overview of the Roads tab opens the atlas. The land of
others is shaded on it, and frames of the least distance between cities are drawn around cities and capitals: no city
is founded inside a frame. Click a chunk of your land, type a name (empty: after the kingdom) and leave "With a builders'
post" on to have a post set up by the town hall and a builder hired at once. "Found" sends the order and takes you back to
the throne.

When the turn of the order comes, the server takes a town hall from the storehouses (none: it orders one from the
workshops and waits; if the workshops do not make it, the list of orders says "put a town hall into a storehouse"), loads
the chunk for a while, sets the town hall on a free place nearest the middle of the chunk (natural ground, three blocks of
air over it, no road and no construction) and founds the city. A captured village without a town hall or city is founded
the same way: by a click on it.

### Limits

- The limit of cities of a kingdom of players, besides its capital: {cfg:roads.maxCities}. There is none by default:
  as many cities as the land and the money allow.
- A city stands at least {cfg:roads.minCityDistance} blocks from any other city or capital in its dimension,
  those of other kingdoms included, counted along x or z. The areas and walls of cities are squares, so the walls of two
  large cities never meet: a strip of land stays between them.
- Kingdoms of the world are not bound by these limits: the plan of the world sets their cities and settlements
  (see [Living settlements](settlements)).

### What a city gives

- **A level**: village, town or large city, by the citizens and buildings around it ([City levels](cities_levels)).
- **A wall with gates** and guards at the gates, and a large city charges an entry fee ([Walls and gates](walls)).
- **Roads**: the ends of a road stick to a city, and caravans travel faster along the network ([Roads](roads)).
- **The law**: every block inside the square of a city is property of the kingdom. A block a stranger breaks
  drops nothing and comes back by itself, and breaking it is damage to property ([Crimes](crimes)). An alarm
  calls the soldiers of the whole city, and they chase through the city and 48 blocks beyond its wall or edge.
- **Festivals**: on a festival the citizens gather at the throne or at the town hall of their city
  ([Festivals and fairs](festivals)).
- **A tavern**: in the evening citizens go to the tavern of their city ([The tavern](tavern)).
- **Ransom**: confiscated things can be bought back at the town hall of a city too ([Fines and punishment](fines)).
- **A goal of war**: a city is taken by holding the square at its town hall ([Capture and destruction](capture)).

### The town hall

Right click a town hall to hear the name of its city and kingdom. A town hall that founded no city says so. A
broken town hall gives the city up: the kingdom hears "The town hall of ... is gone: the city is given up", and
the roads stay. Cities show in the Roads tab of the throne window ([The Roads tab](roads_tab)) and on the atlas
([What the map shows](map_view)).

## city_shops | Treasury and shops of a city
@icon kingdomworld:shop_counter
Every city of a kingdom of players keeps a treasury and a purse of the townspeople (see [Treasuries of cities](city_money)). The hover of the row of a city in the Roads tab shows "Treasury: N · a day +M (to the city a, to the crown b)".

### The counter

A **counter** {item:kingdomworld:shop_counter} is a place for a shop of citizens. A member of the kingdom with the Construction right puts it up in the square of a city of its kingdom, on its land, in a building or in the open. Elsewhere it is not placed and stays with the player. Put up as many counters as you like; a shop opens once the townspeople want more and a keeper is found. A broken counter closes its shop, and the crown buys its goods back into the storehouses.

{recipe:kingdomworld:shop_counter}

### Places of shops

Shops stand in the houses of merchants and craftsmen, at the stalls of markets and at the counters of players (see [Townspeople and shops](shops)). A place works while it stands in the square of a city of its kingdom and its building works. The window of a city in the Treasury tab lists its shops and says why new ones do not open: "Shops have nowhere to open", "No free townspeople for shops" or "The crown gives the shops no goods". A click on a counter or on a keeper opens the window of the shop: a player buys from the account or with coins of the currency of the kingdom, the price with the sales tax. Shops serve strangers while the market is open to them.

### Trading houses

A trading house buys of the masters of its city what they have beyond their local stock, only what citizens made, wholesale and without the sales tax, when the master sells for no more than {cfg:cityEconomy.traderBuyPercent}% of what the house gets abroad, and exports it at once: a village gets {cfg:cityEconomy.craftExportVillage}%, a town {cfg:cityEconomy.craftExportTown}%, a large city {cfg:cityEconomy.craftExportCity}% of the worth. Exports come of a surplus: while raw materials cost the crown their worth, a master does not sell cheaper. A city exports no more than its demand from abroad (see [Buying of citizens](crown_purchases)): roads to other cities and a working market raise it. What the houses did not take is asked of the masters as an order for the next day; a house opens when such an order would feed it. The window of a city shows of its shops the trade, whether it is fixed, what they made and exported a day.

### The regional market

Townspeople buy in their own shops first, and what they did not find in the shops of the nearest larger city of their kingdom reached by road (the key `regionalMarket`). The sales tax goes to the treasury of the city where the shop stands. So a village without the shop it needs buys in the town, and the town grows by its countryside. Townspeople of every city also take lots of players on the market of the kingdom when a lot is no dearer than the cheapest of the item in the shops of their city; the sales tax of the city goes into its treasury.

### A house for a shop

When the townspeople of a city did not find more than {cfg:cityEconomy.houseWhenUnmetPercent}% of their basket, no place for a shop is free and a townsperson would keep one, the head of the city wants a house of a merchant or a craftsman in the style of the kingdom, after the house for tenants and the storehouse. The head first buys the materials of its constructions in the shops and workshops of its city without the tax, then takes them from the storehouses and imports.

### A large city with townsfolk

A city with townsfolk (see [Arrival and growth](growth)) brings the crown the most, as long as it has work for them. The townsfolk on the posts make goods of the crown, their wages stay in the purse of the townspeople and come back to the shops, the hands of workshops make more goods to sell and export, and of their shares and of what the townsfolk buy the city takes the tithe and the sales tax. In the example of 60 days a large city with 280 townsfolk (140 on posts, 90 in workshops, 50 idle) brings the crown more than without them. Without posts and places in workshops the townsfolk cost only food, do not grow and leave.

## cities_levels | City levels
@icon minecraft:bell
Every city and every capital has a **level**: village, town or large city. The level is worked out by itself from
the citizens and buildings around it and grows together with the kingdom.

| Level | Citizens | Buildings |
|---|---|---|
| Town | {cfg:roads.townPeople} | {cfg:roads.townBuildings} |
| Large city | {cfg:roads.cityPeople} | {cfg:roads.cityBuildings} |

A level is reached only when both numbers are reached at once, citizens and buildings. Until then a city stays
a village.

### Who counts

- **Citizens**: a citizen of the kingdom whose home (or, without a home, the last place it was) lies within a
  square of 64 blocks each way from the town hall or throne. If the wall of the city is wider, all inside the
  wall counts.
- **Buildings**: buildings of the kingdom's register in the same square ([The roles of buildings](building_roles)).
  Walls and gates do not count.
- A citizen or a building near two cities counts for both.
- **The townsfolk** of a city (people who live there as a number, see [Arrival and growth](growth)) all count among
  its citizens.

### What the level changes

- **The radius of the wall and the square of the city**: {cfg:roads.wallRadius} blocks for a village, half as much
  again for a town ({cfg:roads.wallRadius*1.5}), twice as much for a large city ({cfg:roads.wallRadius*2}). The
  square of a city without a wall is the one a wall of its level would take: the law of damaged property holds in it.
  A city of a kingdom of players lives within its state: the part of the square beyond the border of its land is no
  city for the law, work, beds, newcomers or the guard.
- **A city short of room.** When the people of a city (named and townsfolk) need more room than the square of its
  level and wall gives, the square is wider by as much as they need built densely: {cfg:citizens.folkDensity} people
  a block of area, without an upper limit. A large city outgrows its {cfg:roads.wallRadius*2} blocks at about 74
  thousand people. Where it grew, it holds for the law, the guards and the storehouses on the kingdom's own land only,
  and no other city is founded there. The wall is not rebuilt by itself: the king orders a new ring, laid around the
  whole city.
- **The entry fee**: only a large city charges one ([Guards](guards)).
- **Fairs**: by default only a large city with a market holds them ([Festivals and fairs](festivals)).

### Where to see it

In the Roads tab, view "cities": the level and wall of every city, and on hover its citizens (named and townsfolk:
"Citizens: 64 (+936 townsfolk)"), buildings and beds ("Beds: 300, 12 free": free of named citizens and townsfolk
alike). The townsfolk go to the winner with their city when it is taken, annexed or breaks away. On the
atlas the sign of a city or the crown of a capital has one to three golden marks: village, town, large city.

> Capitals of kingdoms of the world laid out in districts are built for their level from the start: a small
> kingdom is a village, a medium one a town, a large one a large city
> ([Capitals of the kingdoms of the world](world_capitals)).

## governors | Governors of cities
@icon minecraft:bell
**The governor of a city** builds the city by itself, without the king and without walking there. The governor is the **house of the nobility** granted a title on the city (a baron, a count or a duke), else a **mayor**: a citizen the king appointed. The capital only has a mayor: it is no fief. A title of a player on a city makes no governor (the player builds by hand), and a mayor may still be appointed there.

### How to appoint one

In the Roads tab, the cities view, the Actions button of a city:

- **Appoint a mayor** opens the Workers window on the citizens of the city (on all of them while nobody lives there). Choose a citizen, press Office and Make mayor. It takes the rights Citizens and Construction.
- **Make a baron** (the king only, not for the capital): the same with Make baron. The king raises a new house of the nobility headed by that citizen, with the title of baron on the city (see [Nobility and factions](nobility)).
- **Dismiss the governor**: a mayor loses the office, a house its title on the city.
- **Stop the governor** and **Let the governor go on**: a stopped governor orders nothing new.

The Office button of the Workers window also works without the actions of a city: the city is that of the filter of the window, else that of the post of the citizen, else that of its home.

**Who may govern.** A living citizen of the kingdom who is no soldier, no mayor of another city and no head of a house. A citizen with a post must work in that city: it lives where it works. Otherwise the button is inactive and its hint says why (take it off the post or move it first). Once a game day a mayor moves into a free bed of its city (while there is none it lives where it did), and its name tells its office by the level of the city: elder in a village, mayor in a town, governor in a large city; a baron is a baron.

**When the office ends.** The mayor died, left or became a citizen of another kingdom, joined the army, works at a post in another city, or the city went to a house or was taken: the office ends, the court writes it into its chronicle and the members with the right Construction hear it. The city stops building by itself, the orders of the governor not laid out yet are given up, its constructions stay as ordinary ones.

### What a governor builds

On every played day (somebody of the kingdom was online), a governor looks at its city, one city a second:

1. The city has no post of builders of its own and somebody of the kingdom is without work: the governor orders a post by the town hall and hires a builder (never one of another city). Nobody is without work: helpers of other cities build there; none either: no builder, it waits.
2. The city has as many constructions and waiting orders as builders (one at least): it waits.
3. Otherwise as the rulers of the world: a house when the beds are nearly taken, a storehouse when the storehouses of the city are full, then the first building the level of the city lacks (storehouse, market, wells, workshops, barracks, towers, tavern, mage tower). A house of the nobility leans to its character: a warrior to barracks and towers, a merchant to the market, a mage to the mage tower. A mayor has no leanings.
4. Nothing is lacking and there is no wall: a palisade, a stone wall for a large city.

It takes the cheapest fitting template in the style of the kingdom, on free natural ground by the town hall, as an order from the capital (see [How a kingdom builds](construction)). A governor **never** repairs, upgrades or takes down, never lays a road and never touches what players built: a block a player placed on the place of its construction stays, and a construction that cannot be finished for it is given up, its materials going back to the storehouses. An order or a construction of the governor the king gives up is not ordered again in that city for 3 game days. The server limits neither cities nor governors nor houses of citizens.

**When the places run out.** When the building it wants has no place in the square of the city (or an order of its found none), a governor widens the city by a ring of 8-16 blocks on the land of its kingdom and builds on. There is no limit: the city grows as long as land of the state lies around it. Without land of its own around, the row of the city says it needs land around it: claim or buy land nearby. The wall is not laid out anew for it: the king orders a new ring of the wall himself, and it goes around the whole city.

### The money of a governor

A governor builds from the storehouses (it counts no more than {cfg:roads.governorStockPercent}% of every item, the rest is for the buildings of the king) and with the money the king gives the governors: the field **Governors a day** in the overview of the Roads tab (set by one with the right Treasury; 0 by default). It is shared evenly by the cities with a governor. Each city spends its share a day at most and never lets the treasury fall below its reserve (a few days of its expenses): with that money it buys abroad what its constructions lack (see [Export and import](export_import)). While the sum is 0 a governor builds only what the storehouses pay for and waits for the rest; when what is lacking costs more than its share, it saves. Where the cities keep money of their own (a kingdom of players, see [Treasuries of cities](city_money)), a governor pays out of the treasury of its city instead: what the city kept of its taxes and what the king granted it, less a reserve of a few days of its income and what it promised its constructions. Governors a day then goes into the treasuries of the cities with a governor as a grant, each city its share a day while it holds less than a week of shares granted and unspent, and never so that the treasury of the kingdom falls below its reserve; a city with a governor keeps a month of its own share of the taxes instead of a week, so its governor can save for a building.

### What you see

The row of a city in the Roads tab: "Governor: Irma, elder", "House Velomir, baron" or "No governor"; on hover what the governor decided last (builds, saves and how much is short, waits), what it spent today of its share and the people ("People: 64 (+936 as a number)"). Once a played day the members with the right Construction get one summary when something happened, for example "Governors of the cities: Olkhovka builds: Small house; Kamenka saves for Well, 1s 2c short".

> A city granted to a house of the nobility before this version starts building by itself after the update; the first summary says so.

## walls | Walls and gates
@icon minecraft:stone_bricks
A city can be ringed with a **wall**. It is ordered in the Roads tab of the throne window: view "cities", choose
the city and press **Actions**. The Construction right is needed.

| Wall | Material | Height |
|---|---|---|
| Palisade | logs | 4 |
| Stone | stone, brick | 5-6 |

- **Put up a palisade** {item:minecraft:oak_log}: a ring of logs of any wood. The logs come from the tag
  kingdomworld:wall_wood, which holds every log of the game and of mods.
- **Put up a stone wall** {item:minecraft:stone_bricks}: a higher wall with battlements. Cobblestone, stone, stone
  bricks, cobbled deepslate and deepslate bricks, bricks, mud bricks, sandstone and anything of the item tag
  kingdomworld:wall_stone will do, and a server may add to that tag.
- Every block of a column takes one item. The stone or logs of the kingdom's style go first while there are
  enough of them, then whatever the storehouses hold most of.

### How a wall is built

- The wall is laid out as a square ring around the town hall or throne on the lay of the land; its radius
  depends on the level of the city ([City levels](cities_levels)). Capitals of kingdoms of the world take the
  radius of their plan.
- The ring goes around water, dangerous places and the land of others. If there is no ground for a wall around
  the city, the chat says so.
- The king orders a wall only when its ring lies wholly on the land of the kingdom. Otherwise the order is refused,
  saying how many chunks of the ring are not yours: claim the land around the city first.
- It is built like a road: the free builders of the kingdom pay column after column from the storehouses, and a
  column goes up in the world once its place is loaded. The wall breaks nothing and leaves trees as they are.
- A finished wall and its gates are entered into the register of buildings as fortifications ("City wall" and
  "City gate") and count for the fortification of the kingdom. The integrity of the wall is checked once a minute
  while its place is loaded.
- Upgrades, repairs, demolition and automatic repairs of the Construction tab are off for walls: the Roads tab
  keeps them. To mend a wall, order it again: the new wall only adds what is missing, the blocks standing stay,
  and what was paid for them goes back to the storehouses.
- A new wall replaces the old one: a stone wall replaces a palisade, for example. What was paid and not placed
  goes back to the storehouses.
- On the atlas a wall is drawn as a ring with gates, dashed while it is being built.

### Gates

- A gate opening three blocks wide appears by itself where a road crosses the wall when it is laid out.
- **Cut a gate where I stand** cuts more: stand by the wall, away from other gates, and a gate is cut at its
  nearest block. Only the blocks of the wall itself are taken away, and they go to the storehouses.
- Cutting or opening a gate never loads a chunk on the spot. A gate on the edge of two chunks opens only once
  all its columns are loaded, and a gate cut where the world was not loaded opens as soon as it is.

### The leaves

Once the wall stands, the builders hang **leaves** in the opening: four rows of three blocks.

- A palisade gets leaves of fence gates {item:minecraft:oak_fence_gate}: 12 fence gates or 48 planks from the
  storehouses.
- A stone wall gets an iron portcullis {item:minecraft:iron_bars}: 12 iron bars or 5 iron ingots.

You never move the leaves by hand: the kingdom does it. In peace the gates stand open. In war they close when an
enemy (a player or a soldier) is within 24 blocks, and open before their own people and allies within 4 blocks as
long as no enemy is within 8 blocks. An enemy cannot open a closed gate: it can only break it.

Leaves of planks have a strength of {cfg:siege.woodGateStrength}, an iron portcullis {cfg:siege.ironGateStrength}.
A soldier with the order "siege" takes 1 a second off it, a ram {item:kingdomworld:battering_ram} 3. Broken leaves
fall as items, and the gate stands open until the builders hang new ones (once the enemy is gone).
More in [Sieges and the ram](sieges).

### Entering a city

Guards stand at the gates: soldiers with the order to guard the stretch at a gate. They hail a wanted player and
keep them out. A large city charges an entry fee from everyone who is not a subject, an official or respected
(up to {cfg:law.maxEntryFee}); from a reputation of {cfg:law.highReputation} on, entry is free. Whoever goes past
the gates, over the wall or through a breach, trespasses if seen. In a city without a wall nobody stands at gates.
More in [Guards](guards).

## roads | Roads
@icon minecraft:dirt_path
**Roads** join cities and capitals. On a built road players, horses, caravans and squads of the army travel faster,
and robbers lie in wait on roads less often.

| Class | Width | Surface |
|---|---|---|
| Path | 1 | none needed |
| Gravel road | 3 | gravel |
| Paved road | 3 | stone |

- **Path**: grass and earth become a dirt path {item:minecraft:dirt_path}, as with a shovel. The road itself takes
  no material.
- **Gravel road**: gravel {item:minecraft:gravel}.
- **Paved road**: cobblestone {item:minecraft:cobblestone}, stone bricks, cobbled deepslate and deepslate bricks,
  polished andesite, diorite and granite, bricks or mud bricks.
- Bridges of every class are of planks {item:minecraft:oak_planks}, embankments of dirt, coarse dirt or cobblestone.

> Item tags set the materials, and a server may add blocks of mods to them:
> the surface of a gravel road comes from the tag kingdomworld:road_gravel,
> the surface of a paved road from the item tag kingdomworld:road_paving,
> bridges of every class from planks of any wood, the tag kingdomworld:road_bridge,
> embankments from dirt, coarse dirt and cobblestone, the tag kingdomworld:road_fill.

### How to lay a road

1. In the Roads tab of the throne window press **Plan a road**: the atlas opens in road mode.
2. Left click the start and the end. An end within 48 blocks of a city or a capital leads to it: the road joins
  your roads there, enters through a gate of its wall or ends on free land by the town hall or the throne.
3. The next click puts a point between into the nearest leg: this makes the road wind more or run straighter. A
  point can be dragged, a right click on it removes it, Undo or Ctrl+Z bring the points back as they were.
4. The Class button chooses a path, a gravel road or a paved road, the Way button how it goes: **along the land**
  (cheapest, around steep places), **moderate** or **straight** (shorter, with cuttings, tunnels and bridges).
5. The server plans the way and the card shows its length and the time of a caravan, the earthworks (cutting,
  embankment, tunnel, bridge, piers), the materials (those missing in red), the days of work of builders and miners
  and how long it takes with your workers, about what it is worth in coins, foreign land and the gates to cut, and
  after every change the difference from the last plan. The Profile button shows the road in section.
6. **Build** lays the road, Reset drops the plan.

### How the server finds the way

- A way is always found, unless buildings of players across the whole land, a foreign wall without a gate, the
  void or the border of the world stand in it. Then the leg is red and a cross shows where it got stuck; a point
  between helps to go around buildings.
- The road is **levelled**: it climbs a block at most once in 1, 2 and 3 blocks for a path, a gravel and a paved
  road unless the server changed it (key `maxGrade`). Hills are cut down, hollows filled with embankments up to
  {cfg:roads.maxFill} blocks, deeper ones bridged, mountains pierced by tunnels {cfg:roads.tunnelHeight} blocks
  high, water crossed on bridges.
- A cutting or a tunnel never goes below water beside the road; under lava only a tunnel with a thick roof.
- The search weighs earthworks against length itself: along the land it finds the cheapest road, moderate and
  straight make every block of length dearer (2 and 20 times a plain block of road by default, key
  `routeModeWeights`), and the road runs straighter.
- Foreign land is gone around when that is cheap and crossed when there is no other way.
- A thread of its own on the server searches; it does not slow the game. While points are moved, land that is not
  loaded is read from the saved world when it was generated before, and otherwise taken from the estimate of the
  generator: no new chunk is made. When such a road is laid, the land the save could not give is loaded a little at
  a time (missing chunks are generated then and stay in the world), the way is planned on it again and the road laid
  by itself.
- Kingdoms of the world and dimensions with a ceiling search the old way: no longer than {cfg:roads.maxRouteLength}
  blocks, {cfg:roads.searchStepsPerTick} steps a tick and {cfg:roads.maxSearchSteps} at most, a bridge or an
  embankment no longer than {cfg:roads.maxBridge} blocks.

### The price of earthworks

Every block dug, filled and bored costs work and materials besides the surface:

| Work | Who | Seconds a block |
|---|---|---|
| Soil (earth, sand, gravel, clay, snow) | builders | {cfg:roads.digSoilSeconds} |
| Rock and ore | miners (without them builders take {cfg:roads.rockByBuilders} times as long) | {cfg:roads.digRockSeconds} |
| In a tunnel | the same | half as long again |
| Embankment, lining | builders | {cfg:roads.fillSeconds} |
| Deck of a bridge, pier | builders | {cfg:roads.bridgeSeconds} |

An example: a gravel road meets a ridge of rock 30 high and 150 blocks across, and going round its end is 120 blocks
longer. Going round costs about 1,440 seconds of work, a tunnel about 17,600 (rock for the miners, torches,
lining), an open cutting about 96,000. Along the land and moderate the road goes round the ridge, straight it bores
the tunnel: longer to build, but faster for caravans. The card shows that choice in numbers.

### How many roads

The limit of roads of its own a kingdom of players keeps: {cfg:roads.maxRoads} (none by default). A kingdom of the world
keeps up to {cfg:roads.npcMaxRoads} roads (the roads between the settlements of its plan). How a road is built and mended and what
it gives: [Building and wear of roads](roads_building). A road across the land of others waits for their consent:
[Roads across the land of others](roads_crossing).

## roads_building | Building and wear of roads
@icon minecraft:cobblestone
A road is laid by the **builders** of the kingdom who have no construction within the reach of their post: roads
and walls are the work of free builders ([Builders and materials](builders)). How many are free shows in the
overview of the Roads tab.

### How the building goes

- A road is split into sections of 16 blocks.
- Every block is paid from the storehouses at the pace of its class: by default 1 second of work for a block of a
  path, 3 for a gravel road and 5 for a paved road. What other work has reserved is left alone.
- Paid items wait in the section until the block goes up in the world. A block is placed as soon as its place is
  loaded, so far from players a section counts as built once it is paid.
- Replaced blocks give their drops to the storehouses, grass and flowers over the road are cleared.
- Unbreakable blocks, containers and blocks holding data are never broken: that block of road is left out and its
  items go back to the storehouses.
- A finished road brings the message "Road #... is built".
- A road can be paused and resumed, or given up: it is forgotten, built blocks stay in the world and unused
  materials go back to the storehouses.

### A levelled road

- Cuttings, embankments, tunnels and bridges are paid for column by column: miners give their seconds to the rock
  first, builders dig the rock left, then builders do all the rest. The items of a column are taken all at once
  when its work is paid; embankments and lining first come from what the road digs out (the dug material counted
  in advance), then from the storehouses.
- In the world a column is laid in order: trees whose trunk stands on the road are felled whole (logs to the
  storehouses), water and lava beside it closed off with cobblestone, the roof of a tunnel in earth or sand propped,
  then the cutting from the top down, the embankment from the bottom up, the piers of a bridge, the surface or the
  deck, the rails of a bridge and the torches of a tunnel.
- Only what is natural is dug: earth, stone, ore, plants, trees, paths, the rock of mountains, caves, the Nether
  and the End. Soil and ore go to the storehouses, earth they have no room for to the spoil.
- **When a column is in the way.** A chest, blocks of players, another road, the frame of a building or foreign
  land without consent are left alone: the column waits, a message with the place and the reason comes once a
  game day, and the tab shows how many such columns there are and the first places. Clear the way or bring the
  missing stone and materials, and the laying goes on.
- Far from players the server loads the places of paid columns in turn and lays them.

### Miners on roads

- The miners of the kingdom at their posts with a pickaxe dig the rock of the roads with the builders. A miner
  whose post has met its goal gives all its time to the roads, the others {cfg:roads.roadMinerShare*100}%.
- A miner does not leave its post: only time goes to the roads, so the mine brings in less by the share the roads
  really took. Every block of rock wears the pickaxe of one of the helpers.
- The button "Miners help on roads" in the overview of the Roads tab turns the help off: then builders dig the
  rock, {cfg:roads.rockByBuilders} times as long.

### Wear and repair

- Built sections wear very slowly: by default a path loses 0.4% of its condition a day, a gravel road 0.2%, a
  paved road 0.1%. Busy roads wear faster.
- A worn road is slower for caravans.
- Builders mend sections whose condition fell below {cfg:roads.repairBelow*100}% by themselves, unless the server
  turned that off. You can order a repair too: Actions, Repair the worn sections.
- Repairing a section takes {cfg:roads.repairShare*100}% of its materials.

### Speed on roads

- A player standing on the road itself (on a block of it, not on the grass beside it) moves faster: by default
  10% on a path, 20% on a gravel road and 30% on a paved road. A horse and other mounts get the same. The server
  may turn this off.
- Squads of the army march {cfg:army.roadSpeedBonus*100}% faster on a road, and their ways bend to roads by
  themselves when that saves time ([Orders on the map](map_orders)).

### Caravans

The caravans of deals take the fastest way over the network of roads ([Deals and caravans](deals)). Across country
a caravan covers {cfg:trade.caravanBlocksPerMinute} blocks a minute, and on built sections by default 1.6 times as
fast on a path, 2.5 times on a gravel road and 3.5 times on a paved road. From the end of a road it goes on along
the next roads, and across country straight ahead. The way of a caravan is kept and survives a restart, and on the
atlas and in map mods the caravan moves along it.

Near players a caravan walks the road in the world: a merchant with one or two pack llamas (they cannot be taken,
drop nothing and players cannot hurt them), with the guards of the army beside it. Far from players a caravan
moves only in the data, and its goods always stay in the data of the caravan. On attacks on caravans:
[Ambushes on the roads](bandits_ambush).

## roads_crossing | Roads across the land of others
@icon minecraft:map
- A road may be built over land of nobody without asking.
- Across the **wilds** of kingdoms of the world, the land where they do not live, a road needs no consent and costs
  no more ([The wilds](wilds)).
- If the way touches the land of another kingdom, the atlas says "Crosses the land of: ... (their consent is
  needed)", and the owner gets a **request**, like an offer of a deal.
- The answer is given in the Roads tab, view "requests", within {cfg:roads.crossingDays} game days: the buttons
  **Allow** and **Refuse**.
- Until everybody agrees, the road waits for consent. A refusal or silence gives it up: "Road #... is given up: a
  kingdom on its way did not agree".

### Who builds on the land of others

When the way crosses the land of others, a choice button turns on in the atlas:

- **Jointly**: every kingdom builds the sections on its land with its builders and materials, and the land of
  nobody is built by the one who asked.
- **At our cost**: the builders of the one who asked build all of it from its materials, with the consent of the
  owners.

A block of road is never placed on the land of an owner who did not agree, even when it is paid already.

### Consent remembered

If an owner once let a road of a kingdom "at our cost" cross its land, the next such roads of that kingdom across
its land are built without a new request (a server may turn this off). A joint road, where the owner builds and pays
on its own land, always asks again.

### Rulers of the world

The ruler of a kingdom of the world answers a request at once and by its character: a merchant lets roads pass
gladly, a warrior only for those it thinks well of, generosity helps, pride and caution hinder
([The temper of a ruler](rulers_temper)). A ruler lays roads to neighbours closer than
{cfg:npcKingdoms.rulers.roadDistance} blocks itself, and across the land of players it sends the usual request.

## roads_tab | The Roads tab
@icon kingdomworld:throne
The **Roads** tab of the throne window keeps cities, walls, roads and robbers. The View button goes through its
parts (a right click goes back). The king and members with the Construction right may change things, and then the
tab says "You run the construction". Patrols need the Army right. Everybody else sees the tab as view only.

| View | What is there |
|---|---|
| overview | summary, planning |
| cities | levels and walls |
| roads | building, repairs |
| requests | crossing land |
| robbers | known camps |
| risks | caravans of deals |

### Overview

Cities out of the limit, own roads out of the limit, roads built, builders free for roads, requests to you and
from you, known camps of robbers. The Plan a road button is here too ([Roads](roads)).

### Cities

Every city and the capital: its level, its wall and how much of it is built; on hover the citizens and buildings, the
builders of the city, the helpers from other cities, its constructions and what it needs for the next level ("For Town:
citizens 7/12, buildings 4/6", say). The Actions button of the chosen city: Put up a palisade, Put up a stone wall, Cut a
gate where I stand ([Walls and gates](walls)), Order a building (the catalog of the Construction tab with this city), Send
a builder, Rename and On the map (the atlas on this city). Without cities the tab reminds you to place a town hall
([Cities](cities)).

Send a builder adds one builder to the city a click: a free seat at a post of builders of the city takes a builder without
a post or a citizen without work (on an order of the king also a builder with nothing to build at the moment); without a
free seat the post gets one more; without a post, or with a full one, a new post from the storehouses is set up near the
town hall ([Orders from the capital](construction)). Nobody takes a worker of another profession off his work. The
Construction and Citizens rights are needed.

### Roads

Number, from where and to where, class, length and state: "waits for consent", "paused", "built N%" or
"condition N%". On hover: who builds it with you, how many sections are worn and being repaired, and what your part
still needs for the road, bridges and embankments. The Actions button:

- Repair the worn sections: builders repair them for a share of the materials;
- Pause the building and Resume the building;
- Send a squad to patrol it: choose a free squad and press Send, and it walks the road up and down and keeps
  robbers off it;
- Give the road up ([Building and wear of roads](roads_building)).

### Requests

Requests of other kingdoms to lay a road across your land and your requests to them, with the time left to answer.
The buttons Allow and Refuse ([Roads across the land of others](roads_crossing)).

### Robbers

The camps the kingdom knows of: where the camp stands, how many robbers it has and whether a leader, how far it is,
how much loot it holds and how many raids it made ([Robbers](bandits)).

### Risks

The caravans on the way and the deals of the kingdom: the chance of a robbery with guards and without, the share of
the way on roads and the time on the way ([Ambushes on the roads](bandits_ambush)).

## bandits | Robbers
@icon minecraft:crossbow
**Robbers** live in camps in the wild and fall upon caravans and upon players who come close. Unless the server
turned them off, camps appear by themselves.

### Where camps appear

- Once a game day a new camp appears with a chance of {cfg:roads.campSpawnChance*100}%, while the world has fewer
  than {cfg:roads.maxCamps}.
- A camp stands in the wild: at least 400 blocks from the claimed land of the settlements of kingdoms of the world,
  at least {cfg:roads.campMinDistance} blocks from the claimed land of kingdoms of players and at least 128 blocks
  from roads and other camps. The wilds in the provinces of states keep no distance: that is where robbers live
  ([The wilds](wilds)).
- Camps appear more often near roads with many caravans.
- A new camp has a leader and common robbers, {cfg:roads.campStartBandits} of them. A camp nobody disturbs gains a
  robber a day with a chance of {cfg:roads.campGrowthChance*100}%, up to {cfg:roads.campMaxBandits} (the leader not
  counted). Killed robbers are gone from the camp for good.

### A camp in the world

Within 64 blocks of a player a camp stands in the world: a campfire {item:minecraft:campfire}, two tents and a chest
{item:minecraft:chest}. It is put up only where nothing has to be broken, and its robbers walk about it (at most
{cfg:roads.maxPhysicalBandits} at a time). Blocks of a camp drop nothing when broken, and the chest gives only the
robbed goods. Far from players a camp lives in the data.

### The robber and the leader

- The **Robber** and the **Robber Leader** are people of the mod. A robber has 24 points of health, a leader 50,
  and the leader hits harder and wears armor.
- They take weapons and armor from tables of the server (by default swords, axes, bows and crossbows of any mod),
  the leader picking the best. With a bow a robber shoots.
- A robber breaks no blocks, picks up no items and drops nothing.
- It attacks players nearby, but leaves alone those away from the keyboard, players in creative mode and
  spectators.
- In a fight far from players a robber has a strength of {cfg:roads.banditStrength}, a leader
  {cfg:roads.leaderStrength} (a recruit with bare hands has 10).
- A slave who escaped its owner runs to the nearest camp of its dimension and becomes a robber, and its pack
  becomes loot of the camp ([Companions](companions)).

### How to get rid of a camp

- Kill the robbers and the leader yourself and take the chest. The chat says "The leader of the robbers of the camp
  at ... is dead" and "The camp of robbers at ... is cleared".
- Squads of the army with the order to hold a point, guard or patrol fight a camp within 48 blocks of them once a
  day. Once they clear it, its goods go to the storehouses of their kingdom ([Orders on the map](map_orders)).
- The guild: contracts to hunt robbers and a bounty on the leader work as on any monsters
  ([Contracts](contracts)).

A kingdom learns of a camp when its caravan is robbed, its player comes near the camp or its squad fights it.
Known camps show in the Roads tab and on the atlas. Innkeepers tell of camps too ([Rumors](rumors)).

## bandits_ambush | Ambushes on the roads
@icon minecraft:iron_sword
Robbers fall upon the caravans of deals near their camps ([Deals and caravans](deals)). Ambushes are rare, and you
can make them rarer still.

### Far from players

A caravan goes its way piece by piece. On every piece within {cfg:roads.campReach} blocks of a camp an ambush may
happen:

- the chance is {cfg:roads.ambushChance*100}% for every 100 blocks of way across country next to a full camp;
- it falls with the distance to the camp (down to nothing at the edge of its reach) and with a small camp;
- on a road it is lower: by default times 0.8 on a path, times 0.6 on a gravel road and times 0.45 on a paved road;
- on the land of a kingdom it is multiplied by {cfg:roads.ownLandFactor};
- every squad that holds a point or guards within {cfg:roads.protectRadius} blocks multiplies it by
  {cfg:roads.garrisonFactor}, and every patrolling squad by {cfg:roads.patrolFactor}.

Without camps nearby caravans are never lost.

### How an ambush ends

- The leader and at most {cfg:roads.raidParty} robbers come out.
- The guards of the caravan fight them by the battle model of the army: wounded and fallen as in a battle of squads.
- A caravan without guards loses {cfg:roads.unguardedLoot*100}% of its goods, and guards who lose the fight a share
  by the outcome of the battle.
- The goods taken go to the camp, and their payment goes straight back to the buyer.
- The merchant of the seller dies with a chance of {cfg:roads.merchantDeath*100}%, and the chronicle tells that
  robbers killed him.

### Near players

The ambush is real: the robbers of the camp come at the merchant of the caravan. If they kill him, they take a
share of the goods. Players nearby can stand up for the merchant.

### How to protect caravans

- Lay roads: on a paved road an ambush is more than twice as unlikely as across country.
- Set squads to hold or guard dangerous places and send patrols along roads: Actions, Send a squad to patrol it in
  the Roads tab.
- Give caravans guards of the army ([Squads and commanders](squads)).
- The view "risks" of the Roads tab shows the chance of a robbery of every caravan with guards and without, the
  share of its way on roads and its time on the way.
- Best of all, clear the camp: [Robbers](bandits).

Rulers of the world send patrols along roads, set guards to caravans and post guild rewards on the robbers of the
nearest camp too ([Rulers of the world](rulers)).
