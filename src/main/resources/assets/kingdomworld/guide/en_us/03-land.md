# Land
@icon minecraft:grass_block

## territory | The land of a kingdom
@icon minecraft:grass_block
The land of a kingdom is made of **chunks**: squares of 16 by 16 blocks through the whole height of the world. A kingdom owns the chunks it claimed (its claims) and whole [provinces](provinces), if it has any. A kingdom gets its first chunks when it is founded: the square of radius {cfg:kingdom.initialClaimRadius} around the throne's chunk.

### Rules of the land

- All the land lies in the dimension of the throne.
- The land is connected: from any chunk you can reach the throne's chunk through side neighbours. An own province connects too: a claim that touches an own province is joined to the kingdom.
- The throne's chunk cannot be given up. When the throne [is moved](throne_move), the rule goes over to the chunk of the new throne.
- A gap of {cfg:territory.claimBufferChunks} chunks stays between the land of different kingdoms (square distance, so diagonals count). At zero borders may touch.
- The owner of a chunk is the kingdom that claimed it, otherwise the holder of its province. A claim always wins over a province.
- The number of claims is bounded by [the limit of chunks](chunk_limit), and how to claim and give up land is told in [Claiming and releasing land](claiming).

### What land gives

- **Protection**: outsiders use no buckets, fire or tools, break neither the throne nor containers, and explosions and fire spare its blocks (see [Protection of the land](protection)).
- **Homes for citizens**: a citizen lives only in a bed on the land of its kingdom, and the storehouses of a kingdom count together only on its land (see [Arrival and growth](growth), [The storehouse](storehouses)).
- **Room for work**: the zone of a work post never reaches beyond the land of the kingdom. A lumberjack's zone may, but it fells and plants only on the chunks of its kingdom (see [The work post](work_posts)).
- **Law**: on its land a kingdom judges thefts, blows and damaged property (see [The law of a kingdom](law)).
- **Roads**: a road across the land of another kingdom is built only with its consent (see [Roads](roads)).

### How to see your land

- The kingdom map (key K): the land is filled with the color of the kingdom (see [What the map shows](map_view)).
- Above the hotbar you see "You enter the land of ..." and "You leave the land of ...". These lines can be turned off in the client config of the mod.
- /kingdom info shows the chunks of the kingdom and their limit, and the Overview tab of the throne screen the line "Land".

### When land is lost

A chunk given up is no longer land of the kingdom. A citizen whose bed stood on it is homeless and looks for a new bed, and the workers of a post on such land are without work again (they keep what they learned). In war an enemy may occupy chunks and provinces (see [Capture and destruction](capture), [War for provinces](province_war)).

## chunk_limit | The limit of chunks
@icon minecraft:oak_fence
A kingdom cannot claim as many chunks as it likes. The limit grows with the kingdom: with every player subject and with every citizen.

| What | Chunks |
|---|---|
| Every kingdom | {cfg:territory.baseMaxChunks} |
| For a player subject | {cfg:territory.chunksPerMember} |
| For a living citizen | {cfg:territory.chunksPerCitizen} |

### Who counts

- **Player subjects**: every member of the kingdom but the ruler.
- **Citizens**: every living citizen of the kingdom, soldiers included. It is the same population the throne screen shows.

For example, a king with two subjects and ten citizens has a limit of {cfg:territory.baseMaxChunks} + 2 x {cfg:territory.chunksPerMember} + 10 x {cfg:territory.chunksPerCitizen} chunks.

### What counts against the limit

- Only **claims**, the chunks the kingdom took itself. The first chunks of the founding are claims too and count.
- Provinces bought, ceded or conquered do not count: they are land beyond the limit (see [Provinces](provinces)).
- The limit applies only to chunks claimed by players. An operator's /kingdomworld admin claim adds a chunk without checking rights or the limit.

### When citizens are lost

The land held already stays with the kingdom: nothing is taken away. The kingdom only cannot claim a new chunk while it owns as many chunks as its present limit or more. Trying answers "Chunk limit reached: it grows with citizens".

### Where to look

- The Overview tab of the throne screen: "Land: X of Y chunks", with provinces "Land: N chunks, of them X claimed of Y".
- The command /kingdom info writes the line "Chunks: X of Y" into the chat.
- The message after every claim: "Chunk ... now belongs to ... (X of Y)".

