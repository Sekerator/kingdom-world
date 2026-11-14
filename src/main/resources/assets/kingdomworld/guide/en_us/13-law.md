# Law
@icon minecraft:iron_bars

## law | The law of a kingdom
@icon minecraft:writable_book
On its land every kingdom lives by **its own law**: it records crimes, looks for offenders, takes fines and remembers the reputation and the debts of players by itself. The guards of one kingdom do not look for you over deeds done in another.

### What you may do on the land of others

Doors, trapdoors and fence gates open to every player: you can walk into any house and out again. The leaves of city gates follow the rules of the gate (see [Sieges and the ram](sieges)). Breaking and placing blocks on the land of another kingdom in peace is possible: outside its cities it is free and costs nothing, while in a city and at the buildings, walls and roads of the kingdom it is **damaged property** (see [Crimes](crimes)). Always protected are the throne, containers (you may open one, but what you take is a theft, and you cannot break it), beds and working blocks, the land of a kingdom of players who are all offline, and explosions, fire, buckets and pistons across the border. If the server sets protection.peaceBreaking to forbidden, breaking on the land of others is refused altogether, as before version 1.3.

### Whom the law judges

The law judges everyone without the right for the deed, the subjects of the kingdom itself among them: they answer like strangers. It is no crime:

- for the king and the officials of the kingdom (any position but a plain subject) and for its noble players (see [Nobility and factions](nobility));
- for the owner of the thing: a container a player placed on the land of a kingdom is theirs;
- for whoever has the right for it: "Containers" for chests, "Building" for crops, herds and blocks;
- for an enemy in war: blows at and killings of its people are no crime (see [Battles and the shield of the absent](war_fronts));
- for an operator with bypass; machines of mods are not judged, the usual protection of land holds for them.

Companions of players commit no crimes. In the wilds of states of the world, where the kingdom does not live, everything is free except striking citizens (see [The wilds](wilds)).

### Where to see it all

- The key J (changeable in the controls) or the command /kingdom law opens the [window of the law](law_window): your record, reputation, debt and confiscated goods in every kingdom.
- While you stand on the land of a kingdom that wants you, a line above the hotbar says so (see [Wanted](wanted)).
- The throne tab "Law": the court for the king and the chancellor, and your own status for every subject (see [The court](court)).

More in this chapter: [Witnesses](witnesses), [Guards](guards), [The alarm and relief](law_alarm), [Fines and punishment](fines), [Reputation](law_reputation) and [Entering a large city](law_city_entry).

## crimes | Crimes
@icon minecraft:chest

| Crime | Fine | Reputation |
|---|---|---|
| theft | {cfg:law.theftFinePercent}% of worth | {cfg:law.theftReputation} |
| assault on a citizen | {cfg:law.assaultFine} | {cfg:law.assaultReputation} |
| trespass into a city | {cfg:law.trespassFine} | {cfg:law.trespassReputation} |
| damaged property | {cfg:law.vandalismFinePercent}% of worth | {cfg:law.vandalismReputation} |
| murder | death | {cfg:law.murderReputation} |

Fines are counted in coins of the kingdom, and all of them are multiplied by the multiplier of the court (see [The court](court)). The worth of what was stolen or broken is the price of the kingdom's market, or the worth of the item when the market does not sell it. Theft, assault, trespass and damaged property are petty and are forgotten in time; murder is never forgotten (see [Wanted](wanted)). Reputation falls once for every crime.

### Theft

- Taking something from a container of others. From a storehouse of the kingdom (the storehouse block and the chests of warehouse buildings) it is a **theft from the treasury**: its fine is {cfg:law.treasuryFinePercent}% of the fine of a common theft. From the chest of a house it is a theft from the citizen, and the record names them: that chest holds the goods of those who live there, their share of wages and a little food. A chest a player placed belongs to that player.
- Harvesting ripe crops of others: grain and roots at their last stage, nether wart, cocoa.
- Killing, shearing or milking the herds of the kingdom.

Crops and herds need the right "Building", chests the right "Containers". Thefts from one victim within {cfg:law.mergeSeconds} seconds make one crime.

### Assault and murder

An assault is a blow at a citizen of the kingdom (soldiers are citizens too), even when a shield took it. Blows at one victim within {cfg:law.mergeSeconds} seconds count as one assault. A murder is the death of a citizen at the hand of a player, or the killing of a player on the land of the kingdom. A member of the guild who kills a wanted murderer for a contract is no murderer but carries out a sentence (see [Wanted](wanted)).

