# Money
@icon kingdomworld:coin

## coins | Coins and currency
@icon kingdomworld:coin
Every kingdom has its own **currency**. Until the king renames it, it is named after the kingdom, for example "Eldaria crown". Money lies on the accounts of players, in the [treasury](treasury) of a kingdom and as coins {item:kingdomworld:coin} in your inventory.

### Coins

| Coin | Worth in copper |
|---|---|
| Copper coin | 1 |
| Silver coin | {cfg:economy.silverValue} |
| Gold coin | {cfg:economy.goldValue} |

The worth of the silver and gold coins is fixed when the kingdom is founded and never changes: if the server changes these settings, only new currencies get the new values. Every amount is a whole number of copper coins, never fractional and at most 10^18; the server refuses negative amounts and overflows.

### The coin item

The Coin item knows its currency and its kind; coins of one currency and kind stack. The rim shows the metal of the kind, the face takes the kingdom's color, and the tooltip names the currency and the worth. Coins of a dissolved kingdom stay but are marked as old: they cannot be spent, but a minter melts them back into metal (see [The mint](mint)).

Coins are struck from metal by the [mint](mint). Besides that, money of an account turns into coins when you take it at a [bank](bank), or choose "Get: coins" at a [stall](stall) and when exchanging currencies.

### Accounts and payments

A player has a separate account in every currency. The command **/kingdom balance** shows all your accounts and your debts to treasuries.

- **/kingdom pay <player> <amount> [kingdom]**: pay another player in the currency of your kingdom or of the one named.
- A payment above {cfg:economy.payConfirmAbove} copper coins must be confirmed with **/kingdom pay confirm** within 30 seconds.

### Writing amounts

Any amount box (tax, price, reward, payment) takes a number of copper coins or coins with letters: 1g5s3c means 1 gold, 5 silver and 3 copper coins. The initials of the Russian coin names work too; in commands write those in quotes.

### The name of the currency

Only the king renames the currency: the Treasury tab of the [throne](throne) window, the view "taxes and wages", the box "Currency name:" and the button Rename. A name has 3 to 32 characters (letters, digits, spaces, hyphens and apostrophes) and must differ from the names of other currencies. An empty name gives back the name after the kingdom. The kingdom card on the [map](atlas) shows the currency and its rate.

> The rate of your currency against others moves once a game day and follows the wealth of the kingdom: see [Exchange rates](money_rates).

## mint | The mint
@icon kingdomworld:mint
The **mint** {item:kingdomworld:mint} turns metal of the storehouses into coins for the treasury. It is the workstation of the Minter profession.

{recipe:kingdomworld:mint}

### Setting it up

1. Place a [work post](work_posts) {item:kingdomworld:work_post} on the kingdom's land and choose the Minter profession in its window.
2. Place the mint in the zone of the post.
3. Hire a worker with the hire button of the [post window](post_window).
4. Keep ingots in the [storehouses](storehouses).

The minter takes one ingot from a storehouse and strikes coins for it. The ingot leaves the storehouse and the coins reach the treasury in the same moment, or nothing happens at all: no coin appears without its metal, and no metal vanishes without its coins. Near players the minter works by hand at the mint; far from players it strikes by time: a beginner needs {cfg:economy.mintSeconds} seconds per ingot, up to twice as fast with experience, and [mood](mood) makes the work a little faster or slower.

### Rates of the mint

The list mintRates of the server config sets the rates. By default:

| Ingot | Coins |
|---|---|
| {item:minecraft:copper_ingot} Copper | 5 copper |
| {item:minecraft:iron_ingot} Iron | 2 silver |
| Silver (mods) | 3 silver |
| {item:minecraft:gold_ingot} Gold | 1 gold |

Metals are taken by the tags of ingots, so ingots of mods work too. With metal for more than one kind of coin, the minter strikes the kind the treasury's vault holds the least worth of, so a steady flow of copper does not keep gold waiting. Metal that a keep order holds or another worker set aside is left alone. By default the coins go into the vault of the treasury; the server can send them straight onto the treasury's books instead (then the kinds take turns).

### Which metals to strike

For every metal the king sets "Strike: yes/no" and a **Reserve**: how many ingots stay in the storehouses (0 to 10000, buttons - and + in steps of 16, with Shift 64). This is done in two places:

