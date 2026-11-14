# The world
@icon minecraft:compass

## world_kingdoms | Kingdoms of the world
@icon minecraft:compass
**Kingdoms of the world** appear together with the world, and no player rules them. The interface calls them
ancient: the Overview tab of their throne window says "Ancient kingdom: small", "medium" or "large", with the hint
"It arose together with the world; no player rules it". Each has land, a capital, a ruler, laws, a treasury with a
currency of its own, a market, guilds and an army. They live by the same rules as kingdoms of players: they build,
trade, hire and go to war.

### How they appear

- Unless the server turned them off, the world plans {cfg:npcKingdoms.count} kingdoms of the world in the overworld
  from its seed. The Nether, the End and dimensions of mods take no part.
- A new world lays them out as **states** with provinces by default: 3 large and 5 small ones ([States](states)).
  A world with the older plan of capitals (as every world of version 1.3.0 and older has) places capitals with a
  little land {cfg:npcKingdoms.minSpawnDistance}-{cfg:npcKingdoms.radius} blocks from the world spawn and at least
  {cfg:npcKingdoms.minDistance} blocks from each other.
- A minute or two after the world is made, a kingdom is founded **as data**: the place of its throne, its land,
  names, ruler, laws and money. Its land is protected from that minute on, and the atlas and map mods show its
  borders, capital, future towns and roads at once (dashed until they are built).
- The plan loads and generates nothing. It keeps away from the land of others and from places where players spent
  more than {cfg:npcKingdoms.maxInhabitedMinutes} minutes: a kingdom of the world never appears over what players built.

### When the buildings rise

- By default the buildings rise in the background: one kingdom at a time, at most
  {cfg:npcKingdoms.backgroundBlocksPerTick} blocks and {cfg:npcKingdoms.backgroundChunksPerTick} chunks a tick, while
  players are on the server and the average tick is no longer than {cfg:npcKingdoms.backgroundPauseTickMillis} ms.
- A kingdom a player comes closer to than {cfg:npcKingdoms.triggerChunks} chunks is built first and at the full
  budget ({cfg:npcKingdoms.blocksPerTick} blocks and {cfg:npcKingdoms.chunksPerTick} chunks a tick): you can watch the
  capital grow.
- A server may switch to a mode where buildings rise only when a player comes near. The kingdom is on the map even
  then, holds its land and lives.
- Buildings do not rise while chunks are generated, so flying with elytra does not lag. A restart in the middle of
  the building goes on from the same place without copies.
- The starting wealth is given once, when a kingdom appears: money in the treasury marked "Founding", food for
  {cfg:npcKingdoms.startingFoodDays} days for all its citizens, logs, planks, cobblestone, iron, seed and the gear of
  its soldiers in the storehouses. From then on the kingdom lives by the usual rules.

### How to know them

- A right click on the throne of a capital opens its throne window. Its Overview tab shows the ruler, its leaning
  and traits, the latest decree, needs and attitude to you ([Rulers of the world](rulers)), the population (and
  while the capital rises, how many people to expect), the ranks of the guilds, and the number of settlements and
  people in them.
- The atlas and map mods show their land. The Kingdoms list on the map tells large from small
  ([What the map shows](map_view)).
- The names of kingdoms, cities and rulers are made in two alphabets and never repeat in a world. You see them in
  the language of your client: Cyrillic in Russian, Latin in English.
- A kingdom of the world takes the color that other kingdoms have least.

### What you can do with them

- Become a subject with **/kingdom join** and the name of the kingdom: the ruler takes everyone who applies.
- The throne of a kingdom of the world cannot be taken over: it is taken only by war ([War](war)).
- A subject of a kingdom of the world has the same rights as a subject of a kingdom of players. With a reputation
  of 50 or more the ruler may name one an honorary councillor: an honour that gives no rights.
- Trade at their markets (open to strangers, taking foreign currency, with automatic prices), take contracts of
  their guilds and make deals with them ([The market](market), [Deals and caravans](deals)).
- The law and the guards hold in their cities as in any kingdom ([The law of a kingdom](law)). They have no shield
  of absent players: their army keeps their land.
- The land of their provinces where they do not live is wild and open to players ([The wilds](wilds)).

### Guilds and army

