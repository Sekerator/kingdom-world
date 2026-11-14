# The army
@icon kingdomworld:marshal_baton

## army | The army of a kingdom
@icon minecraft:iron_sword
The army of a kingdom is made of **soldiers**: ordinary citizens with the profession Warrior or Archer. They eat, sleep and heal like every citizen, earn a wage, take their gear from the storehouses themselves, train at a barracks, serve in squads and carry out orders. Besides soldiers, a kingdom may hire [mercenaries](army_mercenaries).

### Who commands

The army is run by members of the kingdom with the Army right (in the table of rights: "Army: recruiting, squads, orders, mercenaries"). By default the king and the marshal hold it (see [The rights of positions](rights)). Every member sees the **Army** tab of the [throne](throne) window, but without the right it is view only. Orders are given in three ways:

- in the Army tab: orders in place, where you stand (see [Squads and commanders](squads));
- with the marshal's baton: points, patrol routes, areas;
- on the map of the atlas with the Army button (see [Orders on the map](map_orders)).

### The Army tab

The View button goes through the views (a left click forward, a right click back):

- **overview**: soldiers and mercenaries out of the limit, the number of squads, the strength of the army, what the army costs a day (wages of soldiers and pay of mercenaries), the barracks and how many soldiers they train at a time;
- **soldiers**: every soldier with its service, weapon, armor and where it is now (At home, Training, Under treatment, On the road with a caravan, or coordinates); the tooltip shows rank, experience, enemies put down and health. Service and Dismiss buttons, and marks to form a squad;
- **recruiting**: citizens who can be recruited (the unemployed first), with Warrior and Archer buttons;
- **squads**: squads with their strength, commander and order, and an Order button;
- **mercenaries**: the offers of the day and the bands hired (see [Mercenaries](army_mercenaries));
- **guards of deals**: the deals of the kingdom whose caravans can get guards (see [Guarding caravans](army_escort)).

### Limits

| What | How many |
|---|---|
| Soldiers in a kingdom | {cfg:army.maxSoldiers} |
| Squads (mercenaries too) | {cfg:army.maxSquads} |
| Soldiers in one squad | {cfg:army.maxSquadSize} |
| Mercenaries | {cfg:army.maxMercenaries} |

The server sets the limits. Kingdoms of the world keep larger armies than these.

### The guard and the army

Every soldier serves in one of two branches. The **city guard** answers an alarm of a city at once; the **royal army** is stronger and stands in reserve until the guard is hard pressed (see [Squads and commanders](squads) and [Alarms and relief](army_relief)). Guards catch offenders by the law (see [Guards](guards)). In a war both branches fight alike (see [War](war)).

## soldiers | Warriors and archers
@icon minecraft:bow
A **warrior** fights in melee; an **archer** shoots a bow or a crossbow, weapons of mods included. Only the Army tab gives and takes these professions: the Citizens tab cannot choose them, and the Workers window marks a soldier as "serves in the army".

### Recruiting

- The Army tab, recruiting view: the Warrior or Archer button next to a citizen. The list holds peaceful citizens, the unemployed first.
- The window of a [barracks](barracks): the buttons Recruit a warrior and Recruit an archer take the nearest healthy unemployed citizen.

A recruit at once takes the best weapons and armor of the storehouses (see [The gear of soldiers](army_gear)) and becomes a fighter of the **city guard**. A kingdom keeps no more than {cfg:army.maxSoldiers} soldiers.

### Dismissing

The Dismiss button in the soldiers view: the soldier leaves its squad and the army and is an unemployed citizen again, and its gear goes back to the storehouses.

### Ranks and experience

The rank grows with experience of war (it is not the post of a captain of the guard or the army):

| Rank | Experience |
|---|---|
| Recruit | 0 |
| Private | 100 |
| Corporal | 300 |
| Sergeant | 700 |
| Lieutenant | 1500 |
| Captain | 3000 |