- the Treasury tab of the throne window, the view "minting": the columns Metal, Stored, Strike and Reserve;
- the window of a minter's post: "Metal:" (choose the metal), "Strike: yes/no" and "Reserve:".

The settings are those of the whole kingdom. Members with the Treasury right change them, everyone else only looks. In kingdoms of players copper, silver and gold are struck by default and **iron is not** (nor the iron ingots of mods), reserve 0: iron does not go into coins until the king turns it on. A metal of which the storehouses hold no more than the reserve is left alone. With every metal off or under its reserve, the post shows "Nothing to strike: the metals are off or kept in reserve" (see [The states of a post](post_states)). Kingdoms of the world strike everything.

### Old coins

Put the coins of a dissolved kingdom into a storehouse of a kingdom with a mint: the minter first melts them back into metal at the rate of the mint (two silver coins give an iron ingot, for example). They become metal, never money.

### What you see

The post window shows the mint in the zone, the metal at work and what was struck today. The overview of the Treasury tab shows the posts and minters and what was struck today and in 7 days.

> Tip: iron is needed for tools and weapons. Turn its minting on only when you have plenty, and set a reserve of, say, 64 ingots.

## treasury | The treasury
@icon minecraft:gold_block
The **treasury** of a kingdom has two parts: its books (money without coins) and a vault of coins. The Treasury tab of the [throne](throne) window shows "Treasury" and, apart, "On the books" and "coins in the vault".

The treasury pays from its books first, then with coins of the vault, the small ones first; the change for the last coin goes back onto the books. No money appears or vanishes on the way.

### What fills the treasury

- the [taxes](taxes) of the subjects and debts paid back;
- struck coins (the [mint](mint));
- sales of the [market](market), the tax on players' lots, [selling abroad](export_import) and [deals](deals);
- the bank's fee for [exchanging currencies](money_rates);
- the guild's commission and the fees for posting contracts ([the guild](guild));
- fines, entry fees of cities and ransoms of confiscated goods ([the law](law));
- what players pay in: **/kingdom treasury deposit <amount> [kingdom]** moves money from your account, paying your debt first.

### What it spends on

- the [wages](wages) of the citizens;
- the market's purchases from players and goods bought abroad;
- contracts of the kingdom and the guild's own hunts;
- mercenaries, spells, festivals, spies, provinces and much more.

### The journal

The journal of the treasury remembers income and expenses by category for 8 days. The categories: Taxes, Wages, Minting, Transfers, Admin, Trade, Exchange, Guild, Army, Magic, Companions, Law, Founding, Taverns and festivals, War: tributes and indemnities, Nobility: fiefs, demands and spies, Land: provinces.

### Views of the Treasury tab

The View button goes through the views: left click forward, right click back.

- **overview**: the currency and the coin values, the tax, the last wages, the time to the next payday, the mint, debts;
- **journal**: the categories for "Today", "Yesterday" or "Last 7 days" (a second button);
- **taxes and wages**: the tax, the wages by profession, the name of the currency;
- **minting**: which metals to strike and how many to keep;
- **payments**: "To player:", "Amount:" and Pay (onto the account of a player who is online, a member of a kingdom or has an account); "From the vault:", the kind of coin and Take put coins of the vault into your inventory;
- **reserves**: money of other currencies in the treasury and its worth;
- **debtors**: who owes the treasury and how much.

### Rights

The Treasury right is needed for the tax, wages, minting and payments; by default the king and the treasurer hold it (see [The rights of positions](rights)). Only the king and the treasurer take coins of the vault into their hands, and the journal writes that down as a transfer. The command **/kingdom treasury** shows the treasury, **/kingdom treasury pay <player> <amount>** pays from it.

## taxes | Taxes
@icon minecraft:paper
Once a game day (every 24000 ticks, that is 20 minutes) every member of a kingdom **except the king** pays the poll tax from the account in the kingdom's currency. In the same moment the treasury pays the [wages](wages) of the citizens. The overview of the Treasury tab shows the time left: "Taxes and wages: in N min".

### How much

A new kingdom has a tax of {cfg:economy.defaultPollTax} copper coins a day. The king or a holder of the Treasury right changes it in the Treasury tab, the view "taxes and wages": the box "Tax a day:" and the button Set. The highest tax is {cfg:economy.maxPollTax} copper coins. The overview shows "Tax per subject: X a day (payers: N)".

