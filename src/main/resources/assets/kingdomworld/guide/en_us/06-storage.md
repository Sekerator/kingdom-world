# Storehouses and workshops
@icon kingdomworld:storehouse

## storehouses | The storehouse
@icon kingdomworld:storehouse
The **Storehouse** {item:kingdomworld:storehouse} is the shared chest of a kingdom, with 54 slots. What it holds is kept
not in its chunk but in the kingdom data, so citizens eat from it and bring their harvest there even when no player is
anywhere near.

{recipe:kingdomworld:storehouse}

Crafting: logs in the corners, planks in the middle of the top and bottom rows, and a chest, a barrel and a chest in the
middle row.

### Where to place it

A storehouse works on the land of a kingdom: on its claimed chunks and in its provinces (see [The land of a kingdom](territory)). A storehouse in the wilds of a kingdom of the world, where it does not live, belongs to nobody: the
kingdom does not count it. Whoever may build on the land may place one. Where exactly it stands does not matter: the
kingdom sees the sum of all its storehouses at once, even when their chunks are not loaded. Still, put storehouses near
the work posts, so that workers and carriers walk less.

### Who opens it

Anybody can open a storehouse, like any chest. But taking from it without the right **Containers** is stealing from
the treasury: the fine for such a theft is {cfg:law.treasuryFinePercent}% of the fine for a common theft, and a soldier
within {cfg:law.storehouseWatchBlocks} blocks of the storehouse notices it at once, without seeing the thief (see
[Crimes](crimes)). By default the king and every position except the plain subject hold the right Containers; the king
changes that in the Rights tab (see [The rights of positions](rights)).

### What goes through the storehouses

- the food of the citizens, and the stock of food newcomers wait for: food for everybody for
  {cfg:citizens.growthFoodDays} days (see [Food](food), [Arrival and growth](growth));
- what posts gather: a post without a container of its own makes its worker carry the harvest to the nearest storehouse,
  otherwise [carriers](carrier) move it;
- the tools and armor of workers, the materials and fuel of [workshops](workshops), the materials of
  [construction](builders);
- the goods of the [market](market), the metal of the [mint](mint), the loads of [caravans](deals).

Hoppers, Create funnels and chutes and the pipes of other mods put items in and take them out as from any chest, and a
comparator reads how full it is. A broken storehouse drops everything it held.

> Without storehouses the posts say "Nowhere to deliver: no storehouse", and without room "The storehouses are full".
> Carriers empty a nearly full storehouse into an emptier one further away by themselves, but add room in time.

> While the kingdom data is read-only (after a failed load) a storehouse does not open and says "Kingdom data is
> read-only: the storehouse is closed".

Chests, barrels and crates inside a building with the role Warehouse count as storehouses of the kingdom too, while
their place is loaded (see [The roles of buildings](building_roles)). Two storehouses side by side can be joined into one
large one: see [The double storehouse](double_storehouse).

## double_storehouse | The double storehouse
@icon minecraft:chest
Since version 1.5.0 two storehouses side by side join the way two chests make a large chest. The result is a
**Large Storehouse** of 108 slots with one shared model and a single lock in the middle, on the seam of the halves.

### How to join them

1. Place the first storehouse {item:kingdomworld:storehouse}. Like a chest, it is placed facing you.
2. Stand facing the same way and place the second one right to its left or right, at the same height.
3. Two storehouses of one kingdom that face the same way become one large storehouse at once.

- To place a storehouse beside another but apart, place it while sneaking (Shift).
- Storehouses of different kingdoms never join. A half that turns out to stand on the land of another kingdom works as a
  storehouse of its own.
- Storehouses that stood side by side before version 1.5.0 stay single. To join them, break one (its contents drop, pick
  them up) and place it again.

### The window

Either half opens one shared window, "Large Storehouse": both halves side by side, 18 slots a row in 6 rows, your
inventory below. Shift clicks and sorting work across the whole window. On the left of the window is the half that
stands on the left as you look at the front of the storehouse.

### How it works

