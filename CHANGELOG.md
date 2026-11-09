# CHANGELOG

## Русский

### 1.1.0

- **Командование армией на карте.** Режим «Армия» в атласе для короля и маршала: свои отряды с приказом, состоянием и временем до цели, бои; выбор кликом (Shift: несколько), приказы прямо на карте (идти, держать, патруль, охрана области, атака отряда, лагеря или города, осада, охрана каравана, разведка, отход в казарму) и маршрут с промежуточными точками для обхода во фланг и тыл.
- **Марш, построения и фланги.** Отряды идут по маршруту, по дорогам быстрее, путь ищется понемногу за тик; линия, колонна и клин; бой отрядов вдали от игроков длится, к нему можно присоединиться, удар сбоку или с тыла по связанному боем противнику даёт перевес (настраивается).
- **Туман войны.** Чужие отряды видны только в зоне видимости своих отрядов, башен и стен, в области разведки или после доклада шпиона; бледные метки «последний раз видели»; видимость считает сервер.
- **Трофеи.** Солдат несёт снаряжение убитого врага на склад; трофеи хранятся с королевством-хозяином и выкупаются после мира за половину ценности в течение 7 дней (`/kingdom trophies`).
- **Прыжок посоха** в чужую защищённую землю только после подготовки (8 с), которую сбивает удар или шаг.
- **Машины модов** бьют жителей только на земле их королевства.
- **Награда за голову:** без проверки адреса (только отряд); с третьей поимки одного человека одним охотником награда не больше того, что королевство у него отобрало.
- Версия протокола 23: клиент и сервер должны быть одной версии.

### 1.0.0

Первый выпуск KingdomWorld для Minecraft 1.20.1 (Forge 47).

