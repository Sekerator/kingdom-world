# Work posts
@icon kingdomworld:work_post

## work_posts | The work post
@icon kingdomworld:work_post
The **work post** {item:kingdomworld:work_post} is a workplace of citizens. Nearly every profession works at posts: gathering (lumberjack, miner, fisher, herder, farmer, carrier), the workshops, the minter, the merchant, the registrar, the builder, the mage and the innkeeper (see [Professions](professions)).

{recipe:kingdomworld:work_post}

Crafting: planks at the sides and along the bottom, a sign in the middle of the top row, a crafting table in the centre.

### Where to place it

A post works only on the kingdom's land. Placed outside it, it does nothing, and a right click says "A work post works only on the land of a kingdom". The kingdom finds a new post by itself and gives it a zone with a radius of {cfg:work.workPostDefaultRadius} blocks (less if the land around it is smaller), and as soon as a profession is chosen it looks at what the zone holds.

### Settings

- **Profession.** A new post has none ("None chosen").
- **Zone radius.** The zone is a square around the post: at a radius of 10 it is 21 by 21 blocks. The radius goes up to {cfg:work.workPostMaxRadius}, and such a zone never reaches beyond the kingdom's land. A lumberjack has a zone of its own: {cfg:work.lumberjackDefaultRadius} by default, up to {cfg:work.lumberjackMaxRadius}, and it may reach beyond the land (see [Lumberjack](lumberjack)). A miner needs no radius; it has a depth instead (see [Miner](miner)).
- **Seats.** From 1 to {cfg:work.workPostMaxSeats} workers. A post inside a working market gets more seats, and in a city during its fair {cfg:life.fairSeats} more (see [The market](market), [Festivals and fairs](festivals)).

All of this is changed in the [window of the post](post_window).

### The container of a post

The container is any block next to the post with an inventory hoppers can see: a {item:minecraft:chest} chest, a {item:minecraft:barrel} barrel, crates and storage of other mods. Workers put their harvest there, and [carriers](carrier) take it to the storehouses and bring the post what it asks for (torches, saplings, inputs of workshops). Without a container a worker carries its harvest to the nearest storehouse within 64 blocks itself, and far from players such a post yields less (see [Work far from players](work_far)). The window of the post says "Container: next to the post" or "No container: workers carry it to a storehouse".

### When a post goes away

The workers of a broken post, of a post on land given up or of a post something else took the place of are without work again. They keep their experience, and a tool unfit for their new work goes back to a storehouse. A new post in the same place takes anybody at once.

> Place the post right by a chest and near a storehouse: workers walk less, and carriers have something to carry.

## post_window | The window of a post and hiring
@icon minecraft:oak_sign
A right click on a post opens its window; one with the Citizens right opens it with a right click on a worker of the post as well (see [Workers window](workers_window)). Every member of the kingdom may look; only those with the **Citizens** right (see [The rights of positions](rights)) change it, and no further than 10 blocks from the post. With that right a post is changed from afar too, with the Settings button of the [Workers window](workers_window); everybody else is told "Come closer to the post". To an outsider a post only names its kingdom.

### What the window shows

The title "Work post at x y z" or, for a post with a name, "Post “Lumberjack 3”: x y z". Below it:

- **Profession:** the button that chooses the profession;
- **Zone radius:** buttons - and + around the value "N of M";
- **Seats:** the same;
- for a herder **Herd size** and **Animals** (see [Herder](herder));
- for a miner **Depth**, **Goal** and **Needed** (see [Miner](miner));
- for a smelter and a cook **Mode** (see [Workshops](workshops)), for a minter **Metal** and **Reserve** (see [The mint](mint)).

Under the settings come the lines: "Status: ..." (see [The states of a post](post_states)), whether there is a container, "Yield: about N a day per worker" marked "measured" or "estimate" (or "not known yet") and "Gathered today: N". At the bottom the list "Workers: N of M": the name, the state and a **Dismiss** button (the dismissed citizen is without work). The list scrolls with the mouse wheel.

### Choice buttons go both ways

Buttons that go through choices (profession, animals, ore, metal) take the next choice on a left click and the one before on a right click: a profession passed by mistake no longer costs a whole round. The tooltip of such a button ends with the line "Right click: back". Every such button of the mod works the same way: the view of a tab, filters, the profession of a citizen, the position, the priority, the coin, the rank and others.

### Hiring