> A kingdom has {cfg:citizens.maxCitizensPerKingdom} citizens at most, so the limit from citizens is not endless either. If you need much more land at once, look for [a province for sale](buying_land).

## claiming | Claiming and releasing land
@icon minecraft:filled_map
Chunks are claimed and given up by a member with the **Territory** right: by default the king, the marshal and the steward (see [The rights of positions](rights)).

### Rules of a claim

A chunk can be claimed when:

- it lies in the dimension of the throne;
- it is nobody's: no other kingdom owns it and it does not lie in another's province;
- it shares a side with the land of the kingdom or with an own province;
- no land of another kingdom, its provinces included, lies within {cfg:territory.claimBufferChunks} chunks (square distance);
- the kingdom has not reached [the limit of chunks](chunk_limit) yet.

A chunk can be given up when it is yours, it is not the throne's chunk and the land does not fall apart without it.

### The map for editing land

1. Open the throne screen and press **"Territory"** in the Overview tab.
2. A map titled "Land of ..." opens. The chunk under the pointer has a white frame.
3. A **left click** on a free chunk claims it, a **right click** on your chunk gives it up.
4. Dragging moves the map, the mouse wheel zooms, and the "To me" button brings you back.
5. The answer of the server shows at the bottom: "Chunk ... now belongs to ... (X of Y)", or the reason of a refusal.

Any visible chunk can be clicked on the map: you need not stand in it.

### Commands

- /kingdom claim claims the chunk you stand in.
- /kingdom unclaim gives up the chunk you stand in.

### Why it fails

- "The chunk must share a side with the kingdom's land": a diagonal does not count.
- "Land of another kingdom is too close": keep the gap.
- "The chunk belongs to another kingdom" or "The chunk already belongs to your kingdom".
- "Chunk limit reached: it grows with citizens".
- "The kingdom's land lies in another dimension".
- "The throne's chunk cannot be given up" and "This would split the kingdom's land".
- "Your position does not allow this": no Territory right.

> Before giving a chunk up, check it for citizens' beds, work posts and storehouses: they stop working for the kingdom.

## protection | Protection of the land
@icon minecraft:shield
The land of a kingdom is protected from outsiders and from its own members without the rights for it. Every check goes through one service: claimed chunks and provinces are protected the same way. A refusal comes with the line "This land belongs to ...".

### Always protected

- **Never broken**: the throne, the working blocks of the mod, workplaces, containers, beds and other blocks that keep data, the leaves of city gates.
- **Item frames and paintings**: the block they hang on does not break anywhere on the land of a kingdom, outside its cities too.
- **Buckets, fire and tools** on blocks need the Building right.
- **Explosions** destroy no blocks on the land of a kingdom, and **fire** neither spreads into it nor burns its blocks. The server may turn either off in its config (the keys protectFromExplosions and protectFromFire).
- **Pistons** move no blocks across a border: a piston outside neither pushes blocks in nor pulls them out.
- **The land of a kingdom of players none of whom is online** is shut as a whole, as under the shield in war.

### Breaking and placing in peace

This is how the server setting peaceBreaking works by default ("crime"):

- Outside cities an outsider breaks and places plain blocks freely.
- In a city and at the buildings, walls and roads of the kingdom a broken block drops nothing, counts as **damaged property** (a crime, see [Crimes](crimes)) and is put back: after {cfg:law.restoreQuietSeconds} s without new damage, a few blocks a second, and at once when the offender has left the land. Blocks placed there are taken out again.
- The fine for damage is {cfg:law.vandalismFinePercent}% of the worth of what was broken (see [Fines and punishment](fines)).
- An outsider places plain blocks only: nothing that holds goods or keeps data (chests, barrels, hoppers, dispensers, droppers, furnaces, shulker boxes, signs), nothing of redstone, no rails, pistons, explosives or fire, no minecarts, boats, armor stands, item frames, paintings, end crystals or spawn eggs.

If the server set peaceBreaking = forbidden, blocks on the land of others are neither broken nor placed at all.

### Doors, containers and switches

