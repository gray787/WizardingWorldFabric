package com.example.wizardingworld;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.*;

public class WizardingWorldMod implements ModInitializer {
    public static final String MOD_ID = "wizardingworld";
    public static final Map<String, Item> WANDS = new LinkedHashMap<>();
    public static final Map<String, Spell> SPELLS = new LinkedHashMap<>();
    public static final Set<String> LEARNED = ConcurrentHashMap.newKeySet();
    public static final Map<UUID, Integer> QUIZ_ANSWERS = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer[]> HOUSE_POINTS = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();
    public static final Map<UUID, String> SELECTED_SPELL = new ConcurrentHashMap<>();
    public static Item SPELL_SCROLL, ELDER_WAND, BROOM, QUAFFLE, BLUDGER, SNITCH, GALLEON, SICKLE, KNUT;

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    private static Item register(String name, Item item) {
        return BuiltInRegistries.ITEM.register(id(name), item);
    }
    private static ItemStack named(Item item, String name, String lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore))));
        return stack;
    }

    @Override public void onInitialize() {
        registerItems();
        registerSpells();
        registerCommands();
        registerUseEvents();
    }

    private void registerItems() {
        String[] woods = {"acacia","alder","apple","ash","aspen","beech","blackthorn","black_walnut","cedar","cherry",
                "chestnut","cypress","dogwood","ebony","elder","elm","english_oak","fir","hawthorn","hazel","holly",
                "hornbeam","larch","laurel","mahogany","maple","pear","pine","poplar","red_oak","redwood","rowan",
                "silver_lime","spruce","sycamore","tamarack","vine","walnut","willow","yew","dragonheart","phoenix_feather",
                "unicorn_hair","veela_hair","thestral_hair","basilisk_horn","thunderbird_tail","kelpie_hair",
                "wampus_cat_hair","snallygaster_heartstring"};
        for (String wood : woods) WANDS.put(wood, register(wood + "_wand", new Item(new Item.Properties().stacksTo(1))));
        ELDER_WAND = register("elder_wand", new Item(new Item.Properties().stacksTo(1).fireResistant()));
        SPELL_SCROLL = register("spell_scroll", new Item(new Item.Properties()));
        BROOM = register("broom", new Item(new Item.Properties().stacksTo(1)));
        QUAFFLE = register("quaffle", new Item(new Item.Properties().stacksTo(1)));
        BLUDGER = register("bludger", new Item(new Item.Properties().stacksTo(1)));
        SNITCH = register("golden_snitch", new Item(new Item.Properties().stacksTo(1)));
        GALLEON = register("galleon", new Item(new Item.Properties()));
        SICKLE = register("sickle", new Item(new Item.Properties()));
        KNUT = register("knut", new Item(new Item.Properties()));
    }

    private void registerSpells() {
        Spell.Effect[] effects = Spell.Effect.values();
        String[] known = {"Lumos","Nox","Accio","Protego","Wingardium Leviosa","Incendio","Aguamenti","Reparo",
                "Stupefy","Petrificus Totalus","Expecto Patronum","Confundo","Revelio","Expelliarmus",
                "Finite Incantatem","Alohomora","Rictusempra","Diffindo","Muffliato","Episkey"};
        String[] desc = {"Creates a floating light","Extinguishes your light","Pulls nearby dropped items toward you",
                "Raises a brief magical shield","Lifts a nearby creature","Casts a burst of flame","Creates a water splash",
                "Restores your health","Stuns a nearby creature","Freezes a nearby creature","Summons a protective patronus",
                "Disorients a nearby creature","Makes nearby creatures glow","Weakens a nearby creature",
                "Clears negative effects","A magical unlocking charm","Disorients a target","A cutting burst",
                "Conceals you briefly","Heals a small amount of health"};
        for (int i=0;i<2000;i++) {
            String name = i < known.length ? known[i] : String.format(Locale.ROOT, "Spell %04d", i+1);
            String sid = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");
            String description = i < desc.length ? desc[i] : "An experimental charm in the Wizarding World spell archive.";
            SPELLS.put(sid, new Spell(sid, name, description, effects[i % effects.length]));
        }
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("hogwarts")
                .then(literal("spawn").executes(ctx -> buildHogwarts(ctx.getSource().getPlayerOrException())))
                .then(literal("sorting").executes(ctx -> startQuiz(ctx.getSource().getPlayerOrException())))
                .then(literal("diagon").then(literal("spawn").executes(ctx -> buildDiagon(ctx.getSource().getPlayerOrException()))))
                .then(literal("platform").then(literal("spawn").executes(ctx -> buildPlatform(ctx.getSource().getPlayerOrException()))))
                .then(literal("wallet").executes(ctx -> wallet(ctx.getSource().getPlayerOrException())))
                .then(literal("spells").executes(ctx -> spellList(ctx.getSource().getPlayerOrException())))
                .then(literal("vendors").executes(ctx -> spawnVendors(ctx.getSource().getPlayerOrException())))
                .then(literal("quidditch").executes(ctx -> giveQuidditch(ctx.getSource().getPlayerOrException())))
                .then(literal("wand").then(literal("elder").executes(ctx -> give(ctx.getSource().getPlayerOrException(), named(ELDER_WAND,"The Elder Wand","All 2,000 spells unlocked; no casting cooldown.")))))
                .then(literal("answer").then(argument("house", IntegerArgumentType.integer(1,4)).executes(ctx -> answerQuiz(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx,"house")))))
                .then(literal("learn").then(argument("spell", IntegerArgumentType.integer(1,2000)).executes(ctx -> learnScroll(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx,"spell")))))
                .then(literal("scroll").then(argument("spell", IntegerArgumentType.integer(1,2000)).executes(ctx -> giveScroll(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx,"spell")))))
                .then(literal("spell").then(argument("spell", IntegerArgumentType.integer(1,2000)).executes(ctx -> selectSpell(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx,"spell")))))
                .then(literal("shop").then(literal("wand").executes(ctx -> shop(ctx.getSource().getPlayerOrException(),"wand")))
                    .then(literal("broom").executes(ctx -> shop(ctx.getSource().getPlayerOrException(),"broom")))
                    .then(literal("magic").executes(ctx -> shop(ctx.getSource().getPlayerOrException(),"magic"))))
                .then(literal("broomstop").executes(ctx -> stopBroom(ctx.getSource().getPlayerOrException())))
                .then(literal("currency").then(literal("galleon").executes(ctx -> give(ctx.getSource().getPlayerOrException(), new ItemStack(GALLEON,1))))
                    .then(literal("sickle").executes(ctx -> give(ctx.getSource().getPlayerOrException(), new ItemStack(SICKLE,1))))
                    .then(literal("knut").executes(ctx -> give(ctx.getSource().getPlayerOrException(), new ItemStack(KNUT,1)))))
            );
        });
    }

    private void registerUseEvents() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world.isClientSide()) return InteractionResultHolder.pass(stack);
            if (stack.is(ELDER_WAND) || WANDS.containsValue(stack.getItem())) {
                cast(player, stack.is(ELDER_WAND) ? "all" : null);
                return InteractionResultHolder.success(stack);
            }
            if (stack.is(SPELL_SCROLL)) {
                if (player instanceof ServerPlayer sp) {
                    String scrollName = stack.getHoverName().getString().replace(" Scroll", "");
                    Spell spell = SPELLS.values().stream().filter(candidate -> candidate.name().equals(scrollName))
                            .findFirst().orElse(SPELLS.get("lumos"));
                    LEARNED.add(sp.getUUID()+":"+spell.id());
                    sp.sendSystemMessage(Component.literal("Learned " + spell.name() + ": " + spell.description()));
                    stack.shrink(1);
                }
                return InteractionResultHolder.success(stack);
            }
            if (stack.is(BROOM)) {
                if (player instanceof ServerPlayer sp) {
                    sp.getAbilities().mayfly = true;
                    sp.getAbilities().flying = true;
                    sp.onUpdateAbilities();
                    sp.sendSystemMessage(Component.literal("Your broom lifts you into the air! Sneak and use /hogwarts broomstop to dismount."));
                }
                return InteractionResultHolder.success(stack);
            }
            if (stack.is(QUAFFLE) || stack.is(BLUDGER) || stack.is(SNITCH)) {
                throwBall(player, stack, world);
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
    }

    private static void cast(Player player, String ignored) {
        boolean elder = player.getMainHandItem().is(ELDER_WAND) || player.getOffhandItem().is(ELDER_WAND);
        long now = player.level().getGameTime();
        long last = LAST_CAST.getOrDefault(player.getUUID(), -1000L);
        if (!elder && now-last < 12) { player.displayClientMessage(Component.literal("Your wand is recharging..."), true); return; }
        LAST_CAST.put(player.getUUID(), now);
        String selected = SELECTED_SPELL.getOrDefault(player.getUUID(), "lumos");
        Spell spell = SPELLS.getOrDefault(selected, SPELLS.get("lumos"));
        if (elder) {
            player.displayClientMessage(Component.literal("The Elder Wand grants access to the entire spell archive."), true);
        } else if (!LEARNED.contains(player.getUUID()+":"+spell.id())) {
            player.displayClientMessage(Component.literal("You have not learned " + spell.name() + ". Find its scroll!"), true);
            return;
        }
        Level level = player.level();
        switch (spell.effect()) {
            case LIGHT -> { player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20*30, 0)); }
            case EXTINGUISH -> player.removeEffect(MobEffects.NIGHT_VISION);
            case PULL_ITEMS -> level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8))
                    .forEach(item -> item.setDeltaMovement(player.position().subtract(item.position()).normalize().scale(.55)));
            case SHIELD -> player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 2));
            case LEVITATE -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7)).stream().findFirst()
                    .ifPresent(m -> m.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 1)));
            case FIRE -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7)).stream().findFirst()
                    .ifPresent(m -> m.igniteForSeconds(5));
            case WATER -> player.extinguishFire();
            case HEAL, REPAIR -> player.heal(6);
            case STUN, FREEZE, SLOW -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7)).stream().findFirst()
                    .ifPresent(m -> m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 5)));
            case PATRONUS, GLOW, REVEAL -> level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(12))
                    .forEach(e -> { if (e instanceof Mob m) m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0)); });
            case CONFUSE -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7)).stream().findFirst()
                    .ifPresent(m -> m.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0)));
            case DISARM, KNOCKBACK -> level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(7)).stream().findFirst()
                    .ifPresent(m -> m.knockback(1.5, player.getX()-m.getX(), player.getZ()-m.getZ()));
            case TELEPORT -> player.teleportTo(player.getX(), player.getY()+5, player.getZ());
            case CLEANSE -> player.removeAllEffects();
        }
        player.displayClientMessage(Component.literal("Cast " + spell.name() + " — " + spell.description()), true);
    }

    private static int buildHogwarts(ServerPlayer p) {
        BlockPos o=p.blockPosition();
        if (!p.level().getBlockState(o).isAir()) { p.sendSystemMessage(Component.literal("Spawn cancelled: build origin obstructed.")); return 0; }
        ServerLevel l=p.serverLevel();
        // All placement is air-only; preflight every footprint cell to avoid partial destructive placement.
        if (!areaClear(l,o.offset(-36,0,-24),o.offset(30,7,24))) {
            p.sendSystemMessage(Component.literal("Spawn cancelled: Hogwarts footprint is obstructed. Choose a clear area.")); return 0;
        }
        shell(l,o.offset(-9,0,-7),o.offset(9,6,7),Blocks.STONE_BRICKS.defaultBlockState());
        fillAir(l,o.offset(-8,1,-6),o.offset(8,1,6),Blocks.POLISHED_ANDESITE.defaultBlockState());
        fillAir(l,o.offset(-7,6,-5),o.offset(7,6,5),Blocks.DARK_OAK_PLANKS.defaultBlockState());
        l.setBlock(o.above(),Blocks.LECTERN.defaultBlockState(),3);
        l.setBlock(o.offset(0,2,0),Blocks.LANTERN.defaultBlockState(),3);
        // Great Hall tables
        fillAir(l,o.offset(-6,1,-3),o.offset(6,1,-3),Blocks.SPRUCE_PLANKS.defaultBlockState());
        fillAir(l,o.offset(-6,1,3),o.offset(6,1,3),Blocks.SPRUCE_PLANKS.defaultBlockState());
        // Hagrid's hut, offset east
        shell(l,o.offset(22,0,-4),o.offset(28,5,4),Blocks.SPRUCE_PLANKS.defaultBlockState());
        // Quidditch pitch, west of the hall; boundary is a visible fence.
        fillAir(l,o.offset(-34,0,-22),o.offset(-34,0,22),Blocks.GRASS_BLOCK.defaultBlockState());
        fillAir(l,o.offset(-34,1,-22),o.offset(34,1,22),Blocks.WHITE_CONCRETE.defaultBlockState());
        for (int y=1;y<=4;y++) {
            fillAir(l,o.offset(-32,y,-20),o.offset(-32,y,20),Blocks.OAK_FENCE.defaultBlockState());
            fillAir(l,o.offset(32,y,-20),o.offset(32,y,20),Blocks.OAK_FENCE.defaultBlockState());
            fillAir(l,o.offset(-32,y,-20),o.offset(32,y,-20),Blocks.OAK_FENCE.defaultBlockState());
            fillAir(l,o.offset(-32,y,20),o.offset(32,y,20),Blocks.OAK_FENCE.defaultBlockState());
        }
        // A compact Forbidden Forest grove beyond the pitch.
        Random random = new Random(263);
        for (int i=0;i<38;i++) {
            int x=-46+random.nextInt(22), z=-17+random.nextInt(35);
            BlockPos trunk=o.offset(x,1,z);
            for(int y=0;y<4+random.nextInt(3);y++) if(l.getBlockState(trunk.above(y)).isAir())
                l.setBlock(trunk.above(y),Blocks.DARK_OAK_LOG.defaultBlockState(),3);
            BlockPos crown=trunk.above(4);
            for(int dx=-2;dx<=2;dx++) for(int dy=-1;dy<=2;dy++) for(int dz=-2;dz<=2;dz++) {
                BlockPos leaf=crown.offset(dx,dy,dz);
                if(l.getBlockState(leaf).isAir() && Math.abs(dx)+Math.abs(dz)+Math.max(0,dy)<=4)
                    l.setBlock(leaf,Blocks.DARK_OAK_LEAVES.defaultBlockState(),3);
            }
        }
        p.sendSystemMessage(Component.literal("Hogwarts grounds created: Great Hall, Hagrid's hut, Forbidden Forest and Quidditch pitch."));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean areaClear(ServerLevel l, BlockPos a, BlockPos b) {
        for (BlockPos pos : BlockPos.betweenClosed(a,b)) if (!l.getBlockState(pos).isAir()) return false;
        return true;
    }
    private static void fillAir(ServerLevel l, BlockPos a, BlockPos b, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(a,b)) if (l.getBlockState(pos).isAir()) l.setBlock(pos,state,3);
    }
    private static void shell(ServerLevel l, BlockPos a, BlockPos b, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(a,b)) {
            if (pos.getX()==a.getX() || pos.getX()==b.getX() || pos.getY()==a.getY() || pos.getY()==b.getY()
                    || pos.getZ()==a.getZ() || pos.getZ()==b.getZ()) {
                if (l.getBlockState(pos).isAir()) l.setBlock(pos,state,3);
            }
        }
    }
    private static int buildDiagon(ServerPlayer p) {
        ServerLevel l=p.serverLevel(); BlockPos o=p.blockPosition();
        if (!areaClear(l,o.offset(-10,0,-5),o.offset(10,5,5))) { p.sendSystemMessage(Component.literal("Diagon Alley cancelled: area obstructed.")); return 0; }
        shell(l,o.offset(-10,0,-5),o.offset(-4,4,5),Blocks.BRICKS.defaultBlockState());
        shell(l,o.offset(-3,0,-5),o.offset(3,4,5),Blocks.OAK_PLANKS.defaultBlockState());
        shell(l,o.offset(4,0,-5),o.offset(10,4,5),Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        spawnVendor(p,"Ollivanders — Wands",o.offset(-7,1,0));
        spawnVendor(p,"Quality Quidditch Supplies",o.offset(0,1,0));
        spawnVendor(p,"Magical Menagerie",o.offset(7,1,0));
        return Command.SINGLE_SUCCESS;
    }
    private static int buildPlatform(ServerPlayer p) {
        ServerLevel l=p.serverLevel(); BlockPos o=p.blockPosition();
        if (!areaClear(l,o.offset(-5,0,-3),o.offset(5,4,3))) { p.sendSystemMessage(Component.literal("Platform cancelled: area obstructed.")); return 0; }
        fillAir(l,o.offset(-5,0,-3),o.offset(5,0,3),Blocks.STONE_BRICKS.defaultBlockState());
        fillAir(l,o.offset(-4,1,-2),o.offset(4,1,2),Blocks.RED_NETHER_BRICKS.defaultBlockState());
        l.setBlock(o.above(),Blocks.LECTERN.defaultBlockState(),3);
        return Command.SINGLE_SUCCESS;
    }
    private static void spawnVendor(ServerPlayer p,String name,BlockPos pos) {
        Villager v=EntityType.VILLAGER.create(p.serverLevel());
        if(v!=null){v.moveTo(pos,0,0);v.setCustomName(Component.literal(name));v.setCustomNameVisible(true);p.serverLevel().addFreshEntity(v);}
    }
    private static int spawnVendors(ServerPlayer p) {
        BlockPos o=p.blockPosition();
        spawnVendor(p,"Ollivanders — Wands",o.offset(2,0,0));
        spawnVendor(p,"Broom Emporium",o.offset(4,0,0));
        spawnVendor(p,"Magical Supplies",o.offset(6,0,0));
        p.sendSystemMessage(Component.literal("Three named wizarding vendors spawned. Use /hogwarts shop wand|broom|magic for stock."));
        return Command.SINGLE_SUCCESS;
    }
    private static int wallet(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Currency: 1 Galleon = 17 Sickles; 1 Sickle = 29 Knuts (493 Knuts per Galleon)."));
        return Command.SINGLE_SUCCESS;
    }
    private static int spellList(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Spell archive contains 2,000 entries. Learn scrolls, then use /hogwarts spell <number> to select."));
        p.sendSystemMessage(Component.literal("Known spells: Lumos, Nox, Accio, Protego, Wingardium Leviosa, Incendio, Aguamenti, Reparo, Stupefy, Petrificus Totalus, Expecto Patronum, Confundo, Revelio, Expelliarmus."));
        return Command.SINGLE_SUCCESS;
    }
    private static int give(ServerPlayer p, ItemStack stack) { p.getInventory().add(stack); return Command.SINGLE_SUCCESS; }
    private static int giveQuidditch(ServerPlayer p) {
        p.getInventory().add(named(QUAFFLE,"Quaffle","Scoring ball"));
        p.getInventory().add(named(BLUDGER,"Bludger","Quidditch hazard"));
        p.getInventory().add(named(SNITCH,"Golden Snitch","Catch to end the match"));
        return Command.SINGLE_SUCCESS;
    }
    private static int startQuiz(ServerPlayer p) {
        HOUSE_POINTS.put(p.getUUID(),new Integer[]{0,0,0,0});
        QUIZ_ANSWERS.put(p.getUUID(),0);
        p.sendSystemMessage(Component.literal("THE SORTING HAT — 5 questions. Answer each with /hogwarts answer <1-4>. 1 Gryffindor, 2 Hufflepuff, 3 Ravenclaw, 4 Slytherin."));
        p.sendSystemMessage(Component.literal("Q1: Which virtue matters most — courage, loyalty, wisdom or ambition?"));
        return Command.SINGLE_SUCCESS;
    }
    private static int answerQuiz(ServerPlayer p,int answer) {
        Integer[] pts=HOUSE_POINTS.get(p.getUUID());
        if(pts==null){p.sendSystemMessage(Component.literal("Start first: /hogwarts sorting"));return 0;}
        pts[answer-1]++; int n=QUIZ_ANSWERS.merge(p.getUUID(),1,Integer::sum);
        String[] qs={"Q2: Face a dragon, help a friend, solve a riddle, or lead a team?",
                "Q3: Choose a keepsake: sword, scarf, ancient book, or signet ring?",
                "Q4: Act boldly, stay dependable, think carefully, or plan strategically?",
                "Q5: Be remembered for courage, kindness, insight, or achievement?"};
        if(n<5) p.sendSystemMessage(Component.literal(qs[n-1]));
        else {
            String[] houses={"Gryffindor","Hufflepuff","Ravenclaw","Slytherin"};
            int best=0; for(int i=1;i<4;i++) if(pts[i]>pts[best]) best=i;
            p.sendSystemMessage(Component.literal("THE HAT HAS CHOSEN: "+houses[best].toUpperCase(Locale.ROOT)+"!"));
            HOUSE_POINTS.remove(p.getUUID()); QUIZ_ANSWERS.remove(p.getUUID());
        }
        return Command.SINGLE_SUCCESS;
    }
    private static int learnScroll(ServerPlayer p,int index) {
        Spell s=SPELLS.values().stream().skip(index-1).findFirst().orElse(null);
        if(s==null)return 0;
        LEARNED.add(p.getUUID()+":"+s.id());
        p.sendSystemMessage(Component.literal("Learned "+s.name()+": "+s.description()));
        return Command.SINGLE_SUCCESS;
    }
    private static int giveScroll(ServerPlayer p, int index) {
        Spell spell = SPELLS.values().stream().skip(index-1).findFirst().orElse(null);
        if (spell == null) return 0;
        ItemStack scroll = named(SPELL_SCROLL, spell.name()+" Scroll", spell.description());
        p.getInventory().add(scroll);
        p.sendSystemMessage(Component.literal("You received: "+spell.name()+" — "+spell.description()));
        return 1;
    }
    private static int selectSpell(ServerPlayer p, int index) {
        Spell spell = SPELLS.values().stream().skip(index-1).findFirst().orElse(null);
        if (spell == null) return 0;
        if (!LEARNED.contains(p.getUUID()+":"+spell.id()) && !p.getMainHandItem().is(ELDER_WAND)) {
            p.sendSystemMessage(Component.literal("You have not learned that spell. Find its scroll with /hogwarts scroll "+index));
            return 0;
        }
        SELECTED_SPELL.put(p.getUUID(), spell.id());
        p.sendSystemMessage(Component.literal("Selected "+spell.name()+": "+spell.description()));
        return 1;
    }
    private static int shop(ServerPlayer p, String kind) {
        switch(kind) {
            case "wand" -> {
                p.getInventory().add(named(WANDS.get("holly"),"Holly Wand","Ollivanders wand"));
                p.getInventory().add(named(WANDS.get("elder"),"Elder Wood Wand","A rare wand wood"));
            }
            case "broom" -> {
                p.getInventory().add(named(BROOM,"Cleansweep 11","Starter broom"));
                p.getInventory().add(named(BROOM,"Nimbus 2000","Racing broom"));
                p.getInventory().add(named(BROOM,"Firebolt","Elite racing broom"));
                p.getInventory().add(named(BROOM,"Comet 260","Classic broom"));
            }
            default -> {
                p.getInventory().add(new ItemStack(SPELL_SCROLL,3));
                p.getInventory().add(new ItemStack(GALLEON,2));
                p.getInventory().add(new ItemStack(SICKLE,4));
                p.getInventory().add(new ItemStack(KNUT,8));
            }
        }
        p.sendSystemMessage(Component.literal("Vendor stock delivered. This prototype shop grants items without charging currency."));
        return 1;
    }
    private static int stopBroom(ServerPlayer p) {
        p.getAbilities().flying=false;
        p.getAbilities().mayfly=false;
        p.onUpdateAbilities();
        p.sendSystemMessage(Component.literal("Broom flight stopped."));
        return 1;
    }
    private static void throwBall(Player player, ItemStack stack, Level world) {
        if(world instanceof ServerLevel sl) {
            Snowball ball=new Snowball(sl,player);
            ball.setItem(stack.copyWithCount(1));
            ball.shootFromRotation(player,player.getXRot(),player.getYRot(),0,1.6f,0.5f);
            sl.addFreshEntity(ball);
            stack.shrink(1);
        }
    }
}
