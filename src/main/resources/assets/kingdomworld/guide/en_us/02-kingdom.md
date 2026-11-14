# The kingdom
@icon kingdomworld:throne

## founding | Founding a kingdom
@icon kingdomworld:founder_crown
A kingdom is founded by a player who has a **Throne** {item:kingdomworld:throne} and a **Founder's Crown** {item:kingdomworld:founder_crown}. The throne becomes the heart of the kingdom and its founder the king.

{recipe:kingdomworld:throne} {recipe:kingdomworld:founder_crown}

The throne is made of gold ingots, red wool and planks shaped like a chair. The crown: a diamond block, a nether star and a diamond block above three gold blocks. The nether star drops from the Wither, so the crown is the first big goal. The recipes come from a data pack and a server may replace them: the grids above always show the ones in force.

### Conditions

- You belong to no kingdom. To found your own, leave the old one first.
- The throne stands in an allowed dimension. By default that is the overworld only; the key allowedDimensions in the server config holds the list.
- The throne does not stand on the land of another kingdom, its provinces included.
- No land of another kingdom lies within {cfg:kingdom.minChunksFromOtherKingdom} chunks of the throne's chunk. The distance is square: a chunk diagonally next to it is at distance 1 too.
- The name is free: no two kingdoms share a name, and letter case does not matter.
- You stand next to the throne, no farther than 8 blocks.

There is one exception: a charter for a province. If you bought a charter (see [Buying provinces and charters](buying_land)), the land of that province is no foreign land to your throne, while the land around it is checked as always.

### How to found

1. Place the throne. An empty throne is only decoration, and if you belong to no kingdom the game tells you once: "Take the Founder's Crown and right-click the throne to found a kingdom".
2. Hold the crown and right-click the throne. Without the crown in hand you only get "Hold a Founder's Crown to found a kingdom".
3. In the **"Found a kingdom"** screen enter a name: 3 to 32 characters, letters of any alphabet, digits, spaces, hyphen, apostrophe and underscore, starting with a letter or a digit. Extra spaces are removed, and the screen shows a mistake at once.
4. Pick a **color** out of 16 (a random one is chosen at first). The land of the kingdom on the map, the middle of its coins and the banners of its great buildings wear it, and the default flag is a banner of this color, shown under the colors (see [The flag](flag)).
5. Pick an **architectural style**: each of the eight styles has a picture, and the one marked "Suits the land" fits the climate around the throne and is chosen at first (see [Architectural styles](styles)).
6. Press **"Found"**. The server checks everything again, and on a refusal the screen names the reason.

### What you get

- The crown is used up (in creative mode it stays).
- The throne becomes the heart of the kingdom and cannot be broken while the kingdom lives.
- The kingdom owns at once the square of chunks of radius {cfg:kingdom.initialClaimRadius} around the throne's chunk (radius 1 gives a 3x3 square). Chunks owned already or too close to foreign land are left out. More in [The land of a kingdom](territory).
- Every player on the server gets a message about the founding.
- The kingdom has a currency and a treasury of its own (see [Coins and currency](coins)). In a new kingdom every subject but the king pays a daily tax of {cfg:economy.defaultPollTax} (see [Taxes](taxes)).
- The [throne screen](throne) opens.

> The crown is needed only for founding. Handing the throne to another player needs no crown: see [The crown, abdication and dissolving](crown).

## throne | The throne and its window
@icon kingdomworld:throne
The Throne {item:kingdomworld:throne} of a founded kingdom is its heart. Right-click it to open the **throne screen**. Any player may open it, but each sees something different:

- an **outsider** sees only Overview and the "Ask to join" button;
- an **invited player** sees the "Accept invitation" and "Decline" buttons;
- a **player of another kingdom** sees the overview with the line "You serve another kingdom";
- a **member** sees every tab, and the [rights of their position](rights) decide what they may do there.

The server checks every action of the screen: without the right it does not happen, and the answer shows at the bottom of the screen.

### The Overview tab

The lines of the overview: the ruler, the number of members and citizens, the land ("Land: X of Y chunks", with provinces "Land: N chunks, of them X claimed of Y"), the position of the throne, the style and your position. Buttons at the bottom:

- **"Territory"** opens the map for editing land (see [Claiming and releasing land](claiming));
- **"Flag"** opens the flag window, and every player sees it (see [The flag](flag));
- **"Leave kingdom"** for members, or a red **"Dissolve kingdom"** for the king, both with a confirmation (see [The crown, abdication and dissolving](crown)).

