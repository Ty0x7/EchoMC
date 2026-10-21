package dev.echo.entity;

import dev.echo.*;
import dev.echo.action.ActionController;
import dev.echo.ai.*;
import dev.echo.net.EchoPackets;
import dev.echo.world.CompanionLedger;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.*;

public final class EchoEntity extends PathAwareEntity {
    private static final TrackedData<Byte> COMPANION_POSE=DataTracker.registerData(EchoEntity.class,TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Long> POSE_START=DataTracker.registerData(EchoEntity.class,TrackedDataHandlerRegistry.LONG);
    private final CompanionIdle idle=new CompanionIdle();
    private Vec3d lastOwnerPosition;
    @Override protected void initDataTracker(DataTracker.Builder builder){
        super.initDataTracker(builder);builder.add(COMPANION_POSE,(byte)0);builder.add(POSE_START,0L);
    }
    public CompanionIdle.Pose companionPose(){return CompanionIdle.Pose.values()[Math.clamp(dataTracker.get(COMPANION_POSE),0,CompanionIdle.Pose.values().length-1)];}
    public float poseTime(float tickDelta){return Math.max(0,getWorld().getTime()-dataTracker.get(POSE_START)+tickDelta);}
    private void pose(CompanionIdle.Pose value){
        if(companionPose()!=value){dataTracker.set(COMPANION_POSE,(byte)value.ordinal());dataTracker.set(POSE_START,getWorld().getTime());}
    }
    public void wakeForInteraction(){
        idle.interrupt();pose(companionPose()==CompanionIdle.Pose.SLEEP?CompanionIdle.Pose.WAKE:CompanionIdle.Pose.IDLE);
    }
    private void alertPose(){idle.interrupt();pose(CompanionIdle.Pose.ALERT);}
    public final SimpleInventory bag = new SimpleInventory(27);
    public UUID ownerId;
    public boolean following = true, mineAllowed, buildAllowed, exploreAllowed, combatAllowed = true, paused;
    public boolean planning, kevPending;
    public int revision, kevDecisionUntil;
    public KevBackend.Decision kevDecision=KevBackend.Decision.VANILLA;
    public long lastRequest = -1000, lastVoice = -1000;
    public final SoundMemory hearing=new SoundMemory();
    private Vec3d soundDanger;
    private int soundDangerUntil;
    private long lastSoundWarning=-1000;
    private long lastAnchorChunk=Long.MIN_VALUE;
    private final ArrayDeque<Plan.Step> tasks = new ArrayDeque<>();
    private final ArrayDeque<String> memories = new ArrayDeque<>();
    private final LinkedHashMap<String, BlockPos> places = new LinkedHashMap<>();
    private final Map<String,String> placeDimensions = new HashMap<>();
    private String taskDimension="";
    private final ActionController controller = new ActionController(this);
    private int attackCooldown, observationCooldown, previousTimeBand = -1;
    private LivingEntity threat;
    private String observed = "";
    public EchoEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world); setPersistent(); setCustomName(Text.literal("Echo")); setCustomNameVisible(true);
    }
    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 30)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.29).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }
    @Override protected void initGoals() { goalSelector.add(0, new SwimGoal(this)); }
    public ServerPlayerEntity owner() {
        if (ownerId == null || getServer() == null) return null;
        ServerPlayerEntity p = getServer().getPlayerManager().getPlayer(ownerId);
        return p != null && p.isAlive() ? p : null;
    }
    @Override public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world) || ownerId == null || !isAlive()) return;
        ServerPlayerEntity owner = owner();
        long chunk=ChunkPos.toLong(getBlockX()>>4,getBlockZ()>>4);
        if (age % 100 == 0 || chunk!=lastAnchorChunk) {
            lastAnchorChunk=chunk;
            dev.echo.world.EchoArrival.anchor(this);
        }
        if (owner == null || owner.getWorld()!=getWorld() || owner.isSpectator()) { idle.clear();pose(CompanionIdle.Pose.IDLE);lastOwnerPosition=null;controller.suspend(); return; }
        boolean ownerMoved=lastOwnerPosition==null || owner.getPos().squaredDistanceTo(lastOwnerPosition)>0.0004;
        lastOwnerPosition=owner.getPos();
        if (attackCooldown > 0) attackCooldown--;
        if (paused) { idle.clear();pose(CompanionIdle.Pose.IDLE);controller.suspend(); return; }
        if(combatAllowed && soundDanger!=null && age<soundDangerUntil) {
            alertPose();
            controller.suspend();
            Vec3d away=getPos().subtract(soundDanger).normalize().multiply(6).add(getPos());
            if(age%10==0)getNavigation().startMovingTo(away.x,away.y,away.z,1.3);
            return;
        }
        if (age % 10 == 0) {
            threat = world.getEntitiesByClass(HostileEntity.class, getBoundingBox().expand(10), mob -> mob.isAlive()
                && (mob.getTarget() == owner || mob.getTarget() == this || mob == owner.getAttacker() || mob == getAttacker()))
                .stream().min(Comparator.comparingDouble(this::squaredDistanceTo)).orElse(null);
            if (combatAllowed && threat != null && age % 40 == 0) Brain.kev(this);
            for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, getBoundingBox().expand(1.3), i -> mineAllowed && i.isAlive() && !i.cannotPickup())) {
                ItemStack remainder = bag.addStack(item.getStack().copy()); item.setStack(remainder);
                if (remainder.isEmpty()) item.discard();
            }
        }
        if (combatAllowed && threat != null && threat.isAlive() && threat.squaredDistanceTo(owner) < 256) {
            alertPose();
            controller.suspend();
            KevBackend.Decision reaction=EchoMod.CONFIG.kevEnabled && age<kevDecisionUntil?kevDecision:KevBackend.Decision.VANILLA;
            if (getHealth() < 8 || reaction==KevBackend.Decision.RETREAT) {
                Vec3d away = getPos().subtract(threat.getPos()).normalize().multiply(6).add(getPos());
                if (age % 10 == 0) getNavigation().startMovingTo(away.x, away.y, away.z, 1.3);
            } else if (reaction==KevBackend.Decision.DEFEND && squaredDistanceTo(owner)>9) {
                if(age%10==0) getNavigation().startMovingTo(owner,1.2);
            } else {
                if (age % 10 == 0) getNavigation().startMovingTo(threat, 1.2);
                getLookControl().lookAt(threat, 30, 30);
                if (squaredDistanceTo(threat) < 6.25 && canSee(threat) && attackCooldown == 0) {
                    swingHand(Hand.MAIN_HAND); tryAttack(threat); attackCooldown = 20;
                    ItemStack held = getMainHandStack(); if (!held.isEmpty()) held.damage(1, this, EquipmentSlot.MAINHAND);
                }
            }
            return;
        }
        boolean danger=(threat!=null && threat.isAlive()) || hurtTime>0 || isOnFire() || isTouchingWater() || isInLava();
        boolean busy=planning || !tasks.isEmpty();
        boolean walking=!getNavigation().isIdle() || getVelocity().horizontalLengthSquared()>0.0004;
        boolean working=!tasks.isEmpty() && !walking && switch(tasks.peek().kind()){case MINE,CRAFT,PLACE->true;default->false;};
        boolean canRest=!busy && !danger && !walking && isOnGround() && squaredDistanceTo(owner)<=16 && !owner.handSwinging && !owner.isUsingItem();
        var oldPose=companionPose();
        var nextPose=idle.tick(ownerMoved,canRest,danger,busy,working,planning,walking,following,squaredDistanceTo(owner));
        pose(nextPose);
        if(nextPose==CompanionIdle.Pose.SLEEP){controller.suspend();getNavigation().stop();return;}
        boolean justWoke=oldPose==CompanionIdle.Pose.SLEEP && nextPose==CompanionIdle.Pose.WAKE;
        if (!tasks.isEmpty() && !taskDimension.isEmpty() && !taskDimension.equals(getWorld().getRegistryKey().getValue().toString())) failStep("task belongs to another dimension; send a new instruction");
        else if (!tasks.isEmpty()) controller.tick(tasks.peek(), owner);
        else if (following && (age % 20 == 0 || justWoke)) {
            if (squaredDistanceTo(owner) > 12 && squaredDistanceTo(owner) < 2304) getNavigation().startMovingTo(owner, 1.1);
            else getNavigation().stop();
            getLookControl().lookAt(owner, 20, 20);
        }
        if (--observationCooldown <= 0) { observe(); observationCooldown = 60; }
        int band = (int)(world.getTimeOfDay() % 24000 / 12000);
        if (previousTimeBand != band && previousTimeBand != -1 && band == 1 && age > 200) say("Night's coming. Want me to stay close and watch for trouble?");
        previousTimeBand = band;
    }
    private void observe() {
        Map<String, Integer> blocks = new TreeMap<>(); ServerWorld w = (ServerWorld)getWorld();
        for (BlockPos p : BlockPos.iterate(getBlockPos().add(-4,-2,-4), getBlockPos().add(4,2,4))) {
            if (!w.isChunkLoaded(p)) continue;
            BlockState state = w.getBlockState(p); if (!state.isAir()) blocks.merge(Registries.BLOCK.getId(state.getBlock()).toString(), 1, Integer::sum);
        }
        var mobs = w.getOtherEntities(this, getBoundingBox().expand(12), Entity::isAlive).stream().limit(16)
            .map(e -> Registries.ENTITY_TYPE.getId(e.getType()) + " at " + e.getBlockPos().toShortString()).toList();
        observed = "blocks=" + blocks + "; nearby entities=" + mobs;
    }
    public String snapshot() {
        observe(); var p = owner();
        return "dimension=" + getWorld().getRegistryKey().getValue() + "; Echo=" + getBlockPos().toShortString() + "; owner=" + (p == null ? "offline" : p.getBlockPos().toShortString())
            + "; ownerDimension="+(p==null?"offline":p.getWorld().getRegistryKey().getValue())+"; health=" + getHealth() + "; biome=" + getWorld().getBiome(getBlockPos()).getKey().map(k->k.getValue().toString()).orElse("unknown")
            + "; raining=" + getWorld().isRaining() + "; dayTime=" + getWorld().getTimeOfDay() + "; " + observed
            + "; Echo inventory=" + inventoryText() + "; player inventory=" + (p == null ? "unavailable" : p.getInventory().main.stream().filter(s->!s.isEmpty()).map(s->Registries.ITEM.getId(s.getItem())+" x"+s.getCount()).toList())
            + "; permissions={mine:" + mineAllowed + ",build:" + buildAllowed + ",explore:" + exploreAllowed + ",combat:" + combatAllowed + "}"
            + "; companion state="+companionPose()+"; task=" + status() + "; memories=" + memories + "; places=" + places + "; sounds heard near Echo="+hearing.summary(getWorld().getTime());
    }
    public String reactionSnapshot() {
        var p=owner();
        return "health="+getHealth()+"/"+getMaxHealth()+"; NPC position="+getBlockPos().toShortString()
            +"; owner distance="+(p==null?"offline":Math.sqrt(squaredDistanceTo(p)))
            +"; threat="+(threat==null?"none":Registries.ENTITY_TYPE.getId(threat.getType())+" distance="+Math.sqrt(squaredDistanceTo(threat))+" visible="+canSee(threat))
            +"; combat permission="+combatAllowed+"; task="+controller.progress()+"; recent sounds="+hearing.summary(getWorld().getTime());
    }
    public void hear(SoundMemory.Cue cue) {
        if(!hearing.add(cue))return;
        boolean primed=cue.id().equals("minecraft:entity.creeper.primed") || cue.id().equals("minecraft:entity.tnt.primed");
        boolean explosion=cue.id().equals("minecraft:entity.generic.explode");
        if(primed && combatAllowed && !paused) {soundDanger=new Vec3d(cue.x(),cue.y(),cue.z());if(squaredDistanceTo(soundDanger)<64){soundDangerUntil=age+40;alertPose();}}
        if((primed || explosion) && cue.tick()-lastSoundWarning>=200) {
            lastSoundWarning=cue.tick();say(primed?"I heard something priming near me. I'm watching for an explosion.":"I heard an explosion near me. Stay alert.");
        }
    }
    public int threatId() { return threat==null?-1:threat.getId(); }
    public String inventoryText() { List<String> items = new ArrayList<>(); for (int i=0;i<bag.size();i++) if (!bag.getStack(i).isEmpty()) items.add(Registries.ITEM.getId(bag.getStack(i).getItem())+" x"+bag.getStack(i).getCount()); return items.toString(); }
    public void submit(Plan plan) {
        ServerPlayerEntity p = owner(); if (p == null) throw new IllegalArgumentException("owner must be present");
        // Validate the entire plan before replacing any active task.
        for (Plan.Step step : plan.steps()) controller.validate(step, p);
        wakeForInteraction();
        say(plan.reply());
        if (plan.steps().isEmpty()) return;
        revision++; planning = false; paused = false; controller.reset(); tasks.clear(); tasks.addAll(plan.steps());
        taskDimension=getWorld().getRegistryKey().getValue().toString();
        remember("Started plan: " + plan.steps());
    }
    public void cancel() { wakeForInteraction();revision++; planning = false; tasks.clear(); controller.reset(); following = false; say("Stopped. I'll wait here."); }
    public void setPaused(boolean value) { wakeForInteraction();revision++; planning = false; paused = value; controller.suspend(); say(value ? "Paused. I'll keep my place in the task." : "Resuming."); }
    public void finishStep(String message) { tasks.poll(); controller.reset(); remember(message); if (tasks.isEmpty()) say(message + " Task complete."); }
    public void failStep(String reason) { tasks.clear(); controller.reset(); following = false; say("Task blocked: " + reason + ". Send a new instruction when ready."); }
    public String status() { return "state="+companionPose().name().toLowerCase(Locale.ROOT)+"; "+(planning ? "thinking; " : "") + (paused ? "paused; " : "") + controller.progress() + "; queued=" + tasks.size() + "; follow=" + following; }
    public void say(String message) {
        if (message.length() > 600) message = message.substring(0,600);
        remember("Echo: " + message); var p = owner();
        if (p != null) {
            p.sendMessage(Text.literal("[Echo] " + message), false);
            if (ServerPlayNetworking.canSend(p, EchoPackets.Speech.ID)) ServerPlayNetworking.send(p, new EchoPackets.Speech(message));
        }
    }
    public void remember(String fact) { memories.addLast(fact.length() > 1200 ? fact.substring(0,1200) : fact); while (memories.size() > 40) memories.removeFirst(); }
    public void rememberPlace(String name) {
        if (name.length()>64) throw new IllegalArgumentException("Location name too long");
        places.put(name, getBlockPos());placeDimensions.put(name,getWorld().getRegistryKey().getValue().toString());
        if (places.size()>32) {String first=places.keySet().iterator().next();places.remove(first);placeDimensions.remove(first);} say("Remembered " + name + " at " + getBlockPos().toShortString());
    }
    public BlockPos place(String name) {
        String dimension=placeDimensions.get(name);
        if(dimension!=null && !dimension.equals(getWorld().getRegistryKey().getValue().toString())) throw new IllegalArgumentException("That waypoint is in "+dimension+". Carry me back there first.");
        return places.get(name);
    }
    public ItemStack findItem(Item item) { for (int i=0;i<bag.size();i++) if (bag.getStack(i).isOf(item)) return bag.getStack(i); return ItemStack.EMPTY; }
    public ItemStack bestTool(BlockState state) {
        ItemStack best = ItemStack.EMPTY; float speed = 0;
        for (int i=0;i<bag.size();i++) { ItemStack s=bag.getStack(i); float candidate=s.getMiningSpeedMultiplier(state); if ((!state.isToolRequired() || s.isSuitableFor(state)) && candidate>speed) { best=s; speed=candidate; } }
        equipStack(EquipmentSlot.MAINHAND, best); return best;
    }
    public void collectOrDrop(ItemStack stack) { ItemStack extra=bag.addStack(stack); if (!extra.isEmpty()) dropStack(extra); }
    @Override protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (player.getUuid().equals(ownerId)) {
            wakeForInteraction();
            if(player.isSneaking() && player instanceof ServerPlayerEntity serverPlayer) {
                try { dev.echo.item.EchoCubeItem.pickup(this,serverPlayer); }
                catch(IllegalArgumentException ex) { serverPlayer.sendMessage(Text.literal("[Echo] "+ex.getMessage()),true); }
            } else say(status() + " Inventory: " + inventoryText());
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
    @Override public void onDeath(DamageSource source) {
        if (!getWorld().isClient) {
            controller.reset(); revision++;
            for (EquipmentSlot slot : EquipmentSlot.values()) equipStack(slot, ItemStack.EMPTY);
            for (int i=0;i<bag.size();i++) if (!bag.getStack(i).isEmpty()) dropStack(bag.removeStack(i));
            if (ownerId != null) { var ledger=CompanionLedger.get(getServer()); ledger.companions.remove(ownerId); ledger.locations.remove(ownerId);ledger.anchors.remove(ownerId);ledger.parked.remove(getUuid()); ledger.markDirty(); }
        }
        super.onDeath(source);
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerId != null) nbt.putUuid("EchoOwner", ownerId);
        nbt.putBoolean("Follow", following); nbt.putBoolean("Mine", mineAllowed); nbt.putBoolean("Build", buildAllowed);
        nbt.putBoolean("Explore", exploreAllowed); nbt.putBoolean("Combat", combatAllowed); nbt.putBoolean("Paused", paused);
        nbt.put("Bag", bag.toNbtList(getRegistryManager()));
        nbt.putString("Tasks", EchoConfig.JSON.toJson(tasks.stream().map(controller::remaining).toList()));
        nbt.putString("TaskDimension",taskDimension);
        NbtList notes = new NbtList(); memories.forEach(s->notes.add(NbtString.of(s))); nbt.put("Memory", notes);
        NbtCompound locations = new NbtCompound(); places.forEach((key,pos)->locations.putLong(key,pos.asLong())); nbt.put("Places", locations);
        NbtCompound dimensions=new NbtCompound();placeDimensions.forEach(dimensions::putString);nbt.put("PlaceDimensions",dimensions);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("EchoOwner")) ownerId=nbt.getUuid("EchoOwner");
        following=nbt.getBoolean("Follow"); mineAllowed=nbt.getBoolean("Mine"); buildAllowed=nbt.getBoolean("Build"); exploreAllowed=nbt.getBoolean("Explore"); combatAllowed=nbt.getBoolean("Combat"); paused=nbt.getBoolean("Paused");
        bag.readNbtList(nbt.getList("Bag", NbtElement.COMPOUND_TYPE), getRegistryManager());
        equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY); // inventory is the sole source of truth
        tasks.clear();
        taskDimension=nbt.getString("TaskDimension");
        try { Plan.Step[] saved=EchoConfig.JSON.fromJson(nbt.getString("Tasks"), Plan.Step[].class); if (saved!=null && saved.length<=32) for (Plan.Step s:saved) if(s!=null && s.kind()!=null && s.count()>0 && s.count()<=64) tasks.add(s); }
        catch(Exception ignored) { tasks.clear(); }
        // An in-progress batch cannot safely be replayed after a restart; require explicit resume/review.
        if (!tasks.isEmpty()) { paused=true; if(taskDimension.isEmpty())taskDimension=getWorld().getRegistryKey().getValue().toString(); }
        memories.clear(); for (NbtElement e:nbt.getList("Memory",NbtElement.STRING_TYPE)) remember(e.asString());
        places.clear();placeDimensions.clear(); NbtCompound locations=nbt.getCompound("Places"),dimensions=nbt.getCompound("PlaceDimensions");
        for (String key:locations.getKeys()) if (places.size()<32) {places.put(key,BlockPos.fromLong(locations.getLong(key)));placeDimensions.put(key,dimensions.contains(key)?dimensions.getString(key):getWorld().getRegistryKey().getValue().toString());}
    }
}