- **Королевства и земля.** Основание у трона, чанки земли с защитой от гриферов, подданство, должности и права, атлас королевств, отметки в модах карт (JourneyMap, Xaero's World Map, BlueMap, Dynmap).
- **Жители.** Приходят в королевство, едят со складов, болеют, умирают; профессии: фермер, лесоруб, шахтёр, рыбак, пастух, носильщик, плавильщик, повар, ремесленник, кузнец, чеканщик, торговец, строитель, воин, лучник, маг, трактирщик; работа телом рядом с игроками и по формуле вдали от них.
- **Хозяйство.** Склады королевства, мастерские и заказы, монетный двор и своя валюта, казна с журналом, налоги и жалование, рынок и прилавки, торговля между королевствами караванами, курсы валют и банк.
- **Строительство.** Чертежи и закладка зданий, строители со складов, роли и уровни зданий, улучшение, ремонт, снос, свои чертежи.
- **Гильдия авантюристов.** Ранги, контракты, отряды игроков, охота на монстров с кристаллами.
- **Магия.** Башня магов, энергия кристаллов, исследования, заклинания королевства, совместимость с Iron's Spells и Ars Nouveau.
- **Армия.** Солдаты с оружием любых модов, отряды и приказы, наёмники, бои вдали от игроков.
- **Спутники.** Невольничий рынок гильдии, свободные спутники, верность, отряды игроков.
- **Дороги и разбойники.** Города, ратуши, стены и ворота, дороги трёх классов, караваны по дорогам, лагеря разбойников.
- **Закон и стража.** Преступления, свидетели, розыск, штрафы и казнь, плата за вход в крупные города, суд короля, репутация.
- **Королевства мира.** Появляются по плану мира при подходе игроков; правители с характером решают каждый день: налоги, стройка, армия, торговля, новые города.
- **Жизнь городов.** Распорядок дня, таверны со слухами, болезни и лазарет, праздники и ярмарки.
- **Дипломатия и война.** Отношения, пакты, союзы, вассалы, объявление войны с целью, военный счёт и усталость, мир по условиям; осады стен и ворот, захват земли, городов и трона, починка разрушенного.
- **Знать и интриги.** Знатные дома, титулы с владениями, стабильность, фракции и их требования, заговоры, шпионы и контрразведка, свадьбы домов.
- **Защита отсутствующих.** Игроки не в сети и AFK не подвергаются нападениям, королевство без игроков в сети под защитой, заговоры и требования ждут игровых дней.
- **Данные.** Каждая система хранит данные в своём файле; непонятные данные уходят в режим только чтения с резервной копией, старые миры обновляются при загрузке.
- **Нагрузка и баланс.** Замер нагрузки `admin perf`, тяжёлые места разнесены по тикам, жалование всех профессий по умолчанию.

## English

### 1.1.0

- **Commanding the army on the map.** An "Army" mode of the atlas for the king and the marshal: own squads with their order, state and time to the target, battles; choosing with a click (Shift: several), orders right on the map (go, hold, patrol, guard an area, attack a squad, a camp or a city, siege, guard a caravan, scout, back to the barracks) and a route through points on the way to go around into a flank or the rear.
- **Marches, formations and flanks.** Squads march along their route, faster on roads, the way searched a little each tick; line, column and wedge; a battle of squads far from players lasts, others may join it, and a blow at the side or rear of an enemy tied in battle gives an edge (configurable).
- **The fog of war.** Squads of others show only in sight of own squads, towers and walls, in a scouted area or after the report of a spy; pale marks "last seen"; the server counts the sight.
- **Trophies.** A soldier carries the gear of a fallen enemy to a storehouse; trophies are kept with the kingdom they belonged to and bought back after peace for half their worth within 7 days (`/kingdom trophies`).
- **The blink of the staff** into the protected land of another kingdom only after a preparation (8 s) that a blow or a step breaks.
- **Machines of mods** strike citizens only on the land of their kingdom.
- **Bounties:** no check of addresses (only the party); from the third head of one player one hunter collects, the reward is no more than what the kingdom took from that player.
- Protocol version 23: client and server must have the same version.

### 1.0.0

The first release of KingdomWorld for Minecraft 1.20.1 (Forge 47).

- **Kingdoms and land.** Founding at a throne, chunks of land protected from griefers, membership, positions and rights, the atlas of kingdoms, marks in map mods (JourneyMap, Xaero's World Map, BlueMap, Dynmap).
- **Citizens.** They come to the kingdom, eat from the storehouses, fall ill and die; professions: farmer, lumberjack, miner, fisher, herder, carrier, smelter, cook, crafter, smith, minter, merchant, builder, warrior, archer, mage, innkeeper; work with bodies near players and by formula far from them.
- **Economy.** Storehouses of the kingdom, workshops and orders, a mint and a currency of its own, a treasury with a journal, taxes and wages, a market and stalls, trade between kingdoms by caravans, exchange rates and a bank.
- **Construction.** Blueprints and laying out buildings, builders working from the storehouses, roles and levels of buildings, upgrades, repairs, demolition, blueprints of your own.
- **The adventurers' guild.** Ranks, contracts, parties of players, hunting monsters for crystals.
- **Magic.** The mage tower, energy of crystals, research, spells of the kingdom, compatibility with Iron's Spells and Ars Nouveau.
- **The army.** Soldiers with weapons of any mod, squads and orders, mercenaries, battles far from players.
- **Companions.** The slave market of the guild, free companions, loyalty, parties of players.
- **Roads and robbers.** Cities, town halls, walls and gates, roads of three classes, caravans on roads, camps of robbers.
- **Law and guards.** Crimes, witnesses, wanted lists, fines and execution, the entry fee of large cities, the king's court, reputation.
- **Kingdoms of the world.** They appear from the plan of the world as players come near; rulers with a character decide every day: taxes, building, the army, trade, new towns.
- **Life of cities.** The daily schedule, taverns with rumors, illnesses and the infirmary, festivals and fairs.
- **Diplomacy and war.** Relations, pacts, alliances, vassals, declarations of war with a goal, the score and weariness of war, peace on terms; sieges of walls and gates, capture of land, cities and thrones, mending of what was broken.
- **Nobility and intrigue.** Noble houses, titles with fiefs, stability, parties and their demands, plots, spies and counter-intelligence, marriages of houses.
- **Protection of the absent.** Players who are offline or away are never attacked, a kingdom with nobody online is shielded, plots and demands wait for played days.
- **Data.** Every system keeps its data in a file of its own; data that cannot be understood goes read-only with a backup, old worlds are upgraded on load.
- **Load and balance.** The load is measured with `admin perf`, heavy work is spread over ticks, every profession has a wage by default.