| What | How |
|---|---|
| Contents | each half keeps its own |
| Joining | moves nothing |
| The kingdom counts | every item once |
| Breaking a half | drops only its own |
| The other half | a single storehouse again |

Each half still keeps its own 54 slots in the kingdom data. So joining moves and doubles nothing: the storehouses, the
reservations of workshops, the market and the meals of citizens count every item exactly once. In the
[Storehouses tab](storage_tab) the halves show as two storehouses of 54 slots.

Hoppers, pipes and comparators see both halves through either: first the left one as you look at the front (its block
data says type=right, as with a vanilla chest), then the right one.

> Construction that clears a place or looks into the block of a half takes only that half's contents. Looting in a war
> counts the pair as one container (see [Capture and destruction](capture)).

## storage_tab | The Storehouses tab
@icon kingdomworld:throne
The **Storehouses** tab of the throne window shows what all the storehouses of the kingdom hold together. Every member
of the kingdom sees it; it is only for looking, and the storehouses themselves are opened in the world (see [The throne and its window](throne)).

### Items

On top are a search field and the summary "Kinds: N, items: N, storehouses: N". The search looks at the name of an item
in your language and at its id (for example minecraft:iron). The list shows the icon, the name and the count. When part
of a kind is already set aside for the work of workshops or constructions, the row says "64, 16 set aside". What is set
aside is taken neither by other workers, nor by hungry citizens, nor by the market until the work is over.

### Storehouses

The **Storehouses** button switches the list to the storehouses themselves: "Storehouse at X Y Z: A of B slots used".
Each half of a [double storehouse](double_storehouse) is a row of its own, and the containers of buildings with the role
Warehouse are listed too. The Items button brings the goods back. The list scrolls with the mouse wheel.

- The lists show up to 512 kinds of items and up to 64 storehouses.
- Empty storehouses: "The storehouses are empty. Workers and carriers bring what they gather here."
- No storehouse at all: "The kingdom has no storehouse on its land yet".

### Where else stocks show

- The Citizens tab shows the food in days, and its production view what was gathered and made today and yesterday (see
  [Who the citizens are](citizens)).
- The [Workers window](workers_window) says on top for how many days the storehouses hold food, and what workers lack:
  a tool, room in the storehouses, saplings.