### Debts

The tax is taken from the account only, never from coins in the inventory. What is missing becomes a debt to the treasury. The debt is paid:

- with the next tax, once there is money on the account;
- by itself from your income in the kingdom's currency: sales to the market and of lots, rewards of the guild, payments of the treasury, payments from players (coins handed in at a bank do not count as income);
- when you pay into the treasury with **/kingdom treasury deposit <amount>**;
- with the button Pay off in the law window.

You get a message about the tax and the debt, and a reminder when you join. Your own debt shows in the [bank](bank) window and with **/kingdom balance**; all debtors show in the view "debtors" of the Treasury tab. A debt has no interest, and guards never attack for one. But with a debt above {cfg:law.debtLimit} copper coins the kingdom's market sells to you only for coins and takes no new lots from you (see [Fines and punishment](fines)).

### Tax on sales

Apart from that the king sets a tax on the sales of players' lots at the market: the Market tab, the view "settings", the box "Tax on lots, %:". A new market has {cfg:trade.defaultSalesTaxPercent}%, the highest is {cfg:trade.maxSalesTaxPercent}%. More in [The market stall](stall).

> Tip: put coins into the bank ahead of time. A tax paid in full adds {cfg:law.taxReputation} to your reputation in the kingdom.

## wages | Wages
@icon minecraft:gold_nugget
On the day of taxes the treasury pays every citizen a wage by its profession. The list wages of the server config sets the usual wages; the king changes them for the kingdom between 0 and {cfg:economy.maxWage} copper coins a day.

### Usual wages (defaults)

| Profession | Copper a day |
|---|---|
| Unemployed | 0 |
| Farmer, lumberjack | 6 |
| Miner | 8 |
| Fisher, herder | 5 |
| Carrier | 4 |
| Smelter, cook | 6 |
| Crafter, builder | 7 |
| Smith | 10 |
| Minter, merchant | 8 |
| Innkeeper | 6 |
| Registrar | 7 |
| Mage | 12 |
| Warrior, archer | {cfg:army.soldierWage} |

### Changing them

The Treasury tab, the view "taxes and wages": every profession has a row "X a day, citizens: N" (or "X a day (usually Y)" when changed) and the buttons - and +, one copper coin a step. The Treasury right is needed.

### Too little money

When the treasury cannot pay everybody, every citizen gets the same share of its wage; with no money at all nobody gets anything. The overview shows "Last wages: X of Y (Z%), citizens: N" and "Citizens paid too little", and the treasurer gets a message.

### Wages and mood

A citizen measures its pay against the usual wage of its profession. Without pay its [mood](mood) tends 20 lower, at twice the pay 10 higher (more than twice pleases no more). A wage below the usual makes citizens unhappy. Mood moves the growth of the kingdom and the pace of work: up to {cfg:economy.moodWorkEffect*100}% faster or slower far from players and in workshops.

### Where the money goes

A citizen takes {cfg:economy.householdSharePercent}% of every wage paid home; the rest leaves the world. The chest of its house holds coins of the kingdom (at most {cfg:economy.householdCoinsMax} copper coins, what goes beyond leaves the world too) and a little food bought from the storehouses (up to {cfg:economy.householdFood} items; the price goes back to the treasury). Food is bought only while the storehouses hold more than a reserve for every citizen. Taking from the chest of a house is a theft from that citizen (see [Crimes](crimes)).

> Soldiers get a wage of their own, officers a multiple of it: see [Warriors and archers](soldiers).

## bank | The bank
@icon kingdomworld:bank
A **bank** {item:kingdomworld:bank} keeps the accounts of players. It works in the currency of the kingdom whose land it stands on; outside the land of kingdoms it does nothing. A bank needs no worker.

{recipe:kingdomworld:bank}

### The bank window

Right click the bank. At the top you see "On your account", "On you" (the coins of this currency in your inventory) and "You owe the treasury" when you do.

- **Hand in:** the buttons All coins, Gold, Silver and Copper put every coin of this currency, or every coin of one kind, onto the account.
- **Take:** type an amount, choose the kind with the button "In coins:" (any, up to gold, up to silver, copper only) and press Take. The bank pays as much as it can in the chosen kind and the rest in smaller coins.
- **Your other accounts:** your money in other currencies (old currencies are marked).