Experience comes from training at a barracks (only up to {cfg:army.trainingCap}), from monsters killed (5 experience, and 5 more for every rank of the monster above F) and from battles far from players ({cfg:army.battleExperience} experience, twice for a won one). Beyond the cap of training only battles raise it. The higher the rank, the stronger the soldier in the reckoning of battles (see [Battles far from players](army_battles)).

### Wage and life

The usual daily wage of warriors and archers is {cfg:army.soldierWage}, unless the king set another in the list of wages (see [Wages](wages)). It is paid with the other wages, and soldiers measure it like other citizens: underpay spoils their mood (see [Mood](mood)). Food, homes, illness and the infirmary are the same for soldiers as for citizens.

### Strength

A guard is tougher than a citizen: 30 health against 20. A fighter of the army is stronger still: 40 health, blows 1 harder and 2 points of armor of its own. Officers are much stronger (see [Squads and commanders](squads)). A soldier with a shield wards off {cfg:army.force.shieldBlockChance*100}% of the blows and arrows from the front; an axe breaks through the shield and lowers it for 5 seconds, as for a player.

### Near players and far from them

Near players soldiers walk as bodies: a warrior strikes up close, an archer keeps its distance and shoots arrows nobody can pick up. They strike monsters that attacked them; monsters that attacked citizens or players of the kingdom on its land (within {cfg:army.alarmRadius} blocks); monsters at the place of their order and near the player their squad follows. In peacetime soldiers never touch players or citizens, and in a war they still leave alone players away from the keyboard or offline. A wounded soldier goes to the infirmary; a fallen one goes into the chronicle. Far from players soldiers without orders train at a barracks, and their squads fight by formula.

## army_gear | The gear of soldiers
@icon minecraft:iron_chestplate
Soldiers take their gear from the [storehouses](storehouses) of the kingdom themselves and swap it for better gear as soon as some is there.

- **Melee weapons** are chosen by damage per second (the attributes of damage and attack speed), so weapons of mods such as Simply Swords and Better Combat count by their own attributes.
- An archer's **bow or crossbow** is chosen by its kind and enchantments. An archer without a bow fights with whatever it finds.
- **Armor** is chosen by its protection and half of its toughness in its slot.
- A warrior takes a **shield**.
- Firearms of mods that do not work like a bow count only by their melee damage, so they are seldom taken.

