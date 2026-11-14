# Getting started
@icon kingdomworld:guide_book

## welcome | About the mod and this guide
@icon kingdomworld:guide_book
**KingdomWorld** adds kingdoms to Minecraft 1.20.1 (Forge) that live and grow all over the world. Any player can found a kingdom of their own: set up a throne, claim land, wait for citizens, give them work and raise a real country around the throne, with money, an army, laws, cities and roads. From the first minutes of a world the ancient kingdoms of the world live nearby as well: no player rules them, but rulers with characters of their own, and you can trade with them, befriend them and fight them.

### What the mod has

- [The kingdom](founding): the throne, player subjects, positions and rights.
- [Land](territory): chunks, provinces and protection from outsiders.
- [Citizens](citizens): people with professions, food, homes, moods and a daily routine.
- [Work posts](work_posts) and [storehouses](storehouses): gathering, carriers, [workshops](workshops) and [the king's orders](orders).
- [Construction](construction): buildings from blueprints in eight [architectural styles](styles).
- [Money](coins): a currency of your own, [the mint](mint), [the treasury](treasury), taxes, [the market](market), [deals and caravans](deals).
- [The adventurers' guild](guild): ranks F-SSS, [monster crystals](crystals), contracts, companions and parties.
- [Magic](energy): the energy of a kingdom, the mage tower, staffs, spells and gates.
- [The army](army): warriors and archers, squads, tactics and orders on the map.
- [War and nobility](diplomacy): treaties, wars, sieges, noble houses, intrigue and spies.
- [Law](law): crimes, witnesses, guards, fines and the court.
- [Cities and roads](cities): town halls, walls, roads and robbers.
- [The world](world_kingdoms): states with provinces, living settlements, taverns, rumors and festivals.
- [The map](atlas): the kingdom atlas and map mods.

### How to use this guide

- **On the left** is the tree of sections and pages: click a page to open it.
- **At the top** is the search: type one or more words and the guide lists the pages whose title or text holds all of them. Letter case does not matter, and the Russian letters "yo" and "ye" count as one. Every page found shows a piece of its text with the words found.
- **Links** in the text open other pages, and the "Back" and "Forward" buttons take you along the way you came, as in a browser. Alt+Left, Backspace and mouse button 4 go back too, Alt+Right and mouse button 5 forward.
- **The mouse wheel** scrolls a long page.
- **Item icons**: hover an icon to see the tooltip of the item. Crafting grids show the real recipes of your game: if a modpack or a data pack replaced a recipe, you see the new one here, and if one was removed, the guide says so.
- **Numbers from the server settings** (limits, prices, times) come from the server you play on; without a server the guide shows the defaults. So the numbers on your server may differ from what others tell you.

### How to open this guide

- The book "Kingdoms: a Guide" {item:kingdomworld:guide_book}: every player gets it once, on first joining the world (unless the server turned that off). Right-click with the book in hand. It can be crafted from a book and a gold ingot.
- The command **/kingdom guide** opens this same guide, and gives you the book again if you carry none.
- The key **"Guide"**: unbound by default; bind it in the controls, category "KingdomWorld".

{recipe:kingdomworld:guide_book}

> Just starting? Open [First steps](first_steps): it covers the whole way from crafting a throne to your first citizens.

## first_steps | First steps
@icon minecraft:red_bed
The way from a lone player to a kingdom with its first citizens takes a few steps. Only one thing is hard at the start: the Founder's Crown needs a nether star, which the Wither drops. Everything else is made of common materials.

### 1. Throne and crown

Craft a **Throne** {item:kingdomworld:throne} (gold ingots, red wool and planks shaped like a chair) and a **Founder's Crown** {item:kingdomworld:founder_crown} (diamond blocks, a nether star and gold blocks).

{recipe:kingdomworld:throne} {recipe:kingdomworld:founder_crown}

### 2. A place

Press K and look at the map: the ancient kingdoms of the world hold huge areas, and a throne may not stand on the land of another kingdom or within {cfg:kingdom.minChunksFromOtherKingdom} chunks of it. By default kingdoms are founded in the overworld only. The conditions in full are in [Founding a kingdom](founding).

### 3. Founding

Place the throne: an empty throne is only decoration, and the game says so in one message. Hold the crown and right-click the throne. Enter a name, pick a color and an architectural style, and press **"Found"**. The crown is used up, the throne becomes the heart of the kingdom and you become its king. The kingdom owns at once the square of chunks of radius {cfg:kingdom.initialClaimRadius} around the throne's chunk (radius 1 gives a 3x3 square).

### 4. Storehouse and food

Craft a **Storehouse** {item:kingdomworld:storehouse} and place it on your land: it is the shared chest of the kingdom, citizens eat from it and bring their gains to it. Put food in it. A newcomer arrives only while the storehouses hold food for everybody for {cfg:citizens.growthFoodDays} days, and one citizen eats {cfg:citizens.dailyNutrition} food points a game day (a loaf of bread gives 5). More in [The storehouse](storehouses) and [Food](food).

{recipe:kingdomworld:storehouse}

### 5. Beds

Place beds on your land, one for every future citizen: a bed is a home. While the kingdom has no houses any bed on its land will do; later citizens live in buildings.

### 6. The first citizens

With a free bed and enough food a new citizen comes to the throne by itself, at most once every {cfg:citizens.growthIntervalMinutes} minutes (happy citizens come sooner, unhappy ones later). Why nobody comes is written in the Citizens tab of the throne screen. More in [Arrival and growth](growth).

### 7. Work

A new citizen arrives without work. Place a **Work Post** {item:kingdomworld:work_post}, right-click it, pick a profession and press "Hire…". Workers take tools and armor from the storehouses, so put an axe, a pickaxe or a fishing rod there. More in [The work post](work_posts) and [Professions](professions).

### 8. What next

- Every living citizen adds {cfg:territory.chunksPerCitizen} chunks to the land limit: widen your borders with the Territory button of the throne screen ([The limit of chunks](chunk_limit), [Claiming and releasing land](claiming)).
- Invite your friends as subjects and hand out positions ([Subjects and applications](members), [Positions](positions)).
- Place an **Infirmary** {item:kingdomworld:infirmary}: the wounded and the sick heal faster in it ([Health, the infirmary and death](health)).
- Build houses and workshops from blueprints ([How a kingdom builds](construction)) and strike your own coin ([The mint](mint)).

> Tip: keep food for at least a few days and a couple of spare tools in the storehouse. Hungry citizens grow sad and stop arriving, and a worker without a tool stands idle.

## keys | Keys and windows
@icon minecraft:tripwire_hook
All keys of the mod are in the controls, category **"KingdomWorld"**, and are rebound there. They work in the game while no window is open.

| Key | What it does |
|---|---|
| K | Kingdom map |
| J | Window of the law |
| P | Party window |
| R | Blueprint: turn the building |
| M | Blueprint: mirror the building |
| unbound | Window of the workers |
| unbound | Guide |

- **K** opens the kingdom map, and pressing it again closes it. The Kingdom Atlas {item:kingdomworld:kingdom_atlas} in hand does the same ([The atlas and the map](atlas)).
- **J** opens the window of the law: for every kingdom your wanted state, reputation, debt with a "Pay off" button and pass, confiscated goods with their price and the place to buy them back, and paying the entry of cities by itself ([The law of a kingdom](law), [Wanted](wanted)).
- **P** opens the window of your party of players ([Parties of players](parties)).
- **R** and **M** work while a Blueprint {item:kingdomworld:blueprint} is in hand and the outline of a building shows on the ground: R turns it a quarter, M mirrors it ([Blueprints and the catalog](blueprints)).
- **Window of the workers** shows who does what and who lacks what. Modpacks have few free keys, so it is unbound by default. It needs the Citizens right ([The Workers window](workers_window)).
- **Guide** opens these pages. Unbound by default.

### The mouse in the windows of the mod

Buttons that go through choices (profession, position, view of a tab, filter, ore, priority, kind of wood, goal of a war and many more) turn both ways: a left click takes the next choice, a right click the one before. The tooltip of such a button ends with the line "Right click: back". On the maps the mouse wheel zooms and dragging moves the map.

### Windows of the mod

- **Throne screen**: right-click the throne of a kingdom ([The throne and its window](throne)). The founding screen opens on a right click at an empty throne with the crown in hand ([Founding a kingdom](founding)).
- **Kingdom map** and its modes: editing land with the Territory button ([Claiming and releasing land](claiming)), planning a road ([Roads](roads)), the army on the map ([Orders on the map](map_orders)).
- **Window of a work post**: right-click the post ([The window of a post and hiring](post_window)). **Window of the workers**: its key, the "Workers…" button in the Citizens tab, or /kingdom workers.
- **Storehouse**: right-click it; a double storehouse has one shared window ([The storehouse](storehouses), [The double storehouse](double_storehouse)).
- **Bank**, **market stall**, **guild desk**, **barracks**, **magic focus** and **magic gate** open with a right click at their block ([The bank](bank), [The market stall](stall), [The guild and its desk](guild), [The barracks](barracks), [The magic focus](focus), [Magic gates](magic_gate)).
- **Tavern**: right-click the innkeeper ([The tavern](tavern)).
- **Party window** and **companion window** ([Parties of players](parties), [Companions](companions)).
- **Window of the law** (J), and the windows of surrendering to the guards, paying entry at the gate of a city and buying back confiscated goods ([Fines and punishment](fines)).

## commands | Commands
@icon minecraft:command_block
Almost everything in the mod is done in windows, but many actions have a command too. Every player command starts with **/kingdom**. Angle brackets hold what you must fill in, square brackets what you may leave out. Rights are checked as in the windows.

### The kingdom and its subjects

- /kingdom info [name]: your kingdom, the one whose land you stand on, or the one named: ruler, subjects, chunks and their limit, capital, your position.
- /kingdom list: every kingdom with its ruler, number of subjects and chunks.
- /kingdom join <name>: apply to join. A kingdom of the world accepts at once.
- /kingdom accept [name]: accept an invitation (with only one, the name may be left out); /kingdom decline <name>: decline an invitation.
- /kingdom leave: leave your kingdom (the king cannot).
- /kingdom invite <player>: invite a player; needs the Recruiting right.
- /kingdom approve <player> and /kingdom reject <player>: accept or reject an application; needs the Recruiting right.
- /kingdom kick <player>: expel a member; the king expels anyone, others with the Recruiting right only plain subjects.
- /kingdom role <player> <position>: appoint a position, king only. Positions are written in English, the list is below.
- /kingdom abdicate <player>: hand the crown to another member of the kingdom.
- /kingdom dissolve, then /kingdom dissolve confirm within 30 seconds: dissolve the kingdom, king only.
- /kingdom conquest swear or /kingdom conquest free: after your kingdom was annexed, become a subject of the winner or stay free (three days to answer).

| Position | In the command |
|---|---|
| Marshal | marshal |
| Treasurer | treasurer |
| Chancellor | chancellor |
| Spymaster | spymaster |
| Court Mage | court_mage |
| Steward | steward |
| Subject | subject |

### Land

- /kingdom claim: claim the chunk you stand in; /kingdom unclaim: give it up. Needs the Territory right ([Claiming and releasing land](claiming)).
- /kingdom province price [id]: the price of the province you stand in, or of the one named, and why it is not for sale.
- /kingdom province buy <id>: buy a province for your kingdom (the Treasury and Territory rights) or buy a charter when you belong to no kingdom ([Buying provinces and charters](buying_land)).

### Citizens, windows and parties

- /kingdom citizens: the citizens of your kingdom (or of the one whose land you stand on): head count, beds, food and mood.
- /kingdom workers: the window of the workers; needs the Citizens right.
- /kingdom law: the window of the law, like the key J.
- /kingdom guide: open this guide anywhere; if you carry no book, you get it again.
- /kingdom party: the window of your party of players, like the key P.
- /kingdom party invite <player>, and also kick <player> and leader <player>: invite to the party, expel from it or make leader.
- /kingdom party accept: accept the invitation to a party its leader sent you.
- /kingdom party decline: decline an invitation to a party you do not want to join.
- /kingdom party leave: leave the party you are in.
- /kingdom party fire <true|false>: the leader of the party allows or forbids hurting each other within the party.
- /kingdom party say <text>, or shorter /party <text>: a message to the party chat.

### Money

- /kingdom balance: your accounts in every currency and your debts to treasuries.
- /kingdom pay <player> <amount> [kingdom]: pay another player in the currency of your kingdom or of the one named. A payment above {cfg:economy.payConfirmAbove} must be confirmed with /kingdom pay confirm.
- /kingdom treasury: the treasury of your kingdom; /kingdom treasury pay <player> <amount>: pay from the treasury (the Treasury right).
- /kingdom treasury deposit <amount> [kingdom]: put money from your account into a treasury; your debt is paid off first.
- /kingdom value [item]: the worth of the item in hand or of the one named ([Coins and currency](coins)).
- /kingdom trophies: the trophies your kingdom may buy back or holds; /kingdom trophies buy <id>: buy one back (the Treasury right, [Trophies and ransom](trophies)).

### Operator commands

Operators (permission level 2) have **/kingdomworld admin** with subcommands for tests and repairs of the world. In short, by group:

- kingdoms: found (set up a throne and found), delete, setruler;
- land: claim and unclaim without rights or limit, owner (whose chunk), bypass (bypass protection until the server restarts);
- provinces and the plan of states: province fill, give, info, clear (fill a rectangle, change the holder, look, abolish) and land (information, unlocking, the progress of the plan, the survey of the land);
- the state of the data and leaving read-only mode: data status and data unlock, and every mechanic has such commands of its own;
- citizens and their work: citizens, posts (work posts), orders, recipes (of workshops), storage (storehouses), simulate (steps of the simulation at once);
- money and trade of kingdoms: economy (accounts and treasuries) and trade (market, deals, caravans);
- the adventurers' guild and companions: guild and companions;
- construction and the army: build and army; magic: magic; roads and robbers: roads;
- kingdoms of the world: npc; the life of cities: life; law: law; war: war.

Names of several words are put in quotes in operator commands, for example "Old Valoria".
