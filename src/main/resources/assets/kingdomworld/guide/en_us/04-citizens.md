# Citizens
@icon minecraft:bread

## citizens | Who the citizens are
@icon minecraft:name_tag
**Citizens** are the people of a kingdom: they come by themselves, live in beds, eat from the storehouses, work at posts, go to the tavern, fall ill and die. Every citizen is a record in the world data: name, gender and looks, age in days, health and state (healthy or wounded), profession, home, workplace, satiety, mood and experience. The person you see in the world is only the "body" of that record.

### The body and life far from players

A body appears while a player is within {cfg:npc.citizenActivationChunks} chunks of the citizen's home or workplace and goes away when the players leave. Bodies are never saved with chunks, so relogging, teleports, unloading chunks and restarting the server cannot duplicate them. The whole server has at most {cfg:npc.maxActiveNpcs} bodies at once, those nearest to players first.

Far from players a citizen lives by formulas: every {cfg:simulation.simulationIntervalTicks/1200} min. (a step of the simulation) it gets hungry and eats, heals, changes its mood and works. So a kingdom lives on and gathers goods while you are away.

### Name, age and experience

Names are built from syllables: a player with the Russian language reads them in Cyrillic, everybody else in Latin (Radomir). A newcomer arrives grown up, 20 to 40 days old. Experience grows with work and stays with the citizen even when it is dismissed or given another profession. Experience lowers the risk of accidents at work (by up to a half at 1000 experience) and makes workshops, builders and minters faster (up to twice).

### Where to look

The **Citizens** tab of the throne window (see [The throne and its window](throne)): on top "Citizens: N, beds taken: N of N" and "Food for N days, storehouses: N, mood: N", below it the reason newcomers do not arrive (see [Arrival and growth](growth)). The **Show** button picks all, the wounded or the homeless, the **View** button switches the list: citizens (profession, state, home), work (post and why it stands still), production (what each profession gathered and made today and yesterday), chronicle (deaths) and life (see [The day of a citizen](daily_life)). Whoever has the **Citizens** right (see [The rights of positions](rights)) changes the profession of a citizen with a button in its row. The [Workers window](workers_window) shows work in the most detail.

### Citizens and land

Every living citizen adds {cfg:territory.chunksPerCitizen} chunks to the kingdom's limit of land (see [The limit of chunks](chunk_limit)). A blow at a citizen and its murder are not refused but judged by the law of the kingdom as assault and murder (see [Crimes](crimes)). Kingdoms of the world have citizens too (see [Living settlements](settlements)).

## growth | Arrival and growth
@icon minecraft:red_bed
New citizens are not bought or bred: they come to the throne by themselves. The check runs once per step of the simulation, and a newcomer arrives when every condition holds:

1. **A free bed** on the kingdom's land.
2. **Food:** the storehouses feed everybody, the newcomer included, for {cfg:citizens.growthFoodDays} days (see [Food](food)).
3. **Time:** {cfg:citizens.growthIntervalMinutes} min. have passed since the last arrival. That is at an average mood of 50; content citizens (100) shorten it to three quarters, miserable ones (0) make it up to twice as long.
4. **The limit:** the kingdom has fewer than {cfg:citizens.maxCitizensPerKingdom} citizens. Besides, the whole world holds at most {cfg:npcKingdoms.maxNamedCitizens} named citizens.

The newcomer appears at the throne, at once takes the nearest free bed (a house first) and comes **without work**: you give it a profession (see [Professions](professions)). The king and the members with the Citizens right are told "A new citizen, ..., has come to ...".

### Which beds will do

A home is a bed on the kingdom's land, one per citizen. While the kingdom has no working house at all, any bed on its land will do: that is how a new kingdom gets its first citizens. Once there are houses, newcomers live only in beds of buildings: a house, a barracks, a mage tower, a building without a role (see [The roles of buildings](building_roles)). A free bed in a house also brings newcomers a quarter sooner. The beds of a tavern are rooms for guests, and a bed a player sleeps in as the point of return is nobody's home: a citizen living there goes to find another. A broken bed, or a bed on land given up, frees its resident, who looks for a new one.

