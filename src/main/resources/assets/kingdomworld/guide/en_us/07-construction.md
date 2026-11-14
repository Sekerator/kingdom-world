# Construction
@icon kingdomworld:blueprint

## construction | How a kingdom builds
@icon minecraft:scaffolding
A kingdom builds by itself. The king picks a building in the catalog and lays it out with a blueprint, citizens with
the profession **Builder** clear the place and put the blocks up, and the materials come from the storehouses. A
finished building enters the register of the kingdom and starts to serve it with its role.

### Where to start

1. You need citizens. While the kingdom has no working house, any bed on its land is a home, and that is how the first
   citizens come (see [Arrival and growth](growth)).
2. Place a [work post](work_posts) {item:kingdomworld:work_post}, give it the profession Builder and hire a worker (see [Builders and materials](builders)).
3. Put materials in the storehouses: planks, logs, cobblestone, glass.
4. In the Construction tab choose a building and press Take blueprint (see [Blueprints and the catalog](blueprints)).
5. With the blueprint in hand, point at the place and right click: the construction is laid out.
6. The builders clear the place and put the building up by themselves; the tab shows how far they are.

### The Construction tab

Every member of the kingdom sees the **Construction** tab of the throne window, but only a holder of the right
Construction lays out and changes constructions (the king and the steward by default). The View button goes through four
views (left click forward, right click back):

- catalog: every building, its cost and its blueprint ([Blueprints and the catalog](blueprints));
- own: blueprints the kingdom saved itself ([Blueprints of your own](own_blueprints));
- constructions: everything being built now;
- buildings: finished buildings, their roles, repairs and demolitions ([Upgrades, repairs and demolitions](buildings_upkeep)).

> Do not mix the right Construction up with the right Building, which only lets a member place and break blocks on the
> land of the kingdom. The Construction tab needs the right Construction.

### Constructions

The top of the view says "Constructions: N". The limit of constructions a kingdom has at the same time:
{cfg:construction.maxConstructionsPerKingdom}; when the server sets one, the top says "Constructions: N of the limit". Without
a limit the builders and the materials decide how many go on. Upgrades, repairs and demolitions of finished buildings count as constructions too. Click a
construction to open its card: the place and how many builders are at work, "Built 45%: 120 of 260" (blocks), the state
with the place of a problem, and the list of what is missing. The buttons of the card:

- **Priority** (Low, Normal, High): builders and storehouses serve the most urgent construction first;
- **Pause** and Resume: a paused construction stands still, and the materials set aside for it are freed;
- **Ordering: on/off**: order what is missing from the workshops (see [Builders and materials](builders));
- **Cancel**: placed blocks stay, while the materials at the site and the blocks paid for but not placed go back to the
  storehouses.

| State | What to do |
|---|---|
| waits for a builder | hire a builder nearer |
| lacks materials | bring or order them |
| a container with items is in the way | empty it |
| blocks paid for: N, put in when the place is loaded | nothing: the server loads the place by itself (see below) |
| the blocks are being put in | nothing: the place is loaded now and the blocks go in |
| part of the place is no longer our land | get the land back |

If the kingdom loses the land under a construction (gives a chunk up or another kingdom takes it), nothing is broken or
placed there. The blocks already paid for stay with the construction and are placed once the land comes back, or go back
to the storehouses if it is cancelled. Other states are "paused", "the template is gone" and "the template has changed"
(the data pack with the template was removed or changed).

### Far from players

With nobody around, builders work by the pace formula: every block is paid in one go (from the materials at the site
first, then from the storehouses, all or nothing) and queued. The blocks are placed when the place is loaded, and nobody
has to go there: the server loads the places of far constructions and walls by itself, one at a time on the whole
server, for a minute or two. In that time the blocks paid for go in, the scaffolds come down, the finished building is
entered into the register and its beds, posts and storehouses go into the data at once, so newcomers move into a far
house without a player. A place is taken when everything, much or long ago was paid for; kingdoms of players and of the
world take turns. While the server is overloaded the loading waits.

The queue and the steps done are saved, so a server restart in the middle of a construction neither loses nor doubles
anything. What did not fit into full storehouses where nothing was loaded is kept with the constructions and dropped at
its place once that is loaded. A message tells when a construction is laid out and when it is finished.