- The adventurers' guild and the guild of mages have a rank from the founding: a small kingdom from
  {cfg:guild.worldRankMin#1} to {cfg:guild.worldRankMax#1}, a medium one from {cfg:guild.worldRankMin#2} to
  {cfg:guild.worldRankMax#2}, a large one from {cfg:guild.worldRankMin#3} to {cfg:guild.worldRankMax#3}. Only one
  kingdom of the world, the strongest, has the rank SS.
- Adventurers and mages come from abroad. The adventurers hunt far away and bring crystals home, and the mages
  keep a ward over the throne with them and heal. Every {cfg:guild.worldGrowthDays} days a kingdom whose treasury is
  no smaller than at its start raises every guild by a rank ([The guild and its desk](guild)).
- The royal army lives in camps on the land of the kingdom, up to {cfg:npcKingdoms.campSoldiers} soldiers a camp and
  three camps at most. The ruler fills a lack of soldiers with recruits from abroad, {cfg:npcKingdoms.armyPerDay} a
  game day ([The army of a kingdom](army)).

## world_capitals | Capitals of the kingdoms of the world
@icon minecraft:lantern
The capital of a kingdom of the world is built whole, by a plan. In a new world, where kingdoms are laid out as
states, and in worlds planned with districts, it is a **capital of districts**.

### A capital of districts

- A palace with the throne stands in the middle: a great hall for a small and a medium kingdom, for a large one a
  castle of 35x35 with a yard, corner towers and a gatehouse.
- A square lies around the palace, and two wide streets run from it to gates in the middle of the sides of the wall.
  A small capital has two gates, north and south, a medium and a large one four.
- Districts: the market in the south, crafts and storehouses in the east, the garrison by the palace in the north,
  houses in the west. Buildings stand in rows facing the street. A walk of the guard runs along the inside of the
  wall, gardens lie behind the houses, lawns with a tree on the corners, and lanterns line the main streets.
- Fields are of carrots, every third one of wheat for the bakers. A small capital keeps its fields inside the wall,
  the larger ones outside.

| Size | Wall | Buildings | Level |
|---|---|---|---|
| Small | 44, palisade | about 23 | village |
| Medium | 54 | about 37 | town |
| Large | 82, stone | about 90 | large |

Such a capital has a wall radius of its own, not the one of cities of players ([City levels](cities_levels)).

### The look

Each such capital takes one of five looks by the climate of its land: stone with the local wood, red brick with
spruce, sandstone with acacia, deepslate brick with dark oak, or mud brick with jungle wood. The wall, the paving
and every building follow the look. Every role of a building has several templates, chosen by the seed, so that a
street does not repeat one building.

### Styled capitals

With the older plan of capitals a new world gives each capital one of eight architectural styles (no two in a world
share one) and a castle around the throne. On the square around the castle stand the guild house, the mage tower,
the treasury with the bank and the mint, the court, the infirmary and a tavern. The larger the kingdom, the higher
the rank of its guild and the bigger its guild house and mage tower ([Architectural styles](styles)). The capital of
a state has no styled castle and no guild house, and only a large one has a mage tower.

### The life of a capital

- There are as many citizens as beds. Each comes to its own house and works near it.
- Work posts stand on the square by the palace (a builder, a carrier, a registrar at the guild desk, a minter at
  the mint, the bank nearby), in the workshops, at the market and in the mage tower. Fields with composters appear as
  food is needed, a grove grows by the lumberjack's post, and the miner's post stands outside the wall.
- The building sets the profession of a post: a workshop with an anvil or a blast furnace is a smithy, one with a
  smoker a bakery.
- The garrison: the guard at the gates, at the storehouses and in the throne hall, a reserve of the army at the
  barracks. The royal army is far larger than the guard: by default 50-100 soldiers in a small kingdom, 80-150 in a
  medium one and 100-200 in a large one, more under a warrior and fewer under a merchant.
- A currency and a mint of its own. The market is open to strangers, takes foreign currency and sets its prices
  by itself.

### Towns of the older plan

In worlds with the plan of capitals, towns stand around the capital {cfg:npcKingdoms.townDistance} blocks from it:
two rows of buildings along a street with a square and a town hall, 7 buildings for a small and a medium kingdom and
9-10 for a large one (houses, a well, a workshop, a wooden watchtower, barracks too for a large one), a field of
carrots, and a palisade around the towns of a large kingdom. A road leads to every town: a path, a gravel road or a
paved road by the size of the kingdom. States have living settlements instead of such towns
([Living settlements](settlements)).

## states | States
@icon minecraft:filled_map
Since version 1.4.0 a new world lays out its kingdoms of the world as **states**. The wide land of each is split
into provinces, which hold large cities, small towns, villages and hamlets with roads between them, while the land
where the state does not live stays wild.

### The plan of states

- A new world gets 3 large and 5 small states within {cfg:npcKingdoms.radius} blocks of the world spawn, no closer
  than {cfg:npcKingdoms.spawnZone} blocks to it.
- A neutral strip at least {cfg:npcKingdoms.gapBlocks} blocks wide lies between states, and at least
  {cfg:npcKingdoms.neutralShare*100}% of the land of the world stays neutral.
- A small state takes 9-13 thousand chunks, a large one 28-36 thousand.
- The plan is made from the noise of the generator without generating chunks and is ready less than a minute after
  start. Chunks generated already and the land of kingdoms of players, with 4 chunks to spare, are kept clear of.
- A world whose plan of capitals was made or begun (every world of older versions included) keeps its capitals: the
  plan is chosen in the server config before the world is made.

### Provinces

The land of a state is split into provinces of about {cfg:npcKingdoms.provinceChunks} chunks.

| Kind | What it is |
|---|---|
| heart of the state | the capital's province |
| province | the core, for towns |
| borderland | empty by the plan |
| wild land | nobody's, between them |

A province protects the land of its holder like a claim and changes hands whole: by the plan, a purchase, a treaty,
a peace or an annexation ([Provinces](provinces)). The main settlement of a province is its seat.

### Settlements

| Settlements | Small | Large |
|---|---|---|
| Large cities | 1-2 | 4-8 |
| Small towns | 3-6 | 10-15 |
| Villages, hamlets | 8-15 | 30-40 |

Every place is checked by the noise of the generator: flat dry land without water, not on generated chunks and not
on the land of kingdoms. If a place does not suit, up to three spare ones are tried, then places nearby in the same
province (rings of 48 and 96 blocks), and a settlement no place suits is left out. Villages and hamlets stand
300-700 blocks from the settlement they belong to. How they live: [Living settlements](settlements).

### How states grow

A state grows by provinces, not by claims. Every 12 days it takes a wild province next to it, while its land is
below 130% of its plan and at least {cfg:npcKingdoms.neutralShare*100}% of the world stays neutral. It takes no
province near the world spawn, none with a claim of a player in it or within 4 chunks of one, and none where players
spent more than {cfg:npcKingdoms.maxInhabitedMinutes} minutes.

A new town of a state is a village in an empty province of its core. Without such a province the ruler makes a
village not raised yet a small town. When no free wild land is near, the ruler quarrels over land with its
neighbours, and a warrior goes to war for land on a neighbouring state of the world half as strong.

### Buy, ask, conquer

- A ruler sells empty provinces of its borderland and core: [Buying provinces and charters](buying_land).
- A neighbour can be asked to cede a province by treaty in the Diplomacy tab, button Ask to cede
  ([Diplomacy](diplomacy)).
- In war provinces are taken whole: [War for provinces](province_war).

### On the map

On the atlas the land of a state is filled with its color: thick borders between states, thin ones between
provinces, wild land hatched. On hover the bottom line shows the province, its kind, its holder and its main
settlement. Settlements are drawn by rank, and until a settlement is raised its sign is dashed
([The atlas and the map](atlas)).

## settlements | Living settlements
@icon minecraft:bell
Every settlement of a state lives as a settlement of its rank, in the style of its kingdom. This is one of the main
novelties of version 1.5.0: large cities stand inside walls, small towns became real towns, and villages and
hamlets work their land.

| Rank | Houses for | Guard | Rises at |
|---|---|---|---|
| Large city | 80-150 | 8 | 16 chunks |
| Small town | 25-45 | 4 | 14 chunks |
| Village | 10-25 | 2 watchmen | 12 chunks |
| Hamlet | 4-8 | 1 watchman | 10 chunks |

### A settlement as data

When a state is founded, all its settlements are written down as data: rank, place, province, the settlement it
belongs to, a name that never repeats in the world, its houses with three quarters of that in people, food for 10
days, a guard, the militia of a village (a tenth of its people), its wealth and the yield of its land. Once a game
day a settlement lives a day in numbers: fed people grow by 2% a day while the houses have room, food is the harvest
less what is eaten (30 days in store at most), and hunger takes people and wealth. The numbers make neither coins
nor items: the treasury and the storehouses live only by named citizens.

### Rising in the world

- Settlements rise in the world one at a time, in the same queue as the capitals.
- A player closer than the distance in the table raises a settlement at once, at the full budget.
- Once every capital stands, the background raises large cities and small towns within
  {cfg:npcKingdoms.backgroundSettlementBlocks} blocks of players online. Villages and hamlets rise only when someone
  comes.
- First the land is looked at: the land of others, chunks where players spent more than
  {cfg:npcKingdoms.maxInhabitedMinutes} minutes, and what may not be broken. If the place does not suit, it moves once
  by 1-3 chunks within its province, otherwise the settlement stays data and its people go as a number to the nearest
  settlement of the kingdom.
- Then step by step: the ground, the town hall and buildings, fields, beds and posts, stores, citizens, guard, wall.
  A restart goes on from where it was, without copies of buildings or citizens.
- Named citizens arrive 4 a tick, and each is always the same person for its place: the seed of the settlement
  sets its name, look and age.

### What stands in a settlement

- **Large city**: the districts of a medium capital, with the residence of its governor in the middle (the small
  castle of the style, or for a kingdom of districts the great hall of its plan) and the town hall in its hall. A
  market with stalls and merchants, a tavern, storehouses, workshops, barracks, an infirmary, a house of the
  adventurers' guild and a mage tower by rank, wells and houses: at most 40 buildings on land of 11x11 chunks, and
  a stone wall with the gates of its plan.
- **Small town**: a palisade of radius 36 blocks with a gate at each end of its street. By the town hall a market,
  a tavern, a smithy, a storehouse, a well, a second workshop and a watchtower (barracks too in a large state),
  behind them two rows of houses along a lane, fields outside the palisade.
- **Village**: houses, a barn, a well and 2-3 fields with their farmers, two to a field.
- **Hamlet**: 2-3 houses, a small storehouse, a well and a field.

### Work by the land

The lumberjack, the miner and the fisher work where it pays: 48 places on three rings around the settlement are
looked at. A fisher stands on a bank by water, a lumberjack by a forest (where there is none, it plants a grove), a
miner by rock and hills. A small town takes the two kinds of work that pay best, a village and a hamlet one, a large
city the places of its plan. The posts in buildings take the profession of what the building is for: smith, cook,
crafter, merchant, innkeeper, mage, registrar of the guild, minter, builders and carriers ([Professions](professions)).

Guard, stores, law and completion: [Guard and stores of settlements](settlements_life). What happens to the people
when no player is near: [Citizens of settlements as numbers](settlements_folding).

## settlements_life | Guard and stores of settlements
@icon minecraft:shield
### The guard

- Large city: 2 guards at the gate towards the settlement it belongs to, 3 on a round through the gates around the
  residence and 3 at the town hall.
- Small town: 1 guard at the gate and 3 on a round along the street.
- Village: 2 watchmen, hamlet: 1 watchman, on a round along the street. The militia of a village stays a number.
- The guard comes from abroad into the guard of the kingdom with kits paid by the treasury, and takes no citizen
  from its work.
- The guards of a settlement stand only at its posts and never go off to the capital. Those it lost come again from
  abroad once a day, at most 8 a kingdom a day.

### The law

The land of a village or a hamlet counts as a town for the law. The alarm is raised only by what was seen, the
chase goes through the settlement and 48 blocks beyond its edge, the alarm ends {cfg:law.alarmSeconds} seconds after
the offender was last seen, and damage to property counts as in a town. Witnesses run to the nearest guards and
watchmen, and the wanted are wanted throughout the kingdom ([Witnesses](witnesses), [Wanted](wanted)).

### Gates

Gates are cut where the plan has them, towards every settlement a road of the plan leads to (only where no gate
lies within 32 blocks), and wherever a road was laid already. A road of a kingdom of the world laid later cuts its own
gate into the wall of the settlement it comes into ([Walls and gates](walls)).

### Stores and tools

When a settlement rises, its storehouse gets once:

- bread {item:minecraft:bread} for its people for {cfg:npcKingdoms.startingFoodDays} days, wood and stone by its rank;
- iron and coal for the smiths, wheat and potatoes for the cooks and innkeepers, planks and wool for the crafters,
  seed for the fields;
- spare tools: 2 of each in a large city, 1 in a small town, a hoe in a village or a hamlet.

Every worker who needs a tool gets one into its hands: an axe for a lumberjack, a pickaxe for a miner and a builder,
a hoe for a farmer, a rod for a fisher, shears for a herder, of iron in a large city. From then on the settlement
lives by its work and its treasury. Once a day the ruler of the world buys stone tools for workers who stand without
one (up to 24 a day) and puts them into the storehouse next to their post.

### Heads of settlements

The first settler has a title over its head: the governor of a large city, the mayor of a small town, the elder of a
village or a hamlet.

### Roads of the plan

The plan lays roads between settlements. A road of the plan becomes real once both settlements stand and a player is
within 12 chunks of it, and without players one every two minutes in the background. When a settlement rises and the
settlement it belongs to stands already, the road between them is looked for at once. It is paid once, as the roads
of the founding are, and laid as its chunks load. Caravans use the roads of the plan not laid yet as tracks, 1.2
times as fast as across country.

### Completing older settlements

A settlement raised by version 1.4.0 or 1.4.1 is completed once, when a player comes near or the background gets to
it:

- the missing buildings of its plan go up only on free ground: at the street and under it only the ground as it
  grew or the paving of the kingdom itself, over the street only air, wild plants, snow and trees with their crowns;
- never over farmland, crops or water, roads, buildings, walls, blocks of the mod or containers holding anything,
  and the ground is looked at once more before the first block;
- a market, tavern, storehouse, workshop, well or barracks whose place is taken looks for free ground in the
  settlement, and a house waits for its own place;
- then come the fields, the missing posts, the stores, the tools, the palisade of a small town, the gates, the guard
  and the posts.

The ground is not levelled as a whole, no citizens are settled again, and nothing players placed is broken. Once
completed, a settlement is never completed again.

### A new owner

A settlement the journal of conquests hands to another kingdom rises no more for the one that lost it, and one that
was rising goes on for the winner from what stands. A settlement won by a kingdom of players does not rise
([War for provinces](province_war)).

## settlements_folding | Citizens of settlements as numbers
@icon minecraft:white_bed
States hold hundreds of settlements and thousands of people. So that the world lives without weighing on the server,
the people of settlements no player came near for a while **fold into numbers**, and when a player comes they come
back as themselves.

### Who stays named

- The holders of the posts of a standing town stay named while it stands.
- The other citizens (and all of a village or a hamlet) stay named while a player was near in the last
  {cfg:npcKingdoms.settlerKeepDays} game days: within the reach of bodies ({cfg:npc.citizenActivationChunks} chunks)
  and 4 chunks more.
- Citizens with a body, soldiers and members of squads, adventurers, hirelings and those away on an expedition of
  the guild, the sick and the wounded, and those holding a reservation of a workshop never fold.

### How they fold and come back

- Citizens nothing refers to fold into the numbers of their settlement, 16 a tick. A citizen keeps its place,
  profession, age and mood, and its things go to the storehouses of the kingdom.
- When a player comes, they come back as themselves (the same name, look, age and profession), 4 a tick, into their
  beds and with nothing in their hands.
- A death in a settlement is one person less in its numbers.

### Limits

- Above {cfg:npcKingdoms.maxNamedCitizens} named citizens of the kingdoms of the world, their newcomers and returns
  wait, and the settlements no player visited longest fold first. The citizens of kingdoms of players do not count
  against it and never fold into numbers.
- The limit of citizens of a kingdom ({cfg:citizens.maxCitizensPerKingdom}) counts the capital only for a kingdom with
  settlements. Settlements are bounded by their beds, and their free beds wait for their citizens.
- A settlement in the journal of conquests neither folds nor brings back citizens until it is the winner's. A
  settlement of a kingdom of players never folds its citizens, and those folded before come back.

### Where to see it

The card of a settlement on the atlas says "People: N, M of them named". In the Citizens tab of the throne window a
line for every settlement follows the named citizens: how many people and how many named, how many beds, the days of
food and the guard ([Who the citizens are](citizens)).

## rulers | Rulers of the world
@icon kingdomworld:founder_crown
Every kingdom of the world has a **ruler**: a king or a queen without a body, the throne standing empty. A ruler has
a name (in both alphabets, as citizens do), a leaning and one or two traits ([The temper of a ruler](rulers_temper)).
A ruler plays by the rules of players: money comes only from taxes, trade and the market, and buildings are built
from the materials of the storehouses by real builders.

### The turn of a ruler

Every {cfg:npcKingdoms.rulers.turnEveryDays} game day the ruler looks at its kingdom: citizens and beds, food,
storehouses, surplus and shortages, treasury and income, buildings and their integrity, the army, camps of robbers
near roads, the wanted, deals and neighbours. In a turn it makes up to {cfg:npcKingdoms.rulers.decisionsPerTurn}
decisions, the most pressing first, so that the kingdom changes step by step. Answers to deals and to roads over its
land come at once and do not count.

### What a ruler decides

- **Building**: a house once no more than {cfg:npcKingdoms.rulers.freeBeds} beds are free; a storehouse once the
  storehouses are {cfg:npcKingdoms.rulers.storageFullPercent}% full; the buildings its level wants; repairs and
  upgrades. A place is looked for on its own flat land by the streets, without pulling down anything of others.
- **Land**: without a place the ruler claims free chunks next to it (up to {cfg:npcKingdoms.rulers.chunksPerClaim}
  a decision), never those of others or where players lived. Without need the border grows by
  {cfg:npcKingdoms.rulers.growthChunks} chunks a turn. States grow by provinces ([States](states)).
- **Towns**: a new town with a town hall and a road for every {cfg:npcKingdoms.rulers.townPopulation} citizens beyond
  the towns that stand, {cfg:npcKingdoms.rulers.maxTowns} at most.
- **Money**: taxes, wages, orders for the workshops (bread, weapons, tools), a minter at an idle mint, surplus to the
  market and abroad, shortages bought, deals with neighbours at market prices with a markup.
- **Army**: about {cfg:npcKingdoms.rulers.soldiersPermille} soldiers for every thousand citizens, more under threat,
  patrols on the roads, guards for caravans, a guild reward on the robbers of the nearest camp.
- **Roads** to neighbours closer than {cfg:npcKingdoms.rulers.roadDistance} blocks
  ([Roads across the land of others](roads_crossing)).
- **Hard times**: a treasury below a reserve of {cfg:npcKingdoms.rulers.reserveDays} days of expenses and losing money
  is poor. A poor ruler lets part of the army go, stops building, raises taxes and sells its surplus.
- **A festival** when the mood of the citizens falls below {cfg:npcKingdoms.rulers.festivalMood} and the treasury can
  pay ([Festivals and fairs](festivals)).
- **Research** of the mage tower, an **honorary councillor** among subjects with a reputation of 50 or more, spies
  (up to {cfg:nobility.npcMaxSpies}, see [Intrigue and espionage](intrigue)).

### War and peace

A merchant looks for pacts and alliances, a builder for pacts. A warrior declares war on a weak neighbour no more
than once in {cfg:war.npcWarDays} days and leaves alone one that fought in the last {cfg:war.npcRecentWarDays} days.
Rulers of the world declare war on a kingdom of players no more than once in {cfg:war.npcPlayerWarDays} days, and
only if the server allows it. A just ruler never strikes first but stands by its allies, a mage keeps out of it
([Diplomacy](diplomacy)).

### Decrees and the chronicle

Important decrees go into the chronicle in one line (the Citizens tab, together with deaths), for example "Queen
Dobroslava ordered a tavern built", at most {cfg:npcKingdoms.rulers.decreesPerDay} a day, and reach the subjects in
the chat. The Overview tab of the throne window shows:

- "Lately": the latest decree, and on hover all recent ones;
- "Needs": what is lacking and what the ruler saves for, with the hint "Sell what it needs at its market or offer it
  a deal";
- "Attitude to you": hostile, cold, even, warm or friendly, by your reputation and your deals.

## rulers_temper | The temper of a ruler
@icon minecraft:book
The character of a ruler is a **leaning** and one or two **traits**. They show in the Overview tab of its throne
window ("Leaning: trade; generosity and justice") and colour all its decisions.

### Leaning

| Leaning | Cares for |
|---|---|
| trade | market, caravans |
| war | army, walls, roads |
| building | buildings, towns |
| magic | the mage tower |

- **Trade**: fewer soldiers (times 0.8), a lower sales tax, a higher markup on its own goods, hard bargains, an entry
  fee of large cities half as much again, lets roads of others pass gladly, looks for pacts and alliances.
- **War**: twice the soldiers, spends up to half its income on the army, builds barracks and towers early, fines half
  as hard again, a larger bounty, lets roads pass only for friends, declares wars.
- **Building**: builds more eagerly, claims land and founds towns more often.
- **Magic** (a large kingdom only): the research of the mage tower first, keeps out of wars.

### Traits

Two traits of a ruler never contradict each other: generosity never comes with thrift, nor caution with boldness.

| Trait | What it does |
|---|---|
| generosity | kinder to people |
| thrift | saves, bargains |
| caution | saves, stays safe |
| boldness | takes risks |
| pride | stricter, dearer |
| justice | milder, fairer |
| ambition | grows, dearer |
| patience | an even temper |

- **Generosity**: lower taxes, a smaller reserve, wages up to 125% with a rich treasury, gives way in a bargain,
  thinks 10 better of you, milder fines, a bounty half as large again, sells provinces cheaper.
- **Thrift**: higher taxes, a reserve half as large again, hard bargains, half the bounty, sells provinces dearer.
- **Caution**: a larger reserve, a quarter more soldiers, lets roads of others pass less readily, goes to war only on
  those far weaker.
- **Boldness**: a smaller reserve, lets roads pass and goes to war more readily.
- **Pride**: higher taxes, thinks 10 worse of you, hard bargains, harder fines, lets roads pass less readily, sells
  provinces dearer.
- **Justice**: lower taxes, half the markup, milder fines, half the entry fee, bans slavery on its land, never strikes
  first.
- **Ambition**: higher taxes, an entry fee a quarter higher, claims land and founds towns more often.
- **Patience**: moves neither taxes nor prices.

### Attitude to a player

The attitude comes from your reputation under its law and from how your deals went; pride takes 10 off, generosity
adds 10. At -50 and below it is hostile, up to -15 cold, up to 15 even, up to 50 warm, above that friendly. Answers
to deals, roads passing and the price of provinces depend on it ([Buying provinces and charters](buying_land)).

## tavern | The tavern
@icon minecraft:honey_bottle
A **tavern** is a building with the role Tavern, where citizens rest in the evening and players buy food and drink,
hear rumors and rent a room.

### How to set up a tavern

1. Build a tavern from a blueprint of the catalog: "Tavern" (a counter with the innkeeper's post, barrels, tables
  with benches, lanterns, three rooms upstairs) or its upgrade "Tavern with rooms" ([Blueprints and the catalog](blueprints)).
  The wood and stone change as with all templates. A building of your own with the role Tavern will do too
  ([The roles of buildings](building_roles)).
2. Place a work post {item:kingdomworld:work_post} inside by the counter and give it the profession Innkeeper
  ([The work post](work_posts)).
3. While a healthy innkeeper works at the counter, the tavern is open. The innkeeper works in the evening too.

A tavern takes 12 guests an evening, and 8 more with every further level of the building.

### An evening in the tavern

After work {cfg:life.tavernShare}% of the citizens (others every evening) go to the tavern of their city. There a
citizen eats or drinks one thing from the storehouses as its supper and gets +{cfg:life.tavernMood} mood for
{cfg:life.tavernMoodDays} days. The tavern serves the food and drink of its tags: bread {item:minecraft:bread}, cooked
meat and fish, baked potatoes, pumpkin pie, cookies, soups and stews, honey bottles {item:minecraft:honey_bottle},
and food and drinks of mods. Without a tavern mood does not fall, there is just no bonus. Far from players an evening
in an open tavern gives mood too ([The day of a citizen](daily_life), [Mood](mood)).

The beds of a tavern are not homes of citizens but rooms for guests.

### The window of the tavern

Right click the innkeeper. The window "Tavern of ..., innkeeper ..." has three views, and you can buy while you
stay within 8 blocks of the innkeeper:

- **Food and drink**: from the storehouses at the prices of the market (the king's price if the market sells the
  item, otherwise the price of the market by worth and stock; reputation moves it as at the market). Up to 64 at a
  time. The money goes to the treasury, ledger line "Taverns and festivals" ([The market](market)).
- **Rumors**: what people talk about today. A camp of robbers from a rumor can be marked on your atlas for
  {cfg:life.rumorPrice} ([Rumors](rumors)).
- **Room**: "Rent for a day" for {cfg:life.roomPrice}. The bed of the room becomes your point of return, and after
  the day the one before comes back (if you were offline, when you come back). "Another day" adds a day.

The payment button switches between "Pay: coins" and "Pay: account". A room is not rented if its bed is somebody's
home, another player's point of return, or somebody lies in it. Without an innkeeper the window says "The tavern is
closed: no innkeeper works at its counter".

> Kingdoms of the world have taverns: a medium capital a tavern with an innkeeper, a large one a tavern with rooms,
> and the large cities and small towns of states too.

## rumors | Rumors
@icon minecraft:paper
An innkeeper knows {cfg:life.rumorsMin} to {cfg:life.rumorsMax} **rumors** a day. New rumors come once a game day,
one of each kind first, so that the talk is not all about one thing. Every rumor is true: it is made from the data of
the world. You hear them in the window of the tavern, view Rumors ([The tavern](tavern)).

### Kinds of rumors

- **Camps of robbers** closer than 1500 blocks to the tavern: one of eight points of the compass, the distance in
  steps of 50 blocks and the strength of the camp: "a few robbers" (up to 3), "a band" (up to 7) or "a large band".
  For example: "They say a band of robbers is camped about 350 blocks to the north-east".
- **Contracts** of the guild of this kingdom with their reward: "The guild pays ... for a contract: ... Ask at the
  desk".
- **News** of the last 10 days: new kingdoms, citizens killed by robbers or fallen in battle, festivals, founding days
  and fairs of other kingdoms.
- **Prices**: where goods are sold cheap and where they are dear.
- **The wanted**: whom the guards of this kingdom look for and what is offered for the head.
- **Wars**: declarations of war, battles, robbed caravans, peace, alliances, vassalage, broken word, sieges, fallen
  cities and provinces, annexations and cessions ([War](war)).
- **Nobility**: weddings, deaths and murders of the heads of houses, a seized throne, a city taken from the crown,
  verdicts, caught spies and whispers in corners ([Nobility and factions](nobility)).

### A mark on the atlas

A rumor of a camp can be bought: the button "Mark on the atlas for ..." ({cfg:life.rumorPrice}). The camp appears
on your atlas ([The atlas and the map](atlas)). Once a camp is cleared, the innkeeper knows nothing of it any more.
That way a camp is easier to find and clear ([Robbers](bandits)).

## festivals | Festivals and fairs
@icon minecraft:cake
### A festival

The king or a member with the **Citizens** right calls a festival: the Citizens tab of the throne window, view
"life", button **Call a festival**.

- The treasury pays {cfg:life.festivalPerCitizen} for every citizen, and the storehouses give a treat of
  {cfg:life.festivalFood} food points per citizen. Without the treat a festival gives half the mood.
- A festival can be held no more than once in {cfg:life.festivalEveryDays} days; otherwise the game asks you to wait.
- From noon the citizens do not work and gather at the throne or the town hall of their city. The mood of
  +{cfg:life.festivalMood} lasts {cfg:life.festivalMoodDays} days ([Mood](mood)).
- Once a year the day the kingdom was founded is a festival by itself, free, with a treat if there is one. A year is
  {cfg:life.daysPerYear} game days.
- On the atlas a festival is marked by a flag in the color of the kingdom over its capital.
- Rulers of the world hold a festival when the mood of their citizens falls below {cfg:npcKingdoms.rulers.festivalMood}.

### A fair

- Every {cfg:life.fairEveryDays} days every large city with a working market holds a fair on a day of its own (a
  server may let lesser cities hold fairs too).
- During a fair the merchant posts of the city take {cfg:life.fairSeats} merchants more, and the market nearby sells
  {cfg:life.fairDiscount/10}% cheaper ([The market](market)).
- The merchants of every kingdom that has a deal with the host and a way at least partly by road bring up to
  {cfg:life.fairVisitorItems} items of their export surplus and put them up as lots of their kingdom. The takings go
  to their treasury, and what is not sold goes back to their storehouses the next day ([Deals and caravans](deals)).
- Members of the kingdom and players on its land get the message "A fair in ... today, with merchants of ...".
- On the atlas a fair is marked by a striped awning over the city.

### The view "life" of the Citizens tab

It shows how many citizens are asleep, at work, in the tavern and resting right now, the day by the hour, the taverns
and their guests tonight, the festival (whether today, how long ago the last one was, when the next is possible and
what it costs, the button), the fairs of the cities ("Fair in ...: in ..."), the sick and the chronicle of festivals
and fairs. Without fairs the tab says "No fairs: they need a large city with a market".
