# The adventurers' guild
@icon kingdomworld:guild_desk

## guild | The guild and its desk
@icon kingdomworld:guild_desk
There is one **adventurers' guild** in the whole world, with a **branch** in every kingdom. Through the guild players take contracts to hunt and gather, rise through the ranks from F to SSS, buy and hire [companions](companions) and form [parties](parties).

### The guild desk

A branch works at a **guild desk** {item:kingdomworld:guild_desk}. The desk stands on the land of a kingdom (outside it the desk does not work).

{recipe:kingdomworld:guild_desk}

A desk is open while it stands in the zone of a [work post](work_posts) {item:kingdomworld:work_post} with the Registrar profession and the post has a healthy registrar. Near players the registrar stands at the desk. Without a registrar the desk is closed ("The desk is closed: no registrar keeps it") and the branch posts no contracts of its own. A registrar is paid the usual [wage](wages).

### The desk window

Right click the desk. The tabs of the window:

- **My card**: your rank and points, what the next rank needs, the exam (the button "Take the exam"), the monsters you killed and contracts you finished, the rank and commission of the branch. The button "Join the guild" is here too.
- **Board**: the contracts of the branch with the filters "Kind" and "Ranks: all/mine", and the button Take.
- **My contracts**: progress, Hand in, Give up, and Call off for your own ones.
- **Post**: a new contract from you or from the kingdom.
- **Market** and **Hire**: slaves and free companions, for members of the guild only.

More in [Contracts](contracts) and [Ranks](ranks).

### Joining

You join at any working desk. Joining costs {cfg:guild.joinFee} copper coins (0 means free), paid to the treasury of the branch's kingdom. A new member gets rank F; the rank holds at every branch of the world. In the list of subjects the tooltip over a name shows the player's rank in the guild.

### The branch of a kingdom

The Guild tab of the [throne](throne) window runs the branch, with the right "Adventurers' guild" (by default the king and the marshal). The view "branch" shows the rank, the registrars and desks, the head of the guild, the commission, the budget and the crystals in the storehouses.

- **Commission**: the share of every reward the branch keeps for the treasury. A new branch has {cfg:guild.defaultCommissionPercent}%; the king sets {cfg:guild.minCommissionPercent}% to {cfg:guild.maxCommissionPercent}% (the box Commission and the button Set).
- **Daily budget**: what the guild may spend from the treasury on hunts of its own. A new branch has {cfg:guild.defaultDailyBudget} copper coins, at most {cfg:guild.maxDailyBudget}.

### The rank of a branch

A branch has a rank of its own from F to SSS; a new one starts at E. It examines nobody above its own rank and posts no contracts for ranks above its own. A branch of players rises with the contracts finished there:

| Rank | Contracts | Own budget |
|---|---|---|
| D | {cfg:guild.branchRankContracts#3} | {cfg:guild.ownBudgetByRank#3} |
| C | {cfg:guild.branchRankContracts#4} | {cfg:guild.ownBudgetByRank#4} |
| B | {cfg:guild.branchRankContracts#5} | {cfg:guild.ownBudgetByRank#5} |
| A | {cfg:guild.branchRankContracts#6} | {cfg:guild.ownBudgetByRank#6} |
| S | {cfg:guild.branchRankContracts#7} | {cfg:guild.ownBudgetByRank#7} |
| SS | {cfg:guild.branchRankContracts#8} | {cfg:guild.ownBudgetByRank#8} |
| SSS | {cfg:guild.branchRankContracts#9} | {cfg:guild.ownBudgetByRank#9} |

"Own budget" is the least daily budget of the guild at that rank. Without a guild house a branch of players rises up to C by default, and up to B with a working building of the guild role (see [The roles of buildings](building_roles)); while no template of the catalog has that role, a branch rises to B without one. From rank C a branch has a **head of the guild**: its best adventurer of the branch's rank or above.

> The guilds of [kingdoms of the world](world_kingdoms) are strong from the first day: a small kingdom's is of rank {cfg:guild.worldRankMin#1} or {cfg:guild.worldRankMax#1}, a medium one's {cfg:guild.worldRankMin#2} or {cfg:guild.worldRankMax#2}, a large one's {cfg:guild.worldRankMin#3}, and rank {cfg:guild.worldRankMax#3} belongs to the strongest kingdom of the world alone.

## ranks | Ranks
@icon minecraft:diamond_sword
The ranks of the guild are **F, E, D, C, B, A, S, SS, SSS**. Players, citizen adventurers, branches and monsters all have one.

### The rank of a player

A new member of the guild gets rank F. Points come from finished [contracts](contracts) and from killed monsters. A new rank needs its points and a passed exam:

| Rank | Points | Exam | Per monster |
|---|---|---|---|
| F | {cfg:guild.rankPoints#1} | none | {cfg:guild.killPoints#1} |
| E | {cfg:guild.rankPoints#2} | {cfg:guild.examKills#2} | {cfg:guild.killPoints#2} |
| D | {cfg:guild.rankPoints#3} | {cfg:guild.examKills#3} | {cfg:guild.killPoints#3} |
| C | {cfg:guild.rankPoints#4} | {cfg:guild.examKills#4} | {cfg:guild.killPoints#4} |
| B | {cfg:guild.rankPoints#5} | {cfg:guild.examKills#5} | {cfg:guild.killPoints#5} |
| A | {cfg:guild.rankPoints#6} | {cfg:guild.examKills#6} | {cfg:guild.killPoints#6} |
| S | {cfg:guild.rankPoints#7} | {cfg:guild.examKills#7} | {cfg:guild.killPoints#7} |
| SS | {cfg:guild.rankPoints#8} | {cfg:guild.examKills#8} | {cfg:guild.killPoints#8} |
| SSS | {cfg:guild.rankPoints#9} | {cfg:guild.examKills#9} | {cfg:guild.killPoints#9} |

"Exam": how many monsters of the new rank or above you must kill; for S and above they are bosses. "Per monster": the points for killing a monster of that rank (fewer at farms, see [Monster crystals](crystals)).

### The exam

When you have the points, the card says "Enough points for rank X: take the exam". Press "Take the exam" at a desk: you have {cfg:guild.examMinutes} minutes for it all. The exam is taken at a branch of the new rank or higher. If the time runs out or you give up, the next try comes after {cfg:guild.examCooldownMinutes} minutes.

### The rank of a monster

A monster's rank is worked out by itself from the attributes of its kind, so the monsters of every mod have ranks. The strength of a monster: health × {cfg:guild.healthWeight} + damage × {cfg:guild.damageWeight} + armor × {cfg:guild.armorWeight} + speed × {cfg:guild.speedWeight}.

| Rank | Strength from | Example |
|---|---|---|
| F | {cfg:guild.monsterRankScores#1} | |
| E | {cfg:guild.monsterRankScores#2} | zombie |
| D | {cfg:guild.monsterRankScores#3} | blaze |
| C | {cfg:guild.monsterRankScores#4} | enderman |
| B | {cfg:guild.monsterRankScores#5} | ravager |
| A | {cfg:guild.monsterRankScores#6} | |
| S | {cfg:guild.monsterRankScores#7} | warden |
| SS | {cfg:guild.monsterRankScores#8} | dragon, wither |
| SSS | {cfg:guild.monsterRankScores#9} | |

Bosses (with the common tag of bosses or a boss bar of their own) stand {cfg:guild.bossRankBonus} ranks above their strength, and at least at rank A (by default). The server can set ranks by hand by id or tag: by default the dragon and the wither are SS, the warden S, the elder guardian B. Animals have no rank and do not count for the guild.

### The rank of an adventurer

A citizen adventurer rises in rank by its points without exams, and the strength of its body follows the rank (see [Adventurers and expeditions](guild_adventurers)). The rank of a branch is explained in [The guild and its desk](guild).

> Your rank also decides how many [companions](companions) you keep.

## crystals | Monster crystals
@icon kingdomworld:monster_crystal
A **monster crystal** {item:kingdomworld:monster_crystal} falls from a hostile monster killed by a player, a player's companion or a citizen adventurer. A crystal knows its rank (the monster's rank); crystals of one rank stack, the color and rarity follow the rank, and the tooltip shows the rank and the energy. There is no recipe: crystals only come from monsters (and from server operators).

### Chance, energy and worth

| Rank | Chance | Energy | Worth |
|---|---|---|---|
| F | {cfg:guild.crystalChance#1*100}% | {cfg:guild.crystalEnergy#1} | {cfg:guild.crystalValue#1} |
| E | {cfg:guild.crystalChance#2*100}% | {cfg:guild.crystalEnergy#2} | {cfg:guild.crystalValue#2} |
| D | {cfg:guild.crystalChance#3*100}% | {cfg:guild.crystalEnergy#3} | {cfg:guild.crystalValue#3} |
| C | {cfg:guild.crystalChance#4*100}% | {cfg:guild.crystalEnergy#4} | {cfg:guild.crystalValue#4} |
| B | {cfg:guild.crystalChance#5*100}% | {cfg:guild.crystalEnergy#5} | {cfg:guild.crystalValue#5} |
| A | {cfg:guild.crystalChance#6*100}% | {cfg:guild.crystalEnergy#6} | {cfg:guild.crystalValue#6} |
| S | {cfg:guild.crystalChance#7*100}% | {cfg:guild.crystalEnergy#7} | {cfg:guild.crystalValue#7} |
| SS | {cfg:guild.crystalChance#8*100}% | {cfg:guild.crystalEnergy#8} | {cfg:guild.crystalValue#8} |
| SSS | {cfg:guild.crystalChance#9*100}% | {cfg:guild.crystalEnergy#9} | {cfg:guild.crystalValue#9} |

Animals, and monsters killed without a player, a companion or an adventurer, leave no crystals. You need not join the guild for crystals, but only its members get points for monsters (a companion's kills give no points).

### Farms

Kills are remembered by place, in squares of 32 by 32 blocks, for {cfg:guild.farmWindowMinutes} minutes. The first {cfg:guild.farmFreeKills} kills in one place count as usual; after that the chance of a crystal and the points halve with every {cfg:guild.farmHalfKills} kills. Monsters from a spawner give only {cfg:guild.spawnerFactor*100}% of the chance and the points from the first kill on, and less after. A restart of the server starts the count afresh.

> Hunt in different places: a dozen monsters here and there give more crystals than a hundred at one farm.

### What crystals are for

- **The energy of the kingdom.** Mages dissolve crystals of the storehouses into the kingdom's energy (see [The energy of a kingdom](energy) and [The mage tower and mages](mage_tower)). The Guild tab, view "branch", shows the energy of the crystals in the storehouses by rank.
- **Spells of players.** Staffs spend the energy of the crystals the player carries (see [Staffs](staffs)).
- **Sale.** Players sell crystals as lots at the [market](stall). Kingdoms never import them. The worth of crystals counts towards the wealth of a kingdom and the [rate](money_rates) of its currency.

An adventurer's crystal goes straight to the storehouses of its kingdom; without room there it falls to the ground.

## contracts | Contracts
@icon minecraft:paper
A **contract** lies on the board of a branch: what to do, for what reward and within how many days. The reward waits in escrow from the moment it is posted, so money is neither made nor lost.

### Kinds of contracts

- **Hunt**: kill N monsters of one kind, or any monsters of rank X and above.
- **Gathering**: hand in N items at a desk.
- **Bounty**: kill a strong monster of a kind.
- **Exam**: a personal contract for the next rank, without money (see [Ranks](ranks)).
- **Head of a criminal**: the branch posts one by itself on every wanted murderer, with a reward from the treasury ({cfg:law.bountyReward} copper coins, or what the court set) for {cfg:law.bountyDays} game days (see [Wanted](wanted)).

The kinds "Escort of a caravan" and "War" are kept for later and cannot be posted yet. Hunts of robbers and bounties on their leaders work as on any monster (see [Robbers](bandits)).

### Taking and finishing

On the Board tab press Take. You take contracts of your rank and one above, at most {cfg:guild.maxActiveContracts} at once. Kills count by themselves; items for a gathering are handed in at a desk with Hand in, all at once (in a [party](parties) in parts). A finished contract pays the reward less the branch's commission onto your account in the branch's currency, and gives points:

- a hunt: the points of a monster of its rank for each one;
- a bounty: three times as many;
- a gathering: the points of the contract's rank for every 16 items begun.

A finished contract also adds {cfg:law.contractReputation} to your reputation in the kingdom of the branch.

### Time and giving up

A contract runs for up to {cfg:guild.maxContractDays} game days. When the time runs out, the reward goes back to whoever posted it, and the taker loses {cfg:guild.penaltyPercent}% of the contract's points. Giving up (Give up) costs the same points; the contract goes back to the board, and the next taker starts from nothing.

### Posting

The Post tab: Kind, Target (the button Choose, a monster or By rank, an item for a gathering), How many, Reward, Rank, Days, "Squads: yes/no" and "From me" or "From the kingdom", then Post.

- **From you**: the reward goes into escrow from your account, plus a fee to the branch of {cfg:guild.postingFeePermille/10}% of the reward. At most {cfg:guild.maxPostedPerPlayer} contracts of yours at once. You get the gathered items (when you join).
- **From the kingdom**: needs the right "Adventurers' guild"; the reward comes from the treasury and the gathered items go to the kingdom's storehouses.
- "Squads: yes" lets squads of the kingdoms' adventurers take the contract.

While nobody holds a contract, its poster can call it off and get the escrow back. A branch's board holds at most {cfg:guild.maxContractsPerBranch} contracts.

### Contracts of the guild itself

Every 10 minutes a working branch posts a hunt on the monsters that live around the throne, paid from the treasury within the daily budget; at most {cfg:guild.maxAutoContracts} such contracts stay open. It hunts only monsters up to the rank of the branch, and pays a quarter more for every rank the branch stands above the monster. The usual reward per monster:

| Monster rank | Copper |
|---|---|
| F | {cfg:guild.autoRewardPerKill#1} |
| E | {cfg:guild.autoRewardPerKill#2} |
| D | {cfg:guild.autoRewardPerKill#3} |
| C | {cfg:guild.autoRewardPerKill#4} |
| B | {cfg:guild.autoRewardPerKill#5} |
| A | {cfg:guild.autoRewardPerKill#6} |
| S | {cfg:guild.autoRewardPerKill#7} |
| SS | {cfg:guild.autoRewardPerKill#8} |
| SSS | {cfg:guild.autoRewardPerKill#9} |

## guild_adventurers | Adventurers and expeditions
@icon minecraft:iron_sword
**Adventurer** is a profession of citizens that needs no workplace. Give it to a citizen in the [Workers window](workers_window) or at the throne (see [Professions](professions)).

### Near players

An adventurer takes weapons and armor of any mod from the storehouses, looks for hostile monsters on the kingdom's land within 16 blocks and fights them, at night too. A wounded one goes to the [infirmary](health). It earns points and experience for the monsters, and their crystals go straight to the kingdom's storehouses.

### Rank and strength

An adventurer rises in rank by its points without exams (the thresholds are those of players, see [Ranks](ranks)). Its body is the tougher the higher its rank:

| Rank | Health | Damage + |
|---|---|---|
| F | {cfg:guild.adventurerHealth#1} | {cfg:guild.adventurerDamage#1} |
| E | {cfg:guild.adventurerHealth#2} | {cfg:guild.adventurerDamage#2} |
| D | {cfg:guild.adventurerHealth#3} | {cfg:guild.adventurerDamage#3} |
| C | {cfg:guild.adventurerHealth#4} | {cfg:guild.adventurerDamage#4} |
| B | {cfg:guild.adventurerHealth#5} | {cfg:guild.adventurerDamage#5} |
| A | {cfg:guild.adventurerHealth#6} | {cfg:guild.adventurerDamage#6} |
| S | {cfg:guild.adventurerHealth#7} | {cfg:guild.adventurerDamage#7} |
| SS | {cfg:guild.adventurerHealth#8} | {cfg:guild.adventurerDamage#8} |
| SSS | {cfg:guild.adventurerHealth#9} | {cfg:guild.adventurerDamage#9} |

A common citizen has 20 health.

### Squads and expeditions

The Guild tab, with the right "Adventurers' guild". The view "adventurers" shows the rank, weapon, armor and whereabouts of each (at home, in a squad, away, wounded). Choose {cfg:guild.minSquadSize} to {cfg:guild.maxSquadSize} adventurers outside squads and press "Form squad". In the view "squads" the button Send opens the list of contracts, then Go; Disband breaks a squad up.

A squad takes contracts of its kingdom and of the guild at its branch, and contracts of players only with "Squads: yes". The rank of a squad is that of its best member; it takes its rank and one above. Every member must be at home and healthy, and the expedition must be back before the contract runs out.

Away, the members have no body. An expedition takes {cfg:guild.expeditionBaseMinutes} minutes, plus {cfg:guild.expeditionMinutesPerRank} minutes for every rank of the contract above F, plus a minute for every monster left (at most 60). How it ends depends on the squad's strength (ranks, weapon damage, armor, experience) against the contract: an even fight succeeds one time in two, a stronger squad more often.

- **Success**: the reward less the commission to the treasury, crystals and the loot of the monsters to the storehouses, experience and points.
- **Part success**: some monsters killed, the contract goes back to the board.
- **Failure**: nothing.

On an even expedition each member is wounded with a chance of {cfg:guild.expeditionInjury*100}% and dies with a chance of {cfg:guild.expeditionDeath*100}% (times the general risk multiplier of work, {cfg:work.taskRiskMultiplier}; armor and experience protect). The fallen go to the chronicle as "fell on an expedition", and the neighbours mourn.

## companions | Companions
@icon minecraft:shield
A **companion** walks with you, fights, carries a pack and eats on its own. There are two kinds: **slaves** (bought) and **free companions** (serving for pay).

### Where to get one

The tabs Market and Hire of the window of a [guild desk](guild), for members of the guild only. Every {cfg:companions.refreshDays} game day the branch offers {cfg:companions.slaveOffers} slaves and {cfg:companions.freeOffers} free companions up to level {cfg:companions.maxOfferLevel}: the name, level, strength, health, skills (fighting, carrying, stamina) and origin.

- A slave costs {cfg:companions.pricePerPower} copper coins per point of strength, at least {cfg:companions.minPrice} (a beginner without gear has a strength of 10). The button Buy.
- A free companion asks {cfg:companions.wagePerPower} copper coins a day per point of strength, at least {cfg:companions.minWage}; the first day is paid at once. The button Hire.

You pay from the account or with the coins you carry ("Pay: account/coins"); the money goes to the treasury of the branch's kingdom. Your [rank](ranks) decides how many companions you keep: F {cfg:companions.rankLimits#1}, E {cfg:companions.rankLimits#2}, D {cfg:companions.rankLimits#3}, C {cfg:companions.rankLimits#4}, B {cfg:companions.rankLimits#5}, A {cfg:companions.rankLimits#6}, S {cfg:companions.rankLimits#7}, SS {cfg:companions.rankLimits#8}, SSS {cfg:companions.rankLimits#9}. The king may close the slave market on the kingdom's land with the button "Close market" of that tab; free companions are still hired.

### The companion window

Right click your companion to open its window: level and experience, strength, health, food, loyalty and its reasons, skills, wage, the pack and the orders.

- **Orders**: Follow, Stand, Guard (the place, from monsters) and "To contract" (hunt the monsters of your contracts and your party's nearby).
- **Pack**: 9 slots at carrying 1-3, 18 at 4-6, 27 at 7-9, 36 at 10. The companion takes the best weapon (melee, or a bow or crossbow with arrows), shield and armor of its pack by itself.
- **Actions**: "Set free", "To kingdom", Sell, "Let go".

### Food, wounds and experience

Food (out of 20) falls by one about every {cfg:companions.hungerTicks/20} seconds; stamina slows hunger. A companion eats on its own at food 14 or lower: from the pack first, then from your inventory nearby; it never eats harmful food. After {cfg:companions.starveTicks/1200} minutes without food it weakens to {cfg:companions.starvingStrength*100}% of its strength and loses loyalty. Wounds heal a point of health every {cfg:companions.healTicks/20} seconds, at an [infirmary](health) of your kingdom every {cfg:companions.infirmaryHealTicks/20}. Kills of monsters give experience: a level needs {cfg:companions.experiencePerLevel} experience times the level, the highest level is {cfg:companions.maxLevel}. A level adds health and raises the lowest skill.

### Loyalty

Loyalty goes from 0 to 100. A slave comes with {cfg:companions.slaveLoyalty}, a free companion with {cfg:companions.freeLoyalty}.

- It falls: {cfg:companions.hungerLoss} for every {cfg:companions.starveTicks/1200} minutes of hunger, {cfg:companions.woundLoss} for a heavy wound, {cfg:companions.beatenLoss} for a blow of yours and for an unpaid day.
- It rises: a point for every {cfg:companions.wellKeptTicks/1200} minutes fed and healthy (a slave only up to {cfg:companions.wellKeptCap} this way), with gifts and with freedom.
- A gift: Shift and right click the companion with an item in hand. A point of loyalty for every {cfg:companions.giftPerPoint} of worth, at most {cfg:companions.maxGift} a gift.

Below {cfg:companions.escapeBelow} a companion warns you (a message and a "!" in the window and in the list). If loyalty does not rise again within {cfg:companions.escapeTicks/24000} game day, a slave runs away with its whole pack (to the nearest camp of [robbers](bandits), or it vanishes when there is no camp), and a free companion leaves your service and gives back what it carried.

### Pay, freedom and sale

A free companion is paid at the start of a day while you are online: from your account to the treasury of the branch. Unpaid, it loses loyalty and leaves after {cfg:companions.unpaidDays} day. "Set free" makes a slave a free companion with loyalty of at least {cfg:companions.freedLoyalty}, who asks a daily wage. "To kingdom" lets it settle as a citizen of your kingdom under the same name (it needs a free bed and fewer citizens than the limit). Sell returns a slave to the market of the branch where it was bought for {cfg:companions.sellPercent}% of its price from the branch's treasury, when the treasury has the money. "Let go" dismisses a free companion. In every case the pack comes back to you.

### Body, peace and death

A companion is in the world while you are online and near: one that follows appears next to you, one told to stand or guard appears at its place when you come within {cfg:companions.bodyRange} blocks. While you are away it waits, neither getting hungry nor being paid. Companions never hurt players, citizens or other companions, and other players cannot hurt yours. Death is final: the chronicle remembers the companion, and its pack falls where it died. The court of a kingdom may forbid slavery: then the slaves of its subjects and foreign slaves on its land go free, and the owner of a slave freed on that land pays a fine of {cfg:law.slaveryFine} copper coins (see [The court](court)).

## parties | Parties of players
@icon minecraft:white_banner
A **party** brings several members of the guild together to finish contracts and split the reward. Parties are for members of the [guild](guild) only.

### Forming one

The party window opens with the key P (change it in the controls settings) or the command **/kingdom party**. Type the name of a player of the guild who is online and press Invite, or use **/kingdom party invite <player>**. An invitation holds for {cfg:companions.inviteTicks/1200} minutes; the invited player presses Accept or Decline (or **/kingdom party accept** and **decline**). A party has up to {cfg:companions.maxParty} players, the leader included.

### The leader

The leader removes members (Remove), hands over the lead (Lead) and changes the settings:

- **Split**: evenly, or by shares from 0 to 100 for each member;
- **Friendly fire**: off by default, so members and their companions do not hurt each other.

A leader who leaves hands the lead to the member who joined first.

The same with commands:

- remove a member: **/kingdom party kick <player>**;
- hand over the lead: **/kingdom party leader <player>**;
- turn friendly fire on or off: **/kingdom party fire true** or **false**;
- leave the party yourself, taking your contracts along: **/kingdom party leave**.

### Contracts of a party

A contract the leader takes becomes the party's; it is taken by the leader's rank, the own rank and one above. The kills of every member and of their companions count, and any member hands in items for a gathering in parts. The reward less the commission and the points are split by the shares: everybody gets the whole part, and the rest goes one coin at a time to those who lost most to rounding, so not a coin is lost. A member who leaves the party takes the contracts they hold along.

### The list and the chat

The list of the party shows in the top left corner of the screen: the members with their health (the leader with a star, those offline grey), then your companions; on a narrow screen extra lines fold into "and N more". The client config turns it off. Party chat: **/party <text>** or **/kingdom party say <text>**.

> A hunter and a wanted player in one party get no bounty for the head: the escrow goes back to the treasury.
