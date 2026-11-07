package io.github.sekerator.kingdomworld.common.network.data;

import io.github.sekerator.kingdomworld.core.economy.Coins;
import io.github.sekerator.kingdomworld.core.economy.CurrencyUnits;
import io.github.sekerator.kingdomworld.core.kingdom.Kingdom;
import io.github.sekerator.kingdomworld.core.kingdom.KingdomNames;
import io.github.sekerator.kingdomworld.core.trade.Caravan;
import io.github.sekerator.kingdomworld.core.trade.Deal;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * What the screens of trade show: the stall, the "Market" and "Trade" tabs of the throne screen, the exchange of the bank,
 * and the caravans on the map. Every list is bounded so that a view always fits a packet.
 */
public final class TradeViews {
    /** Most rows of one list of a view. */
    public static final int MAX_ROWS = 256;
    /** Most item ids the market tab offers for ordering from abroad. */
    public static final int MAX_IMPORTABLE = 2_048;
    public static final int MAX_NAME = 64;

    private TradeViews() {
    }

    // ---------------------------------------------------------------- helpers

    static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        for (T value : list) {
            writer.accept(buf, value);
        }
    }

    static <T> List<T> readList(FriendlyByteBuf buf, int max, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new DecoderException("List size out of range: " + size);
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    static void writeCoins(FriendlyByteBuf buf, Coins coins) {
        buf.writeVarLong(coins.copper());
        buf.writeVarLong(coins.silver());
        buf.writeVarLong(coins.gold());
    }

    static Coins readCoins(FriendlyByteBuf buf) {
        return new Coins(TreasuryView.amount(buf), TreasuryView.amount(buf), TreasuryView.amount(buf));
    }

    static CurrencyUnits units(long silver, long gold) {
        return CurrencyUnits.valid(silver, gold) ? new CurrencyUnits(silver, gold) : CurrencyUnits.DEFAULT;
    }

    private static <T> List<T> bounded(List<T> list) {
        List<T> copy = List.copyOf(list);
        if (copy.size() > MAX_ROWS) {
            throw new IllegalArgumentException("Too many rows: " + copy.size());
        }
        return copy;
    }

    /**
     * A currency as a screen of trade knows it.
     *
     * @param name the name its king gave it, empty for the one derived from the kingdom's
     * @param rate its exchange rate, see {@link io.github.sekerator.kingdomworld.core.trade.ExchangeRates}
     */
    public record Currency(UUID id, String name, String kingdomName, int color, long silver, long gold, long rate) {
        public Currency {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kingdomName, "kingdomName");
            color &= Kingdom.MAX_COLOR;
        }

        public CurrencyUnits units() {
            return TradeViews.units(silver, gold);
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name, KingdomNames.MAX_LENGTH);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            buf.writeVarLong(silver);
            buf.writeVarLong(gold);
            buf.writeVarLong(rate);
        }

        static Currency decode(FriendlyByteBuf buf) {
            return new Currency(buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
        }
    }

    // ---------------------------------------------------------------- the stall

    /**
     * What the kingdom sells at the stall.
     *
     * @param available items a player may buy now
     */
    public record Offer(ItemStack sample, long price, long available) {
    }

    /**
     * What the kingdom buys at the stall.
     *
     * @param wanted     items it takes now, by stock and budget
     * @param carried    plain items of the kind the player carries
     */
    public record Wanted(ItemStack sample, long price, long wanted, long carried) {
    }

    /**
     * A lot of a player.
     */
    public record LotRow(long id, ItemStack stack, long price, String seller, boolean mine) {
        public LotRow {
            Objects.requireNonNull(seller, "seller");
        }
    }

    /**
     * Money a player could pay with: the account and the coins of a currency.
     */
    public record Purse(Currency currency, long balance, Coins carried) {
    }

    /**
     * The stall screen.
     *
     * @param own        the currency of the stall's kingdom
     * @param member     whether the player is a subject and may put up lots
     * @param purses     what the player could pay with: the own currency first, others if the market takes them
     */
    public record StallView(BlockPos pos, UUID kingdomId, String kingdomName, int color, Currency own, boolean member, int salesTaxPermille,
                            int maxLots, int feePermille, List<Offer> offers, List<Wanted> wanted, List<LotRow> lots, List<Purse> purses) {
        public StallView {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(kingdomName, "kingdomName");
            Objects.requireNonNull(own, "own");
            color &= Kingdom.MAX_COLOR;
            offers = bounded(offers);
            wanted = bounded(wanted);
            lots = bounded(lots);
            purses = bounded(purses);
        }

        public int myLots() {
            return (int) lots.stream().filter(LotRow::mine).count();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(kingdomId);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            own.encode(buf);
            buf.writeBoolean(member);
            buf.writeVarInt(salesTaxPermille);
            buf.writeVarInt(maxLots);
            buf.writeVarInt(feePermille);
            writeList(buf, offers, (b, offer) -> {
                b.writeItem(offer.sample());
                b.writeVarLong(offer.price());
                b.writeVarLong(offer.available());
            });
            writeList(buf, wanted, (b, entry) -> {
                b.writeItem(entry.sample());
                b.writeVarLong(entry.price());
                b.writeVarLong(entry.wanted());
                b.writeVarLong(entry.carried());
            });
            writeList(buf, lots, (b, lot) -> {
                b.writeVarLong(lot.id());
                b.writeItem(lot.stack());
                b.writeVarLong(lot.price());
                b.writeUtf(lot.seller(), MAX_NAME);
                b.writeBoolean(lot.mine());
            });
            writeList(buf, purses, (b, purse) -> {
                purse.currency().encode(b);
                b.writeVarLong(purse.balance());
                writeCoins(b, purse.carried());
            });
        }

        public static StallView decode(FriendlyByteBuf buf) {
            return new StallView(buf.readBlockPos(), buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), Currency.decode(buf),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    readList(buf, MAX_ROWS, b -> new Offer(b.readItem(), TreasuryView.amount(b), b.readVarLong())),
                    readList(buf, MAX_ROWS, b -> new Wanted(b.readItem(), TreasuryView.amount(b), b.readVarLong(), b.readVarLong())),
                    readList(buf, MAX_ROWS, b -> new LotRow(b.readVarLong(), b.readItem(), TreasuryView.amount(b), b.readUtf(MAX_NAME), b.readBoolean())),
                    readList(buf, MAX_ROWS, b -> new Purse(Currency.decode(b), TreasuryView.amount(b), readCoins(b))));
        }
    }

    // ---------------------------------------------------------------- the market tab

    /**
     * An item the kingdom sells.
     *
     * @param price the king's price, 0 for the auto price
     * @param auto  the auto price now
     */
    public record SellRow(ItemStack sample, long price, long auto, int keep, long stock) {
    }

    /**
     * An item the kingdom buys.
     *
     * @param spent money paid for it today
     */
    public record BuyRow(ItemStack sample, long price, long auto, int max, long budget, long spent, long stock) {
    }

    /**
     * Items the merchants sell abroad above a stock.
     *
     * @param income what the external market pays for one item, in thousandths of the smallest coin
     */
    public record ExportRow(ItemStack sample, int above, long stock, long income) {
    }

    /**
     * Goods ordered from abroad.
     *
     * @param ticksLeft game ticks until they arrive, 0 when they wait for room
     */
    public record ImportRow(ItemStack sample, int count, long cost, long ticksLeft) {
    }

    /**
     * The counters of the market today.
     *
     * @param merchants     healthy merchants at posts with a stall
     * @param exportVolume  items the merchants may sell abroad today
     */
    public record Day(long sold, long bought, long lotTax, int merchants, int exportVolume, int exported, long exportIncome,
                      long exportMaxIncome, int imported, int importMax, long importCost) {
    }

    /**
     * The "Market" tab of the throne screen.
     */
    public record MarketView(UUID kingdomId, boolean canManage, Currency own, List<SellRow> sells, List<BuyRow> buys, List<LotRow> lots,
                             List<ExportRow> exports, List<ImportRow> imports, boolean openToOutsiders, boolean foreignCurrency,
                             int salesTaxPermille, int maxSalesTaxPermille, int feePermille, int maxFeePermille, Day day,
                             List<String> importable, int maxEntries) {
        public MarketView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(own, "own");
            Objects.requireNonNull(day, "day");
            sells = bounded(sells);
            buys = bounded(buys);
            lots = bounded(lots);
            exports = bounded(exports);
            imports = bounded(imports);
            importable = List.copyOf(importable);
            if (importable.size() > MAX_IMPORTABLE) {
                throw new IllegalArgumentException("Too many importable items");
            }
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            own.encode(buf);
            writeList(buf, sells, (b, row) -> {
                b.writeItem(row.sample());
                b.writeVarLong(row.price());
                b.writeVarLong(row.auto());
                b.writeVarInt(row.keep());
                b.writeVarLong(row.stock());
            });
            writeList(buf, buys, (b, row) -> {
                b.writeItem(row.sample());
                b.writeVarLong(row.price());
                b.writeVarLong(row.auto());
                b.writeVarInt(row.max());
                b.writeVarLong(row.budget());
                b.writeVarLong(row.spent());
                b.writeVarLong(row.stock());
            });
            writeList(buf, lots, (b, lot) -> {
                b.writeVarLong(lot.id());
                b.writeItem(lot.stack());
                b.writeVarLong(lot.price());
                b.writeUtf(lot.seller(), MAX_NAME);
                b.writeBoolean(lot.mine());
            });
            writeList(buf, exports, (b, row) -> {
                b.writeItem(row.sample());
                b.writeVarInt(row.above());
                b.writeVarLong(row.stock());
                b.writeVarLong(row.income());
            });
            writeList(buf, imports, (b, row) -> {
                b.writeItem(row.sample());
                b.writeVarInt(row.count());
                b.writeVarLong(row.cost());
                b.writeVarLong(row.ticksLeft());
            });
            buf.writeBoolean(openToOutsiders);
            buf.writeBoolean(foreignCurrency);
            buf.writeVarInt(salesTaxPermille);
            buf.writeVarInt(maxSalesTaxPermille);
            buf.writeVarInt(feePermille);
            buf.writeVarInt(maxFeePermille);
            buf.writeVarLong(day.sold());
            buf.writeVarLong(day.bought());
            buf.writeVarLong(day.lotTax());
            buf.writeVarInt(day.merchants());
            buf.writeVarInt(day.exportVolume());
            buf.writeVarInt(day.exported());
            buf.writeVarLong(day.exportIncome());
            buf.writeVarLong(day.exportMaxIncome());
            buf.writeVarInt(day.imported());
            buf.writeVarInt(day.importMax());
            buf.writeVarLong(day.importCost());
            buf.writeVarInt(importable.size());
            for (String id : importable) {
                buf.writeUtf(id, 256);
            }
            buf.writeVarInt(maxEntries);
        }

        public static MarketView decode(FriendlyByteBuf buf) {
            UUID kingdomId = buf.readUUID();
            boolean canManage = buf.readBoolean();
            Currency own = Currency.decode(buf);
            List<SellRow> sells = readList(buf, MAX_ROWS, b -> new SellRow(b.readItem(), TreasuryView.amount(b), TreasuryView.amount(b),
                    b.readVarInt(), b.readVarLong()));
            List<BuyRow> buys = readList(buf, MAX_ROWS, b -> new BuyRow(b.readItem(), TreasuryView.amount(b), TreasuryView.amount(b),
                    b.readVarInt(), TreasuryView.amount(b), TreasuryView.amount(b), b.readVarLong()));
            List<LotRow> lots = readList(buf, MAX_ROWS, b -> new LotRow(b.readVarLong(), b.readItem(), TreasuryView.amount(b),
                    b.readUtf(MAX_NAME), b.readBoolean()));
            List<ExportRow> exports = readList(buf, MAX_ROWS, b -> new ExportRow(b.readItem(), b.readVarInt(), b.readVarLong(), b.readVarLong()));
            List<ImportRow> imports = readList(buf, MAX_ROWS, b -> new ImportRow(b.readItem(), b.readVarInt(), TreasuryView.amount(b),
                    b.readVarLong()));
            boolean open = buf.readBoolean();
            boolean foreign = buf.readBoolean();
            int tax = buf.readVarInt();
            int maxTax = buf.readVarInt();
            int fee = buf.readVarInt();
            int maxFee = buf.readVarInt();
            Day day = new Day(TreasuryView.amount(buf), TreasuryView.amount(buf), TreasuryView.amount(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), TreasuryView.amount(buf), TreasuryView.amount(buf), buf.readVarInt(), buf.readVarInt(), TreasuryView.amount(buf));
            List<String> importable = readList(buf, MAX_IMPORTABLE, b -> b.readUtf(256));
            return new MarketView(kingdomId, canManage, own, sells, buys, lots, exports, imports, open, foreign, tax, maxTax, fee, maxFee, day,
                    importable, buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the trade tab

    /**
     * Another kingdom to trade with.
     */
    public record Partner(UUID id, String name, int color, Currency currency) {
    }

    /**
     * A deal as one of its kingdoms sees it.
     *
     * @param weSell      whether the viewer's kingdom sends the goods
     * @param ourCurrency whether the price is in the viewer's currency
     * @param incoming    whether it waits for the viewer's kingdom to answer
     * @param ticksToNext game ticks to the next delivery of a regular deal, or to the end of a proposal
     */
    public record DealRow(long id, UUID partner, String partnerName, int partnerColor, boolean weSell, ItemStack sample, int count, long price,
                          boolean ourCurrency, int periodDays, Deal.Status status, boolean incoming, boolean canCancel, long ticksToNext,
                          int deliveries) {
    }

    /**
     * A caravan of the viewer's kingdom on the way.
     *
     * @param outgoing whether it carries the kingdom's goods away
     */
    public record CaravanRow(long id, boolean outgoing, String partnerName, int partnerColor, ItemStack sample, long items, long payment,
                             boolean ourCurrency, int progressPercent, long ticksLeft, Caravan.State state) {
    }

    /**
     * The "Trade" tab of the throne screen.
     */
    public record TradeView(UUID kingdomId, boolean canManage, Currency own, List<Partner> partners, List<DealRow> deals,
                            List<CaravanRow> caravans, int maxCount, int maxPeriod) {
        public TradeView {
            Objects.requireNonNull(kingdomId, "kingdomId");
            Objects.requireNonNull(own, "own");
            partners = bounded(partners);
            deals = bounded(deals);
            caravans = bounded(caravans);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(kingdomId);
            buf.writeBoolean(canManage);
            own.encode(buf);
            writeList(buf, partners, (b, partner) -> {
                b.writeUUID(partner.id());
                b.writeUtf(partner.name(), KingdomNames.MAX_LENGTH);
                b.writeInt(partner.color());
                partner.currency().encode(b);
            });
            writeList(buf, deals, (b, deal) -> {
                b.writeVarLong(deal.id());
                b.writeUUID(deal.partner());
                b.writeUtf(deal.partnerName(), KingdomNames.MAX_LENGTH);
                b.writeInt(deal.partnerColor());
                b.writeBoolean(deal.weSell());
                b.writeItem(deal.sample());
                b.writeVarInt(deal.count());
                b.writeVarLong(deal.price());
                b.writeBoolean(deal.ourCurrency());
                b.writeVarInt(deal.periodDays());
                b.writeEnum(deal.status());
                b.writeBoolean(deal.incoming());
                b.writeBoolean(deal.canCancel());
                b.writeVarLong(deal.ticksToNext());
                b.writeVarInt(deal.deliveries());
            });
            writeList(buf, caravans, (b, caravan) -> {
                b.writeVarLong(caravan.id());
                b.writeBoolean(caravan.outgoing());
                b.writeUtf(caravan.partnerName(), KingdomNames.MAX_LENGTH);
                b.writeInt(caravan.partnerColor());
                b.writeItem(caravan.sample());
                b.writeVarLong(caravan.items());
                b.writeVarLong(caravan.payment());
                b.writeBoolean(caravan.ourCurrency());
                b.writeVarInt(caravan.progressPercent());
                b.writeVarLong(caravan.ticksLeft());
                b.writeEnum(caravan.state());
            });
            buf.writeVarInt(maxCount);
            buf.writeVarInt(maxPeriod);
        }

        public static TradeView decode(FriendlyByteBuf buf) {
            UUID kingdomId = buf.readUUID();
            boolean canManage = buf.readBoolean();
            Currency own = Currency.decode(buf);
            List<Partner> partners = readList(buf, MAX_ROWS, b -> new Partner(b.readUUID(), b.readUtf(KingdomNames.MAX_LENGTH), b.readInt(),
                    Currency.decode(b)));
            List<DealRow> deals = readList(buf, MAX_ROWS, b -> new DealRow(b.readVarLong(), b.readUUID(), b.readUtf(KingdomNames.MAX_LENGTH),
                    b.readInt(), b.readBoolean(), b.readItem(), b.readVarInt(), TreasuryView.amount(b), b.readBoolean(), b.readVarInt(),
                    KingdomView.readEnum(b, Deal.Status.class), b.readBoolean(), b.readBoolean(), b.readVarLong(), b.readVarInt()));
            List<CaravanRow> caravans = readList(buf, MAX_ROWS, b -> new CaravanRow(b.readVarLong(), b.readBoolean(),
                    b.readUtf(KingdomNames.MAX_LENGTH), b.readInt(), b.readItem(), b.readVarLong(), TreasuryView.amount(b), b.readBoolean(),
                    b.readVarInt(), b.readVarLong(), KingdomView.readEnum(b, Caravan.State.class)));
            return new TradeView(kingdomId, canManage, own, partners, deals, caravans, buf.readVarInt(), buf.readVarInt());
        }
    }

    // ---------------------------------------------------------------- the exchange of a bank

    /**
     * The exchange screen of a bank.
     *
     * @param funds   money the treasury of the bank's kingdom could pay out
     * @param sources what the player could exchange: accounts and coins of other current currencies
     */
    public record ExchangeView(BlockPos pos, UUID kingdomId, String kingdomName, int color, Currency own, int feePermille, long funds,
                               long balance, List<Purse> sources) {
        public ExchangeView {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(kingdomName, "kingdomName");
            Objects.requireNonNull(own, "own");
            color &= Kingdom.MAX_COLOR;
            sources = bounded(sources);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUUID(kingdomId);
            buf.writeUtf(kingdomName, KingdomNames.MAX_LENGTH);
            buf.writeInt(color);
            own.encode(buf);
            buf.writeVarInt(feePermille);
            buf.writeVarLong(funds);
            buf.writeVarLong(balance);
            writeList(buf, sources, (b, purse) -> {
                purse.currency().encode(b);
                b.writeVarLong(purse.balance());
                writeCoins(b, purse.carried());
            });
        }

        public static ExchangeView decode(FriendlyByteBuf buf) {
            return new ExchangeView(buf.readBlockPos(), buf.readUUID(), buf.readUtf(KingdomNames.MAX_LENGTH), buf.readInt(), Currency.decode(buf),
                    buf.readVarInt(), TreasuryView.amount(buf), TreasuryView.amount(buf),
                    readList(buf, MAX_ROWS, b -> new Purse(Currency.decode(b), TreasuryView.amount(b), readCoins(b))));
        }
    }

    // ---------------------------------------------------------------- caravans on the map

    /**
     * A caravan on the map: where it left, where it goes and when.
     */
    /**
     * A caravan on its way, for the maps.
     *
     * @param way      points of its way through the roads, packed as {@code x << 32 | z}; empty for a straight way
     * @param wayTicks game times it passes them
     */
    public record CaravanInfo(long id, UUID seller, UUID buyer, int color, String dimension, int fromX, int fromZ, int toX, int toZ,
                              long departTime, long arriveTime, List<Long> way, List<Long> wayTicks) {
        /** Most points of a way sent. */
        public static final int MAX_WAY = 512;

        public CaravanInfo {
            way = List.copyOf(way);
            wayTicks = List.copyOf(wayTicks);
            if (way.size() != wayTicks.size()) {
                throw new IllegalArgumentException("A way needs a time for every point");
            }
        }

        public CaravanInfo(long id, UUID seller, UUID buyer, int color, String dimension, int fromX, int fromZ, int toX, int toZ, long departTime,
                           long arriveTime) {
            this(id, seller, buyer, color, dimension, fromX, fromZ, toX, toZ, departTime, arriveTime, List.of(), List.of());
        }

        public static long pack(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFF_FFFFL);
        }

        public static int x(long packed) {
            return (int) (packed >> 32);
        }

        public static int z(long packed) {
            return (int) packed;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarLong(id);
            buf.writeUUID(seller);
            buf.writeUUID(buyer);
            buf.writeInt(color);
            buf.writeUtf(dimension, 256);
            buf.writeInt(fromX);
            buf.writeInt(fromZ);
            buf.writeInt(toX);
            buf.writeInt(toZ);
            buf.writeLong(departTime);
            buf.writeLong(arriveTime);
            buf.writeVarInt(way.size());
            for (int i = 0; i < way.size(); i++) {
                buf.writeLong(way.get(i));
                buf.writeLong(wayTicks.get(i));
            }
        }

        public static CaravanInfo decode(FriendlyByteBuf buf) {
            long id = buf.readVarLong();
            UUID seller = buf.readUUID();
            UUID buyer = buf.readUUID();
            int color = buf.readInt();
            String dimension = buf.readUtf(256);
            int fromX = buf.readInt();
            int fromZ = buf.readInt();
            int toX = buf.readInt();
            int toZ = buf.readInt();
            long depart = buf.readLong();
            long arrive = buf.readLong();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_WAY) {
                throw new IllegalArgumentException("Too many points of a way: " + size);
            }
            List<Long> way = new ArrayList<>(size);
            List<Long> ticks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                way.add(buf.readLong());
                ticks.add(buf.readLong());
            }
            return new CaravanInfo(id, seller, buyer, color, dimension, fromX, fromZ, toX, toZ, depart, arrive, way, ticks);
        }

        /**
         * How far along the way it is at a game time, from 0 to 1.
         */
        public double progress(long gameTime) {
            return arriveTime <= departTime ? 1.0 : Math.max(0.0, Math.min(1.0, (gameTime - departTime) / (double) (arriveTime - departTime)));
        }

        /**
         * Where it is at a game time: along its way through the roads, or on the straight line.
         */
        public double[] position(long gameTime) {
            if (way.size() < 2) {
                double progress = progress(gameTime);
                return new double[]{fromX + (toX - fromX) * progress, fromZ + (toZ - fromZ) * progress};
            }
            if (gameTime <= wayTicks.get(0)) {
                return new double[]{x(way.get(0)), z(way.get(0))};
            }
            for (int i = 1; i < way.size(); i++) {
                long until = wayTicks.get(i);
                if (gameTime <= until) {
                    long since = wayTicks.get(i - 1);
                    double t = until <= since ? 1.0 : (gameTime - since) / (double) (until - since);
                    long a = way.get(i - 1);
                    long b = way.get(i);
                    return new double[]{x(a) + (x(b) - x(a)) * t, z(a) + (z(b) - z(a)) * t};
                }
            }
            long last = way.get(way.size() - 1);
            return new double[]{x(last), z(last)};
        }
    }

    public static void writeCaravans(FriendlyByteBuf buf, List<CaravanInfo> caravans) {
        writeList(buf, caravans, (b, caravan) -> caravan.encode(b));
    }

    public static List<CaravanInfo> readCaravans(FriendlyByteBuf buf) {
        return readList(buf, 4_096, CaravanInfo::decode);
    }
}
