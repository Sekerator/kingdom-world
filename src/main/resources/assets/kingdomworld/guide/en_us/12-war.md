# War and nobility
@icon kingdomworld:battering_ram

## diplomacy | Diplomacy
@icon minecraft:writable_book
Every pair of kingdoms, of players and of the world alike, has a **relation** from -100 to 100. It moves with treaties, the alliance, help in war and help refused, broken treaties (with you and with others: word gets around), declined offers, war and peace, a vassal let go, caught spies, a backed plot, marriages of noble houses and ceded provinces. Every day the relation cools a little, one step back towards zero. A ruler of the world adds its own memory of the neighbour: deals, roads, disputed land.

### Bonds between kingdoms

| Bond | What it means |
|---|---|
| peace | the usual state |
| pact | no war for {cfg:war.pactDays} days |
| alliance | defensive, with a call |
| vassalage | the vassal pays {cfg:war.vassalPermille/10}% of income |
| war | from the declaration to peace |
| truce | {cfg:war.truceDays} days after a war |

While a **pact of non-aggression** holds there is no war between you. A **defensive alliance**: when one is attacked, the other is called to war and decides for itself whether to join (a refusal cools the alliance). A vassal pays its overlord {cfg:war.vassalPermille/10}% of the income of the day before every day (taxes, trade, the law, taverns), declares no wars of its own, and the overlord joins all its wars. A truce can be neither broken nor ended by a war.

### Offers

A treaty is made by an offer, like a deal of trade: one kingdom offers, the other accepts or declines. An offer waits {cfg:war.proposalMinutes} minutes for an answer, then runs out. You can offer a pact, an alliance, vassalage (become a vassal or demand fealty), ask your overlord for freedom, offer peace on terms and ask for the cession of a province (see [War for provinces](province_war)). Those with the right "Diplomacy" decide: the king and the chancellor by default (see [The rights of positions](rights)).

A pact or an alliance can be broken with "Break treaty", but that costs the relation not only with the victim but with everybody who knows you both: the chronicle and the rumors of taverns tell of it. The same button lets an overlord set its vassal free, and the former vassal remembers it kindly. A vassal that wants to leave in peace presses "Ask freedom".

### The Diplomacy tab

The throne tab "Diplomacy" switches its view with the button "Show: ..." (the left mouse button goes forward, the right one back):

- **kingdoms**: every kingdom with the relation and the bond, the main reason on hover; "Actions" opens treaties and war with the chosen one;
- **offers**: offers of others and ours ("Accept", "Decline", "Call off") and calls of allies to war ("Join", "Refuse");
- **wars**: goal, time, score, exhaustion, allies and the buttons of peace (see [Score, exhaustion and peace](war_score));
- **in the world**: sieges, holds, occupied land, war damage and its mending (see [Capture and destruction](capture));
- **chronicle**: declarations, battles, raids, treaties and peace.

The "Actions" of a chosen kingdom are "Offer a pact", "Offer alliance", "Become vassal", "Demand fealty", "Ask freedom", "Break treaty", the goal button and "Declare war" (see [War](war)), the choice of a province and "Ask to cede".

> While all players of your kingdom are away, the tab shows "Shielded: all our players are away". What the shield does is told in [Battles and the shield of the absent](war_fronts).

### Rulers of the world

A ruler of the world makes diplomacy by its character: a merchant looks for pacts and alliances, a builder for pacts, a warrior for alliances with strong friends, and a mage keeps out of it. It answers your offers by character and relation and names the reason of a refusal: it keeps out of such matters, does not trust you, finds you too weak, is too proud, thinks it is winning or finds the terms too heavy. More in [Rulers of the world](rulers).

On the [atlas](atlas) a bar in the colour of the relation lies under the crown of a capital, with a sign of war, alliance, pact, vassalage or truce, and a shielded kingdom has a blue frame.

## war | War
@icon minecraft:iron_sword
War is declared by whoever has the right "Declare war": the king alone by default. In the Diplomacy tab open the kingdom with "Actions", choose the goal with the button "Goal: ..." (the left mouse button goes forward, the right one back, the hint explains the goal) and press "Declare war".

### Goals of war

| Goal | What a full victory gives |
|---|---|
| tribute | the enemy becomes a vassal |
| indemnity | {cfg:war.indemnityPermille/10}% of its treasury |
| raid | loot and a third of an indemnity |
| land | 4 chunks or 2 provinces |
| a city | one city of the enemy |
| the throne | the whole enemy kingdom |