### Why nobody comes

The Citizens tab of the throne window names the reason:

- "A newcomer will arrive soon": all is ready;
- "Newcomers need a free bed on the kingdom's land";
- "Newcomers need more food in the storehouses";
- "A newcomer arrived recently; the next comes later";
- "The kingdom has as many citizens as it may".

> To start, place a storehouse with bread and a couple of beds near the throne. Every citizen eats, so the stock of food needed grows with the population.

## food | Food
@icon minecraft:bread
Citizens eat from the [storehouses](storehouses) of the kingdom, the nearest first. Food is any item with food properties, so the food of other mods works by itself. Only harmful food (with a bad effect: {item:minecraft:rotten_flesh} rotten flesh, raw chicken, spider eye) and food that leaves a container ({item:minecraft:honey_bottle} honey bottle) is never eaten. Food a cook has set aside for cooking is not eaten raw.

### How much they eat

A citizen's satiety goes from 0 to 20. In a game day a citizen eats {cfg:citizens.dailyNutrition} food points ({item:minecraft:bread} bread gives 5 points). When satiety falls below 14 it eats its fill. Ten citizens need {cfg:citizens.dailyNutrition*10} points a day.

The stock is counted in days: all food points in the storehouses divided by the number of citizens and the daily need. It shows in the Citizens tab of the throne window ("Food for N days") and in the [Workers window](workers_window). If the need is 0 in the server config, citizens need no food.

### Hunger

- Satiety below 6: the citizen is hungry, its mood drops by 20, it falls ill more often, and far from players it does not heal.
- Satiety 0: the mood is 35 lower and health falls by a point a minute. By default hunger does not kill: health stops at one point (the server setting starvationCanKill). With it on, a citizen may starve to death.

### Where food comes from

Food is brought by the [farmer](farmer), the [fisher](fisher) and the [herder](herder), and a [cook](workshops) cooks the raw kind. In the evening at an open tavern a citizen eats or drinks one item from the storehouses as its supper (see [The tavern](tavern)). A citizen takes part of its wage home and buys a little food for the chest of its house (see [Wages](wages)).

> Keep an eye on "Food for N days": without a stock for {cfg:citizens.growthFoodDays} days no newcomers arrive, and hunger spoils the mood fast.

## mood | Mood
@icon minecraft:cake
A citizen's mood goes from 0 to 100, 50 being usual. Every step of the simulation it moves by at most 5 towards a "target" made of the circumstances:

| What | Mood |
|---|---|
| Fed (satiety 14 or more) | +15 |
| Hungry (below 6) | -20 |
| No food at all | -35 |
| Has a home | +10 |
| No home | -15 |
| Wounded | -5 |
| Death of a neighbour | -5, to -20 |
| No wage | down to -20 |
| Double wage | up to +10 |
| Comfort of home, a well | up to +25 |
| Tavern, festival, illness | -25...+25 |
| Stability of the kingdom | -25...+25 |

Notes:

- a death of a neighbour counts for three days, -5 each, no more than -20 in all;
- the wage is measured against the usual one: an unpaid wage lowers the mood, a generous one raises it (see [Wages](wages));
- a house gives +5 by default (more at a higher level of the building), a working well within 24 blocks of the home another +4 (see [The roles of buildings](building_roles));
- an evening at the tavern gives +{cfg:life.tavernMood} for {cfg:life.tavernMoodDays} days, a festival +{cfg:life.festivalMood} for {cfg:life.festivalMoodDays} days, an illness -{cfg:life.sickMood} (see [The tavern](tavern), [Festivals and fairs](festivals));
- unrest and rebels lower the mood, a steady crown raises it (see [Nobility and factions](nobility)).

### What it changes

- **Growth:** at an average mood of 100 newcomers arrive in three quarters of the usual time, at 0 in twice the time (see [Arrival and growth](growth)).
- **Work:** a content citizen works up to {cfg:economy.moodWorkEffect*100}% faster, a miserable one as much slower.
- **Stability:** the mood of the citizens is one of the causes of the stability of a kingdom (see [Nobility and factions](nobility)).