## blueprints | Blueprints and the catalog
@icon kingdomworld:blueprint
### The catalog

The catalog of the Construction tab holds every building template of the server: 247 built-in ones (36 plain ones and 211
in the eight [architectural styles](styles)) and the buildings of data packs; the window gets up to 512 templates.
Buildings of the style of your kingdom come first, marked with a gold bar on the left, then those of no style, then the
other styles. The button beside the View button filters the catalog: "Styles: all", "Style: own" or one of the eight
styles. You may build in any style.

Click a building to see its card:

- its category: housing, household, production, trade, defense, decoration or magic;
- its role and level, for example "role: House, level 1" (see [The roles of buildings](building_roles));
- its size and how many blocks to place: "Size 9×7×11, 312 blocks to place";
- its cost as a list with icons; with the wood "of the place" the cost may change a little;
- the wood and stone buttons and the **Take blueprint** button.

### The cost

The cost comes from the blocks of the template. Doors, beds and tall plants cost one item for two blocks, a double slab
two, candles and sea pickles by their number. Liquids are not built (the card says how many are left out), air and fire
cost nothing. Containers are placed empty, and the texts of signs and the patterns of banners in the template are not
taken over. Flags of the kingdom cost nothing (see [Flags on buildings](building_flags)).

### Wood and stone

The wood button chooses the species: "of the place" (the wood that grows in the biome of the site), "as drawn" or any
species of the game and of mods. The stone button: "as drawn" or any stone that has stairs and a slab. The wooden and
stone blocks of the template become blocks of the chosen kind; a block the kind lacks stays as it was. Cost, outline,
payment and the integrity check all use the replaced blocks. The wood and stone show in the tooltip of the blueprint.

### The blueprint