Land, a city and the throne are taken **in the world**: they must be captured and held (see [Sieges and the ram](sieges), [Capture and destruction](capture) and [War for provinces](province_war)). For tribute, an indemnity and a raid a lead in the score is enough. A defender that wins asks for its costs back: half an indemnity.

### Declaration and preparation

A war starts after {cfg:war.preparationMinutes} minutes of preparation (20 minutes are one game day). Both sides are told, and the war shows in the tab, on the atlas and in the chronicle. The vassals of both leading sides and the overlord of the defender join at once. The allies of the defender get a **call** and answer it themselves in the tab, in the view "offers"; a refusal costs the relation. The allies of the attacker are not called.

War cannot be declared:

- during a truce;
- during a pact or an alliance without breaking it: then the button reads "Break and war", the enemy starts the war with 15 points of score, and everybody who knows you both thinks worse of you;
- on your own vassal or overlord; a vassal declares no wars of its own at all;
- on a kingdom of players with nobody online now (the tab says so).

A war lasts {cfg:war.maxWarDays} game days at most, then ends in a white peace. The leading sides make peace; an ally fights beside them but offers no terms. How to gain a lead and end a war is told in [Score, exhaustion and peace](war_score), where and how the fighting goes in [Battles and the shield of the absent](war_fronts).

> Before you declare, remember: in war the enemy may break your city. Registered buildings and walls are mended from the storehouses after the war, while builds outside the register are not mended by themselves.

### Rulers of the world at war

A warrior (unless it is just) declares war on a neighbour at least one and a half times weaker, at most once every {cfg:war.npcWarDays} game days and never on a kingdom that fought in the last {cfg:war.npcRecentWarDays} days. Rulers of the world declare war on a kingdom of players at most once every {cfg:war.npcPlayerWarDays} days per kingdom (unless the server config turns it off) and only for tribute or a raid: for land, cities and thrones rulers of the world fight only one another. A just ruler never attacks first but stands by its allies. The wars of kingdoms of the world go on by themselves and show in the chronicle, the rumors and on the atlas.

## war_score | Score, exhaustion and peace
@icon minecraft:white_banner
Each side of a war has a **score** and **exhaustion**, both from 0 to 100. They show in the Diplomacy tab, view "wars": the line "Score ours : theirs, exhaustion ours : theirs".

### Where score comes from

| For what | Points |
|---|---|
| a battle of squads won | 8 |
| an enemy soldier killed | 1 |
| an enemy player killed | 5 |
| an enemy caravan robbed | 6 |
| an enemy deal blocked | 3 |
| a gate broken | 5 |
| a chunk occupied | 2.5 |
| a province taken | 5, settled 10 |
| a city taken | 10 |
| the throne hall taken | 15 |
| a chest looted | 2, up to 12 in all |

Each place gives score once per war, however often it is taken and lost. An enemy player counts only when they were online and at the keyboard. Whoever broke a pact or an alliance for the war gives the enemy 15 points at the start.

### Exhaustion

Exhaustion grows by {cfg:war.exhaustionPerDay} for every game day of war, faster with an empty treasury, and also for every fighter and every caravan lost. A side worn out to 100 makes peace on the terms of the enemy by itself and pays what it can. A kingdom shielded for its absent players does not tire, and no score grows against it (see [Battles and the shield of the absent](war_fronts)).

### Terms of peace

In the view "wars" a leading side has three buttons: "White peace" (no terms), "Demand" (our terms) and "Give in" (accept the terms of the enemy, always possible). What you may demand depends on your lead in score:

- a lead of {cfg:war.imposeLead} points or more: the goal in full;
- a smaller lead: half the goal. Sums and land are halved, vassalage becomes a tribute of half of {cfg:war.tributePermille/10}% of income for {cfg:war.tributeDays} days, and a city or the throne is not asked;
- no lead: only a white peace.