The average mood shows in the Citizens tab of the throne window, the mood of each citizen in the [Workers window](workers_window) (the ☺ sign).

> The cheapest mood is food and a home. A citizen with no food at all and no home loses 50 points of its target, a fed one with a home gets +25.

## daily_life | The day of a citizen
@icon minecraft:clock
Citizens have a day. The times are given in ticks of the day (0 is six in the morning):

| Part of the day | From tick |
|---|---|
| Morning | {cfg:life.wakeUp} |
| Work | {cfg:life.workStart} |
| Evening | {cfg:life.eveningStart} |
| Sleep | {cfg:life.sleepStart} |

By default that is getting up at about 5 in the morning, work from 7 to 17, the evening until half past eight, then sleep.

- **Morning:** the citizen leaves home and walks to work.
- **Day:** work. Gathering posts work only in the morning and by day, but a lumberjack finishes a tree it began at dusk as well.
- **Evening:** about {cfg:life.tavernShare}% of the citizens (different ones every evening) go to a tavern of their city no further than 96 blocks from home, the others walk near home. A tavern with supper gives mood (see [The tavern](tavern)). Without a tavern the mood does not drop; there is just no bonus.
- **Night:** sleep in its own bed, lying down.

Every citizen keeps its day up to {cfg:life.spreadTicks} ticks earlier or later than the others, so a city does not get up and lie down in step.

### Exceptions

Soldiers and adventurers on duty do not sleep, and the innkeeper works in the evening too. On the day of a festival citizens stop working at noon and gather at the throne or the town hall of their city (see [Festivals and fairs](festivals)). The wounded and the ill go to an infirmary or home (see [Health, the infirmary and death](health)).

### Far from players

The day is reckoned without bodies too: an evening at an open tavern gives mood, a night's sleep at home heals a little. In the [Workers window](workers_window) what a citizen far from players is doing comes from its day and its abstract work: "Sleeps", "Rests", "Goes to work", "Fells trees" and so on.

### Sleep, fright and the law

A sleeping citizen is no witness: a theft does not wake it, a blow closer than 8 blocks does, and the citizen who wakes sees the offender if it is in plain sight (see [Witnesses](witnesses)). A citizen who sees or hears a blow at a citizen or a soldier takes fright and runs for {cfg:law.fearSeconds} s after the last blow (see [The law of a kingdom](law)).

In the Citizens tab of the throne window the "life" view shows how many citizens sleep, work, are at the tavern and rest now, the day by the hour, the taverns and their guests tonight, the festivals, the fairs and the ill.

## health | Health, the infirmary and death
@icon kingdomworld:infirmary
A citizen has 20 points of health (soldiers have more). Below {cfg:citizens.woundedHealthPercent}% of its health a citizen is **wounded**: it stops working (the state "Being treated") and goes to an infirmary with room, or home if there is none. Healed above the threshold, it goes back to work.

### The infirmary

The **infirmary** {item:kingdomworld:infirmary} is a block placed on the kingdom's land. One infirmary treats {cfg:citizens.infirmaryCapacity} citizens at once, the others wait at home. The wounded and the ill share the places. The catalog of blueprints also has a ready infirmary building (see [Blueprints and the catalog](blueprints)).

{recipe:kingdomworld:infirmary}

How fast they heal:

- near players: a point of health every 10 s, every 5 s asleep, every 2 s at an infirmary;
- far from players: a point a minute, four times as much in an infirmary, half a point a minute more asleep at home at night;
- far from players a hungry citizen (satiety below 6) does not heal at all;
- the kingdom spell "Healing" gives the wounded in an infirmary {cfg:magic.healingPerSecond} health a second more (see [Spells](spells)).

### Illness