### Trespass

Getting into a city past its gates (over the wall or through a gap) so as not to pay for the entry or be stopped by the guards is a trespass when somebody saw it. Subjects may go anywhere in their own cities. More in [Entering a large city](law_city_entry).

### Damaged property

The property of a kingdom is every block inside one of its cities (the square of the wall with the wall itself, and without a wall the square a wall would take at the level of the city) and every block of its registered building, wall or road anywhere on its land. A broken block of property drops no item, is written down and comes back for free, 4 blocks a second, once there was no new damage for {cfg:law.restoreQuietSeconds} seconds or the offender left the land. Whatever cannot stand without the block goes with it (a torch, a door, a crop, sand on top) and comes back after it. Such a block cannot be carried away, so "break, wait, break" gives nothing. The line above the hotbar reads "Property of ...: the block will be put back".

A block does not break at all when an item frame or a painting hangs on it, when it holds up a block with data or more than 64 blocks, or while the book of damage of the kingdom is full. An outsider places only simple blocks in a city: no containers, hoppers, dispensers, redstone, rails, pistons or explosives, no minecarts, boats, stands or frames. A placed block stands until the place is put in order, then it is taken out and falls as an item; you may also take your own block back yourself. Blocks broken one after another make one crime.

> A witness is needed for any crime (see [Witnesses](witnesses)), but blocks come back without one too.

## witnesses | Witnesses
@icon minecraft:ender_eye
Without a witness nobody is wanted: if nobody saw it, there is no alarm, and a soldier who sees you later does not put that deed on your record.

### Who is a witness

Witnesses are the citizens and soldiers of the kingdom and its subject players (online and not away) within {cfg:law.witnessRadius} blocks who see the offender in a direct line of sight. Nobody sees through walls. At night citizens notice a theft and other quiet deeds only from half the distance, while soldiers on duty see as by day. A sleeping citizen is no witness: a theft does not wake it, a blow within 8 blocks does, and the one woken sees the offender if they are in sight. The victim is a witness only when it lives and saw who struck it: the dead tell nothing.

A soldier within {cfg:law.storehouseWatchBlocks} blocks of a storehouse notices a theft from it at once, even without seeing the thief. A soldier a player strikes is a witness itself: that is an assault on a guard even if nobody else saw it.

### The report

A soldier reports at once. A citizen is frightened and runs to the guards, and the report arrives after {cfg:law.reportMinSeconds}-{cfg:law.reportMaxSeconds} seconds if any witness is still alive. The line "A witness ran to the guards!" shows only when a witness really ran to a soldier of its kingdom (one within 48 blocks and not behind you). If every witness died first, nobody is wanted. But a soldier who sees the offender itself records at once every report witnesses still carry, even if the witnesses are dead already.

### Fright

A blow at a citizen or a soldier frightens peaceful citizens: the one struck, everybody who sees it within {cfg:law.fearSightRadius} blocks and everybody who hears it within {cfg:law.fearHearRadius} blocks without a line of sight (the noise wakes sleepers). Each one who starts to run frightens the calm ones within {cfg:law.fearSpreadRadius} blocks once. A frightened citizen drops its work and runs from the threat, home or to a soldier when the way there does not lead past the attacker, and keeps running for {cfg:law.fearSeconds} seconds after the last cause. Whoever only heard a blow is frightened but does not know who it was and makes nobody wanted. Soldiers, guards and adventurers do not run, they fight.

> The land of a village or a hamlet of a state counts as a town for the law: witnesses run to the nearest guards and watchmen (see [Living settlements](settlements)).

## guards | Guards
@icon minecraft:shield
**Guards** are soldiers of the army whose squad holds a point, guards an area or patrols, and soldiers guarding the gates of cities (see [Squads and commanders](squads)). A guard looks for the wanted within 16 blocks in a direct line of sight, in the cities of its kingdom and within 48 blocks beyond their wall or edge.

### Calling out and giving up

A guard that sees a wanted player calls out: "Halt! Fine ... Give up!" or "Halt, murderer! Give up!". Right-click the soldier to open the window "Give up":

- for petty crimes the button "Give up and pay ...": the fine is taken from the coins of the kingdom in your inventory (with change), then from the account, and what is missing becomes a debt to the treasury;
- for a murder the button "Accept the sentence": the law asks your life, and all your goods go to the guards.

Who neither gives up within {cfg:law.hailSeconds} seconds nor leaves the land is attacked.