The **Hire… (N)** button opens the list "Whom to put on the post". The number N is the citizens who take a seat without leaving other work. The order of the list:

1. citizens of this profession without a post (made lumberjacks at the throne, say): "Lumberjack without a post";
2. those without work: "Without work";
3. other workers: "Moves over from another post" or "Leaves other work (now: ...)".

In every group the nearest come first. The **Put on the post** button hires the chosen one. For a worker from other work the window first asks "Move the worker?", and after **Move** it comes here at once with the profession of the post. Soldiers and those who already work here are not listed. Hire… cannot be pressed while the post has no profession ("Choose the profession of the post first") or every seat is taken ("Every seat is taken: add seats or dismiss somebody").

### What changes do

- A new profession of the post: workers of another profession leave the post but keep their profession and look for a free post of it by themselves. The radius follows the new profession: a lumberjack's is at least its default, the others' never beyond their land.
- Fewer seats: those who came last leave.
- The server checks every change (rights, distance, limits) and answers with a line at the bottom of the window.

## post_states | The states of a post
@icon minecraft:compass
The "Status" line of the window of a post tells whether the post works and why it stands still. The state of a post comes from the states of its workers: if even one of them works, the post is "Working", otherwise it shows why work stands still (real problems first). Green means work, orange a problem you can fix, yellow everything else. The state of each worker shows in the list below, and the "Problems only" filter of the [Workers window](workers_window) gathers every post that stands still.

### Common ones

- **Working.**
- **Resting:** night; work goes on in the morning.
- **Being treated:** the worker is wounded (see [Health, the infirmary and death](health)).
- **No work:** the citizen has no profession.
- **No workplace:** the profession needs a post and no post of it is free.
- **No profession chosen:** give the post a profession.
- **No workers:** hire somebody.
- **The post is outside the land:** the land under the post is no longer yours.
- **Part of the zone is outside the land:** work goes on in the rest of the zone.
- **No tool in the storehouses:** put an axe, a pickaxe or a fishing rod in a storehouse (see [Tools and armor](work_gear)).
- **The tool is too weak:** what is left in the zone needs a stronger tool (an ore that needs an iron pickaxe, say).
- **Nowhere to deliver: no storehouse:** the post has no container and there is no storehouse within 64 blocks.
- **The storehouses are full:** the container of the post and the storehouses are full.
- **Waiting for the zone to recover:** saplings are growing, the herd is at its size, or the zone has not been looked over yet.
- **The work is not going well:** something unusual, such as the profession of a mod that was removed.

### Gathering

- **No trees in the zone** and **No saplings to plant** for a [lumberjack](lumberjack).
- **Nothing to dig** and **The mine is worked out** for a [miner](miner).
- **No water in the zone** for a [fisher](fisher).
- **No animals in the pen** and **No feed in the storehouses** for a [herder](herder).
- **No crops** for a [farmer](farmer).
- **Nothing to carry** for a [carrier](carrier): everything is carried, no trouble.

### Workshops and other posts