The account is charged for exactly what fits into your inventory. Without room for everything the bank pays a part and says how much; with no room at all nothing is taken. This bank does not take coins of other currencies: hand them in at a bank of their kingdom or exchange them.

### Exchange

The Exchange button opens the exchange of money of other currencies for the currency of this bank. Rates and fees are explained in [Exchange rates](money_rates).

### Why use a bank

- the [tax](taxes) is taken from the account only, so coins belong in the bank;
- at a [stall](stall), at the [guild](guild) and with **/kingdom pay** you can pay from the account;
- coins in your hands can be lost, an account cannot.

> Place a bank next to the stalls and the guild desk: players take money out and spend it on the spot.

## money_rates | Exchange rates
@icon minecraft:clock
Every currency has a **rate**: what one of its copper coins is worth in the common units of worth that items are measured in ([The worth of items](money_worth)). The rate shows in the Treasury tab, in the Market tab and on the kingdom card of the [map](atlas).

### How the rate moves

Once a game day the rate moves {cfg:trade.rateSmoothingPercent}% of the way to its target. The target is the wealth of the kingdom divided by the money of its currency.

- Wealth: the worth of the goods in the storehouses, the coins of the vault, the reserves of other currencies in the treasury and {cfg:trade.rateProductionDays} days of what the citizens produce.
- Money of the currency: the accounts of players, the treasury, the reserves of this currency in other treasuries, payments in escrow and coins in hands, but at least {cfg:trade.rateMinSupply}, so that a new currency is not worth a fortune.

The rate stays between {cfg:trade.minRate} and {cfg:trade.maxRate}. The more goods and production a kingdom has for the same money, the dearer its currency; the more money on accounts and in hands for the same goods, the cheaper it gets.

### Exchanging at a bank

In the [bank](bank) window press Exchange. Choose the currency you give, where from ("From: account" or "From: coins"), how to get the result ("Get: account" or "Get: coins") and the amount, then press Exchange. The window shows the rates of both currencies, the fee, what the treasury can pay out and about how much you get.

The treasury of the bank's kingdom puts the money it gets into its reserves and pays its own currency by the rates of the day less the fee, never more than it has. An exchange makes no money: each currency only changes hands, and the fee stays with the treasury. The reserves show in the Treasury tab, the view "reserves"; they count as wealth of the kingdom too.

### The fee

A new bank keeps a fee of {cfg:trade.defaultExchangeFeePercent}%. The king or a holder of the Market right changes it in the Market tab, the view "settings", the box "Exchange fee, %:", up to {cfg:trade.maxExchangeFeePercent}%.

### Other currencies at a stall

If the king allows it ("Other currencies: taken" in the settings of the market), you can pay at a [stall](stall) in another currency: the price is converted by the rates with the same exchange fee.

## market | The market
@icon minecraft:emerald
The **market** of a kingdom trades at its [market stalls](stall): it sells goods of the storehouses to players, buys what it needs from them and keeps the lots of players. The Market tab of the [throne](throne) window runs it (the Market right: by default the king, the treasurer and the steward).

### Views of the Market tab

The View button goes through them (right click goes back):

- **we sell**: what the kingdom sells. Every item has "Price:" (empty means the auto price) and "Keep:" (how many are never sold).
- **we buy**: what the kingdom buys from players. "Price:", "Buy up to:" (while the storehouses hold fewer) and "Budget a day:".
- **lots of players**: what players put up and who sells it.
- **selling abroad** and **buying abroad**: trade with the outside world, see [Export and import](export_import).
- **settings**: "Outsiders: welcome/refused", "Other currencies: taken/refused", "Tax on lots, %:", "Exchange fee, %:" and the rate of the currency.

Items are added with Add and a search of items by name or id. Each list holds at most {cfg:trade.maxMarketEntries} items. At the bottom are the counters of the day: "Today: sold for X, bought for Y, tax on lots Z".

### The auto price

Without a price of its own, the market takes the worth of the item ([The worth of items](money_worth)) times the demand: ((target + 1) / (stock + 1)) to the power {cfg:trade.demandElasticity}, where the target is {cfg:trade.priceStockTarget} items in the storehouses. Little stock makes it dearer and much stock cheaper, but never below {cfg:trade.minPricePercent}% or above {cfg:trade.maxPricePercent}% of the worth.