- The Orders tab shows the stock of every "Keep in stock" order (see [The king's orders](orders)).

## workshops | Workshops
@icon minecraft:furnace
A workshop is a [work post](work_posts) {item:kingdomworld:work_post} with one of four professions and workstations in
its zone. Workshop workers make things out of what lies in the storehouses and put the results back there.

| Profession | Workstations |
|---|---|
| Smelter | furnace, blast furnace |
| Cook | smoker, campfire, furnace, crafting table |
| Crafter | crafting table, stonecutter |
| Smith | crafting table, smithing table |

- The **smelter** smelts ore, raw metal and everything else that smelts and is not food (glass, bricks).
- The **cook** roasts and smokes food and makes at the crafting table whatever gives food: bread, stews and the like.
- The **crafter** makes everything else from crafting table and stonecutter recipes: planks, torches, stairs.
- The **smith** makes tools, weapons and armor at the crafting table and upgrades at the smithing table (netherite gear,
  for one).

Recipes come from the game itself, so recipes, furnaces, tools and weapons of other mods just work. Left out are special
recipes (fireworks, map copies, dyed armor), recipes that depend on the data of an item, and melting tools down to
nuggets. Furnaces of mods count when they are built on the vanilla furnace or work its way (input from above, fuel from
the side, result from below); tables of mods built on the vanilla crafting table count too.

### Setting up a workshop

1. Place a work post and choose the profession in its window (see [The window of a post and hiring](post_window)).
2. Put the workstations in the zone of the post: its radius is {cfg:work.workPostDefaultRadius} blocks, up to
   {cfg:work.workPostMaxRadius}.
3. Put a chest or a barrel right next to the post: [carriers](carrier) bring the materials there.
4. Press Hire…: a post has up to {cfg:work.workPostMaxSeats} seats.

### How the work goes

Near players a worker sets the ingredients aside in the storehouses, takes them from the container of the post or
fetches them itself, works by hand at tables, and really fills a furnace from above and the side and empties it from
below. A furnace whose output holds some other item is left alone. Any fuel that burns is used (coal first), except
tools, food and the ingredients themselves; a campfire needs none. The worker carries the result to a storehouse.

Far from players each thing is made in one go: the ingredients and fuel leave the storehouses and the result goes there
in the same tick, or nothing happens at all. Without room in the storehouses the work stands still. A post without a
container next to it, or a kingdom without a single carrier at work, works a quarter slower.

### Time

- Hand work of a beginner: {cfg:production.craftSeconds} s per thing; furnaces, smokers and campfires take as long as the
  recipe says.
- Experience makes a worker up to twice as fast, and mood changes the pace by up to {cfg:economy.moodWorkEffect*100}%
  either way.
- Posts inside a building with the role Workshop work 25% faster (see [The roles of buildings](building_roles)).

### Mode

The post of a smelter or a cook has a mode button: "Own work and orders" or "Orders only". On its own a smelter smelts
ore and raw metal, and a cook cooks while the storehouses hold food for fewer than {cfg:production.cookFoodDaysTarget}
days for everybody: it roasts raw meat and bakes from crops when the result feeds more than what it is made of. Crafters
and smiths make only what is ordered (see [The king's orders](orders)).

### The window of a workshop post

The window shows the workstations in the zone, the mode, what is being made ("Making: Bread, 40%"), what was made today
and the output per worker. When work stands still it gives the reason: "No workstation in the zone", "Not enough
materials", "No fuel in the storehouses" or "No orders".

## orders | The king's orders
@icon minecraft:writable_book
In the **Orders** tab of the throne window the king gives orders to the workshops: [smelters, cooks, crafters and smiths](workshops). Every member of the kingdom sees the list; giving and changing orders takes the right **Orders**
(the king and the steward by default).

### Two kinds of order

- **Keep in stock**: citizens hold at least N in the storehouses and make new ones when some are taken. The order
  stays.
- **Make once**: citizens make N once, then the order is closed. The members who manage it get the message
  "Order done".

### Giving an order

1. Press **New order**.
2. Find the item with the search: the list shows only what citizens can make, with its icon and profession, page by page
   ("Page 1 of 5").
3. Choose the item, type the amount (up to 100,000), choose the kind and the priority: Low, Normal or High.
4. Press **Order**.

The top of the tab shows "Orders: N of {cfg:production.maxOrdersPerKingdom}": that many orders a kingdom may have at the
same time, sub-orders not counted. A click on the row of an order opens it for changes (amount, kind, priority, then
Save). The row also has the priority button, Pause (Resume) and Cancel. Orders of higher priority are made first.

### Chains

When an ingredient is missing but citizens can make it, the order adds a sub-order by itself: a sword waits for ingots,
the ingots wait for raw iron to be smelted. A chain is at most {cfg:production.orderChainDepth} steps deep and never
loops (no ingot from a block made of ingots). The recipe chosen is one whose ingredients are there, else the one with
the shortest way to them; an ingredient given by a tag (any planks) takes any of its items. Sub-orders show under their
order ("Have 2/4") and change and go away together with it.

### States

| State | Meaning |
|---|---|
| Not planned yet | not looked at yet |
| Waiting for a worker | all is there |
| Being made | a worker makes it |
| Waiting for sub-orders | ingredients being made |
| Missing | must be gathered |
| No workshop | the post is missing |
| Enough in stock | the stock is there |
| Paused | the order is paused |

Missing names what is lacking, how much and who gathers it, for example "Raw Iron x2 (gathered by: Miner)". No workshop
tells which post is needed: "Needs a post: Smith with a crafting table". "Citizens cannot make it" means there is no
recipe at all.

> Other orders never use the stock a Keep in stock order holds, and the market does not sell it. Handy standing orders:
> bread for the citizens, torches for [miners](miner), planks and glass for [construction](builders).

A construction can order what it lacks from the workshops too: the Ordering switch on its card (see [Builders and materials](builders)).