- **No workstation in the zone**, **Not enough materials**, **No fuel in the storehouses**, **No orders** for workshops (see [Workshops](workshops), [The king's orders](orders)).
- **Nothing to strike: the metals are off or kept in reserve** for a minter (see [The mint](mint)).
- **No construction within reach**, **Not enough materials**, **A container with items is in the way**, **Cannot reach a block** for a builder (see [Builders and materials](builders)).

## work_gear | Tools and armor
@icon minecraft:iron_pickaxe
Workers take tools and armor from the kingdom's storehouses by themselves. A tool is known by what it can do, so axes, pickaxes, fishing rods and shears of other mods serve as well as vanilla ones.

| Profession | Tool |
|---|---|
| Lumberjack | axe, required |
| Miner | pickaxe, required |
| Fisher | fishing rod, required |
| Herder | shears and a weapon, if any |
| Builder | pickaxe, if any |

A herder without a weapon culls bare handed, without shears it just does not shear. A builder with a pickaxe takes stone apart so that it drops. Farmers, carriers and workshops work without a tool.

### Which tool is taken

Of the suitable ones a worker takes the weakest that is still strong enough for what the zone holds, and of equal ones the most worn: good tools stay for work that needs them, and old ones are used up one after another. The worker holds the tool in its hand, the tool wears out, and a broken one is replaced by the next from a storehouse. Without a required tool the post stands still: "No tool in the storehouses". If the storehouses hold only a weak pickaxe and the ore of the mine needs a stronger one, the post says "The tool is too weak". The Workers window adds up what is lacking: "An axe needed by 3 workers".

### Armor

Lumberjacks, miners, fishers and herders put on armor from the storehouses. Far from players armor lowers the risk of accidents by up to 75%: 20 armor points give full protection, a full iron set gives 15 (see [Work far from players](work_far)).

### Gear belongs to the citizen

Tools and armor belong to the citizen rather than to its body: far from players the tool wears out all the same, and after death the gear goes back to a storehouse. A citizen that changes its work hands in a tool its new work does not need.

> Keep a couple of spare axes and pickaxes in a storehouse: a worker takes a new one by itself when the old one breaks.

## work_far | Work far from players
@icon minecraft:clock
Near players workers really fell, dig and fish. When the players leave, the bodies go away and the harvest is reckoned by formulas once per step of the simulation ({cfg:simulation.simulationIntervalTicks/1200} min.).

### Measurement and estimate

While players watch the work of a post it is measured: what and how much was gathered in the time worked, how much feed and how many uses of the tool it took. A measurement counts after 2 minutes of watched work and is averaged with the earlier ones. Far from players workers gather at that yield. Before the first measurement a post works by an estimate of its zone (trees, rock, water, animals). The window of the post says "Yield: about N a day per worker (measured)" or "(estimate)". A working day is half a game day.

Estimates before a measurement:

- a miner opens up to 240 blocks a day along the plan of its mine, and what counts is what really lies at that depth;
- a fisher makes about 24 catches a day by the fishing loot table;
- a lumberjack: a tree is 5 logs on average, and the grove never runs out (see [The lumberjack's grove](lumberjack_grove));
- a herder culls about half of the herd in 2 days.

The harvest goes into the container of the post if it is loaded, otherwise into the storehouses, and the tool wears out. A worker without a container at its post carries its harvest to a storehouse itself and far from players gathers three quarters. A content worker works faster, a miserable one slower (by up to {cfg:economy.moodWorkEffect*100}%), an ill one at {cfg:life.sickWorkPercent}% of its pace.

### Record of production

A kingdom remembers what each profession gathered and made today and yesterday. It shows in the Citizens tab of the throne window, the "production" view.

### Risks

Near players the dangers are real: monsters, falls, lava, water. Far from players a worker has, per minute of work, a chance to be wounded and a smaller chance to die by profession (the defaults, the list professionRisks of the server config):

| Profession | Wound | Death |
|---|---|---|
| Miner | 0.4% | 0.04% |
| Lumberjack | 0.3% | 0.02% |
| Herder | 0.15% | 0.01% |
| Fisher | 0.1% | 0.01% |
| Builder | 0.1% | 0.005% |
| Farmer, carrier | 0.05% | none |
| Smelter | 0.05% | none |
| Smith | 0.04% | none |
| Cook | 0.03% | none |
| Crafter | 0.02% | none |

Every risk is multiplied by {cfg:work.taskRiskMultiplier} (0 turns them off). Armor lowers the risk by up to 75%, experience by up to 50% (fully at 1000 experience), and the two multiply. A wounded worker does not work until healed, a dead one goes to the chronicle with its cause ("died in the mine", "was killed felling trees", "drowned while fishing", "was killed tending the herd") and the king is told (see [Health, the infirmary and death](health)).

## lumberjack | Lumberjack
@icon minecraft:iron_axe
The **lumberjack** fells trees in the zone of its post and plants new ones, so its forest never runs out. It needs an {item:minecraft:iron_axe} axe (any) and puts on armor from the storehouses.

### The zone

A lumberjack has a zone of its own: the radius is {cfg:work.lumberjackDefaultRadius} by default, and the radius button goes up to {cfg:work.lumberjackMaxRadius}. The zone may reach beyond the kingdom's land, but the lumberjack fells and plants only on the kingdom's chunks. Lumberjack posts of older worlds with a smaller radius get the default radius once.

### What it fells

Only natural trees: logs (tag minecraft:logs) standing on the ground with leaves that grew there (tag minecraft:leaves, not placed by a player). Logs without such leaves count as a building and are left alone, so wooden houses in the zone are safe. A tree of more than {cfg:work.maxTreeBlocks} logs or one reaching out of the zone or the land stays standing. Only the tree's own crown comes down: leaves nearer to the trunk of a neighbouring tree stay with the neighbour. Trees of other mods with the same tags just work.

### Planting again

The drops are gathered, and a sapling of the same kind is planted at once where every felled tree stood: the kind comes from the loot table of the leaves, or failing that from their name. Where a giant 2x2 tree stood, four saplings go in if there are four. Saplings that grow only four together (tag kingdomworld:saplings_in_fours, the {item:minecraft:dark_oak_sapling} dark oak by default) are never planted alone: any sapling that takes on that ground goes in instead.

The lumberjack takes saplings from its bag, the container of its post or a storehouse. Spare ones stay in the container (up to 32, the rest go to a storehouse), and when it holds fewer than 8 the post asks [carriers](carrier) to bring saplings of its kind. With nothing to plant the post says "No saplings to plant".

### Small things

- A tree it began to fell it finishes at dusk too, and one it had to leave (the players went away, it was wounded) it finishes when it comes back.
- Besides planting again it plants new trees on free ground of the zone (see [The lumberjack's grove](lumberjack_grove)).
- "No trees in the zone": nothing to fell and nowhere to plant. While saplings grow, the post is "Waiting for the zone to recover".

> Put a chest with a dozen saplings by the post and keep a few in a storehouse: the lumberjack replants the forest itself and brings logs for ever.

## lumberjack_grove | The lumberjack's grove
@icon minecraft:oak_sapling
A lumberjack's grove is not only planted again, it spreads. On free natural ground of the zone the lumberjack plants new {item:minecraft:oak_sapling} saplings.

### Where it plants

- on grass, earth, podzol and the like under the open sky;
- on the kingdom's land, outside the square of every city;
- no closer than 2 blocks to buildings, roads, paths, fields, farmland and other blocks of people;
- on a sparse grid every 5 blocks from the post, no closer than 4 blocks to a tree or a sapling.

In one round it plants up to 4 new saplings, until the zone holds one tree or sapling for every 25 blocks of its ground. It never touches a sapling and fells a tree that has grown.

### Far from players

The grove does not run out in the reckoning either: the yield of the post is never below what a grove planted again gives. Every tree, sapling and free spot for a new sapling gives a tree every day and a half, but no more than 16 trees a day for one lumberjack. Saplings planted do not count as the harvest of the post.

> Want a clearing without trees? Lay a path there or put up a fence: the lumberjack does not plant next to buildings and roads.

## miner | Miner
@icon minecraft:iron_pickaxe
The **miner** digs a mine at its post. It needs an {item:minecraft:iron_pickaxe} pickaxe, puts on armor from the storehouses and takes {item:minecraft:torch} torches for the tunnels from a storehouse or the container of its post. A miner can be given a goal: what to dig, how much and at what depth.

### The goal of the mine

In the window of a miner's post there are three settings in place of the radius (changed by whoever may change the post):

- **Goal:** "Anything" or one kind of ore. The kinds are the tags forge:ores of the running game, so the ores of other mods come by themselves, each with the icon and the name of the first block of its tag. The button goes forward on a left click and back on a right click.
- **Needed:** how much of it to keep in the storehouses. Buttons - and + in steps of 16, with Shift 64; "∞" means no limit. What counts is what the ore drops under an iron pickaxe without enchantments: {item:minecraft:raw_iron} raw iron, a {item:minecraft:diamond} diamond, {item:minecraft:coal} coal.
- **Depth:** "auto: Y" or "Y N". A click on the value switches between auto and a height set by hand, buttons - and + change it by 1, with Shift by 8. The depth is never lower than the bottom of the world + 6 and never higher than the post (a height above the post means the level of the post).

### The "auto" depth

| Ore | Height Y |
|---|---|
| Coal | 96 |
| Copper | 48 |
| Iron | 16 |
| Lapis | 0 |
| Gold | -16 |
| Redstone, diamonds | -58 |
| Emeralds | 232 |
| Other kinds | 16 |

This is the list minerOreDepths of the server config. The height of emeralds is above nearly any post, so a mine for emeralds at the automatic depth runs at the level of the post. "Anything" starts at the height of 16 (or at the level of the post if it stands lower) and goes 16 blocks deeper with every level (see [The mine and veins](miner_mine)).

### When the goal is met

The line "In the storehouses: 23 of 64" shows the stock. Once it is there the window says "Goal met: Iron Ore 64/64": the miner keeps digging the same mine and takes ore as it comes. As soon as the stock falls below the amount, it takes its own ore first again. Another ore or depth starts a new mine, and the old tunnels stay as they are.

### States

- "No tool in the storehouses": no pickaxe.
- "The tool is too weak": the ore needs a stronger pickaxe and stays in the wall.
- "Nothing to dig": the plan of the mine has no block that can be taken.
- "The mine is worked out": everything is dug; set a new goal or depth.

The kingdoms of the world set no goals (they dig "Anything") but have the same mines and veins. The posts of older worlds dig "Anything" at the automatic depth without a limit.

## miner_mine | The mine and veins
@icon minecraft:cobblestone
### How a mine is laid out

1. **Staircase.** A spiral staircase goes down from the post inside a square of 3x3 blocks beside it (one block per step, three blocks high), so a deep mine never leaves the land.
2. **Corridor.** At the working depth a main corridor of 1x2 runs from the staircase in both directions, up to {cfg:work.minerCorridorLength} blocks each way.
3. **Branches.** Branches leave the corridor every three blocks, up to {cfg:work.minerBranchLength} blocks to each side. Two blocks of rock lie between neighbouring branches, so every block of the layer is seen.
4. **Levels.** When a level is dug, the staircase goes on down inside the same square, up to {cfg:work.mineLevels} levels in all.

The axis of the corridor is chosen by itself: the one along which more of the corridor lies on the kingdom's own land (x when both are the same). Only what lies under the chunks of the kingdom is dug: the corridor and the branches stop at the edge of the land, and the radius of the zone of the post does not matter to a mine.

### Levels

- For a kind of ore and for a depth set by hand the levels are neighbouring layers: the next one lies 4 blocks lower (higher at the bottom of the world).
- "Anything" at the automatic depth goes for the depths: 16, 0, -16, -32, -48, then the bottom of the world + 6, so in time the miner reaches redstone and diamonds.
- **A barren level** is not dug to the end: once the miner has opened {cfg:work.minerBarrenBlocks} blocks in its tunnels one after another without ore, it leaves the rest and goes down to the next one (the steps of the staircase do not count, 0 turns the rule off). It comes back to the levels it left when there is nothing else to dig.
- When everything is dug, the post says "The mine is worked out".

### Veins

Every time it opens a block the miner looks at what has shown in the walls, the floor and the ceiling. Ore (tag forge:ores) it takes, then the ore touching the ore it took, and so on up to {cfg:work.minerVeinBlocks} blocks per vein (0 turns veins off). The ore of its goal comes first, but all of it is taken. Only ore: it leaves the rock around alone and does not see ore behind rock. The holes ore leaves in the floor it fills with cobblestone, so that a tunnel has no pits.

### What it breaks and what it fears

- It breaks only natural rock, earth, gravel, sand and ores (tags forge:stone, forge:ores and the like, the stones of mods included), never blocks with contents and nothing off the kingdom's land.
- Lava and water next to a tunnel it walls off with {item:minecraft:cobblestone} cobblestone before opening the block.
- Every 5 cells where it is dark it places a torch. When the container of the post holds fewer than 8 torches, the post asks [carriers](carrier) to bring them from a storehouse.

### Far from players

The estimate of the yield looks at the cells of the plan at the working depth, at the blocks around them and at the veins, so a post asked for diamonds at -58 yields what that layer really holds.

> A mine brings up a lot of stone: put a large chest by the post and give it a carrier, or the miner climbs to a storehouse with every bag.

## fisher | Fisher
@icon minecraft:fishing_rod
The **fisher** fishes with a rod in the water of its zone. It needs a {item:minecraft:fishing_rod} fishing rod (any that can be cast) and puts on armor from the storehouses.

### Where it fishes

The fisher looks in its zone for still water (a source with air above it) with a bank it can stand on, on the kingdom's land. A pond, a lake or a river by the post will do. With no such water the post says "No water in the zone".

### What it catches

The catch comes from the vanilla fishing loot table: {item:minecraft:cod} cod, salmon and other fish, and now and then junk. Fish that mods add to that table are caught too. The luck enchantment of the rod counts. The rod wears out with every catch. After 8 catches the fisher takes them to the container of the post or a storehouse.

### Far from players

Before the first measurement a fisher makes about 24 catches a working day. The risk is small, but far from players a fisher may drown ("drowned while fishing", see [Work far from players](work_far)).

> Fish feed citizens without fields: a fisher's post by a pond with a chest beside it soon gives a stock of food (see [Food](food)).

## herder | Herder
@icon minecraft:lead
The **herder** keeps a herd of one kind in its pen: it breeds it up to the size asked for, culls the extra ones and shears.

### Settings of the post

- **Animals:** a kind from those living in the zone of the post on the kingdom's land, mobs of other mods included (up to 16 kinds, the most numerous first). Until a kind is chosen, the herder takes the most numerous one.
- **Herd size:** from 2 to {cfg:work.maxHerdSize}, {cfg:work.maxHerdSize/2} for a new post.

### What it does

- **Breeds** the herd with food that kind eats (taken from a storehouse) while the herd is smaller than asked. Without feed the post says "No feed in the storehouses".
- **Culls** grown animals beyond the size of the herd with any weapon or bare handed, and gathers the drops (only its own, items of players nearby stay where they are).
- **Never touches** young animals, named ones, those on a lead, those ridden and anybody's pets. Tamed animals are not part of the herd at all.
- **Shears** what can be shorn (sheep and the like) when it has {item:minecraft:shears} shears.

A herder puts on armor from the storehouses. With no animals of the chosen kind in the zone the post says "No animals in the pen", and when the herd is at its size and there is nothing to do, "Waiting for the zone to recover".

### Far from players

Before the first measurement a herder culls about half of the herd in two days. The risk is higher than a fisher's: "was killed tending the herd".

> Fence a pen in the zone of the post and drive at least two animals in: the herder breeds the herd from there. An animal with a {item:minecraft:name_tag} name is never culled.

## farmer | Farmer
@icon minecraft:wheat
The **farmer** brings in the harvest and replants the crops. It works at a composter or at a work post.

### Where it works

- A **composter** {item:minecraft:composter} on the kingdom's land: the workplace of one farmer. The farmer tends the field within {cfg:citizens.farmRadius} blocks around it and 2 rows above and below.
- A **work post** with the profession Farmer: several seats, the field in the zone of the post.

A farmer given the profession without a post takes the nearest free composter or farmer's post by itself.

{recipe:minecraft:composter}

### What it does

Near players the farmer harvests ripe crops, replants them with seeds from the harvest and carries the harvest to the nearest storehouse (within 64 blocks) or the container of the post. Crops of other mods work too (ordinary crops, or the tag minecraft:crops with an age property), and they are replanted with their own seeds.

- From melons and pumpkins it picks the fruit and leaves the stem.
- Two block crops it harvests by their lower half.
- Sugar cane it cuts above its lowest block.

It does not till or sow new plots: you plant the field, and the farmer keeps it going. It works only by day and only on its own land. It needs no tool. With no crops in the zone the state is "No crops".

### Far from players

The farmer brings in a harvest by formula from the number of plots it saw last time: every plot ripens about once in two days. The kingdom spell "Fertility" makes crops grow faster within {cfg:magic.fertilityRadius} blocks around composters and herder posts (see [Spells](spells)).

> Till and sow a field around the composter: one farmer with a full field feeds many citizens (see [Food](food)).

## carrier | Carrier
@icon minecraft:chest
The **carrier** moves goods between posts and storehouses. It needs no tool; it serves the posts within {cfg:work.carrierRange} blocks of its own and the storehouses of its city (outside the cities any on the land of the kingdom). The goods of a post go to a storehouse of the profession of that post, then to a common one (see [Storehouses by profession](storehouses)).

### Three kinds of trips

1. **Collecting:** from the containers of posts to the storehouses, the fullest first. This is the main work.
2. **Supplying:** from a storehouse to the container of a post that asks for something: torches for a miner, saplings for a lumberjack, inputs for the orders of workshops (see [Workshops](workshops)).
3. **Unloading:** from a storehouse more than 90% full to one less than half full that stands at least 24 blocks away; only between common storehouses.

### States

- "Nothing to carry": everything is carried, no trouble.
- "Nowhere to deliver: no storehouse" and "The storehouses are full": place a storehouse or make room (see [The storehouse](storehouses)).

### Far from players

The carriers of a kingdom together move {cfg:work.carrierItemsPerMinute} items a minute each out of the loaded containers of posts into the storehouses. A carrier has the smallest risk of all.

> Without a carrier the containers of posts fill up, and workers without a container spend their time on the road. One carrier near a group of posts soon pays for itself.