When it buys from a player, the kingdom pays {cfg:trade.spreadPercent}% less than it asks itself, so buying and selling straight back loses money.

### What the market leaves alone

Selling never takes the items to "Keep", the reservations of workshops or what keep orders hold. Buying goes on while the storehouses hold fewer than the number set and the budget of the day lasts.

### Outsiders and reputation

The king may close the market to outsiders: then only subjects trade. A player's reputation in the kingdom moves the market's prices for that player by up to {cfg:law.priceEffectPercent}% either way, and every {cfg:law.tradePerPoint} copper coins of trade there win a point of reputation (see [The law of a kingdom](law)).

> A working building with the market role gives the merchant posts inside it more seats and players more lots (see [The roles of buildings](building_roles)). At fairs the market sells cheaper (see [Festivals and fairs](festivals)).

## money_worth | The worth of items
@icon minecraft:diamond
Every item has a **worth**. The market takes its auto prices from it, the outside world pays and asks by it, and the exchange rate counts the wealth of a kingdom with it.

### Where the worth comes from

- **Raw materials** get their worth from the server config (the list itemValues by id and tag): ores, ingots, logs, crops, meat, stone and so on.
- **Made things** get the worth of their cheapest recipe: the ingredients plus a markup of {cfg:economy.laborMarkup*100}% for the work, divided by what one making gives. Recipes of the crafting table, the furnaces, the stonecutter and the smithing table count, and so do recipes of the machines of mods. Cycles such as "ingot, block, ingot" make nothing cheaper.
- **Everything else** (without a recipe or an entry) is worth {cfg:economy.defaultItemValue}.

The table of worth is built when the server starts and again after data packs and tags reload. Monster crystals are worth what their rank says (see [Monster crystals](crystals)).

### Finding it out

The command **/kingdom value** shows the worth of the item in your hand, **/kingdom value <item>** that of the item named. The market's auto price in copper coins of the kingdom's currency comes straight from the worth; paid in another currency, it is converted by the [rates](money_rates).

### Using it

- Selling to a kingdom pays best for what its storehouses lack: the auto price is higher.
- Set a price of your own to sell above or below the demand.
- Made things are worth more than raw materials by the markup for the work, so workshops raise the wealth of a kingdom and the rate of its currency.

## stall | The market stall
@icon kingdomworld:market_stall
A **market stall** {item:kingdomworld:market_stall} opens the market of the kingdom whose land it stands on.

{recipe:kingdomworld:market_stall}

### Opening it

A stall is open while it stands in the zone of a [work post](work_posts) with the Merchant profession and the post has a healthy merchant. Without one the stall is closed: "The market is closed: no merchant works at this stall". Near players the merchant stands at the stall. The same merchants sell surplus abroad (see [Export and import](export_import)).

### The stall window

Right click the stall. The window "Market of ..." has four tabs:

- **Buy**: what the kingdom sells from its storehouses, with the price of one item and the stock. Type a number into "Count:" and press Buy.
- **Sell**: what the kingdom buys, how many it takes and how many of them you carry. The kingdom buys plain items only, without damage, enchantments or names.
- **Lots**: the lots of other players with the price and the seller.
- **My lots**: take a lot back (Take back) or put up what is in your hand: "Count:", "Price:" (the price of the whole lot) and Put up.

The button "Pay: account / coins" chooses how you pay, and "Get: account / coins" how you get money for what you sell. Prices are in the kingdom's currency, or, if the king allows it, in another currency by the rates with the exchange fee. Change comes in coins, or onto the account when there is no room in the inventory.

### Lots of players

Only **subjects** of the kingdom put up lots, at most {cfg:trade.maxLotsPerPlayer} on one market (buildings of the market add more). The item is kept by the market with its enchantments and name. When a lot sells, the seller gets the price less the tax on sales on the account in the kingdom's currency, online or not; the tax stays in the treasury. A lot taken back returns to the seller. When a kingdom is dissolved, its lots wait for their sellers and are handed over when they join.

### Who may trade

Until the king closes the market to outsiders, any player trades at the stall. With a debt to the treasury above {cfg:law.debtLimit} copper coins the market sells to you only for coins and takes no new lots.