### Punishment by force

For petty crimes the guards beat but do not kill: the blow that would kill leaves {cfg:law.beatenHealth} points of health (in hearts: {cfg:law.beatenHealth/2}) and a weakness for {cfg:law.beatenWeaknessSeconds} seconds, and the fine is taken at once in the same order: coins, account, debt.

For a murder the guards execute. Before anything falls, everything in the inventory and the armor goes into the **confiscated goods** of the kingdom (a record of the law, not a chest in the world), and coins of the kingdom go into its treasury. Jack's Gravestones makes no grave, and the rule keepInventory saves nothing: the sentence does not depend on the rules of death. The goods can be bought back (see [Fines and punishment](fines)).

### Whom the guards leave alone

Guards never touch players who are offline, away from the keyboard or spectators: their record waits for them. A player in creative mode is chased like anyone else (a server setting, on by default), but takes no harm, so no punishment is carried out and the record stays until an operator clears it. The chase of such a player ends when they leave the land or the alarm runs out.

Guards at the gates check whoever comes in: a wanted player is called out and kept out (see [Entering a large city](law_city_entry)). When an alarm is raised, every soldier near runs to the place, and officers and the army follow (see [The alarm and relief](law_alarm)).

## law_alarm | The alarm and relief
@icon minecraft:bell
A blow at a citizen or a soldier and a killing that somebody saw (the victim who lived, a witness, a soldier) raise the **alarm** of the kingdom at once, with the offender and the place: nobody waits for a report. Every soldier of the kingdom with a body in the city of the place and within 16 blocks beyond its wall, or within {cfg:law.alarmRadius} blocks of the place, runs there: guards with an order and soldiers without one from the barracks alike. A theft from a storehouse next to a soldier raises the alarm too, but without the right to strike at once.

The place of the alarm is where a soldier (within 32 blocks) or a citizen last saw the offender. A soldier that does not see the offender goes there and searches. While blows keep falling ({cfg:law.ongoingSeconds} seconds after a blow) and after a killing the guards strike without calling out; for petty matters and for whoever stopped striking they call out first. A wounded soldier does not walk off to be cared for while an alarm lasts close by.

The alarm ends {cfg:law.alarmSeconds} seconds after the offender was last seen, or when the offender died, paid or went further than 48 blocks beyond the wall or the edge of the city. There is no chase across the whole land of a state: the soldiers go back to their posts, and the record stays.

### Relief step by step

| Who comes | When |
|---|---|
| fighters of the guard | at once |
| vice-captain of the guard | {cfg:army.force.viceLosses} out or {cfg:army.force.viceSeconds} s of fight |
| captain of the guard | {cfg:army.force.captainLosses} out or {cfg:army.force.captainSeconds} s of fight |
| the army, {cfg:army.force.armyFirstShare*100}% of fighters | {cfg:army.force.armyLosses} out or {cfg:army.force.armySeconds} s of fight |
| captain of the army, all | {cfg:army.force.allLosses} out |

Out means soldiers killed or wounded. A step also comes sooner when the officer of the step before is out: the captain of the guard comes when its vice-captain is out, the army when the captain of the guard is out, the captain of the army when the vice-captain of the army is out. An officer is out when dead or below {cfg:army.force.officerOutPercent}% of its health. The fight counts from the first blow, so an alarm for a theft alone calls nobody but the guard. The army of a kingdom without a guard answers at once. A step once reached holds until the alarm ends; then everybody goes back to the posts and the barracks. The offender sees a line when the army comes out: nobody takes a capital alone.

The guard and the army are told of in [The army of a kingdom](army).

## fines | Fines and punishment
@icon kingdomworld:coin
There is no prison. For petty crimes the law takes a **fine**: the sum of the fines of every deed on the record, times the multiplier of the court. For a murder the law asks **death**, and if the court spared the murderer, a fine of {cfg:law.murderFine} takes its place (times the multiplier as well).

### How a fine is paid

A fine is taken from the coins of the kingdom {item:kingdomworld:coin} you carry (with change), then from your account in the kingdom's currency, and what is missing becomes a **debt to the treasury**. You pay it by giving up to a guard, or it is taken at once when the guards beat you (see [Guards](guards)). Once it is paid, you are no longer wanted.