A healthy citizen falls ill with a chance of {cfg:life.sickChance} per mille a day, {cfg:life.sickFactor} times as often for each of three: hungry, homeless, wounded. An ill citizen works at {cfg:life.sickWorkPercent}% of its pace, its mood is {cfg:life.sickMood} lower, it goes to an infirmary like the wounded, and it gets well in {cfg:life.infirmaryDays} days in an infirmary or {cfg:life.homeDays} days at home. Nobody dies of an illness and nobody catches it from another. The "life" view of the Citizens tab shows who is ill, where it is treated and how far it has recovered.

### Death

Citizens are mortal. They are killed by monsters (zombies and illagers hunt citizens), players, falls, lava, fire, water, hunger (if the server allows it), and far from players by accidents at work (see [Work far from players](work_far)), battle and guild expeditions. Death is final:

- the record goes to the chronicle of the kingdom: who, when, the cause and where (the Citizens tab, the "chronicle" view). A line of the chronicle is the name, the cause and the day; the profession, the place and the whole text of the line show on hover;
- the king and the members with the Citizens right are told the same cause, for example "Citizen ... died: killed by a monster (Zombie)";
- the tools and armor of the dead go back to a storehouse;
- for three days every death spoils the neighbours' mood by 5 (no more than 20 for all of them);
- the bed and the seat at the post are free again.

Causes in the chronicle:

- **who killed:** a player by name; a monster, an animal, a golem or another creature with its kind and its own name if it has one ("killed by a monster (Skeleton)", "killed by an animal (Wolf "Rex")", "killed by a golem (Iron Golem)"); a citizen or a soldier of a kingdom by the name of the kingdom ("killed by a soldier of ..."); robbers;
- **what killed:** fell to death, burned, fell into lava, drowned, starved, suffocated in a block, died in an explosion, was pricked to death by a cactus or a berry bush, was killed by magic, withered away, froze to death, was struck by lightning, was squashed by a falling block or anvil, fell out of the world and more. Damage the chronicle has no words of its own for (the damage of other mods, say) is told by the death message of the game itself: "... was killed by ...";
- **far from players:** died in the mine, was killed felling trees, drowned while fishing, was killed tending the herd, fell from a building going up, died at work, fell on an expedition, fell in battle, was killed by robbers.

Entries of earlier versions stay as they were: they know no kind of creature and no kind of damage.

> Armor from the storehouses and experience lower the risk at work, and an infirmary brings the wounded back quickly: place it near the houses.

## professions | Professions
@icon minecraft:iron_axe
The profession decides where and how a citizen works. A newcomer arrives **unemployed**. Most professions work at a [work post](work_posts), a farmer at a composter as well, while an adventurer and soldiers need no post.

| Profession | Works at |
|---|---|
| Unemployed | nowhere |
| [Farmer](farmer) | composter or post |
| [Lumberjack](lumberjack) | post in a wood |
| [Miner](miner) | post, mine below |
| [Fisher](fisher) | post by water |
| [Herder](herder) | post by a pen |
| [Carrier](carrier) | post |
| [Smelter](workshops) | post by furnaces |
| [Cook](workshops) | post by furnaces |
| [Crafter](workshops) | post by a table |
| [Smith](workshops) | post by a table |
| [Minter](mint) | post by a mint |
| [Merchant](market) | post by stalls |
| [Registrar](guild) | post by a guild desk |
| [Builder](builders) | post near sites |
| [Mage](mage_tower) | post by a focus |
| [Innkeeper](tavern) | post in a tavern |
| [Adventurer](guild) | no place |
| [Warrior, archer](soldiers) | the army |

### How to give a profession

1. **At a post:** the Hire… button in the window of the post. The citizen takes the profession of the post (see [The window of a post and hiring](post_window)).
2. **In the Workers window:** Assign… puts the chosen citizen on a post with a free seat or gives it a profession without a post (see [The Workers window](workers_window)).
3. **At the throne:** in the Citizens tab, the profession button in the row of a citizen (needs the Citizens right). A left click goes forward, a right click back.
4. **Soldiers** are recruited and dismissed only in the Army tab (see [Warriors and archers](soldiers)).

### A profession without a post

A citizen with a post profession but no post (made a lumberjack at the throne, say, or whose post was broken) takes the nearest post of its profession with a free seat by itself, checked once per step of the simulation. A farmer takes whichever is nearer: a free composter or a farmer's post. Until there is a seat the citizen's state is "No workplace", and the Workers window lists it "without a post".