**Take blueprint** gives you the item Blueprint {item:kingdomworld:blueprint}. The blueprints you had go, a dropped
blueprint vanishes, and it cannot be traded. Hold it: the building shows half transparent where the crosshair points (up
to 48 blocks away), its entrance towards you. The box of the building and the blocks in the way are outlined ("In the
way: ...; the builders will clear them"), and a forbidden place is red. A hint at the top of the screen names the keys and
what the server says, for example "The building can stand here".

- R turns the building and M mirrors it (both can be changed in the controls, in the KingdomWorld category).
- A right click lays the construction out: "Construction laid out: ...".

### Why it cannot be built

The server checks the place again and refuses with a reason:

- "The building must stand entirely on the land of your kingdom";
- "Another construction or building stands there";
- "A throne, bank or another block of the kingdoms is in the way";
- "The place is too far away to be looked at" (the place is not loaded);
- "The building does not fit between the bottom and the top of the world";
- "The kingdom has as many constructions as it may".

A template that cannot be built on this server stays in the catalog with the reason: larger than
{cfg:construction.templateMaxWidth}×{cfg:construction.templateMaxHeight}×{cfg:construction.templateMaxLength} blocks
(the mod's own buildings may be up to 48 on every side), it needs a mod that is not installed, it has unknown blocks or
blocks no item makes (a portal, a spawner, bedrock, a command block), it cannot be read or it has nothing to build.

## builders | Builders and materials
@icon minecraft:iron_pickaxe
Citizens with the profession **Builder** do the building. Place a [work post](work_posts) {item:kingdomworld:work_post}, choose the profession Builder in its window and hire people (up to {cfg:work.workPostMaxSeats} a post, see [The window of a post and hiring](post_window)).
A builder needs no workstation. It takes the constructions of its kingdom within {cfg:construction.builderRadius} blocks
of its post, the most urgent first; builders of several posts may work on one construction. A post of builders in a city
or a village also takes every construction of its city, however far inside its square: one post is enough for a village,
and several cities build at the same time, each with its own builders.

### Helpers from other cities

A construction no post reaches (a city without builders of its own, a place outside every city) is built by helpers: the
builders of the kingdom who have no construction of their own. While roads and walls wait too, they give the far
constructions half of their time, otherwise all of it; the most urgent and oldest construction comes first. Where a built
road leads from the city of a helper to that of the construction they build at full speed, without one slower (by a
quarter by default). They need not go there: they pay for the blocks, as builders far from players do, and the server puts
them in, loading the place. The details of a construction say "building: N of its own, M helping (75%)".

### Carts

When a builder of a village near players needs a material no storehouse in its reach holds but a storehouse of the kingdom
does, it is brought: ten seconds later it lies at the site, and meanwhile the builder places other blocks. The tab says
"The materials are being brought from storehouses of the kingdom".

### The order of work

1. **Clearing** from the top down. Blocks in the way are broken and their drops, as from an ordinary break (with a
   pickaxe from the storehouses if there is one), go to a storehouse. A container holding items is never broken: the
   construction waits and shows where it is, and the rest of the clearing goes on.
2. **Building** from the bottom up: full and standing blocks first, then leaning and partial ones (torches, doors,
   ladders, carpets, rails, redstone, plants).

Never broken are blocks no player can break (bedrock, an end portal frame, a barrier, command blocks), the blocks of the
tag kingdomworld:unclearable (a spawner, reinforced deepslate, budding amethyst, the dragon egg) and the blocks of the
mod. Blocks are placed the way a player places them, so storehouses, work posts, stalls and blocks of other mods in the
building register as if a player had placed them.

### Reach and scaffolding

A builder walks up to a place from which it reaches the block: up to {cfg:construction.builderReach} blocks away. It
gets to a roof, the top of a high wall and overhanging parts on **scaffolding**, as a player jumps and places a block
under its feet:

1. It picks a column on the site or within 3 blocks around it: where the fewest scaffolds are needed, near itself and not
   where walls still go up.
2. It climbs the column to the height from which it reaches and places everything it reaches from there (the blocks of
   the same layer first).
3. It comes down taking the scaffolding apart from the top and moves the column on to the next group of blocks.

Scaffolding is free: the block Builder's Scaffolding has no item, takes nothing from the storehouses and drops nothing.
It goes only into air, grass or a layer of snow, only on the kingdom's land and never on a road, a wall, a building of
others, the site of another construction, a chest or a post. A column is up to 16 blocks high. A player may break
scaffolding and the builder puts it up again; a builder does not fall off scaffolding, and a fall does it no harm.
Right-clicking scaffolding with an empty hand tells what it is.

No scaffolding is left behind: a construction is finished only once none is left, and after a restart of the server, a
cancel, a demolition and in unloaded chunks (once they load) it is taken down by itself. Scaffolding where the blueprint
wants a block gives way to the block. Builders work the same way on [earthworks](earthworks).

When scaffolding does not help either (the block is walled in, there is no room for a column), the builder places the
block from the nearest point it got to: at once if it stands within {cfg:construction.builderFarReach} blocks,
otherwise after {cfg:construction.builderGiveUpSeconds} s. A block out of reach never stops a construction.

### Speed and risk

A beginner needs {cfg:construction.builderSecondsPerBlock} s per block, experience makes it up to twice as fast, and mood
changes the pace by up to {cfg:economy.moodWorkEffect*100}%. Near players a builder placing a block high above its feet
sometimes falls (a chance of {cfg:construction.builderFallChance*100}% per block); far from players the risk of the
profession counts (see [Health, the infirmary and death](health)).

### Materials

- The materials lie in the storehouses. [Carriers](carrier) bring them to the site, or the builder fetches them itself.
- What a construction still needs is set aside in the storehouses (for the most urgent constructions first), and
  workshops, the market and hungry citizens leave it alone. The reservation ends with a pause, a cancel or the end.
- What is missing the card of the construction shows with icons: "... missing, gathered by: ...", "the workshops can make
  it", "ordered from the workshops" or "nobody in the kingdom makes it".
- The switch **Ordering: on** gives the workshops one-off orders for what they can make: planks, stairs, glass (see
  [The king's orders](orders), [Workshops](workshops)).

### The window of a builder's post

The window shows the current construction and how far it is, what is being placed, how many blocks were placed today
and why work stands still: "No construction within reach", "Not enough materials" or "A container with items is in
the way".

> Builders also build [roads](roads) and the [walls](walls) of cities. Keep a storehouse and a builder's post near the
> site and planks, cobblestone and glass in stock: then large buildings rise without pauses.

## building_roles | The roles of buildings
@icon minecraft:bell
A finished construction enters the **register of buildings** of the kingdom: template, place, turn, kind, category and
time. Many templates have a role, which gives the kingdom homes, storehouses, faster work and more. Built-in buildings
get the role of their kind; the drill yard, the palaces and decorations have none.

### When a role works

- the building stands on the land of its kingdom;
- it is not being demolished;
- at least {cfg:construction.roleIntegrityPercent}% of its blocks stand (its integrity);
- what the role needs is inside: a bed for a house, a container for a warehouse, a barracks block
  {item:kingdomworld:barracks} for a barracks, a magic focus {item:kingdomworld:magic_focus} for a mage tower.

Otherwise the role rests and the tab says why: "rests: only 60% of the building stands, it needs 70%" and the like. The
server checks integrity a little at a time in loaded places; a building not looked at yet counts as whole.

### What roles give

| Role | Level 1 |
|---|---|
| House | homes, mood +5 |
| Warehouse | up to 8 containers |
| Workshop | workshops 25% faster |
| Market | +2 merchants, +2 lots |
| Watchtower | sight +16 blocks |
| Wall with a gate | fortification 100 |
| Well | mood +4 within 24 blocks |
| Barracks | +4 soldiers train at once |
| Mage tower | mages 25% faster |
| Tavern | 12 citizens an evening |

- **House.** Its beds are homes; a homeless citizen gets a bed in a house first, and residents get comfort. A free bed
  in a house brings newcomers a quarter sooner. Mind this: once the first house of a kingdom works, new beds outside
  buildings no longer become homes (whoever already lives in one stays). Beds of a tavern are never homes.
- **Warehouse.** Chests, barrels, crates and shulker boxes inside (the tag kingdomworld:warehouse_containers and blocks
  whose names end in chest, barrel, crate, shulker_box) count as storehouses of the kingdom while the place is loaded.
  [Storehouse](storehouses) blocks count always.
- **Workshop.** The posts of smelters, cooks, crafters and smiths inside work faster (see [Workshops](workshops)).
- **Market.** Merchant posts inside take more merchants, and players keep more lots at the kingdom's stalls (see [The market](market)).
- **Watchtower.** Guards and garrisons within 64 blocks see farther. A working tower or wall sees
  {cfg:army.postSightRadius} blocks around it in the [fog of war](fog_of_war), and a tower is a place to buy confiscated goods back (see [Fines and punishment](fines)).
- **Wall with a gate.** The strength of fortifications adds up and shows in the buildings view.
- **Well.** The mood of citizens who live nearby (see [Mood](mood)).
- **Barracks** and **mage tower**: see [The barracks](barracks), [The mage tower and mages](mage_tower).
- **Tavern.** An innkeeper at a post inside feeds the citizens and serves them drink in the evening; the beds of a
  tavern are rooms for guests (see [The tavern](tavern)).

### Levels

A template has a level, and a template of the next level makes an [upgrade](buildings_upkeep) possible. Every level
beyond the first adds by default: +5 comfort to a house, +8 containers to a warehouse, +15% to a workshop and a mage
tower, +1 merchant and +2 lots to a market, +8 blocks of sight to a tower, +50 to a wall, +2 mood and +8 blocks to a
well, +4 soldiers to a barracks, +8 guests to a tavern. The built-in second levels are the Cosy small house, the Stone
storehouse and the Tavern with rooms.

> Data packs add roles of their own with files data/<namespace>/kingdomworld_roles/<role>.json, and mods through the
> API. Such a role is shown and counted, and other mods read what it does.

## buildings_upkeep | Upgrades, repairs and demolitions
@icon minecraft:anvil
The **buildings** view of the Construction tab shows the register: the building, its place and integrity ("intact 92%"
or "not checked yet"), and on top "Buildings: N, fortification: N". Click a building to open its card: the role and
whether it works, the level and the next one, the integrity and the number of damaged blocks, the wood and stone, and
what the role gives. Below are the buttons **Upgrade**, **Repair**, **Demolish** and "Repairs itself: on/off". Each work
first shows what it takes or what goes back to the storehouses and waits for Confirm. While builders already work on a
building, no second work can be ordered for it.

### Upgrade

The button is there when the template has a next level. Builders change only the blocks that differ at the new level:
the same ones are not touched, and those no longer needed are taken out with their usual drops to a storehouse. Only the
difference is paid. The building stays in the register with its new level and the new numbers of its role. Built-in
examples: the small house becomes the cosy small house with a stone plinth and a second bed, the storehouse becomes the
stone storehouse with chests.

### Repair

The integrity check remembers the missing and changed blocks, and a repair puts them back by the template. Builders
bring and pay the materials as for a construction; far from players a repair goes by the pace formula. If the building
was not looked at yet, come closer so that the server sees it.

- "Repairs itself: on": the building orders its own repair when its integrity falls below
  {cfg:construction.autoRepairBelowPercent}%.
- During a war builders mend the damage once no enemy has been near for {cfg:siege.repairQuietMinutes} minutes (0 means
  they wait for the peace, see [Sieges and the ram](sieges)).

### Demolition

Builders take the building down from the top, leaning blocks first. Only what belongs to the building by its template is
taken. Its materials go to the storehouses less {cfg:construction.demolitionLossPercent}% (counted over every kind
together), what containers hold goes there whole, and what cannot be broken stays. A finished demolition removes the
building from the register; one cancelled half way leaves it damaged.

> The walls and gates of cities are kept in the Roads tab, where a wall put up again only adds what is missing (see
> [Walls and gates](walls)).

## building_flags | Flags on buildings
@icon minecraft:red_banner
The buildings of a kingdom fly its flags. A flag on a building always shows the flag the kingdom flies now: once the king saves a new one (see [The flag](flag)), every building shows it at once, and no block changes.

### Where they hang

- The red banners of the built-in buildings other than houses became flags of the kingdom: in castles, gates of walls, camps, guilds, taverns and workshops. Banners of other colors stay ornaments, and the red banners of houses stay the cloth of the home.
- A building with few flags of its own gets the ones it lacks: a house one, the others one, two or three by the area of their footprint. A flag hangs on the wall by the door at the height of its top, and with two wanted, one on each side. Where the door has no room, a flag stands on the roof, on the tops of towers, and where there is no roof either, at the front corners of the site. A building of defense gets one more on its roof.
- Two flags stand beside the throne, left and right, where the place is free. When the throne is moved they move with it.
- A city wall flies flags on its outer side beside every gate and every 32 blocks along it.
- Flags go only into empty places: they replace nothing and dig nothing out.

### What happens to them

- A flag drops nothing, whoever breaks it: a player, an explosion, a machine or a builder. A flag has no item.
- A stranger cannot break a flag in peace, as any block of the kingdom.
- A member of the kingdom may take down any flag. The place stays empty until the template or the owner of the building changes.
- A flag knocked down by an explosion, in a fight or left without support comes back by itself half a minute later, and in a war once the city is calm.
- A conquered city flies the flag of the winner, and the flags of a dissolved kingdom are taken down.
- Flags never stand in the way of the builders: in the way of a construction or an upgrade they are taken down without a drop, and once the work is done they go up at their new places.
- Flags do not count in the integrity of a building, are not repaired for materials and cost nothing in the price of a template.
- In a blueprint of your own, flags of the kingdom stay flags and red banners stay banners (see [Blueprints of your own](own_blueprints)).

### Older worlds

Flags appear on buildings when players come near: no chunk is ever loaded for them. Plain banners without patterns and without a name at the places of flags of buildings other than houses become flags of the kingdom, and nothing is given back for them. Banners with patterns or a name stay.

> The key buildingFlags in the section [construction] of the server config (true by default) switches off added flags and the flags of walls and thrones: those that hang stay, new ones are not put up or put back. The flags of templates stay part of the templates.

## own_blueprints | Blueprints of your own
@icon kingdomworld:architect_ruler
Any building made by hand can be saved as a blueprint of the kingdom, and the builders will put up copies of it. For
that you need the **Architect's ruler** {item:kingdomworld:architect_ruler} and the right Construction (the one the
Construction tab needs); there is no right of its own.

{recipe:kingdomworld:architect_ruler}

Crafting: paper in the top right corner and two sticks running down diagonally from it.

### Saving a building

1. Right click a block in one corner of the building with the ruler: "First corner: X Y Z".
2. Right click a block in the opposite corner: "Second corner: ...; the box is 9 x 7 x 11". The box between the corners
   shows in the world, and hints show above the hotbar.
3. A third click on a block starts again from the first corner.
4. Stand on the side where the entrance should be and use the ruler while sneaking (Shift + right click).
5. In the window "Save as a blueprint" type the name (1-32 characters), choose the category and the role (or none) and
   press Save.

The server reads the blocks between the corners itself: the blocks only, without what chests hold and without the texts
of signs. The entrance of the building faces the player who saved it, and when the blueprint is laid out the building
turns that entrance towards you.

### Why it is not saved

- larger than {cfg:construction.templateMaxWidth} x {cfg:construction.templateMaxHeight} x {cfg:construction.templateMaxLength} blocks;
- "Come closer to the building to save it": you are more than 64 blocks (plus half its size) from the middle of the box;
- the place is not loaded or lies outside the height of the world;
- "The box holds blocks that cannot be built" (a portal, a spawner, bedrock and the like);
- "There is nothing to build in the box";
- "The building holds too many different blocks": more than 4096 different block states;
- "A house needs at least one bed inside", "A warehouse needs at least one container inside";
- the kingdom already keeps {cfg:construction.maxBlueprintsPerKingdom} blueprints: delete one first.

### The own view

Saved blueprints are kept in the kingdom data of the world and show in the own view of the Construction tab: "Own
blueprints: N of {cfg:construction.maxBlueprintsPerKingdom}". Click a blueprint to take it and build it (the wood and
stone are chosen as for a template), to **Rename** it or to **Delete** it. A blueprint cannot be deleted while a
construction is built from it. Buildings built from a deleted blueprint stay and keep their role.

> Mark the corners so that nothing extra gets into the box: the ground under the floor and the trees next door become
> part of the blueprint too.

## styles | Architectural styles
@icon minecraft:stone_bricks
An architectural style sets not only colors but shapes: the outline of roofs and towers, windows, arches, ornament,
materials and furniture. There are eight styles.

| Style | What it looks like |
|---|---|
| Classic | stone, half-timbering, round towers |
| Terem | log frames, tented roofs, onion domes |
| Nordic | dark staves, dragon heads |
| Southern | whitewashed walls, red tiles |
| Desert | sandstone, domes, minarets |
| Eastern | curved roofs, pagodas |
| Gothic | dark slate, needle spires |
| Mountain | heavy stone, forges |

### The style of your kingdom

The style is chosen at the founding (see [Founding a kingdom](founding)): the window shows the eight styles with a
picture of the large castle, the name and a caption on hover. The style the climate and the mountains around the throne
suggest is chosen at first and marked "Suits the land"; the arrows choose another. Styles of players need not be unique:
several kingdoms may share one. A kingdom founded before styles existed takes the one its land suggests by itself.

The Overview tab of the throne window shows the style ("Style: Terem"). Only the king of a kingdom of players may change
it:

1. "Change style" opens the choice on the current style.
2. Choose another with the arrows and press "Build in this style" (the button shows the fee).
3. Confirm: the treasury pays {cfg:kingdom.styleChangeCost} copper coins (usually {cfg:kingdom.styleChangeCost/100}
   gold), and the ledger of the treasury shows it as "Admin".

The same style, or a treasury that cannot pay, is refused. New buildings follow the new style; those standing are not
rebuilt. A kingdom of the world never changes its style.

### Styles in the catalog

The catalog shows the buildings of the kingdom's style first, with a gold bar, but you may build in any style. Every
style has 24 templates: three castles (small 31x31, medium 41x41, large 47x47), a guild house and a mage tower in four
sizes for the ranks B, A, S and SS, six houses from a hut to a manor, a smithy, a tavern, a city gate and four buildings
of an army camp. The classic style also has six more houses and the shared buildings: a treasury with a mint and the
place of a bank, a court with a guardroom, an infirmary, a builders' yard, a carters' yard, the barracks of the royal
guard, stables, a garden, a barbican and decorations without a role.

The look of a building (roof, plaster, cloth, stone, trim, shutters, chimney) is chosen from 3-4 variants of its style by
the place where it is laid out and kept with the construction, so houses of one template look different.

The walls of a kingdom of players are built of the stone of its style, a palisade of the logs of its wood, while the
storehouses hold enough of it, otherwise of the most plentiful stone (see [Walls and gates](walls)).

### Kingdoms of the world

The style of a [kingdom of the world](world_kingdoms) comes from the climate of its land: a cold land puts the nordic
style first, a dry one the desert style, a lush one the eastern style, a temperate one the classic style, and mountains
or high land around the throne put the mountain style first. A kingdom takes the first style of its list that no other
kingdom of the world has yet, so styles repeat only beyond eight kingdoms. A kingdom of the world never changes its
style.

A styled capital, with a castle around the throne and the guild house, mage tower, treasury, court, infirmary and tavern
on its square, belongs to kingdoms of the older plan of capitals. The capitals of the states of a new world are built in
districts, and their settlements rise in the style of their kingdom (see [States](states), [Living settlements](settlements)).

## earthworks | Earthworks
@icon kingdomworld:survey_stakes
Earthworks level a plot, cut a hill down or fill a hollow up. The same builders do them, and the work itself becomes a
construction in the Construction tab.

### Survey stakes

A plot is marked with the item **Survey Stakes** {item:kingdomworld:survey_stakes}. They are crafted from four sticks and a string: sticks at both ends of
the top row, and stick, string, stick in the second row. You need the right Construction.

{recipe:kingdomworld:survey_stakes}

1. Right click a block in one corner of the plot with the stakes, then a block in the other corner. A plot is at most {cfg:construction.earthworkMaxSize}
   by {cfg:construction.earthworkMaxSize} blocks, and its outline shows over it.
2. After the second corner the window **Earthworks** opens. Using the stakes in the air opens it again, and using them in
   the air while sneaking (Shift + right click) pulls them out.

### The window

- **Work**: "level to the height" (cut everything above and fill everything below), "cut down above the height" or
  "fill up to the height".
- **Height Y**: at first the height of the first corner.
- **Smooth the edges**: the edges of the plot slope down to the land around one to one, without cliffs.

The server counts and shows how many blocks to dig and to fill, whether there is material enough for the fill, how many
places are left as they are and how much is left over for the storehouses or the spoil heap. The plane of the height and
the places left as they are are drawn over the plot. **Lay out** makes a construction "Earthworks" (levelling, cutting
down or filling up) with everything a construction has: priority, pause, cancel, progress, materials and carriers, work
far from players (see [How a kingdom builds](construction)).

### How the builders work

They dig only what grew there: earth, grass, gravel, sand, stone and ore (as a [miner](miner) does), plants and trees;
first from the top down, then they fill from the bottom up. The fill takes what the same work dug first (earth, then
stone and cobblestone, then gravel and sand), then earth and cobblestone from the storehouses; its top layer is earth
while there is earth enough. What is left over goes to the storehouses while they have room and the rest to the spoil
heap, so full storehouses never stop the work; ore, wood and everything of worth always go to the storehouses. A fill
fills water, and lava next to a dig is closed off with cobblestone from the storehouses (without cobblestone the
builders wait).

Never touched are blocks that hold something, blocks of the mod, buildings of the register, roads, walls, posts,
anything made by hand and anything off the land of the kingdom: those places are left as they are, and the work shows
them. Whatever is placed after the work is laid out stays too.

### Limits and refusals

- a column is dug down or filled up by at most {cfg:construction.earthworkMaxHeight} blocks from its ground: otherwise "Too deep";
- one plot takes at most 160,000 blocks of work: otherwise "Too much work at once";
- the height of the work stays within the world;
- "Nothing to do here" when the land lies at that height already or may not be touched.

> Farmland and crops count as the land and will be dug up: keep your garden out of the plot.