A debt is paid back by itself from your income in the kingdom's currency: sales to the market and of lots, rewards of the guild, payments of the treasury, transfers from players (coins handed in at a bank are no income). You can also pay it off with "Pay off" in the [window of the law](law_window): from the account, then with coins. There is no interest, and guards never attack for a debt. But with a debt above {cfg:law.debtLimit} the market of the kingdom sells to you only for coins and takes none of your lots.

### Execution and confiscated goods

Whoever is executed by the guards or a hunter of heads (or accepted the sentence themselves) loses their goods before anything falls: everything in the inventory and the armor goes into the confiscated goods of the kingdom, coins of the kingdom into its treasury. The chat tells what the goods are worth and what buying them back costs.

The goods are bought back whole for {cfg:law.ransomPercent}% of their worth, with coins or from the account. You buy them back at the throne, a town hall of a city or a barracks block (within 8 blocks) of that kingdom, or in its working watchtower or barracks building (and within 4 blocks around it). In the window of the law, view "confiscated", press "Buy back": the window "Buy back goods" opens with the buttons "Pay with coins" and "From the account". Exactly those goods come back, and what does not fit falls at your feet. Goods nobody claimed in {cfg:law.confiscationDays} days go to the storehouses of the kingdom. If the kingdom is gone, the goods are given back for free anywhere (the button "Take back").

> It pays to give up early: every new deed adds to the fine, and a new crime undoes an earlier decision of the court on the fine and on sparing.

## court | The court
@icon kingdomworld:throne
The court is the tab "Law" of the throne screen. It is held by whoever has the right "Court": the king and the chancellor by default (old worlds gave it to the chancellor once). The button "View: ..." switches between three views (the right mouse button goes back); whoever lacks the right sees only their own status.

### Wanted

The list of the wanted: the name (white when the player is online, grey when not) and the punishment, a yellow fine or a red "death for murder". Hover a name to see the record: every crime, the decision of the court, the contract for the head and the reputation. The buttons:

- "Pardon": the record is cleared, the player is told;
- "Fine": set another fine, only a lower one (otherwise "The court may only lower a fine.");
- "Spare" for a murderer: replace the death with a fine; the field holds {cfg:law.murderFine} by default.

Type the amount into the field and press "Set" or "Cancel". A new crime brings the court back to the case: a fine it set no longer holds, and a new murder means death again.

### Laws of the kingdom

| Law | How it is changed |
|---|---|
| Fines | "-" and "+" by 10%, from 10% to 500% |
| Entry into large cities | up to {cfg:law.maxEntryFee}, 0 is free |
| Reward for a head | up to {cfg:law.maxBounty}, 0 is none |
| Slavery | "Forbid" or "Allow" |

The multiplier multiplies the fines of every crime. Only large cities ask an entry fee (see [Entering a large city](law_city_entry)). The reward for a head is {cfg:law.bountyReward} by default: that is what the guild pays from the treasury for every wanted murderer (see [Wanted](wanted)).

**The ban of slavery**: the slaves of the kingdom's subjects go free, and a foreign slave on its land is set free and stays as a free companion of high loyalty; the owner pays {cfg:law.slaveryFine} to the treasury (as a debt when offline). An owner with slaves hears a warning once per approach, {cfg:law.slaveryWarnBlocks} blocks before the border. The slave market of such a kingdom is closed. Slaves are told of in [Companions](companions).

### My status

Every subject sees here their own record, reputation, debt to the treasury and the number of their confiscated goods in this kingdom; the button "My law" opens the [window of the law](law_window). An operator of the server can clear the record of any player too, with the command /kingdomworld admin law pardon followed by the player and the kingdom.

## wanted | Wanted
@icon minecraft:player_head
Each kingdom keeps its own record: the list of your deeds and the fine, or death for a murder. A chat message comes only when you become wanted ("... wants you for ...") and when you are wanted no longer. While you stand on the land of a kingdom that wants you, the line "Wanted: kingdom, fine 12g 5s 3c" (in gold, silver and copper coins of its currency) or "death for murder" shows above the hotbar. The record holds on all the land of the kingdom: in its capital, cities and settlements.

### How it ends

- Petty deeds (theft, assault, trespass, damaged property) are forgotten after {cfg:law.pettyDays} game days; up to twice as fast with a good reputation, up to half again as slow with a bad one.
- A murder is never forgotten.
- The record is cleared when you paid the fine (gave up or were beaten by the guards), when you were executed, when the court pardoned you or when an operator cleared it.

While you are wanted, guards call you out and stop you (see [Guards](guards)), gates keep you out of cities, and players of other kingdoms may strike you even if your kingdoms are at peace: the protection of peaceful players does not cover the wanted.