A dismissed citizen is unemployed and keeps its experience. A tool its new work does not need goes back to a storehouse.

## workers_window | The Workers window
@icon minecraft:spyglass
The Workers window shows what every citizen is doing, who lacks what and every post of the kingdom, and moves people between posts without walking to them.

### How to open it

- the **Workers…** button in the Citizens tab of the throne window;
- the command **/kingdom workers**, typed in the chat from anywhere;
- the key "Window of the workers": unbound by default, set it in the controls settings (see [Keys and windows](keys));
- **a right click on a citizen** of your kingdom: for a citizen with a post the window of its post opens with all its settings, as a click on the post itself opens it (see [The window of a post and hiring](post_window)); for a citizen without a post (without work, of a profession without a post, an adventurer, a soldier) this window opens with the citizen chosen in the list, to put it on a post or dismiss it at once. An innkeeper at the counter still opens the tavern, and **Shift + right click** only names the citizen, as before.

It needs the **Citizens** right; without it the window says "This needs the right Citizens". Without that right, and for the citizens of others, a click only names the citizen.

### The summary

On top: "Working: N · without work: N · without a post: N · in the army: N", then what is lacking by kind ("An axe needed by 3 workers", "A stronger pickaxe needed by 1 worker", "A home needed by 2 citizens") and for how many days the storehouses hold food. When nothing is lacking the window says so. The whole summary shows on hover.

### The Citizens tab

Filters: **Where** (all, the capital, a city, outside the towns), **Work** (all, no profession or one profession) and **Problems only**. Citizens with problems always come first. A row shows:

- the name and the profession;
- the post and how far it is ("Lumberjack 3, 25 m"), "composter", "without a post" or "needs no post";
- what the citizen is doing: works (fells trees, digs the mine, fishes...), carries to the storehouse, gets ready for work, goes to work, sleeps, at the tavern, at the festival, rests, being healed, ill, idle, serves in the army; for work that stands still its reason, for example "No tool in the storehouses";
- "Lacks: ...": a tool (axe, pickaxe, fishing rod, shears, weapon) or a stronger one, saplings, feed, fuel, materials, a workstation, a storehouse, room in the storehouses, a post, care, food, a home;
- health ♥ and mood ☺.

Near players what a citizen does comes from what its body is doing now, far from them from its day and its abstract work.

The buttons under the list act on the chosen citizen (a double click does what the first button does):

- **Dismiss**: the citizen is without work, its seat at the post is free, its experience stays;
- **Assign…**: the window "Where to assign" with the posts that have a free seat (profession, place, distance, free seats) and a row "No post:" with the professions that need no post (an adventurer, a farmer at a composter of its own). The **Assign here** button moves the citizen at once, from another post in one step.

Soldiers are listed, but they are assigned in the Army tab.

### The Posts tab

On top "Posts: N · without a profession: N · free seats: N". A row of a post shows its name (by default its profession and number: "Lumberjack 3", "Empty post 2"), its profession, seats taken and in all, where it is and how far, its state and what lies in its container (the two largest kinds and "and N more"; "The container is empty", "No container", and when the post is far and the server cannot see it, "Container: too far to look into"). The same filters, posts where work stands still first.

- **Settings** opens the window of the post from afar with all its settings: profession, radius, seats, animals, the mode of a workshop, the goal of a miner, the metals of a minter and the workers. **Back** returns to the Workers window.
- **Put on…** chooses a citizen for a free seat: first those without work and those of the profession without a post; a worker from other work is moved once you confirm with **Move**.
- **Clear** leaves every worker of the post without work.
- **Name…** gives the post a name of its own of up to 32 characters; an empty name brings back the default one.

An empty post without a profession is listed too: give it a profession through Settings and people through Put on….

The lists come from the server a page at a time: turn them with the mouse wheel or the arrows.

> Start with the Problems only filter: the summary on top tells at once how many axes, pickaxes and beds are lacking.
