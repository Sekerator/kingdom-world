# Magic
@icon kingdomworld:staff

## energy | The energy of a kingdom
@icon kingdomworld:crystal_accumulator
Every kingdom has its own store of **energy**: a whole number that never goes below zero. Energy pays for the research of the mage tower, the spells of the kingdom, magic gates, the mages who defend the land, crystal generators and the wells for the magic of other mods.

### Where energy comes from

Energy comes only from **monster crystals** {item:kingdomworld:monster_crystal}. They drop from hostile monsters killed by a player or an adventurer (see [Monster crystals](crystals)). A crystal holds the energy of its rank, shown in its tooltip. A mage dissolves the crystal at a magic focus, and all of its energy enters the store of the kingdom (see [The mage tower and mages](mage_tower)). Neither money nor Forge Energy ever turns into energy of a kingdom; only an operator of the server can add it out of nothing.

| Rank of the crystal | Energy |
|---|---|
| F | {cfg:guild.crystalEnergy#1} |
| E | {cfg:guild.crystalEnergy#2} |
| D | {cfg:guild.crystalEnergy#3} |
| C | {cfg:guild.crystalEnergy#4} |
| B | {cfg:guild.crystalEnergy#5} |
| A | {cfg:guild.crystalEnergy#6} |
| S | {cfg:guild.crystalEnergy#7} |
| SS | {cfg:guild.crystalEnergy#8} |
| SSS | {cfg:guild.crystalEnergy#9} |

### The store and accumulators

Without accumulators a kingdom holds up to {cfg:magic.baseCapacity} energy. Each **crystal accumulator** {item:kingdomworld:crystal_accumulator} on the land of the kingdom adds {cfg:magic.accumulatorCapacity} more. Anybody who may build on that land can place one: it belongs at once to the kingdom whose land it is. When an accumulator is taken away, the energy already stored stays, but no new energy comes in until the store holds less than its capacity. While the store is full, mages stop dissolving crystals.

{recipe:kingdomworld:crystal_accumulator}

### The crystal generator

A **crystal generator** {item:kingdomworld:crystal_generator} gives the energy of the kingdom to the machines next to it as Forge Energy (FE): {cfg:magic.feRate} FE for a unit of energy, at most {cfg:magic.feMaxPerTick} FE a tick. Units are taken from the store only when a machine asks for energy. FE never turns back into energy of a kingdom. The generator works with any mod that uses FE, such as Create Crafts & Additions.

{recipe:kingdomworld:crystal_generator}

A member of the kingdom with the Mage tower right places a generator on the land of the kingdom; a player without the right is refused. A generator placed by a machine or a command stands idle until a member with the right places it. When the land goes to another kingdom, the generator stops until a member of the new kingdom with the right places it again.

### The journal of energy

What came in and went out shows in the Magic tab (see [The Magic tab](magic_tab)) by kind: Crystals (the work of mages), Operators, Research, Spells of the kingdom, Gates, Defense of the land (fireballs and healing of mages), Machines (generators) and Mana and source (see [The magic of other mods](magic_wells)).

> The store of the kingdom and the energy of the crystals in your inventory are different things: the spells of a staff spend the crystals you carry, not the store (see [Staffs](staffs)).

## mage_tower | The mage tower and mages
@icon kingdomworld:magic_focus
A **mage** is a profession of a citizen, like a lumberjack or a miner. Mages turn monster crystals into energy of the kingdom, work on the research of the tower and defend the land near players. The mod calls both the research of a kingdom (the level of the tower) and a building with that role the "mage tower".

### How to get a mage

1. Place a [work post](work_posts) {item:kingdomworld:work_post} on the land of the kingdom and choose the Mage profession on it.
2. Put a **magic focus** {item:kingdomworld:magic_focus} into the zone of the post (see [The magic focus](focus)). Without a focus in the zone the post says "No workstation in the zone".
3. Hire a citizen in the window of the post (see [The window of a post and hiring](post_window)) or give a citizen the Mage profession in the Citizens tab or in the [Workers window](workers_window).
4. Put a chest or a barrel right next to the post: it is the container of the post. [Carriers](carrier) keep up to {cfg:magic.crystalsAtPost} crystals from the storehouses in it. Without a container the mage takes crystals straight from the storehouses of the kingdom.

### How a mage works

The mage goes to the nearest focus in the zone and dissolves crystals one by one. A mage of usual speed needs {cfg:magic.dissolveTicks/20} s for a crystal. With experience it works up to twice as fast; a good mood speeds it up, a bad one slows it down. The crystal leaves the container and its energy enters the store in the same moment. If there is no room in the store, the crystal stays where it is and the post says "The storehouses are full": for a mage this means the store of energy is full. Without crystals the post says "Not enough materials". Far from players mages work by the same rules, by formula.

Only healthy mages at a post with a focus on the kingdom's own land work. All of them together work on the current research, and their speeds add up (see [Research and the level of the tower](magic_research)).

### Defending the land

Near players a mage throws a fireball every {cfg:magic.mageCastTicks/20} s at the nearest monster it sees on the land of the kingdom within 12 blocks, for {cfg:magic.mageFireballCost} energy of the store. With no monster around, it heals a citizen or a subject of its kingdom with less than 70% of their health, for {cfg:magic.mageHealCost} energy. The fireball deals {cfg:magic.fireballDamage} damage and the healing gives {cfg:magic.healAmount} health; both grow with the strength of the mages of the guild (see [The guild of mages and its rank](magic_guild)). The fireball of a mage hurts monsters only and never sets blocks on fire. Without energy a mage casts nothing.

### The Mage tower building

The built-in blueprint Mage tower and any building with the Mage tower role (see [The roles of buildings](building_roles)) work only with a magic focus inside and at least {cfg:construction.roleIntegrityPercent}% of the building standing. Mages at posts inside a working tower dissolve crystals and research faster: by default 25% faster in a building of the first level and 15% more for each further level. Every working tower also raises the rank of the guild of mages.

## magic_research | Research and the level of the tower
@icon minecraft:enchanted_book
Research opens the spells of the kingdom and raises the **level of the tower**: the number of finished researches, from 0 to 8. A focus needs the level of the tower to teach the strong spells of players (see [Spells](spells)).

### How research goes

A member of the kingdom with the Mage tower right (by default the king and the court mage) starts a research with the **Start** button in the Research view of the Magic tab. Its price in energy is paid at once. Then the research goes on with the work of mages: the time in the table is the work of one mage of usual speed, and all working mages of the kingdom add their speeds. Only one research goes on at a time. When it is done, members with the right get the message "The mage tower finished a research".

| Research | Energy | Minutes | Needs |
|---|---|---|---|
| Protective Ward | {cfg:magic.researchCost#1} | {cfg:magic.researchMinutes#1} | - |
| Healing | {cfg:magic.researchCost#2} | {cfg:magic.researchMinutes#2} | - |
| Fertility | {cfg:magic.researchCost#3} | {cfg:magic.researchMinutes#3} | - |
| Fair Wind | {cfg:magic.researchCost#4} | {cfg:magic.researchMinutes#4} | - |
| Gates | {cfg:magic.researchCost#5} | {cfg:magic.researchMinutes#5} | Ward |
| Arcana I | {cfg:magic.researchCost#6} | {cfg:magic.researchMinutes#6} | - |
| Arcana II | {cfg:magic.researchCost#7} | {cfg:magic.researchMinutes#7} | Arcana I |
| Arcana III | {cfg:magic.researchCost#8} | {cfg:magic.researchMinutes#8} | Arcana II |

The first five open the [spells of the kingdom](kingdom_spells) of the same names. Arcana opens nothing by itself and only raises the level of the tower.

The list shows the state of each research: "done", "under way, N%", its cost, or "needs ..." when another research must come first. Without enough energy a research does not start: let the mages dissolve crystals first.

> Two mages of usual speed finish a research twice as fast as the table says. An experienced mage, or one in a working mage tower building, counts for more.

## magic_guild | The guild of mages and its rank
@icon minecraft:experience_bottle
The mages of every kingdom have a **guild of mages** with a rank from F to SSS. The rank shows in the Research view of the Magic tab and in the window of a magic focus. It decides up to which level the focus here teaches the spells of players and how strong the mages of the kingdom are.

### How the rank grows

The guild of a kingdom of players works out its rank once a minute from a score: {cfg:magic.researchWorth} for every finished research, {cfg:magic.mageWorth} for every mage at work and {cfg:magic.towerWorth} for every working mage tower building. The rank is worked out anew each time, so it can fall as well: for example when mages are wounded or the tower is broken. The guild of a kingdom of the world gets its rank at the founding and grows by its own rules.

| Rank | Score | Teaches up to level |
|---|---|---|
| F | {cfg:magic.guildScore#1} | {cfg:magic.lessonLevelByRank#1} |
| E | {cfg:magic.guildScore#2} | {cfg:magic.lessonLevelByRank#2} |
| D | {cfg:magic.guildScore#3} | {cfg:magic.lessonLevelByRank#3} |
| C | {cfg:magic.guildScore#4} | {cfg:magic.lessonLevelByRank#4} |
| B | {cfg:magic.guildScore#5} | {cfg:magic.lessonLevelByRank#5} |
| A | {cfg:magic.guildScore#6} | {cfg:magic.lessonLevelByRank#6} |
| S | {cfg:magic.guildScore#7} | {cfg:magic.lessonLevelByRank#7} |
| SS | {cfg:magic.guildScore#8} | {cfg:magic.lessonLevelByRank#8} |
| SSS | {cfg:magic.guildScore#9} | {cfg:magic.lessonLevelByRank#9} |

### Levels of spells

A spell of players is learned up to level IV. The lesson of the next level is taken with the same Learn button at a focus, if the guild here teaches up to that level. Every level is stronger (the damage of the fireball, healing, the reach of the blink, the time of light, detection and the shield, the radius of detection) and dearer:

| Level | Strength | Price of the lesson |
|---|---|---|
| I | x{cfg:magic.levelPower#1} | x{cfg:magic.levelPrice#1} |
| II | x{cfg:magic.levelPower#2} | x{cfg:magic.levelPrice#2} |
| III | x{cfg:magic.levelPower#3} | x{cfg:magic.levelPrice#3} |
| IV | x{cfg:magic.levelPower#4} | x{cfg:magic.levelPrice#4} |

The price of a lesson grows from the price of the spell (see [Spells](spells)). Someone who is not a subject of the kingdom pays {cfg:magic.foreignerPrice} times as much. The guild teaches nobody wanted in the kingdom and no subject of a kingdom at war with it.

### The strength of mages

| Rank | Health of a mage | Spell power |
|---|---|---|
| F | {cfg:magic.mageHealth#1} | x{cfg:magic.mageSpellPower#1} |
| E | {cfg:magic.mageHealth#2} | x{cfg:magic.mageSpellPower#2} |
| D | {cfg:magic.mageHealth#3} | x{cfg:magic.mageSpellPower#3} |
| C | {cfg:magic.mageHealth#4} | x{cfg:magic.mageSpellPower#4} |
| B | {cfg:magic.mageHealth#5} | x{cfg:magic.mageSpellPower#5} |
| A | {cfg:magic.mageHealth#6} | x{cfg:magic.mageSpellPower#6} |
| S | {cfg:magic.mageHealth#7} | x{cfg:magic.mageSpellPower#7} |
| SS | {cfg:magic.mageHealth#8} | x{cfg:magic.mageSpellPower#8} |
| SSS | {cfg:magic.mageHealth#9} | x{cfg:magic.mageSpellPower#9} |

Spell power multiplies the damage of the fireballs and the healing of mage citizens. A plain citizen has 20 health.

### Guilds of the kingdoms of the world

The guild of mages of a [kingdom of the world](world_kingdoms) has researches, mages and a store of energy by its rank from its very founding (nothing below rank B):

| Rank | Researches | Mages | Energy |
|---|---|---|---|
| B | {cfg:magic.worldResearch#5} | {cfg:magic.worldMages#5} | {cfg:magic.worldEnergy#5} |
| A | {cfg:magic.worldResearch#6} | {cfg:magic.worldMages#6} | {cfg:magic.worldEnergy#6} |
| S | {cfg:magic.worldResearch#7} | {cfg:magic.worldMages#7} | {cfg:magic.worldEnergy#7} |
| SS | {cfg:magic.worldResearch#8} | {cfg:magic.worldMages#8} | {cfg:magic.worldEnergy#8} |
| SSS | {cfg:magic.worldResearch#9} | {cfg:magic.worldMages#9} | {cfg:magic.worldEnergy#9} |

So the strong spells of high levels are easiest to learn at the foci of large ancient kingdoms.

## magic_tab | The Magic tab
@icon kingdomworld:throne
Every member of the kingdom sees the **Magic** tab of the [throne](throne) window, but only a member with the Mage tower right changes anything there (Magic for short in the table of rights; by default the king and the court mage, see [The rights of positions](rights)). The others see a hint at the bottom that only members with the right change it.

The View button goes through the views of the tab: a left click forward, a right click back.

- **Energy**: the store and its capacity, the accumulators, the crystals in the storehouses and the energy they hold, the foci and the level of the tower (see [The energy of a kingdom](energy)).
- **Journal**: energy that came in and went out, by kind; the period button switches between Today, Yesterday and Week.
- **Mages**: every mage with the state of its post, its speed in percent and its experience. With no mages the tab tells how to get them.
- **Research**: every research with its price, progress or requirement, and Start buttons; the tooltip of a button shows the price and the work of one mage. On top, the level of the tower and the rank of the guild of mages (see [Research and the level of the tower](magic_research)).
- **Spells**: the spells of the kingdom with a description, the price and the state, and Switch on and Switch off buttons. On top, the counter "Ward: N of M" and the **Ward this chunk** button (Lift the ward here): it puts the chunk you stand in under the protective ward or takes it out, if it is land of the kingdom (see [Spells of the kingdom](kingdom_spells)).
- **Gates**: every magic gate of the kingdom with its number and coordinates (see [Magic gates](magic_gate)).
- **Generators**: crystal generators with their numbers and coordinates; on top, the rate in FE for a unit of energy and the limit a tick.

> When the tab says "No mages yet", give a citizen the profession of mage and a post with a magic focus in its zone (see [The mage tower and mages](mage_tower)).

## focus | The magic focus
@icon kingdomworld:magic_focus
A **magic focus** {item:kingdomworld:magic_focus} is the block where mages dissolve crystals and players learn spells and fill their staffs.

{recipe:kingdomworld:magic_focus}

A focus works only on the land of a kingdom. Placed there, it belongs at once to that kingdom; no special right is needed. When the land goes to another kingdom, the focus goes with it. On land of nobody its window does not open and the game says "A magic focus works only on the land of a kingdom".

### The window of a focus

Use the focus to open the window "Magic Focus: <kingdom>". On top: the level of the tower, the rank of the guild of mages, your rank in the adventurers' guild, the energy of the crystals in your inventory and the money on your account in the currency of this kingdom. A guest (not a subject) sees "As a guest" at the start of that line. Below it, the current research of the tower. The buttons on top switch between two views.

**Spells.** The six spells of players: the price of the lesson, the energy of the crystals one cast costs, and the requirements (rank in the adventurers' guild, level of the tower, the level taught here). The **Learn** button takes the lesson of the next level; when a lesson is out of reach, the tooltip of the button says why. The lesson is paid from your account (see [Coins and currency](coins)) into the treasury of the focus's kingdom.

**Staff.** Hold a [staff](staffs) {item:kingdomworld:staff} in your main hand and the window shows its slots. A click on a slot puts the next learned spell into it; after the last one the slot is empty again, and a right click goes back. Without a staff in hand the window reminds you to take one.

### Whom the focus teaches

- A subject of the kingdom, at the usual price.
- Any other player as a guest, {cfg:magic.foreignerPrice} times dearer.
- Nobody wanted in this kingdom and no subject of a kingdom at war with it.

A lesson needs a rank in the adventurers' guild (see [Ranks](ranks)) and the level of the tower of this kingdom. When the rank of the guild of mages here is low, the higher levels are not taught: look for a focus of a kingdom with a stronger guild (see [The guild of mages and its rank](magic_guild)).

### The focus and mages

For a mage to work, a focus must stand in the zone of its post (see [The mage tower and mages](mage_tower)). A focus inside a building with the Mage tower role makes that building work. With Iron's Spells 'n Spellbooks or Ars Nouveau on the server, the focus also refills the mana of subjects nearby (see [The magic of other mods](magic_wells)).

## staffs | Staffs
@icon kingdomworld:staff
A **staff** {item:kingdomworld:staff} casts the spells of players you learned at a [magic focus](focus).

{recipe:kingdomworld:staff}

### Slots

The server sets how many slots a staff has: {cfg:magic.staffSlots}. Slots are filled only at a focus, in the Staff view of its window, with the staff in your main hand. A slot takes only a spell you learned. The tooltip of the staff shows all slots and marks the chosen one.

The spells you learned stay with you, and the slots stay with the staff. A staff holding spells of someone else does not work until you learn them yourself: the game answers "You have not learned this spell".

### How to cast

- Use the staff: cast the chosen spell.
- Sneak and use the staff: choose the next slot that holds a spell. The line above the hotbar shows "Staff: <spell> (N of M)".

Each cast costs the energy of the **monster crystals you carry in your inventory**, not the store of the kingdom. Crystals already started go first, then the weakest, so that strong crystals stay whole. A crystal that gave part of its energy stays in your inventory with what is left ("Energy left: N of M") and stacks only with crystals just like it. Either your crystals hold the whole cost and it is taken, or nothing is taken. Energy is taken only when the spell works: a blink into a wall costs nothing.

After a cast the spell recharges (times in [Spells](spells)). When something is wrong, the line above the hotbar says so: "Recharging: ...", "Your crystals hold less than ..." or "The staff is empty: put spells in at a focus".

### Whom spells hurt

In peacetime the spells of players hurt no other player and no citizen, neither of your own kingdom nor of another. A player without a kingdom, or one away from the keyboard or offline, is never hurt. The fireball never sets blocks on fire. Exemptions of the rules of access, such as a [war](war), let you strike the players and citizens of an enemy.

> Carry a few weak crystals: cheap spells use them first, and your strong crystals stay whole for the mages of your kingdom.

## spells | Spells
@icon minecraft:blaze_powder
The mod has two kinds of spells. **Spells of players** are learned at a [focus](focus) and cast with a [staff](staffs) for the energy of your own crystals. **Spells of the kingdom** are switched on by the king in the Magic tab for the energy of the store (see [Spells of the kingdom](kingdom_spells)).

### Cost and recharge

| Spell | Energy | Recharge, s |
|---|---|---|
| Fireball | {cfg:magic.spellCost#1} | {cfg:magic.spellCooldownTicks#1/20} |
| Healing | {cfg:magic.spellCost#2} | {cfg:magic.spellCooldownTicks#2/20} |
| Light | {cfg:magic.spellCost#3} | {cfg:magic.spellCooldownTicks#3/20} |
| Detect Monsters | {cfg:magic.spellCost#4} | {cfg:magic.spellCooldownTicks#4/20} |
| Shield | {cfg:magic.spellCost#5} | {cfg:magic.spellCooldownTicks#5/20} |
| Blink | {cfg:magic.spellCost#6} | {cfg:magic.spellCooldownTicks#6/20} |

### What a lesson needs

| Spell | Rank | Tower | Lesson |
|---|---|---|---|
| Fireball | F | {cfg:magic.spellTowerLevel#1} | {cfg:magic.lessonPrice#1} |
| Healing | F | {cfg:magic.spellTowerLevel#2} | {cfg:magic.lessonPrice#2} |
| Light | F | {cfg:magic.spellTowerLevel#3} | {cfg:magic.lessonPrice#3} |
| Detect Monsters | E | {cfg:magic.spellTowerLevel#4} | {cfg:magic.lessonPrice#4} |
| Shield | D | {cfg:magic.spellTowerLevel#5} | {cfg:magic.lessonPrice#5} |
| Blink | C | {cfg:magic.spellTowerLevel#6} | {cfg:magic.lessonPrice#6} |

Rank: your rank in the adventurers' guild (the defaults, see [Ranks](ranks)). Tower: the level of the tower of the focus's kingdom. Lesson: the price of the first level in copper coins of the focus's kingdom; further levels cost more, and guests pay more still (see [The guild of mages and its rank](magic_guild)).

### What they do

- **Fireball** flies where you look and deals {cfg:magic.fireballDamage} damage. It never sets blocks on fire; in peacetime it hurts no players and no citizens.
- **Healing** gives {cfg:magic.healAmount} health to the citizen or subject of your kingdom you look at within 8 blocks; otherwise it heals you.
- **Light** gives night vision for {cfg:magic.lightSeconds} s.
- **Detect Monsters** makes every monster within {cfg:magic.detectRadius} blocks of you glow for 10 s; the line above the hotbar says how many were found.
- **Shield** gives Absorption II (4 hearts of extra health that take damage first) for {cfg:magic.shieldSeconds} s; from level III, Absorption III.
- **Blink** takes you forward along your look at once, up to {cfg:magic.blinkDistance} blocks, to the last free spot before a wall. With no room the game says "No room to jump there" and no energy is spent. A blink into the protected land of another kingdom takes {cfg:magic.blinkPrepareSeconds} s to prepare: stand still while a bar above the hotbar fills; a step or a blow breaks the preparation. In your own land and on land of nobody the blink is instant.

Damage, healing, time, reach and radius grow with the level you learned the spell to (see [The guild of mages and its rank](magic_guild)).

### Spells of the kingdom in short

Protective Ward, Healing, Fertility, Fair Wind and Gates are opened by the research of the tower and spend the energy of the kingdom's store. Prices and details are in [Spells of the kingdom](kingdom_spells).

## kingdom_spells | Spells of the kingdom
@icon minecraft:amethyst_shard
The spells of the kingdom are opened by [research](magic_research) and run on the energy of the kingdom's store. A member with the Mage tower right switches them on and off in the Spells view of the Magic tab (see [The Magic tab](magic_tab)).

| Spell | Price | Per |
|---|---|---|
| Protective Ward | {cfg:magic.spellPrice#1} | chunk a day |
| Healing | {cfg:magic.spellPrice#2} | wounded |
| Fertility | {cfg:magic.spellPrice#3} | post a day |
| Fair Wind | {cfg:magic.spellPrice#4} | caravan |
| Gates | {cfg:magic.spellPrice#5} | trip |

### How they pay

The ward and fertility pay as soon as they are switched on and then at the start of every game day. The others pay for each use. Without enough energy a spell switches off and the members with the right get a message; there is never a debt of energy. Gates never switch off: without energy the trip just does not happen.

### Protective Ward

In the chosen chunks {cfg:magic.wardReduction*100}% of the natural spawns of hostile monsters do not happen; spawners are not touched. Put a chunk under the ward with the Ward this chunk button: stand in the chunk of the kingdom's land and press it. At most {cfg:magic.wardMaxChunks} chunks are under the ward; chunks the kingdom lost leave the ward by themselves. The daily price is the number of chunks under the ward times the price of a chunk.

### Healing

Wounded citizens in the infirmary get {cfg:magic.healingPerSecond} more health a second on top of the usual healing (see [Health, the infirmary and death](health)). The spell pays once for each wounded citizen, when it starts to treat it. Far from players the wounded of a kingdom with an infirmary are healed this way.

### Fertility

Around every farm (the composter of a farmer) and every herder's post, within {cfg:magic.fertilityRadius} blocks, random spots (this many a second: {cfg:magic.fertilityGrowthPerSecond}) get an extra step of growth of crops, sweet berries and cocoa, and young animals grow up twice as fast. It works only in loaded chunks. The daily price is the number of such farms and posts (see [Farmer](farmer) and [Herder](herder)).

### Fair Wind

A caravan of a deal saves {cfg:magic.tailwindShare*100}% of its remaining way, once per journey. The seller pays if it has the spell on, otherwise the buyer (see [Deals and caravans](deals)).

### Gates

Subjects travel between the [magic gates](magic_gate) of the kingdom; each trip costs the price in the table.

## magic_gate | Magic gates
@icon kingdomworld:magic_gate
**Magic gates** {item:kingdomworld:magic_gate} take subjects from one gate of their kingdom to another for the energy of the kingdom.

{recipe:kingdomworld:magic_gate}

### Placing gates

- A member of the kingdom with the Mage tower right places gates on the land of the kingdom. A player without the right, or on land of nobody, is refused.
- A kingdom has at most {cfg:magic.maxGates} gates.
- Gates placed by a machine or a command stay an idle block until a member with the right places them.
- When the land under gates goes to another kingdom, they stop working until a member of the new kingdom with the right places them again.

### Travelling

1. The kingdom must finish the researches Protective Ward and Gates, and the spell Gates must be on in the Magic tab (see [Spells of the kingdom](kingdom_spells)).
2. Use the gate to open the window "Gates #N of <kingdom>": the other gates of the kingdom with their coordinates and distance (for gates in another world, the name of that world instead), the price of a trip and the energy of the kingdom.
3. The **Go** button next to a gate takes you there. The trip is paid from the kingdom's energy just before it.

Only subjects of the kingdom use its gates; for anybody else "These gates lead nowhere". Gates may lead into another dimension if gates of the same kingdom stand there. Gates someone broke vanish from the list when you try to travel to them. All gates with their coordinates show in the Gates view of the Magic tab.

> Put one gate by the throne and others by far mines, farms or cities: subjects get where they need for a few units of energy.

## magic_wells | The magic of other mods
@icon minecraft:lapis_lazuli
With **Iron's Spells 'n Spellbooks** or **Ars Nouveau** on the server, every working magic focus on the land of a kingdom serves as a well of magic and spends the energy of the kingdom's store for it.

- Every {cfg:magic.wellIntervalTicks/20} s a focus refills the mana of the subjects of its kingdom who stand within {cfg:magic.wellRadius} blocks of it.
- For Iron's Spells one unit of the kingdom's energy gives {cfg:magic.ironsManaPerEnergy} mana.
- For Ars Nouveau one unit gives {cfg:magic.arsManaPerEnergy} mana to a player. The focus also fills source jars (and other blocks that take source) on the land of its kingdom within the same radius: {cfg:magic.arsSourcePerEnergy} source a unit.
- In one pulse a focus spends at most {cfg:magic.wellMaxEnergy} energy on one player or one jar.
- The energy is paid before the mana is given, and only as much as is missing.
- The spending shows in the journal of the Magic tab as Mana and source (see [The energy of a kingdom](energy)).

A focus never fills the jars of a neighboring kingdom. The server switches the wells off with the settings ironsWell and arsWell. Iron's Spells sends the new mana to your screen itself, with its own regeneration, so it may show up to half a second late.