### Hunters of heads

For every wanted murderer the branch of the guild of the kingdom posts a contract "Head of a criminal" by itself, with a reward from the treasury ({cfg:law.bountyReward} or the reward the court set; 0 turns it off) for {cfg:law.bountyDays} game days, and posts it anew while the murderer is wanted. A member of the guild who kills the wanted player while they are online and at the keyboard does the contract: it is an execution (confiscation, record cleared), not a murder. A pardon or an execution by the guards withdraws the contract, and the stake goes back to the treasury. A hunter and a target in one party of the guild get no reward. From the third head of the same player one hunter collects, the reward is the least of three: the usual reward, the ransom of the goods taken and the coins taken, so the kingdom always gains. Contracts are told of in [Contracts](contracts).

## law_reputation | Reputation
@icon minecraft:emerald
Every player has a **reputation** of their own in every kingdom, from -100 to 100. In the window of the law and in the Law tab a word stands by the number:

| Reputation | Word |
|---|---|
| 50 and above | respected |
| 10 to 49 | liked |
| -9 to 9 | neutral |
| -49 to -10 | distrusted |
| -50 and below | hated |

### How it rises and falls

Every crime lowers the reputation once (by how much is in [Crimes](crimes)). It rises:

- with trade on the market of the kingdom: +1 for every {cfg:law.tradePerPoint} sold and bought;
- with a tax or an entry fee paid in full: +{cfg:law.taxReputation};
- with a guild contract done at the branch of this kingdom: +{cfg:law.contractReputation}.

### What it gives

- The prices of the [market](market) of this kingdom move for you by up to {cfg:law.priceEffectPercent}% either way: with a good name you buy cheaper and sell dearer. The prices of a tavern and the entry fee move the same way.
- From a reputation of {cfg:law.highReputation} the large cities of the kingdom let you in for free.
- Petty deeds are forgotten faster (up to twice), and with a bad reputation slower.
- A ruler of the world may make a player of high reputation in its land a baron (see [Nobility and factions](nobility)).

## law_city_entry | Entering a large city
@icon kingdomworld:town_hall
Guards at the gate of a city wall check everyone who comes in. A wanted player is called out and kept out, as by any guard. A **large city** (see [Cities](cities)) also asks an entry fee when the court has set one (up to {cfg:law.maxEntryFee}).

### Who pays

Subjects and officials of the kingdom, its noble players and players with a reputation of {cfg:law.highReputation} or more enter for free. Everyone else pays the price of the court, a little lower or higher by their reputation. Paid once, you get a **pass** for a game day into every city of this kingdom. Small towns, villages and hamlets ask nothing.

### How to pay

At the gate the window "Entry into the city" opens with the price: pay with "Pay with coins" or "From the account". If "Pay entry myself: on" is set in the window of the law, the fee is taken at the gate at once, without the window. Without paying, the guard politely names the price ("Entry costs ...") and puts you back outside the gate. An entry fee paid in full adds +{cfg:law.taxReputation} to your reputation.

Whoever gets into the city past the gates, over the wall or through a gap, so as not to pay or not to be stopped, commits a **trespass** when seen: a fine of {cfg:law.trespassFine}. Subjects may go anywhere in their own cities. Nobody stands at the gate of a city without a wall.

## law_window | The window of the law
@icon minecraft:book
The window of the law opens with the key J (changeable in the controls), the command /kingdom law or the button "My law" in the throne tab "Law". The button "View: ..." switches it between kingdoms and confiscated goods.

### Kingdoms

A line for every kingdom that has something against you: "not wanted" or your record, "reputation ...", "debt ..." and the pass ("Pass into the cities: ... more" or "No pass into the cities"). Hover a kingdom to see the details. The button "Pay off" pays the debt from the account, then with coins. When nobody has anything against you, it says "No kingdom has anything against you." The bottom shows what your account holds.

### Confiscated goods

Your confiscated goods by kingdom: what was taken, the ransom, the days left and where to buy them back. The button "Buy back" opens the window of the ransom, and you can pay in it only while standing where that kingdom gives goods back. If the kingdom is gone, the button "Take back" gives the goods back for free anywhere (see [Fines and punishment](fines)).

### Paying the entry fee

The switch "Pay entry myself: on" or "off". With "on" the fee at the gate of a large city is taken at once, without the window (see [Entering a large city](law_city_entry)).