- **Doors, trapdoors and fence gates** open to everybody, from inside and outside, in any game mode: no player can be shut in a house. The only exception are the leaves of a city gate: they follow the rules of the gate (see [Walls and gates](walls)).
- **Containers of others** open too, but what is taken from them, ripe fields of others and the herds of the kingdom are judged by the law. A member with the Containers right takes freely.
- **Switches** (buttons, levers, repeaters, comparators, daylight sensors, note blocks), item frames and armor stands are not touched without the Switches and frames right. The server may lift this protection with the key protectInteractions.
- A click on a door or a container with an item in hand simply opens it, without the line of refusal.

### Citizens and machines

- Striking a citizen is not forbidden, but the law judges it as an assault and a killing as a murder (see [Crimes](crimes)).
- Machines of other mods that act as players (the deployers of Create, for example) work with blocks, containers and crops on the land of a kingdom as long as the server allows it (the key allowFakePlayers). Such a machine strikes a citizen only at home: the citizen on the land of its kingdom, the machine on that land too and set up by a member of that kingdom.

### When protection is weaker

- **War**: the land of an enemy is open to the players of the other side, and explosions break blocks there (see [War](war), [Sieges and the ram](sieges)). The land of third kingdoms and land under the shield of absent players stay protected.
- **The wilds** of kingdoms of the world are open as land of nobody (see [The wilds](wilds)).
- An operator bypasses protection with /kingdomworld admin bypass, which lasts until the server restarts.

## provinces | Provinces
@icon minecraft:lodestone
Besides claimed chunks, land can be held as **provinces**: large areas of about {cfg:npcKingdoms.provinceChunks} chunks that a kingdom holds as a whole. The plan of states of a new world lays the provinces out: the land of every state of the world is split into provinces, and the neutral land between states into wild provinces of nobody (see [States](states)).

### Kinds of provinces

- **Heart of the state**: the province of the capital.
- **Province of the core**: room for the towns of the state.
- **Borderland**: provinces the plan leaves empty.
- **Wild land**: provinces of nobody between the states.

A province has a number and a name: a player with the Russian language sees the name in Cyrillic, the others in Latin letters. The main settlement of a province is its seat.

### How a province protects

- A province protects the land of its holder as a claim does: the rules are the same (see [Protection of the land](protection)).
- The owner of a chunk is the kingdom that claimed it, otherwise the holder of its province. A claim always wins.
- The gap {cfg:territory.claimBufferChunks} and the founding distance {cfg:kingdom.minChunksFromOtherKingdom} keep away from provinces too.
- A claim beside an own province is allowed, and a claim in the province of another is refused as foreign land.
- Land stays connected through its own provinces.
- [The limit of chunks](chunk_limit) counts claims only: provinces come on top of it. The throne screen shows the land with them: "Land: N chunks, of them X claimed of Y".
- The land of the provinces of a kingdom of the world where it does not live is wild and open to players (see [The wilds](wilds)). The land of kingdoms of players, bought provinces included, is never wild.

### How a province changes hands

Always as a whole, in one event, however many chunks it has:

- by the plan, when a state is founded;
- bought from a ruler of the world (see [Buying provinces and charters](buying_land));
- by a treaty of cession in the Diplomacy tab (see [Diplomacy](diplomacy));
- by a peace after a war or by an annexation (see [War for provinces](province_war)).

When a kingdom is dissolved its provinces become nobody's. The states of the world grow not by claims but by wild provinces beside them (see [The wilds](wilds)).

### On the map

On the atlas the land of a state is filled with its color, borders between states are thick and between provinces thin, wild land is hatched, and the footer tells the name of the province under the pointer, its kind, holder and seat (see [What the map shows](map_view)).

### Older worlds

The plan of states acts only on a new world. A world of version 1.3.0 and older, and a world whose plan of capitals is made or begun already, stay without provinces: their kingdoms of the world live as before. For a new world with the old plan of capitals an operator sets npcKingdoms.plan = legacy in the server config before the world is made.

> Operators shape provinces with /kingdomworld admin province (fill, give, info, clear) and look at the plan with /kingdomworld admin land.

## buying_land | Buying provinces and charters
@icon minecraft:paper
A ruler of the world sells part of its land by provinces. A province is expensive: a big goal rather than a purchase on the way. A kingdom may buy one, and a player without a kingdom may buy a **charter** and found a kingdom right in the province.

### What is for sale

Only an empty province of the borderland or the core of a state of the world. A ruler does **not** sell:

- the heart of its land, nor a province next to it;
- a province where a settlement stands or is planned;
- wild land: it is nobody's to sell;
- when it would keep less than three quarters of its planned provinces, or its land would fall apart without the province;
- more often than once in 10 days, nor more than a quarter of its planned provinces in all;
- to an enemy at war with it, nor to one it thinks worse of than -20;
- a province held for the bearer of a charter.

Even when everything fits, a ruler may not want to sell today: a merchant sells readily, a warrior seldom, and it answers the same all day.

### The price

The chunks of the province are multiplied by {cfg:land.provincePricePerChunk} in the currency of the seller, then by the character of the ruler and its relation to the buyer.

| Ruler | Factor |
|---|---|
| merchant | x0.85 |
| generous | x0.9 |
| builder, mage | x1 |
| thrifty, proud | x1.25 |
| warrior | x1.4 |

A relation of 0 and worse adds x1.2, falling to x0.9 at a relation of 50 and better. A province costs at least 1500. With the default settings a province of usual size costs about 3700-8100.

### Where to see the price

- On the atlas, click a province: a card shows its name and number, the price, and the reason when it is not for sale. The footer line on hover says "for sale" with the price.
- With /kingdom province price [id]: without an id, of the province you stand in.

### Buying for a kingdom

- The province must border the land of the kingdom.
- A member with the Treasury and Territory rights buys it.
- Press **"Buy"** on the card of the province or type /kingdom province buy <id>.
- The treasury pays in its own currency by the rates of the day into the reserves of the seller, and the books of both get a line "Land".
- The province is yours at once and does not count against [the limit of chunks](chunk_limit).

### A charter for a player without a kingdom

- Press **"Buy a charter"** or type the same /kingdom province buy <id>.
- The price goes from your account into escrow, in the currency of the seller or another current one, and the province is held for you for 3 days. You hold one charter at a time.
- Set a throne in the province and found a kingdom with a Founder's Crown (see [Founding a kingdom](founding)): the land of this province is no foreign land to it, other land is checked as always. The province is yours at once and the money goes to the seller.
- Without a kingdom founded in 3 days, the money comes back whole.
- A charter on a province that changed hands by war or by treaty is void, and the money comes back as well.

> One more way to a neighbour's province: a treaty of cession in the Diplomacy tab (see [Diplomacy](diplomacy)). A ruler of the world asks the price of a sale for it, twice that where people live, and never gives away its heart.

## wilds | The wilds
@icon minecraft:dead_bush
Two kinds of land are called wild, and both are free to players.

### Land of nobody

Land no kingdom owns, and the wild provinces between the states. No protection of kingdoms holds there. You may found a kingdom of your own there, if foreign land is at least {cfg:kingdom.minChunksFromOtherKingdom} chunks away.

### The wilds of kingdoms of the world

The land of the provinces of a kingdom of the world where it does not live (no claim, building, wall or road of it) is as free to players as land of nobody:

- breaking and placing blocks, opening chests, buckets, fire and entities go without a crime and without a record of damage;
- explosions break and fire burns;
- striking a citizen stays a matter of the law;
- a bed, a workbench or a storehouse of a player in the wilds is no home, post or storehouse of the kingdom;
- a road through the wilds needs no consent and costs no more (see [Roads](roads)).

Where the kingdom lives (its capital, towns, buildings, walls, roads) the old rules and the law hold. The land of kingdoms of players, bought provinces included, is never wild. The server may close the wilds with the key land.wildernessOpen.

### Camps of robbers

Camps stand in the wilds: at least 400 blocks from the claimed land of kingdoms of the world, {cfg:roads.campMinDistance} blocks from the claims of players and 128 blocks from roads and other camps. A world holds up to {cfg:roads.maxCamps} camps (see [Robbers](bandits)).

### The growth of states

The states of the world grow by wild provinces: once in 12 days each may take a wild province next to it, while its land is under 130% of its plan. No province is taken within {cfg:npcKingdoms.spawnZone} blocks of the world spawn, with a claim of a player in it or within 4 chunks of it, or where players spent more than {cfg:npcKingdoms.maxInhabitedMinutes} minutes. At least 35% of the land of provinces always stays neutral.

> A claim of a player in a wild province or within 4 chunks of it keeps a state from taking that province. On the atlas wild land is hatched, and the footer line on hover says "wild land".