The king sees a **"Change style"** button: with it he goes through the styles and picks a new one. New buildings follow the chosen style, those standing stay as they are, and the treasury pays {cfg:kingdom.styleChangeCost} copper coins.

In the line of the throne the king sees a **"Move throne"** button: with it the throne moves to another place of the kingdom's land, a throne hall for example (see [Moving the throne](throne_move)). While a move goes on, the line of the throne says "(being moved)" and the button becomes **"Cancel move"**.

The throne of a kingdom of the world shows, instead of a player ruler, the size of the ancient kingdom, the leaning of its ruler, its people, the ranks of its guilds, the number of its settlements, the latest decree, its needs and its attitude to you. Hover a line to see more. Such a ruler takes every subject who applies, and its throne is taken only by war (see [Rulers of the world](rulers)).

### Tabs of a member

- **Members**: members, positions and applications ([Subjects and applications](members)).
- **Citizens**: head count, beds, food, mood, the list of citizens with professions, the chronicle and the "Workers…" button ([Who the citizens are](citizens), [The Workers window](workers_window)).
- **Storehouses**: what the storehouses of the kingdom hold ([The Storage tab](storage_tab)).
- **Orders**: orders for the workshops ([The king's orders](orders)).
- **Treasury**: money, the books, taxes, wages and minting ([The treasury](treasury), [Taxes](taxes), [Wages](wages)).
- **Market**: what the market of the kingdom sells and buys ([The market](market)).
- **Trade**: export, import, exchange rates, deals and caravans ([Export and import](export_import), [Deals and caravans](deals)).
- **Guild**: the branch of the adventurers' guild ([The guild and its desk](guild)).
- **Construction**: the catalog of buildings, constructions and the register of buildings ([How a kingdom builds](construction)).
- **Army**: warriors, squads and mercenaries ([The army of a kingdom](army)).
- **Magic**: energy, research and spells ([The energy of a kingdom](energy), [The mage tower and mages](mage_tower)).
- **Roads**: cities, walls and roads ([Cities](cities), [Roads](roads)).
- **Law**: the wanted, the court and the laws of the kingdom ([The law of a kingdom](law), [The court](court)).
- **Diplomacy**: relations, treaties and wars ([Diplomacy](diplomacy), [War](war)).
- **Nobility**: noble houses, titles, factions and spies ([Nobility and factions](nobility), [Intrigue and espionage](intrigue)).
- **Rights**: what each position may do ([The rights of positions](rights)).

The tabs stand in one or more rows by the width of the screen. On the narrowest screen some labels are shorter ("Main", "Court", "Stores", "Envoys"), and the full name shows on hover. Buttons that go through choices turn forward on a left click and back on a right click.

### Protection of the throne

The heart of a kingdom never breaks: anyone who tries sees "The heart of a kingdom cannot be broken", and the king gets the hint "Move the throne from its screen or dissolve the kingdom". A throne can be taken away only by war (see [Capture and destruction](capture)), and its king may [move it](throne_move).

## throne_move | Moving the throne
@icon kingdomworld:throne
When a throne was set up badly or belongs in a throne hall, the king moves it to another place of the kingdom's land. The kingdom is never without a heart, not for a minute: the old throne works until the new one stands.

### How to move it

1. Open the throne screen and on the Overview tab press **"Move throne"** in the line of the throne. Only the king of a kingdom of players sees the button.
2. The screen asks for a confirmation and tells what comes next.
3. You get a **throne for the move** {item:kingdomworld:throne}: it shines, and its tooltip tells which kingdom it belongs to and that it is a move. With a full inventory the throne drops at your feet.
4. Place it on any block of the kingdom's claimed land, in the dimension where the throne stands. At that very moment the capital moves there, and the old throne vanishes without a drop.

### What moves with the throne

- **The heart of the kingdom**: the new throne does not break, and the old place becomes an ordinary one.
- **The main chunk**: the chunk of the new throne cannot be given up, and the chunk of the old one becomes an ordinary claim, which may go when the land does not fall apart without it (see [Claiming and releasing land](claiming)).
- **The atlas and map mods**: the mark of the capital stands at the new throne at once.
- **Sieges**: the enemy takes the throne hall at the new throne (see [Capture and destruction](capture)).
- **The capital**: new citizens come to the new throne, and the area of a capital without a wall is counted from it. A capital with a wall stays the square of its wall (see [Cities](cities)).
- **Roads**: new roads from the capital start at the new throne.
- The throne screen, the commands and the distances to the capital.

What is built stays where it is: roads, walls, gates and buildings do not move.

### When it cannot be done

- Only the king places the throne for the move. A member or a stranger sees "Only the king may do this", and keeps the throne.
- Only on the claimed land of your kingdom: not on land of nobody, not on the land of another kingdom and not in another dimension. In the wilds of your own province, its unclaimed chunks, the throne does not stand either. A throne for a move founds no new kingdom.
- While the kingdom is at war (declared or already fought) or besieged, a move can neither start nor finish. The throne for the move waits with you for the end of the war, and the move can be called off during the war too.
- At least {cfg:kingdom.throneMoveCooldownDays} days of game time pass between moves (a game day lasts 20 minutes, 0 lifts the limit). The refusal tells how many minutes are left.

Every refusal is told in a message, and the throne for the move stays with the player.

### Calling it off

Until the new throne stands, the line of the throne says "(being moved)" and its button is **"Cancel move"**. Once the move is called off, the throne for the move leaves the king's inventory, and anywhere else it vanishes with a message when somebody tries to place it, dropping nothing. So a throne lost or thrown away gives neither a second throne nor a copy. A new move can start at once: the one called off does not count.

> The rulers of the world do not move their thrones.

## flag | The flag
@icon minecraft:white_banner
Every kingdom has a flag. In the throne window it shows as two small flags beside the name of the kingdom: hover over them to see its layers, or click to open the **flag window**. The **"Flag"** button at the bottom of the Overview tab opens the same window, and every player sees it.

### The default flag

Until the king draws one, the flag of a kingdom is a plain banner of the dye nearest to the color of its name. So the color chosen at founding sets the flag too (see [Founding a kingdom](founding)).

### The flag window

Only the king of a kingdom of players changes the flag. Subjects, outsiders and players of other kingdoms see the flag and the names of its layers, without buttons.

- On the left is a large preview. While the mouse is over a pattern or a color, the preview shows what a click would make.
- The **"Layers"** column: the first row is the color of the field, then the layers in the order they are drawn, the lowest first, as the tooltip of a banner lists them. A click picks a row. A layer has three buttons: "Up", "Down" and "Remove".
- Under it is the **palette** of the 16 dyes of the game: a swatch paints the row picked.
- On the right is the **grid of patterns**: every pattern of the loom, the six patterns the loom needs an item for (globe, creeper, skull, flower, thing and snout) for free, and the patterns of other mods. A click puts the pattern on the layer picked. With the field picked or with Shift held, the pattern is added as a new layer on top in the color of the brush. The brush is at first the dye that stands out most on the field, then the swatch picked last.
- A flag holds at most 6 layers, as a banner from a loom.

The buttons at the bottom:

- **"Default"** brings back the plain flag of the color of the kingdom.
- **"Random"** draws a flag the way the kingdoms of the world get theirs, in the style of your kingdom.
- **"From banner"** takes the banner in your hand: the main hand first, then the other, and a shield with a banner works too. A banner of more than six layers gives its first six.
- **"Save"** saves the flag. It costs nothing and takes effect at once, and the subjects get a message.

Keys: Delete removes a layer, Ctrl+Up and Ctrl+Down move it, Up and Down pick a row, Ctrl+Z and Ctrl+Y undo and redo, Enter saves. While the flag is changed and not saved, "Back" and Esc first ask whether to leave without saving.

The window tells you when another kingdom already flies the same flag: that is allowed. A pattern of a mod that is gone shows as "Pattern of a missing mod" and is dropped the next time the flag is saved.

### Flags of the kingdoms of the world

A kingdom of the world gets its flag as soon as it appears: a field of its color and a motif of its architectural style, and the character of its ruler adds favourite patterns and colors. The flag never changes, not even after a coup, and cannot be changed (see [Kingdoms of the world](world_kingdoms)).

## members | Subjects and applications
@icon minecraft:player_head
**Subjects** are the players who belong to a kingdom. A player is a member of one kingdom at most. A newcomer gets the position "Subject", and the king gives another one (see [Positions](positions)). Every player subject other than the ruler adds {cfg:territory.chunksPerMember} chunks to the land limit (see [The limit of chunks](chunk_limit)) and pays a tax into the treasury (see [Taxes](taxes)).

### How to join

- **Application.** Press "Ask to join" in the screen of another throne, or type /kingdom join <name>. Members with the Recruiting right get a message with a button to accept you at once.
- **Invitation.** A member with the Recruiting right types /kingdom invite <player>. The invited player gets a message with buttons, or answers with /kingdom accept [name] and /kingdom decline <name>; the throne screen of that kingdom has the buttons too.
- When an application meets an invitation of the same kingdom, the player joins at once.
- Applications and invitations never expire: they wait for an answer, and the player sees them on logging in. A recruiter is told on logging in how many applications wait.
- A player has 5 open applications at most, a kingdom 50 applications and, apart from them, 50 invitations.
- A kingdom of the world accepts any application at once: its ruler takes everybody.

### The Members tab

- The list of members: a green dot by those online, a grey one by the others. Hover a name to see the player's rank in the adventurers' guild.
- **The position button** (only the king sees it): a left click sets the next position, a right click the one before.
- **"Expel"** (the Recruiting right): the king expels anyone, others only plain subjects. The screen asks for a confirmation first.
- **"Crown"** (king only): hand the crown to this member (see [The crown, abdication and dissolving](crown)).
- Below, if you have the Recruiting right, the list **"Applications (N)"** with "Accept" and "Reject" buttons. /kingdom approve <player> and /kingdom reject <player> do the same.

### Leaving and expelling

- A member leaves with the "Leave kingdom" button of the overview or with /kingdom leave.
- Expelling works with /kingdom kick <player> too. The expelled player gets a message.
- The king cannot leave: he hands the crown on or dissolves the kingdom first.

> Tip: give the Recruiting right to someone who is often online, so that applications do not wait for the king.

## positions | Positions
@icon kingdomworld:marshal_baton
Every member of a kingdom holds exactly one position. There is one king; any number of players may hold the other positions. The positions in order, from the highest:

| Position | Main defaults |
|---|---|
| King | everything, he alone |
| Marshal | army, guild, people, land |
| Treasurer | treasury, market, deals |
| Chancellor | people, court, diplomacy, nobility |
| Spymaster | secret service |
| Court Mage | mage tower |
| Steward | land, citizens, orders, construction |
| Subject | building and switches |

### The king

The king holds every right, and none can be taken from him. Only he appoints positions and changes the rights of the other positions. The crown passes only by abdication: the position "King" cannot be given with a button or a command.

### How to appoint

- In the Members tab of the throne screen the king presses the position button in the row of a member: a left click gives the next position, a right click the one before.
- Or with /kingdom role <player> <position>, the position written in English as in the table on the page [Commands](commands).
- Nobody changes their own position, nor that of the king.
- A new position arrives as a message: "You are now ... of ...".

### What a position does

A position is a set of rights. The king may arrange the rights any way he likes in the Rights tab (see [The rights of positions](rights)), and then "marshal" means what the king decided. By default:

- **Marshal**: the army and mercenaries, the adventurers' guild, recruiting, territory, and also building, containers and switches.
- **Treasurer**: the treasury, the market, deals with kingdoms, and also building, containers and switches.
- **Chancellor**: recruiting, deals, the court, diplomacy and nobility, and also building, containers and switches.
- **Spymaster**: the secret service, and also building, containers and switches.
- **Court Mage**: the mage tower, and also building, containers and switches.
- **Steward**: territory, citizens, orders, the market and construction, and also building, containers and switches.
- **Subject**: building and switches only.

By default only the king may declare war, until he gives that right to some position.

> In a kingdom of the world the ruler may name a subject of good reputation an honorary councillor. It is an honour without rights: the councillor stays a plain subject.

## rights | The rights of positions
@icon minecraft:writable_book
Rights decide what a member may do on the land of the kingdom and in its windows. The king always holds every right. The king hands the others out in the **Rights** tab of the throne screen.

### The Rights tab

The rows are the positions (the king left out), the columns the rights under short labels. A cell holds a green tick when the right is held and a red cross when not; the full name of the right shows on hover. When the columns do not fit, arrows page through them. Clicking a cell grants or revokes the right, and the answer shows at the bottom, for example "Marshal: Treasury granted". Only the king changes rights; the others see the table with the line "Only the king changes rights".

### Default rights

| Right | Held by |
|---|---|
| Building (Build) | every position |
| Containers (Chests) | all but the subject |
| Switches and frames (Use) | every position |
| Recruiting (People) | marshal, chancellor |
| Territory (Land) | marshal, steward |
| Citizens (Cit.) | steward |
| Orders (Ord.) | steward |
| Treasury (Trs.) | treasurer |
| Market (Mkt.) | treasurer, steward |
| Deals (Deals) | treasurer, chancellor |
| Guild (Guild) | marshal |
| Construction (Build) | steward |
| Army (Army) | marshal |
| Mage tower (Magic) | court mage |
| Court (Law) | chancellor |
| Diplomacy (Treaties) | chancellor |
| Declare war (War) | nobody |
| Nobility (Nobles) | chancellor |
| Secret service (Spies) | spymaster |
| Appointments | the king alone |

### What each right gives

- **Building** (Build): break and place blocks, use buckets, fire and tools on the land of the kingdom (see [Protection of the land](protection)).
- **Containers** (Chests): open chests, furnaces, machines and storehouses and take from them. Without the right a container opens too, but the law counts what is taken from it as a theft.
- **Switches and frames** (Use): buttons, levers, repeaters and comparators, daylight sensors, note blocks, item frames and armor stands, the leaves of city gates. Doors, trapdoors and fence gates open to everybody.
- **Recruiting** (People): accept and reject applications, invite and expel subjects.
- **Territory** (Land): claim and give up chunks; together with Treasury, buy provinces.
- **Citizens** (Cit.): give citizens their professions, the window of the workers, setting up posts from afar.
- **Orders** (Ord.): give, change and cancel orders for the workshops.
- **Treasury** (Trs.): pay from the treasury, set taxes and wages, decide which metals to strike, buy back trophies.
- **Market** (Mkt.): what the market sells and buys, prices and taxes, export abroad.
- **Deals with kingdoms** (Deals): propose, accept and call off deals.
- **Adventurers' guild** (Guild): run the branch of the guild, post its contracts and send its squads.
- **Construction** (Build): lay out buildings, pause, hurry and cancel constructions. Do not mix it up with Building: that one is about blocks, this one about buildings.
- **Army** (Army): recruit and dismiss soldiers, form squads, give orders, hire mercenaries.
- **Mage tower** (Magic): research, the spells of the kingdom, the ward, gates and generators.
- **Court** (Law): pardons, lowering fines, sparing murderers, fines, the entry fee and the ban of slavery.
- **Diplomacy** (Treaties): propose, accept and break treaties, answer allies and offer peace.
- **Declare war** (War): declare war on another kingdom.
- **Nobility** (Nobles): grant, take back and hand over titles, answer the parties at court, judge plotters.
- **Secret service** (Spies): hire spies, send them abroad, hunt foreign ones, pay ransoms.
- **Appointments**: the king's alone; it cannot be given away.

> The server checks the right on every action. If a button does nothing and the bottom of the screen says "Your position does not allow this", ask the king for the right.

## crown | The crown, abdication and dissolving
@icon minecraft:golden_helmet
### The Founder's Crown

The Founder's Crown {item:kingdomworld:founder_crown} is needed only for founding and is used up by it. Every new kingdom needs a new crown (see [Founding a kingdom](founding)).

### Abdication

The king may hand the crown to another member of the kingdom:

- in the Members tab with the **"Crown"** button in the row of the heir. The screen asks: "Hand the crown to ...? You become a subject.";
- or with /kingdom abdicate <player>, the player being your heir among the members.

The heir becomes king with every right, the old king a plain subject. Every player on the server gets the message "... has a new ruler". There is no other way to pass the crown: the position "King" can be given neither with a button nor with /kingdom role.

### The king cannot just leave

The king has no "Leave kingdom" button, and /kingdom leave answers: "The king cannot leave: abdicate or dissolve the kingdom".

### Dissolving

Only the king may dissolve the kingdom:

- with the red **"Dissolve kingdom"** button of the Overview tab and a confirmation;
- or with /kingdom dissolve and then the confirmation /kingdom dissolve confirm: it must come within 30 seconds, otherwise start again.

Dissolving is for good: the members lose the kingdom, its land becomes free, its bought provinces become nobody's, and the throne stops being a heart. The coins of its currency stay with players but turn old: they cannot be spent, and a minter melts them back into metal (see [Coins and currency](coins)). Every player on the server learns of it.

### Losing the throne in war

The throne of a kingdom can be taken only by a war with the goal "throne", and an annexation may take the kingdom as a whole (see [War](war), [Capture and destruction](capture)). A player whose kingdom was annexed has three days to decide whether to become a subject of the winner (/kingdom conquest swear) or to stay free (/kingdom conquest free).