Where vassalage cannot be (the loser is somebody's vassal already or has vassals of its own, or the winner is a vassal), a full tribute takes its place: {cfg:war.tributePermille/10}% of income for {cfg:war.tributeDays} days.

The terms apply together. The indemnity moves from the treasury of the loser into the reserves of the winner by the books (the line "War: tributes and indemnities", see [The treasury](treasury)), then a truce of {cfg:war.truceDays} days begins, and vassalage, tribute and the claims of land, a city and the throne are carried out in the world. If the loser cannot pay, there is no peace. After {cfg:war.maxWarDays} game days a white peace ends the war, whoever leads.

## war_fronts | Battles and the shield of the absent
@icon minecraft:shield

### Far from players

Where no players are, a war goes on in steps every {cfg:war.stepSeconds} seconds. Squads ordered to hold, guard or patrol a place on the land of the enemy advance: in every step, with a chance of {cfg:war.battleChance*100}%, they meet a squad of the enemy that holds its land or stays home and fight by the rules of the battles of the army (losses, wounds, experience, wear of gear). With no defenders the squad ravages the land.

A squad in the field falls upon a caravan of the enemy with a chance of {cfg:war.raidChance*100}%, as robbers do from an ambush: the guards of the caravan fight, and if they lose, part of the cargo goes to the storehouses of the raiders (what does not fit stays on the road) and the payment for what was taken goes back to the buyer. Deals between enemies are frozen, and a caravan of the enemy does not pass your land: that is a **blockade**, and a blocked delivery is no failure of the deal. Orders are told in [Orders on the map](map_orders), caravans in [Deals and caravans](deals).

### Near players

Soldiers and guards of kingdoms at war strike enemy soldiers and enemy players who are online and at the keyboard. Players of kingdoms at war may fight each other. Players of kingdoms not at war cannot hurt each other (a server setting, on by default; a blow shows "Your kingdoms are at peace"), but that does not protect the wanted. Citizens of the enemy may be struck. Neither the law of the enemy nor your own counts a blow at or the killing of an enemy in war as a crime. When you enter the land of the enemy, the line "Enemy land: ..." shows above the hotbar.

### The shield of the absent

Players who are offline or away from the keyboard (after {cfg:player.afkTimeoutSeconds} seconds without activity) are struck neither by soldiers nor by players: "The player is away: no blows". A kingdom of players with nobody online at the keyboard is **shielded** {cfg:war.absenceMinutes} minutes after the last one left (and no sooner than {cfg:war.fightGraceSeconds} seconds after a fight near its players):

- no battles of squads on its land, its caravans are not robbed;
- no score grows against it, and it does not tire;
- its land is closed and a siege stands still: "Siege stands still: the defenders are away";
- no war can be declared on it, and hostile missions of spies against it wait at its border.

The shield shows on the [atlas](atlas) (a blue frame) and in the Diplomacy tab ("The enemy is shielded while its players are away"). Kingdoms of the world have no such shield: their army keeps them.

## sieges | Sieges and the ram
@icon kingdomworld:battering_ram
While a war goes on (not in preparation and not in a truce), the land of a kingdom is **open** to the players and soldiers of the other side, unless it is shielded for its absent players. The land of third kingdoms and land of nobody are never touched. What can be done on open land is told in [Capture and destruction](capture).

### Gates and their leaves

The gates of city walls get **leaves**: on a palisade leaves of fence gates {item:minecraft:oak_fence_gate}, on a stone wall a portcullis of iron bars {item:minecraft:iron_bars}, four rows in the opening. Builders hang them from the storehouses at the first check of the wall, on walls of old worlds too: 12 fence gates or 48 planks, 12 iron bars or 5 iron ingots. Walls are told of in [Walls and gates](walls).

In war gates close by themselves when an enemy is within 24 blocks, and open before subjects and allies (within 4 blocks, while no enemy is close). An enemy cannot open them, only break them: "The gate is shut against you; break it". Wooden leaves have a strength of {cfg:siege.woodGateStrength}, an iron portcullis {cfg:siege.ironGateStrength}. Broken leaves fall as items, and the gate stands open until the builders hang new ones.

### The order "Siege"

Gates are broken by soldiers. Take the marshal's baton {item:kingdomworld:marshal_baton}, switch its mode to "besiege the city of the enemy" and point it at a city of the enemy within 160 blocks, or give the order "Siege" on the army map (see [Orders on the map](map_orders)). The squad goes to the nearest closed gate, strikes it, and after the breach goes on to the square. The mode "defend our city" sets a squad to defend your own city.

Every soldier at a gate takes 1 of strength a second (six strike at most), every ram 3 more (two at most). Six soldiers with a ram break a palisade gate in 80 seconds and an iron portcullis in 3-4 minutes. Rams count only while soldiers strike the gate. The line above the hotbar shows what is left of the gate: "Siege: gate 40%".

### The ram

{recipe:kingdomworld:battering_ram}

The battering ram {item:kingdomworld:battering_ram} is crafted from two logs of any kind and a block of iron in one row. In the hands of a player it breaks fence gates, iron bars, logs, stone bricks, cobblestone, stone and the stone of city walls 4 times faster. A player holding a ram at a gate while your soldiers strike it counts as a ram of the siege. A ruler of the world builds its ram itself: two logs and nine iron ingots (or a block of iron) from its storehouses.

### Far from players

With no players near, a besieging squad strikes the gate with half its strength, and after the breach holds the square while no squad of the defenders with the order to defend, hold or guard stands there. Near players soldiers of both sides fight as bodies, but one siege has at most {cfg:siege.maxBodies} bodies per kingdom; the rest fight abstractly.

A ruler of the world that wages a war for a city or the throne besieges the nearest city of the enemy (or the capital) once its land is open: it builds a ram and sends half its squads, one siege at a time. A ruler whose city is besieged sends squads to defend it.

## capture | Capture and destruction
@icon minecraft:tnt

### Destruction and loot

On open land of the enemy (see [Sieges and the ram](sieges)) you may break and place blocks and open chests and storehouses: that is **loot of war**, and the law does not judge it. Every container looted gives a little score once. The throne and blocks of the tag kingdomworld:unclearable never break: "This block does not break in war". Explosions (TNT and the like) break blocks on open land unless the server config turns that off; on the land of third kingdoms and under the shield of the absent they break nothing.

### Mending

Blocks of registered buildings and walls broken in war are written down. After the war (or sooner, when no enemy came near for {cfg:siege.repairQuietMinutes} minutes) builders put them back, paying with items from the storehouses. The Diplomacy tab, view "in the world", shows how much is broken, what mending takes and what is missing in the storehouses; a ruler of the world orders what is missing from its workshops. Builds **outside the register** are not mended by themselves: the hint of "Declare war" and the tab warn of it. The register is told of in [The roles of buildings](building_roles).

### Holds

| What is held | Where | How long |
|---|---|---|
| a chunk | any claimed one | {cfg:siege.occupyMinutes} min |
| a city | the square at the town hall, 12 blocks | {cfg:siege.plazaMinutes} min |
| the throne | the throne hall, 8 blocks | {cfg:siege.throneMinutes} min |

A place is held by attackers (players online or soldiers) while no defender is near. Defenders coming interrupt the hold, and the time starts over; defenders free an occupied chunk by holding it as long. While the other side is shielded for its absent players, the hold stands still. The throne can be taken only if the player king of the defenders was online in this war, otherwise you see "No throne yet: the king was not here in this war". The line above the hotbar shows the hold: "Occupying the chunk", "Holding the square" or "Holding the throne hall". Far from players a besieging squad holds the square after the breach while no squad of the defenders is there. The land of states with provinces is held another way: see [War for provinces](province_war).

### What the peace hands over

- **Land**: the chunks occupied by the winner go to it, but no more than the terms (4 by full terms, 2 by halved ones) and never the capital chunk; the rest go back to their holder.
- **A city**: the taken city goes to the winner with the land around it (64 blocks), its buildings, wall and citizens, soldiers excepted; the mood of the citizens falls.
- **The throne**: an annexation. The treasury (at its worth in the currency of the winner), land, cities (the capital becomes a city of the winner), buildings and citizens go to the winner, and the former king loses the crown. Players of the loser get the choice "[Become a subject]" or "[Stay free]" in the chat, the same as the commands /kingdom conquest swear and /kingdom conquest free; they have three days to answer.

With nothing taken, the peace hands nothing over in the world. Everything is handed over by the steps of the journal of conquests, so a restart of the server in the middle neither loses nor doubles anything. On the [atlas](atlas) occupied chunks are hatched, a besieged city has a ring with the share of its gate broken, and a taken one a banner.

## province_war | War for provinces
@icon minecraft:filled_map
The land of [states](states) of the world is split into provinces (see [Provinces](provinces)), and in war it is taken **province by province**, from any kingdom, the bought provinces of a kingdom of players among them. Claimed chunks are still held one by one (see [Capture and destruction](capture)).

### How a province is taken

- A **settled** province (its main settlement stands or waits to rise): hold the square of that settlement for {cfg:siege.plazaMinutes} minutes.
- An **empty** province: hold the boundary stone in its middle chunk for {cfg:siege.occupyMinutes} minutes. The stone is a mark in the data, not a block; the atlas shows it as a sign.

Players hold a province standing in the square or in the chunk of the stone while no defender is near; the line above the hotbar shows "Holding the province". Far from players a squad ordered to hold, guard or besiege within 24 blocks of the place holds it. But first, at most once a minute, it fights by numbers the garrison of the settlement (a guard of strength 25, a militiaman of 12) and the squads of the defender there: the fallen are taken from the guards first, then from the militia. While defenders stand, the hold is interrupted.

A war for land may hold any province except the **heart** (the province of the capital), a war for a city only settled ones. The town of a settlement of a state is held only as the square of its province. A province taken stays taken until the peace, gives score once (a settled one twice as much) and goes into the chronicle and the rumors.

### What the peace hands over

A peace for land hands over the provinces taken, the first taken first: 2 by full terms, 1 by halved ones. A peace for a city hands over one settled province (of the settlement named, or the first taken). Only an annexation takes the heart. The terms of the peace name the provinces one by one. A kingdom of players with bought provinces gives up its chunks and its city by the usual rules beside them.

### The treaty of cession

A province can be asked for without a war too. Open a neighbour in the Diplomacy tab: a button below the actions goes through its provinces that touch your land (never the heart nor one promised by a charter; the name and the price on hover), and "Ask to cede" sends the offer:

- a ruler of the world asks the price it would sell the province for (twice that for a settled one, nothing if it would give it away anyway); your treasury pays when it accepts;
- a kingdom of players gives the province for nothing and answers among its offers.

A ruler never gives up its heart, never gives to one it thinks worse of than -20, and a warrior never gives anything for nothing. The price is paid by the books on the line "Land: provinces", the province is the asker's at once, and what stands in it follows by the journal of conquests. You cannot ask for a province an enemy holds or has taken, one named by the terms of a peace or by the journal, or a province of a kingdom being annexed. A war between the two calls the offer off. Buying provinces for money (at {cfg:land.provincePricePerChunk} a chunk in the currency of the seller) is told in [Buying provinces and charters](buying_land).

### The journal of conquests

The journal hands a province over whole in one event, then the claims of the loser in it (512 a second), the buildings (64 a second), the citizens (16 a second) and the cities and settlements (one a second) with their walls. An annexation hands every province over at once. After a restart the journal finds what is left again, so nothing is lost or doubled.

A ruler of the world at war sends its field army to hold the nearest settled province of the enemy, and in a war for land then the boundary stone of the nearest empty one, one at a time. The defender sends its field army or half of its free squads to relieve it and calls them home at the peace. On the [atlas](atlas) a province being held is marked with a diamond and the share held, a taken one with a banner.

## trophies | Trophies and ransom
@icon minecraft:iron_helmet
A soldier who kills a soldier of another kingdom gathers the gear that fell from that very body (it leaves other things lying around alone) and carries it to a storehouse of its kingdom. If it is killed on the way, the gear falls again and can be won back. Far from players the carrier brings the trophies home by itself. What is brought in is kept as a **trophy** together with the kingdom it belonged to, and those in charge of the army get the message "Our soldiers brought trophies to the storehouse". A player who kills a soldier simply picks up what fell, as usual.

### Buying trophies back

While the war goes on, trophies cannot be bought back. After peace the kingdom they belonged to may buy its gear back for {cfg:army.trophyBuybackPercent}% of its worth within {cfg:army.trophyBuybackDays} game days. Members with the right "Treasury" get a message with the button "[Buy back]".

- **/kingdom trophies**: the list of trophies, ours held by the other side and theirs held by us, with the number, the count of items, the buyback price and the time left;
- **/kingdom trophies buy <number>**: buy back the trophies with that number (the right "Treasury" is needed).

The money goes from treasury to treasury by the books, the gear goes to your storehouses, and what does not fit goes to the player. What is not bought back in time stays with the kingdom that took it and goes to its storehouses.

### Other ransoms

- A caught spy is ransomed for {cfg:nobility.ransom} within {cfg:nobility.ransomDays} days, else it is put to death (see [Intrigue and espionage](intrigue)).
- Goods the guards took at an execution are bought back at the throne, a town hall, a barracks or a watchtower of the kingdom that took them (see [Fines and punishment](fines)).
- After an annexation the players of the beaten kingdom choose whether to become subjects of the winner (see [Capture and destruction](capture)).

## nobility | Nobility and factions
@icon minecraft:golden_helmet
A **noble house** is a family with a name, a head (with a character and a trait of its own), a party, wealth and loyalty to the crown from 0 to 100. Houses have no bodies: heads grow old and die, and the heir takes over. A kingdom of the world has 2-5 houses from its first day: counts on its cities and barons on its marches, and the first house of a large kingdom is a duke. A kingdom of players has no houses until its king grants a title. There are at most {cfg:nobility.maxHouses} houses; a house the crown raised for a title goes away when its last title is taken back.

### Titles and fiefs

The titles are **Baron**, **Count** and **Duke**. A title always comes with a fief: a city of the kingdom or one of its four marches (North, East, South and West march: the land of the kingdom on that side of the capital, outside its cities). Every day the holder gets a share of yesterday's income of the fief, the part that falls to the citizens who live there:

| Title | Share |
|---|---|
| Baron | {cfg:nobility.shareBaron}% |
| Count | {cfg:nobility.shareCount}% |
| Duke | {cfg:nobility.shareDuke}% |

A player gets the share onto their account, a house into its wealth; all of it goes by the treasury journal on the line "Nobility: fiefs, demands and spies".

Titles are granted by the king and those with the right "Nobility" (the chancellor by default, see [The rights of positions](rights)) in the throne tab "Nobility". In the view "Houses" choose the "Title" and the "Fief", then press "Grant" by a house, by the line "A new house" or by a subject. A house given a title grows more loyal. In the view "Titles" a title can be taken back with "Take back" (the house is offended, the others a little too) or handed to another house or subject with "Hand over". A player may hold only one title of one kingdom.

A **noble player** does not answer to the law of their kingdom, enters its large cities for free and gets the share of the fief, but has no rights over the treasury. A ruler of the world makes a player a baron by itself when they dealt well with it and have a high reputation in its land, at most once in 7 days.

### Stability

The stability of a kingdom goes from 0 to 100: a base of 50 plus the causes shown in the Nobility tab, view "Factions":

| Cause | Effect |
|---|---|
| loyalty of the houses | up to ±15 |
| mood of the citizens | up to ±15 |
| taxes | from +5 to -15 |
| weariness of war | down to -20 |
| defeats and lost cities | down to -24 |
| festival | +6 |
| content of the parties | up to ±10 |

A tax of no more than half the usual one pleases, one above the usual angers. For some days events move the stability too: a title granted (+3), taken back (-4), a demand met (+4), a bargain (-3), a refusal (-6), a demand ignored (-3), an execution (-4), mercy (+2), an exile (-3), unrest stirred by foreign spies (-8), rebels (-5), a city that left (-5). Below {cfg:nobility.plotBelow} stability spoils the mood of the citizens (they work worse and arrive more slowly) and breeds plots (see [Intrigue and espionage](intrigue)), from 70 it cheers them. Festivals (see [Festivals and fairs](festivals)) and a moderate tax (see [Taxes](taxes)) raise it.

### Factions and demands

There are four factions: the "War party" wants war, loot and a strong army, the "Merchants" low taxes, peace and open roads, the "Old nobility" titles, land and privileges, the "Crown party" order and works. Houses join them by their character, and the influence of a faction grows with its houses, their titles and their wealth.

At most once in {cfg:nobility.demandEveryDays} played days the least content faction demands: a lower poll tax, a war (a raid on a named enemy), an offer of peace, a title for its house, paid works or a share of the loot (for {cfg:nobility.demandValue}). The king answers with a button:

- "Agree": the demand is carried out and costs money, the faction and the house are pleased;
- "Bargain": the house gets half, and the offence is half too;
- "Refuse": content and stability fall.

A demand not answered in {cfg:nobility.demandDueDays} played days counts as a mild refusal. Rulers of the world answer by their traits and never start a war or make peace because a faction asks.

> A **played day** of a kingdom of players is a game day on which one of its players was online. Demands, plots, rebels and the shares of fiefs move only on such days, so coming back after a long absence you find no catastrophe.

## intrigue | Intrigue and espionage
@icon minecraft:spyglass

### Plots

When stability stays below {cfg:nobility.plotBelow} for two played days in a row, a discontented house (loyalty below 40) starts a **plot** with a chance of {cfg:nobility.plotChance*100}% on each such day. A plot ripens in {cfg:nobility.plotDays} played days; incitement by foreign spies speeds it up, and their kingdom becomes the backer of the plot.

Every day **counter-intelligence** may find the plot out: 15% in any kingdom, 10% more for every spy kept at home to hunt foreign ones, and 15% if a spymaster is appointed, 80% at most. A plot found out shows in the Nobility tab, view "Factions", and the king decides the fate of the house:

- "Execute": the head of the house is put to death and the heir takes the house; the other houses and the faction are displeased;
- "Pardon": the house grows much more loyal;
- "Exile": the house is driven out and its titles go back to the crown.

If another kingdom backed the plot, the tab names it: that is a reason for war.

A ripe plot against a ruler of the world makes the head of the house the new ruler with its own character (the kingdom stays the same). Against a king of players a plot never takes the crown: rebels keep {cfg:nobility.rebelPercent}% of the income for {cfg:nobility.rebelDays} played days. Only after long unrest, with stability low for {cfg:nobility.secessionDays} played days (a warning "Unrest!" comes after {cfg:nobility.secessionWarnDays}), the city of the house leaves the kingdom: to the neighbour who backed the plot by incitement, or as a small kingdom of the world with the head of the house on its throne. The city leaves by the journal of conquests with its land, buildings, citizens and walls, and can be won back by war or diplomacy.

> Festivals, a moderate tax, titles granted, demands answered and peace work against plots.

### Spies

Spies are hired by the spymaster (the right "Secret service", which the king holds too) in the Nobility tab, view "Spies": "Hire" costs {cfg:nobility.spyHire}, the wage is {cfg:nobility.spyWage} a day, and you keep {cfg:nobility.maxSpies} spies at most. A new spy comes with a skill from 30 to 69; a mission done adds 3, a failed one 1, up to 95. Choose the "Task" and the "Target", then press "Send".

| Task | Days | Chance |
|---|---|---|
| scouting | 2 | 70% |
| incitement | 3 | 55% |
| sabotage | 3 | 50% |
| theft | 3 | 40% |
| assassination | 4 | 30% |

The chance is for a skill of 50 against a kingdom without counter-intelligence. Every point of skill above 50 adds 0.6%, the counter-intelligence of the target takes away half of its value, a war with the target 10% more, and a relation of 30 or better adds 5%; the result lies between 5 and 95%.

- **Scouting**: a report of the treasury, soldiers, wars, plans of the ruler, stability and plots; the squads of the target stay visible on the army map for {cfg:army.spyRevealMinutes} minutes (see [The fog of war](fog_of_war)).
- **Incitement**: the stability of the target falls for some days, and a plot brewing there ripens faster with us as its backer.
- **Sabotage**: part of the food in the storehouses of the target spoils.
- **Theft**: {cfg:nobility.theftPercent}% of the foreign treasury, at most {cfg:nobility.theftMax}, by the books.
- **Assassination**: kill the head of a noble house (kingdoms of the world only).

On a failure the spy comes back with nothing or is caught. A caught spy is held for a ransom of {cfg:nobility.ransom} for {cfg:nobility.ransomDays} days: the button "Ransom" pays it into the treasury of the captor, otherwise the spy is put to death, and proud and bold rulers of the world execute at once. A caught spy and a backed plot spoil the relations ("their spy caught", "they backed a plot"). Hostile missions against a kingdom of players with nobody online wait at its border.

At home a spy can be set to "Hunt" foreign spies and plots, sent to "Rest" or let go with "Dismiss". What your spies saw and did shows in the view "Reports". Rulers of the world keep from 0 to {cfg:nobility.npcMaxSpies} spies and send them against enemies and neighbours they dislike.

### Marriages of houses

Houses of two kingdoms on good terms marry with a chance of {cfg:nobility.marriageChance*100}% a day: the loyalty of both houses grows and the relations of the kingdoms improve ("a marriage of houses"). Marriages, deaths of the great, caught spies and hints of plots go into the rumors of taverns (see [Rumors](rumors)).
