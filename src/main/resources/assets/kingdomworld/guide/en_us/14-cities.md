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

### Limits

- A kingdom of players founds at most {cfg:roads.maxCities} cities besides its capital.
- A city stands at least {cfg:roads.minCityDistance} blocks from any other city or capital in its dimension,
  those of other kingdoms included.
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

### What the level changes

- **The radius of the wall and the square of the city**: {cfg:roads.wallRadius} blocks for a village, half as much
  again for a town ({cfg:roads.wallRadius*1.5}), twice as much for a large city ({cfg:roads.wallRadius*2}). The
  square of a city without a wall is the one a wall of its level would take: the law of damaged property holds in it.
- **The entry fee**: only a large city charges one ([Guards](guards)).
- **Fairs**: by default only a large city with a market holds them ([Festivals and fairs](festivals)).

### Where to see it

In the Roads tab, view "cities": the level and wall of every city, and on hover its citizens and buildings. On the
atlas the sign of a city or the crown of a capital has one to three golden marks: village, town, large city.

> Capitals of kingdoms of the world laid out in districts are built for their level from the start: a small
> kingdom is a village, a medium one a town, a large one a large city
> ([Capitals of the kingdoms of the world](world_capitals)).

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
2. Left click the two ends. An end within 48 blocks of a city or a capital sticks to it. A right click starts
  again.
3. The Class button chooses a path, a gravel road or a paved road (a right click goes back).
4. The server looks for a way over the land and shows its length, the blocks of bridges and embankments, the
  materials, about how long the work takes and whose land the way crosses.
5. **Build** turns the plan into a road, Reset drops the plan.

### How the server finds the way

- A road climbs or falls one block a step at most, so it goes around cliffs and climbs a mountain in serpentines.
- The way goes around lava, fire, cacti, magma and anything that may not be broken: unbreakable blocks, blocks of
  the mod, containers and any block holding data.
- Over water a bridge runs at the level of the water. A shallow ravine gets an embankment up to
  {cfg:roads.maxFill} blocks high, a deep one a bridge. A bridge or an embankment is {cfg:roads.maxBridge} blocks
  long at most.
- The land of other kingdoms costs the search more, and the way goes around it when that is cheap.
- The longest road is {cfg:roads.maxRouteLength} blocks. The search goes a little at a time so that the server
  does not lag: {cfg:roads.searchStepsPerTick} steps and up to {cfg:roads.searchChunksPerTick} new chunks a tick,
  {cfg:roads.maxSearchSteps} steps in all at most.

### How many roads

A kingdom of players keeps up to {cfg:roads.maxRoads} roads of its own, a kingdom of the world up to
{cfg:roads.npcMaxRoads} (the roads between the settlements of its plan). How a road is built and mended and what
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

Every city and the capital: its level, its wall and how much of it is built, on hover the citizens and buildings.
The Actions button of the chosen city: Put up a palisade, Put up a stone wall, Cut a gate where I stand
([Walls and gates](walls)). Without cities the tab reminds you to place a town hall ([Cities](cities)).

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