> Place a stall next to a [bank](bank): buyers take money out and spend it at once, sellers put their takings onto the account.

## export_import | Export and import
@icon minecraft:oak_boat
A kingdom trades with the **outside world** without other players. The settings are in the Market tab, the views "selling abroad" and "buying abroad" (the Market right).

### Selling abroad

The king sets "Sell above:" N for an item: merchants sell abroad whatever the storehouses hold above N. Healthy merchants at posts with a [stall](stall) do it, near players and far from them.

- Price: the worth of the item times {cfg:trade.exportFactor}.
- A beginner merchant sells up to {cfg:trade.exportPerMerchant} items a game day, an experienced one more: at 1000 experience {cfg:trade.exportExperienceBonus*100}% more (twice as many by default).
- A kingdom earns at most {cfg:trade.exportMaxIncomePerDay} copper coins abroad a day.

The money goes to the treasury under Trade. The view shows "Merchants: N. Sold abroad today: ..." and the price for a hundred.

### Buying abroad

In the view "buying abroad" the king chooses an item, "How many:" and presses Order. Common goods with a worth in the server config can be ordered, except those the server forbids: the nether star, elytra, the dragon egg and head, the totem of undying, netherite scrap, the trident, coins, monster crystals and the like.

- Price: the worth times {cfg:trade.importFactor}.
- At most {cfg:trade.importMaxPerDay} items a day per kingdom.
- The money is paid at once and the goods reach the storehouses after {cfg:trade.importDelayMinutes} minutes; without room they wait for it.

### No money in a circle

Selling abroad always pays less than buying abroad costs, so bringing goods in to sell them out loses money. Selling abroad suits a surplus: extra stone, logs, crops.

> Set "Sell above" with a margin, so that merchants do not sell what workshops and construction need. Keep orders and the reservations of workshops are never sold abroad.

## deals | Deals and caravans
@icon minecraft:lead
Kingdoms trade with each other by **deals**, and **caravans** carry the goods. All of it is in the Trade tab of the throne window, the right "Deals with kingdoms" (by default the king, the chancellor and the treasurer).

### A new deal

The view "new deal": choose "Kingdom:", "We sell" or "We buy", the item, "Count:" (up to 4096 a delivery), "Price:" of the whole delivery "in our money" or "in their money" and "Every, days:" (0 for a single delivery, at most {cfg:trade.maxDealPeriodDays}), then Propose. A kingdom has at most {cfg:trade.maxDealsPerKingdom} open deals and proposals.

The other kingdom sees the proposal in the view "deals" and presses Accept or Decline. Unanswered, a proposal expires after {cfg:trade.proposalDays} game days. Call off takes back your proposal, and either side may call off a regular deal; caravans already on the way go on.

### A delivery

When a deal is accepted and on the day of every delivery, the payment leaves the buyer's treasury (or its reserves of the seller's currency) into escrow, the goods leave the seller's storehouses, and a caravan travels between the capitals at {cfg:trade.caravanBlocksPerMinute} blocks a minute, faster on finished [roads](roads). The view "caravans", the [map](atlas) and map mods show it. On arrival the goods go into the buyer's storehouses and the payment to the seller. Without room at the buyer's, the caravan waits up to {cfg:trade.caravanMaxWaitMinutes} minutes, then the deal is closed: the payment goes back to the buyer and the goods to the seller. A regular deal whose delivery could not leave three times in a row is called off. Caravans survive restarts.

### Risks on the way

Near players a caravan walks the road in the world: a merchant with pack llamas. The danger on the way is [robbers](bandits): next to their camp a hundred blocks across country carry a chance of an ambush of up to {cfg:roads.ambushChance*100}%, less on roads and on the land of a kingdom. A caravan without guards loses {cfg:roads.unguardedLoot*100}% of its goods, the payment for what was taken goes straight back to the buyer, and the seller's merchant may die. Without camps nearby caravans are never lost.

A squad of the army can be attached to a deal: it goes with each of its caravans and fights the robbers (see [Squads and commanders](squads)). The Fair Wind spell saves a caravan {cfg:magic.tailwindShare*100}% of its remaining way (see [Spells](spells)). In a [war](war) the deals between enemies are frozen.

> A regular deal is handy for what your land lacks: iron from a mountain kingdom every 3 days, for example.