Weapons and armor wear in battle; a soldier replaces what breaks from the storehouse. To keep the stock up, give the smith an order to keep it in stock (see [The king's orders](orders)). A dismissed soldier brings its gear back to the storehouses. A new officer at once takes the armor of its post from the storehouses (see [Squads and commanders](squads)).

The server can change how items are scored or forbid them by id and tag (the setting gearScores) and add ranged weapons of mods (the setting rangedWeapons). What a soldier took is written to the server log.

### Trophies

A soldier who kills a soldier of another kingdom gathers the gear the fallen dropped (it leaves other things nearby alone) and carries it to the storehouse of its kingdom; if it is killed on the way, the gear drops again and can be won back. What it brings is kept as a trophy the former owner may buy back after peace (see [Trophies and ransom](trophies)). A player who kills a soldier picks up the gear as usual.

> Keep swords, bows, arrows and armor in stock: a recruit without gear fights with bare hands and is much weaker.

## barracks | The barracks
@icon kingdomworld:barracks
A **barracks** {item:kingdomworld:barracks} is the block where soldiers without orders train.

{recipe:kingdomworld:barracks}

Place a barracks on the land of the kingdom: it belongs to that kingdom at once. A kingdom may have several. The first barracks of a kingdom is also the home of its army: squads ordered back to the barracks go there (without barracks, to the capital).

### Training

- Near players a soldier without orders walks to the nearest barracks of its kingdom and drills with its weapon; far from players it trains by formula.
- Every minute of training gives {cfg:army.trainingPerMinute} experience of war, until the experience reaches {cfg:army.trainingCap}. Beyond that only battles raise it.
- Each barracks trains up to {cfg:army.barracksCapacity} soldiers at a time. A building with the Barracks role and a barracks block inside adds more places: by default 4 for a building of the first level and 4 more for each further level (see [The roles of buildings](building_roles)).
- Without barracks soldiers without orders do not train, and the Army tab says so.
- The wounded do not train.

### The window of a barracks

Use a barracks to open its window, if you are a member of the kingdom whose land it stands on. The window shows the soldiers out of the limit, the barracks and places to train, up to which rank and how fast training goes, who trains now (with experience) and the nearest unemployed citizen. A member with the Army right recruits that citizen with the buttons Recruit a warrior and Recruit an archer; without the right the window says the Army right is needed. Orders are given in the Army tab of the throne.

> Put the barracks near the homes of your soldiers: near players they walk there on foot and train only while they stand by it.

## squads | Squads and commanders
@icon minecraft:shield
### Forming a squad

In the soldiers view of the Army tab mark soldiers without a squad (the "[ ]" button of the row) and press **Form a squad (N)**. The first one marked is the commander. A squad holds at most {cfg:army.maxSquadSize} soldiers, and a kingdom keeps at most {cfg:army.maxSquads} squads, bands of mercenaries included. A soldier serves in one squad only.

### Orders in place

In the squads view the **Order** button of a squad opens a list:

- Hold the point where I stand: a garrison, the squad stands there and fights the monsters around;
- Guard the land around me: 3x3 chunks around you;
- Follow me: the squad follows you and fights what threatens you;
- No order: train at the barracks;
- Disband the squad: soldiers stay in the army, while a band of mercenaries leaves the service.

The squad gets the order at once; soldiers without bodies move to the place right away, bodies walk there. Points and patrol routes come from the marshal's baton or the map (see [Orders on the map](map_orders)). A squad on the road with a caravan cannot get an order or be disbanded. Squads that hold a point, guard an area or patrol serve as guards (see [Guards](guards)).

### The guard and the army

A recruit serves in the city guard. The **Service** button of a soldier's row opens a choice: Move to the royal army or Move to the city guard (an officer leaves its post when moved), Appoint vice-captain, Appoint captain, Take the post away.

### Officers

Each branch has at most one vice-captain and one captain; a new appointee takes the place of the one before, who serves as a fighter again. A post counts only while the officer wears **armor of its post**: all four pieces, each enchanted (when the server demands enchantments), diamond or better for a vice-captain, netherite or better for a captain. The toughness of each piece counts, so armor of mods goes by its own numbers. A new officer takes such armor from the storehouses at once; without it, it is no stronger than a fighter.

| Post | Health |
|---|---|
| Guard | 30 |
| Vice-captain of the guard | 80 |
| Captain of the guard | 120 |
| Army fighter | 40 |
| Vice-captain of the army | 110 |
| Captain of the army | 160 |

Officers resist knockback well. When its post or armor changes, a soldier keeps its share of health: the wounded stay just as wounded. A vice-captain earns {cfg:army.force.viceWagePercent}% and a captain {cfg:army.force.captainWagePercent}% of the wage of a soldier.

A player in full enchanted diamond armor with an enchanted diamond sword can beat a vice-captain of the guard but loses to a captain of the guard; in full enchanted netherite armor with such a sword the player can beat a captain of the guard. The officers of the army cannot be beaten alone: bring allies, potions or cunning.

### Multipliers

For packs with strong weapons of mods the server multiplies the health and damage of soldiers (the numbers of the table above are without them):

| Multiplier | Health | Damage |
|---|---|---|
| Guard | x{cfg:army.force.guardHealthMultiplier} | x{cfg:army.force.guardDamageMultiplier} |
| Army | x{cfg:army.force.armyHealthMultiplier} | x{cfg:army.force.armyDamageMultiplier} |
| Officers, on top | x{cfg:army.force.officerHealthMultiplier} | x{cfg:army.force.officerDamageMultiplier} |

## army_relief | Alarms and relief
@icon minecraft:bell
When an alarm goes up in a city (a blow at a citizen or a soldier, a killing; see [Guards](guards)), the soldiers of the kingdom answer **step by step**:

1. The fighters of the guard come at once.
2. The vice-captain of the guard comes when the soldiers out of the fight (killed or wounded) reach {cfg:army.force.viceLosses}, or the fight lasts {cfg:army.force.viceSeconds} s.
3. The captain of the guard: {cfg:army.force.captainLosses} out, the vice-captain of the guard out, or {cfg:army.force.captainSeconds} s.
4. The royal army with its vice-captain and {cfg:army.force.armyFirstShare*100}% of its fighters (those nearest the place): {cfg:army.force.armyLosses} out, the captain of the guard out, or {cfg:army.force.armySeconds} s.
5. The captain of the army with everybody left: {cfg:army.force.allLosses} out, or the vice-captain of the army out.

An officer is out when it is dead or below {cfg:army.force.officerOutPercent}% of its health. The fight counts from the first blow: an alarm over a theft alone calls only the guard. In a kingdom without a guard the army answers at once. The step reached holds until the alarm ends; then everybody goes back to their posts and the barracks. When the army comes out, the offender sees the line "The royal army of ... is coming out against you!". A soldier who is struck answers by itself at any step. When bodies run short, the soldiers an alarm calls get theirs before the others.

In a war and a siege there are no steps: officers fight alongside everybody (see [Sieges and the ram](sieges)).

### Monsters on the land of the kingdom

When a monster attacks a citizen or a player of the kingdom on its land, soldiers within {cfg:army.alarmRadius} blocks come to help.

### Armies of the kingdoms of the world

[Kingdoms of the world](world_kingdoms) keep their armies themselves. They issue soldiers kits by branch and post and pay for them from the treasury: the worth of every piece, and {cfg:army.force.kitEnchantPercent}% of it more for every level of enchantment. By default:

- guard: iron, Protection {cfg:army.force.guardKit#3}, iron sword, Sharpness {cfg:army.force.guardKit#5}, shield;
- vice-captain of the guard: diamond, Protection {cfg:army.force.guardViceKit#3}, diamond sword, Sharpness {cfg:army.force.guardViceKit#5};
- captain of the guard: netherite, Protection {cfg:army.force.guardCaptainKit#3}, netherite sword, Sharpness {cfg:army.force.guardCaptainKit#5};
- army fighter: iron with a diamond chestplate, Protection {cfg:army.force.armyKit#3}, iron sword, Sharpness {cfg:army.force.armyKit#5}, and for an archer a bow with Power {cfg:army.force.armyKit#6};
- vice-captain of the army: diamond, Protection {cfg:army.force.armyViceKit#3}, Sharpness {cfg:army.force.armyViceKit#5};
- captain of the army: netherite, Protection {cfg:army.force.armyCaptainKit#3}, Sharpness {cfg:army.force.armyCaptainKit#5}.

Without money for a kit the post stays vacant. The issued gear of an officer never drops on death and never goes back to a storehouse. A kingdom of the world replaces the fallen a few at a time from the unemployed (a day: {cfg:army.force.replacePerDay}). The post of a fallen officer stays vacant for some game days ({cfg:army.force.officerReplaceDays}); then the vice-captain rises to captain and the best fighter of the branch takes the free post. A branch with fewer than {cfg:army.force.viceMinBranch} soldiers has no vice-captain, with fewer than {cfg:army.force.captainMinBranch} no captain. In a kingdom of players, officers are appointed by a member with the Army right.

## army_mercenaries | Mercenaries
@icon kingdomworld:coin
Every game day a kingdom is offered bands of mercenaries (offers a day: {cfg:army.mercenaryOffersPerDay}). A band has {cfg:army.minBand} to {cfg:army.maxBand} fighters with their own gear and rank. Its daily price follows its strength: {cfg:army.mercenaryPricePerPower} copper coins for every point of strength, rounded up and at least one copper coin.

### Hiring

The mercenaries view of the Army tab shows the offers of the day and the bands hired: captain, fighters, rank, strength, price a day and, for those hired, the days paid. The **Hire** button takes an advance for some days ({cfg:army.mercenaryAdvanceDays}) from the treasury at once; after that the treasury pays the price every day. When the treasury does not pay, the band leaves the service with a message. The **Release** button lets a hired band go. A kingdom keeps no more than {cfg:army.maxMercenaries} mercenaries, and every hired band takes a place among the kingdom's squads.

### How mercenaries differ from soldiers

- They are not citizens: they need no food, homes or mood.
- They have no bodies: they obey orders like a squad but fight far from players, by formula.
- Fallen mercenaries never come back, and their gear never drops into the world.
- The money paid to mercenaries leaves the world (the Army line of the treasury journal, see [The treasury](treasury)).
- The offers change every day.

> Mercenaries are good for strengthening the army quickly before a war or for guarding caravans while your own soldiers train.

## army_escort | Guarding caravans
@icon minecraft:chest
A squad can be set to guard a deal with another kingdom (see [Deals and caravans](deals)): it goes with every caravan of that deal and comes back with it.

1. In the Army tab choose the view guards of deals: it lists the deals of the kingdom with the goods, the partner, the guards and the chance to lose a caravan.
2. The **Guards** button of a deal opens the list of squads; the **Assign** button sets a squad to it. The No guards button takes the guards away.
3. While a caravan is on the road the guards cannot change, and a squad on the road gets no orders and cannot be disbanded.

On the map the order Guard a caravan does the same: click a caravan of your kingdom (see [Orders on the map](map_orders)).

Far from players the guards go with the caravan without bodies; near players the soldiers walk beside the merchant and its llamas. Caravans are attacked by robbers of the camps along the road (see [Robbers](bandits)): the guards fight them by the reckoning of battle, and the wounded and fallen count as in any battle. The stronger the guards, the lower the chance of a loss; the row of the deal shows it. Squads that hold a point, guard an area or patrol near the way of a caravan (within {cfg:roads.protectRadius} blocks) lower the chance of an ambush too.

## tactics | Tactics
@icon minecraft:netherite_sword
Near players a squad in a fight fights **by the plan of its leader**, unless the server turned tactics off (the setting enabled of the tactics section). Without a plan every fighter fights on its own.

### The leader

The leader is the captain or vice-captain, otherwise the commander of the squad or a fighter of sergeant rank or above, otherwise the most experienced. It looks at the fight every few ticks, the more often the more skilled it is:

| Leader | Ticks between decisions |
|---|---|
| No officer | {cfg:tactics.decideTicks#1} |
| Sergeant | {cfg:tactics.decideTicks#2} |
| Vice-captain | {cfg:tactics.decideTicks#3} |
| Captain | {cfg:tactics.decideTicks#4} |

A squad answers an enemy it has just seen after {cfg:tactics.reactionTicks} ticks. The leader sees only enemies in the line of sight of someone of the squad, within {cfg:tactics.sight} blocks. It gives out roles, and the fighters carry them out themselves.

### Against one enemy

Melee fighters **ring** it: each has its own sector {cfg:tactics.ringRadius} blocks from the target; the first stand facing the squad, the others go round to the sides and the back without running through the target. An enemy in a narrow passage or a house is awaited at the ways out, {cfg:tactics.exitRadius} blocks away, instead of going in one by one. An enemy on a pillar or a ledge is awaited at its foot until the archers bring it down.

### Against a group

Against a group (from {cfg:tactics.pincerFoes} enemies, with twice as many fighters) a squad with an officer or a sergeant holds a **front**, shields first, and sends **wings** to the sides of the group, {cfg:tactics.flankRadius} blocks out, and under an officer also into its rear (a pincer). Without an officer the squad simply surrounds the nearest enemy. The leader picks one target for the whole squad by how dangerous it is and how fast it falls, and prefers one already striking its own; a captain weighs distance more exactly. Without an officer the target is the nearest.

### Blows from the side and the back

A blow of a fighter of a squad from the side of its target (60 to 120 degrees from where it looks) is {cfg:tactics.flankBonusPercent}% stronger, from behind (beyond 120 degrees) {cfg:tactics.rearBonusPercent}% stronger.

### Archers

Archers stand on an arc {cfg:tactics.archerMin}-{cfg:tactics.archerMax} blocks from the target, shift until no friend stands in the line of fire, prefer high ground and shoot those tied in a fight with their own. When an enemy comes closer than {cfg:tactics.archerTooClose} blocks, an archer steps back to the far edge.

### The wounded and retreat

A fighter below {cfg:tactics.woundedPercent}% of its health falls back {cfg:tactics.fallBack} blocks behind the front, covered by a fighter with a shield; no more than half of the squad falls back, and the wounded go to the infirmary after the fight. A squad that lost {cfg:tactics.retreatLossesPercent}% of its fighters, or is worth less than {cfg:tactics.retreatBalancePercent}% of the enemies it sees, falls back in order towards help: to the post of its order or to the capital, shields last. Guards falling back from an offender raise the relief to the next step at once (see [Alarms and relief](army_relief)). Nobody is sent into lava, fire, water or off a cliff.

### Robbers and adventurers

Robbers lie in wait crouching until the prey comes closer than 8 blocks, one of them is hurt, or 30 seconds pass; then they surround it together and run to their camp when the enemy is stronger. Adventurers fight like soldiers, and an experienced one (rank C and above) leads them as a sergeant would.

## army_battles | Battles far from players
@icon minecraft:crossbow
Far from players soldiers have no bodies, and the server reckons battles by a **model of strength**.

### Strength

The strength of a fighter depends on its rank, its weapon and attack speed, its armor and the armor's toughness, its experience, health and wounds. The strength of a squad adds up the strengths of its fighters; the squads view of the Army tab shows it, and the overview shows the strength of the whole army. The chance to win grows with the square of the ratio of strengths: an even fight is a coin toss, and twice the strength wins four times in five. The loser suffers more.

### Battles of squads

When a squad ordered to attack comes within {cfg:army.engageDistance} blocks of the enemy, a battle begins. It lasts {cfg:army.fieldBattleSeconds} s, and meanwhile other squads may join it. A blow at an enemy already tied in the battle adds {cfg:army.flankBonus*100}% to the strength from the side and {cfg:army.rearBonus*100}% from the rear. To strike a flank, lead the squad through points on the way (see [Orders on the map](map_orders)). The formation gives an edge too:

| Formation | Speed | Attack | Defense |
|---|---|---|---|
| Line | x1 | x1 | x1.1 |
| Column | x1.25 | x0.9 | x0.9 |
| Wedge | x0.9 | x1.15 | x0.95 |

After the battle the losers go back to their barracks, the winners stand where they fought. Members with the Army right get messages: a battle began, a blow at a flank or the rear, how it ended.

### Wounds and experience

In an even battle its side won, each fighter may be wounded with a chance of {cfg:army.battleInjury*100}% and fall with a chance of {cfg:army.battleDeath*100}%; a lost battle and a weaker side are more dangerous, and the server's risk of work (taskRiskMultiplier) multiplies these chances. Armor and experience protect. The fallen go into the chronicle, the wounded keep their wounds, and the others gain {cfg:army.battleExperience} experience of war (twice for a win). Weapons and armor wear.

### Skirmishes at night

Squads that hold a point, guard an area or patrol far from players may meet monsters at night: a chance of {cfg:army.skirmishChance*100}% every step of the simulation (every {cfg:simulation.simulationIntervalTicks/1200} min). There are 1 to 5 monsters, each of strength {cfg:army.monsterStrength}; a recruit with bare hands has a strength of 10.

## map_orders | Orders on the map
@icon kingdomworld:marshal_baton
### The army on the map of the atlas

Open the map of kingdoms (the K key, changeable in the controls, or the [Kingdom Atlas](atlas) {item:kingdomworld:kingdom_atlas}) and press the **Army** button on top. The map shows the squads of your kingdom in your current dimension: their place, fighters, order and state ("in place", "on the way, ... left", "in battle"), their way and the battles going on. Squads of others show too, when your kingdom sees them (see [The fog of war](fog_of_war)). The Lands button brings back the usual map. Only members with the Army right command; for the others the map says that only the king and the marshal command the army.

### Giving an order

1. Click a squad of yours to choose it; with Shift you can choose several (up to 32).
2. Press an order button at the bottom. On a narrow screen the buttons stand in two rows; the full name and a hint show on hover.
3. A right click on the map sets **points on the way** (up to 8): so the squad goes round the enemy and into its flank or rear. Then a left click sets the place or the target.

| Button | Order |
|---|---|
| Go | Go to a point and stand there |
| Hold | Hold a point |
| Patrol | Left click the route, right click send |
| Guard | Guard 3x3 chunks around a point |
| Attack | An enemy squad, a camp or a city |
| Siege | Besiege a city of the enemy |
| Escort | Left click a caravan of yours |
| Scout | Scout 3x3 chunks around a point |
| Back | Go back to the barracks at once |

A patrol route has up to 8 points and takes no points on the way. The formation button (Line, Column, Wedge) changes the formation of the chosen squads; a right click goes back. A line holds best, a column marches fastest, a wedge strikes hardest (see [Battles far from players](army_battles)).

A squad of the enemy can be attacked only in a war and only when it is seen or remembered (a pale mark). A city of the enemy is attacked and besieged only in a war (see [Sieges and the ram](sieges)); land under the shield of absent players cannot be attacked. A camp of robbers can be attacked when your kingdom knows of it (see [Robbers](bandits)). All points must lie in the dimension of the capital. The server checks rights, targets and places.

### The march

A squad marches to the place of its order through the points given, in line at {cfg:army.marchSpeed} blocks a second, {cfg:army.roadSpeedBonus*100}% faster on roads; its way bends to roads by itself when that is faster. Near players the soldiers walk themselves. An order lasts until it is changed, and the place of the squad is saved. Messages are rare: arrived, battle begun, battle over, back home.

### The marshal's baton

{recipe:kingdomworld:marshal_baton}

The **marshal's baton** {item:kingdomworld:marshal_baton} gives orders right in the world:

- sneak and use it in the air: choose the next squad of your kingdom;
- use it in the air: where the squads are now (the order, coordinates and distance of each in the chat);
- use it on a block: the order of the current mode at that place;
- sneak and use it on a block: change the mode.

The modes in turn: hold a point; add a point of the patrol (to the end of the route; a route has up to 8 points, the oldest drops out); guard an area (3x3 chunks); besiege the city of the enemy (a city of the enemy near that place); defend our city (a city of your own within 160 blocks). The tooltip of the baton shows the chosen squad and mode. The server checks the Army right.

## fog_of_war | The fog of war
@icon minecraft:spyglass
Armies of others far away are not seen. On the map of the army (see [Orders on the map](map_orders)) a squad of another kingdom shows only while one of the "eyes" of your kingdom sees it:

| Who sees | Radius, blocks |
|---|---|
| A squad of yours | {cfg:army.sightRadius} |
| A tower or a wall | {cfg:army.postSightRadius} |

- Archers add up to {cfg:army.archerSightBonus*100}% to the sight of their squad, by their share of the squad.
- A squad ordered to scout sees {cfg:army.scoutSightBonus*100}% farther, and once there it sees the whole area it scouts and around it.
- Towers and walls: working buildings with the Watchtower or Wall with a gate role, and the built walls of the kingdom's cities (see [Walls and gates](walls)).
- A spy on a scouting mission reveals all squads of the target kingdom for {cfg:army.spyRevealMinutes} min (see [Intrigue and espionage](intrigue)).

Your own squads and those of your allies are always seen.

### Pale marks

When a squad of others leaves the sight, a pale mark "Last seen" with its fighters and the time stays on the map. It lasts {cfg:army.lastSeenMinutes} min or until the squad is seen again. Only a squad in sight or one with such a mark can be attacked.

### How it is reckoned

The server works out what is seen every {cfg:army.fogSeconds} s, and only for kingdoms whose map of the army is open. A client gets only what its kingdom sees, so a map mod cannot peek at the squads of others.

> Keep a watchtower or a wall on the border and send a squad with archers to scout before a campaign: you will see the enemy before it comes.
