package dev.echo.test;

import dev.echo.*;
import dev.echo.action.ActionController;
import dev.echo.ai.Plan;
import dev.echo.entity.EchoEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

public class EchoGameTests implements FabricGameTest {
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="sleep-follow",tickLimit=780)
    public void smallCubeSleepsThenWakesAndFollowsOwner(TestContext c){
        for(int x=0;x<16;x++)for(int z=0;z<5;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        var e=echo(c,new BlockPos(2,2,2));var p=player(c,e);e.setNoGravity(false);e.following=true;e.combatAllowed=false;
        c.assertTrue(Math.abs(e.getWidth()-0.3125f)<0.0001 && Math.abs(e.getHeight()-0.3125f)<0.0001,"half-size physical bounds");
        c.waitAndRun(640,()->{
            c.assertTrue(e.companionPose()==dev.echo.entity.CompanionIdle.Pose.SLEEP,"safe idle reaches sleep");
            p.refreshPositionAndAngles(e.getX()+8,e.getY(),e.getZ(),0,0);
        });
        c.waitAndRun(720,()->{
            c.assertTrue(e.companionPose()!=dev.echo.entity.CompanionIdle.Pose.SLEEP,"owner movement wakes Echo");
            c.assertTrue(e.following && e.squaredDistanceTo(p)<36,"native navigation resumes following");c.complete();
        });
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="sleep-task",tickLimit=800)
    public void sleepingCubeAcceptsTaskAndRespectsPauseAndStay(TestContext c){
        for(int x=0;x<16;x++)for(int z=0;z<5;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        var e=echo(c,new BlockPos(2,2,2));var p=player(c,e);e.setNoGravity(false);e.following=true;e.combatAllowed=false;
        var start=new net.minecraft.util.math.Vec3d[1];
        c.waitAndRun(640,()->{
            c.assertTrue(e.companionPose()==dev.echo.entity.CompanionIdle.Pose.SLEEP,"starts asleep");
            e.bag.setStack(0,new ItemStack(Items.OAK_LOG,1));e.submit(new Plan("Crafting.",java.util.List.of(new Plan.Step(Plan.Kind.CRAFT,"minecraft:oak_planks",1,0,0,0))));
            c.assertTrue(e.companionPose()!=dev.echo.entity.CompanionIdle.Pose.SLEEP,"instruction wakes immediately");
        });
        c.waitAndRun(670,()->{
            c.assertEquals(e.findItem(Items.OAK_PLANKS).getCount(),4,"real crafting still executes after sleep");
            e.setPaused(true);start[0]=e.getPos();p.refreshPositionAndAngles(e.getX()+8,e.getY(),e.getZ(),0,0);
        });
        c.waitAndRun(710,()->{
            c.assertTrue(e.paused && e.getPos().squaredDistanceTo(start[0])<0.05,"owner movement cannot override pause");
            e.submit(Plan.single(Plan.Kind.STAY));
        });
        c.waitAndRun(750,()->{c.assertTrue(!e.following && e.getPos().squaredDistanceTo(start[0])<0.05,"wake behavior cannot override stay");c.complete();});
    }
    private void index(EchoEntity e,ServerPlayerEntity p) { p.getInventory().clear();var ledger=dev.echo.world.CompanionLedger.get(p.getServer());ledger.companions.put(p.getUuid(),e.getUuid());ledger.markDirty(); }
    private EchoEntity echo(TestContext c, BlockPos relative) {
        EchoEntity e=EchoMod.COMPANION.create(c.getWorld());
        BlockPos p=c.getAbsolutePos(relative); e.refreshPositionAndAngles(p.getX()+0.5,p.getY(),p.getZ()+0.5,0,0); e.setNoGravity(true); e.following=false;
        c.getWorld().spawnEntity(e); return e;
    }
    private ServerPlayerEntity player(TestContext c, EchoEntity e) {
        EchoMod.CONFIG.autoSpawnOnJoin=false; // Tests drive arrival explicitly; mock logins must not change unrelated fixtures.
        ServerPlayerEntity p=c.createMockCreativeServerPlayerInWorld(); p.refreshPositionAndAngles(e.getX()-1,e.getY(),e.getZ(),0,0); e.ownerId=p.getUuid(); return p;
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void craftingConsumesRealIngredients(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e);
        e.bag.setStack(0,new ItemStack(Items.OAK_LOG,2));
        ActionController actions=new ActionController(e); var step=new Plan.Step(Plan.Kind.CRAFT,"minecraft:oak_planks",1,0,0,0);
        for(int i=0;i<10;i++) actions.tick(step,p);
        c.assertEquals(e.findItem(Items.OAK_LOG).getCount(),1,"one log consumed");
        c.assertEquals(e.findItem(Items.OAK_PLANKS).getCount(),4,"four planks crafted"); c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void placementConsumesInventoryEvenForCreativeOwner(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e); e.buildAllowed=true;
        BlockPos target=c.getAbsolutePos(new BlockPos(2,2,1)); c.getWorld().setBlockState(target.down(),Blocks.STONE.getDefaultState());
        e.bag.setStack(0,new ItemStack(Items.OAK_PLANKS,2));
        var step=new Plan.Step(Plan.Kind.PLACE,"minecraft:oak_planks",1,target.getX(),target.getY(),target.getZ());
        new ActionController(e).tick(step,p);
        c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_PLANKS),"actual block placed");
        c.assertEquals(e.findItem(Items.OAK_PLANKS).getCount(),1,"one block consumed"); c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void miningUsesToolsAndVanillaLoot(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e); e.mineAllowed=true;
        BlockPos target=c.getAbsolutePos(new BlockPos(2,2,1)); c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());
        e.bag.setStack(0,new ItemStack(Items.IRON_PICKAXE));
        ActionController actions=new ActionController(e); var step=new Plan.Step(Plan.Kind.MINE,"minecraft:stone",1,0,0,0);
        for(int i=0;i<40 && !c.getWorld().getBlockState(target).isAir();i++) actions.tick(step,p);
        c.assertTrue(c.getWorld().getBlockState(target).isAir(),"stone physically mined");
        c.assertEquals(e.findItem(Items.COBBLESTONE).getCount(),1,"vanilla cobblestone drop");
        c.assertTrue(e.findItem(Items.IRON_PICKAXE).getDamage()>0,"pickaxe wears"); c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void deniedMiningLeavesWorldUntouched(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e);
        BlockPos target=c.getAbsolutePos(new BlockPos(2,2,1)); c.getWorld().setBlockState(target,Blocks.OAK_LOG.getDefaultState());
        var step=new Plan.Step(Plan.Kind.MINE,"minecraft:oak_log",1,0,0,0);
        new ActionController(e).tick(step,p);
        c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_LOG),"permission denied preserves block");
        c.assertTrue(e.findItem(Items.OAK_LOG).isEmpty(),"no invented loot"); c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE)
    public void inventoryOwnerAndMemorySurviveNbtRoundtrip(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e); e.mineAllowed=true; e.buildAllowed=true;
        e.bag.setStack(0,new ItemStack(Items.DIAMOND,7)); e.rememberPlace("home");
        NbtCompound nbt=new NbtCompound(); e.writeCustomDataToNbt(nbt);
        EchoEntity restored=EchoMod.COMPANION.create(c.getWorld()); restored.readCustomDataFromNbt(nbt);
        c.assertEquals(restored.ownerId,p.getUuid(),"owner"); c.assertEquals(restored.findItem(Items.DIAMOND).getCount(),7,"inventory");
        c.assertEquals(restored.place("home"),e.getBlockPos(),"remembered waypoint"); c.assertTrue(restored.mineAllowed && restored.buildAllowed,"permissions persisted"); c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, batchId="navigation", tickLimit=180)
    public void followsUsingRealGroundNavigation(TestContext c) {
        for(int x=0;x<14;x++) for(int z=0;z<5;z++) c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        EchoEntity e=echo(c,new BlockPos(1,2,2)); var p=player(c,e); e.setNoGravity(false); e.following=true;
        BlockPos destination=c.getAbsolutePos(new BlockPos(10,2,2)); p.refreshPositionAndAngles(destination.getX()+0.5,destination.getY(),destination.getZ()+0.5,0,0);
        double original=e.squaredDistanceTo(p);
        c.waitAndRun(100,()->{c.assertTrue(e.squaredDistanceTo(p)<original/2,"Echo walks closer to its owner");c.complete();});
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, batchId="combat", tickLimit=100)
    public void defendsOwnerWithActualMeleeDamage(TestContext c) {
        for(int x=0;x<7;x++) for(int z=0;z<5;z++) c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        EchoEntity e=echo(c,new BlockPos(2,2,2)); var p=player(c,e); e.setNoGravity(false);
        var zombie=net.minecraft.entity.EntityType.HUSK.create(c.getWorld()); zombie.setAiDisabled(true);
        zombie.refreshPositionAndAngles(e.getX()+1.5,e.getY(),e.getZ(),0,0); zombie.setTarget(p); c.getWorld().spawnEntity(zombie);
        float health=zombie.getHealth();
        c.waitAndRun(40,()->{c.assertTrue(zombie.getHealth()<health,"native melee attack damaged the threat");c.complete();});
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, batchId="objective", tickLimit=200)
    public void completesQueuedGatherCraftBuildObjective(TestContext c) {
        for(int x=0;x<7;x++) for(int z=0;z<5;z++) c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);
        EchoEntity e=echo(c,new BlockPos(1,2,2)); var p=player(c,e); e.mineAllowed=true;e.buildAllowed=true;
        BlockPos target=c.getAbsolutePos(new BlockPos(3,2,2)); c.getWorld().setBlockState(target,Blocks.OAK_LOG.getDefaultState());
        e.submit(new Plan("Gather, craft, build.",java.util.List.of(new Plan.Step(Plan.Kind.MINE,"minecraft:oak_log",1,0,0,0),new Plan.Step(Plan.Kind.CRAFT,"minecraft:oak_planks",1,0,0,0),new Plan.Step(Plan.Kind.PLACE,"minecraft:oak_planks",1,target.getX(),target.getY(),target.getZ()))));
        c.waitAndRun(140,()->{
            c.assertTrue(c.getWorld().getBlockState(target).isOf(Blocks.OAK_PLANKS),"gathered log became actual placed planks");
            c.assertEquals(e.findItem(Items.OAK_PLANKS).getCount(),3,"exact material accounting");c.assertTrue(e.status().contains("queued=0"),"objective complete");c.complete();
        });
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE, batchId="resume", tickLimit=100)
    public void savesAndResumesRemainingCraftBatches(TestContext c) {
        EchoEntity e=echo(c,new BlockPos(1,2,1)); var p=player(c,e); e.bag.setStack(0,new ItemStack(Items.OAK_LOG,2));
        e.submit(new Plan("Crafting.",java.util.List.of(new Plan.Step(Plan.Kind.CRAFT,"minecraft:oak_planks",2,0,0,0))));
        c.waitAndRun(12,()->{
            e.setPaused(true);c.assertEquals(e.findItem(Items.OAK_PLANKS).getCount(),4,"first batch complete");
            NbtCompound saved=new NbtCompound();e.writeCustomDataToNbt(saved);e.discard();
            EchoEntity restored=EchoMod.COMPANION.create(c.getWorld());restored.readCustomDataFromNbt(saved);restored.refreshPositionAndAngles(e.getX(),e.getY(),e.getZ(),0,0);restored.setNoGravity(true);c.getWorld().spawnEntity(restored);
            c.assertTrue(restored.paused,"saved tasks require resume");restored.setPaused(false);
            c.waitAndRun(40,()->{c.assertEquals(restored.findItem(Items.OAK_PLANKS).getCount(),8,"only remaining batch executes");c.assertTrue(restored.findItem(Items.OAK_LOG).isEmpty(),"both logs consumed exactly once");c.complete();});
        });
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="carry")
    public void cubeCarryPreservesIdentityInventoryHealthAndMemory(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);var id=e.getUuid();
        e.bag.setStack(0,new ItemStack(Items.DIAMOND,7));e.setHealth(17);e.rememberPlace("home");e.mineAllowed=true;
        dev.echo.item.EchoCubeItem.pickup(e,p);
        c.assertTrue(e.isRemoved(),"world entity removed while carried");ItemStack item=p.getInventory().getStack(0);
        c.assertTrue(item.isOf(EchoMod.CUBE),"holdable cube item created");
        BlockPos target=c.getAbsolutePos(new BlockPos(3,2,1));c.getWorld().setBlockState(target.down(),Blocks.STONE.getDefaultState());
        c.assertTrue(dev.echo.item.EchoCubeItem.deploy(p,item,target),"cube redeploys");
        var restored=(EchoEntity)c.getWorld().getEntity(id);
        c.assertTrue(restored!=null && restored.getUuid().equals(id),"same companion identity");
        c.assertEquals(restored.findItem(Items.DIAMOND).getCount(),7,"inventory preserved");c.assertEquals(restored.getHealth(),17f,"health preserved");
        c.assertEquals(restored.place("home"),e.getBlockPos(),"memory preserved");c.assertTrue(restored.mineAllowed && restored.paused,"permission preserved and work paused");
        c.assertTrue(item.isEmpty(),"carried item consumed even in creative");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="carrycopy")
    public void copiedCubeCannotDuplicateCompanionAndLedgerRoundtrips(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);dev.echo.item.EchoCubeItem.pickup(e,p);
        var ledger=dev.echo.world.CompanionLedger.get(p.getServer());NbtCompound saved=new NbtCompound();ledger.writeNbt(saved,c.getWorld().getRegistryManager());
        var loaded=dev.echo.world.CompanionLedger.read(saved,c.getWorld().getRegistryManager());c.assertTrue(loaded.carried.containsKey(e.getUuid()),"canonical carry state persists in world ledger");
        ItemStack original=p.getInventory().getStack(0),copy=original.copy();BlockPos target=c.getAbsolutePos(new BlockPos(3,2,1));c.getWorld().setBlockState(target.down(),Blocks.STONE.getDefaultState());
        dev.echo.item.EchoCubeItem.deploy(p,original,target);
        boolean rejected=false;try {dev.echo.item.EchoCubeItem.deploy(p,copy,target.add(0,0,1));}catch(IllegalArgumentException ex){rejected=true;}
        c.assertTrue(rejected,"copied token rejected after deployment");c.assertFalse(ledger.carried.containsKey(e.getUuid()),"canonical state consumed exactly once");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="carryfull")
    public void fullInventoryDoesNotRemoveOrPauseEcho(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);
        for(int i=0;i<36;i++)p.getInventory().setStack(i,new ItemStack(Items.STONE,64));
        boolean rejected=false;try{dev.echo.item.EchoCubeItem.pickup(e,p);}catch(IllegalArgumentException ex){rejected=true;}
        c.assertTrue(rejected && !e.isRemoved() && !e.paused,"full inventory leaves world companion untouched");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="carryblocked")
    public void obstructedDeploymentKeepsStoredCompanionAndItem(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);dev.echo.item.EchoCubeItem.pickup(e,p);
        ItemStack token=p.getInventory().getStack(0);BlockPos target=c.getAbsolutePos(new BlockPos(3,2,1));
        c.getWorld().setBlockState(target.down(),Blocks.STONE.getDefaultState());c.getWorld().setBlockState(target,Blocks.STONE.getDefaultState());
        boolean rejected=false;try{dev.echo.item.EchoCubeItem.deploy(p,token,target);}catch(IllegalArgumentException ex){rejected=true;}
        c.assertTrue(rejected && token.getCount()==1,"blocked placement does not consume item");
        c.assertTrue(dev.echo.world.CompanionLedger.get(p.getServer()).carried.containsKey(e.getUuid()),"stored state retained");c.complete();
    }
    private void floor(TestContext c) {for(int x=-5;x<8;x++)for(int z=-5;z<8;z++)c.setBlockState(new BlockPos(x,1,z),Blocks.STONE);}
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="arrivalnew")
    public void firstArrivalCreatesOneNearbyCube(TestContext c) {
        floor(c);var dummy=echo(c,new BlockPos(1,2,1));var p=player(c,dummy);dummy.discard();
        var ledger=dev.echo.world.CompanionLedger.get(p.getServer());ledger.companions.remove(p.getUuid());
        c.assertTrue(dev.echo.world.EchoArrival.arrive(p),"first arrival succeeds");var first=dev.echo.world.EchoArrival.loaded(p);var id=first.getUuid();
        c.assertTrue(first.squaredDistanceTo(p)<36,"spawned beside player");
        c.assertTrue(dev.echo.world.EchoArrival.arrive(p),"second arrival reuses identity");c.assertEquals(dev.echo.world.EchoArrival.loaded(p).getUuid(),id,"no duplicate on rejoin");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="arrivalpark")
    public void disconnectParksAndArrivalRestoresSameCompanion(TestContext c) {
        floor(c);var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);e.bag.setStack(0,new ItemStack(Items.DIAMOND,5));var id=e.getUuid();
        dev.echo.world.EchoArrival.park(p);c.assertTrue(e.isRemoved(),"offline entity parked");
        var ledger=dev.echo.world.CompanionLedger.get(p.getServer());var data=new NbtCompound();ledger.writeNbt(data,c.getWorld().getRegistryManager());
        c.assertTrue(dev.echo.world.CompanionLedger.read(data,c.getWorld().getRegistryManager()).parked.containsKey(id),"parked state survives world ledger save");
        c.assertTrue(dev.echo.world.EchoArrival.arrive(p),"join restores parked cube");var restored=dev.echo.world.EchoArrival.loaded(p);
        c.assertEquals(restored.getUuid(),id,"identity retained");c.assertEquals(restored.findItem(Items.DIAMOND).getCount(),5,"inventory retained");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="arrivalcarried")
    public void arrivalDoesNotUnpackACarriedCube(TestContext c) {
        floor(c);var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);dev.echo.item.EchoCubeItem.pickup(e,p);
        c.assertTrue(dev.echo.world.EchoArrival.arrive(p),"carried state acknowledged");c.assertTrue(dev.echo.world.EchoArrival.loaded(p)==null,"no live duplicate");
        c.assertTrue(p.getInventory().getStack(0).isOf(EchoMod.CUBE),"cube remains carried");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="arrivalunknown")
    public void missingStoredUuidNeverCreatesDuplicate(TestContext c) {
        floor(c);var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);var ledger=dev.echo.world.CompanionLedger.get(p.getServer());
        var missing=java.util.UUID.randomUUID();ledger.companions.put(p.getUuid(),missing);
        c.assertFalse(dev.echo.world.EchoArrival.arrive(p),"unresolved UUID fails safely");c.assertEquals(ledger.companions.get(p.getUuid()),missing,"ownership not replaced");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="hearing")
    public void actualServerSoundReachesEchoAndRemoteOwnerContext(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);p.refreshPositionAndAngles(e.getX()+100,e.getY(),e.getZ(),0,0);
        c.getWorld().playSound(null,e.getX(),e.getY(),e.getZ(),net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_AMBIENT,net.minecraft.sound.SoundCategory.HOSTILE,1,1);
        c.assertTrue(e.hearing.summary(c.getWorld().getTime()).contains("minecraft:entity.zombie.ambient"),"server sound hook records actual nearby sound");
        c.assertTrue(e.snapshot().contains("minecraft:entity.zombie.ambient"),"planner receives auditory context even while owner is far away");
        c.assertEquals(dev.echo.world.EchoArrival.loaded(p),e,"remote communication resolves ownership rather than distance");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="hearingfar")
    public void distantSoundsAreNotOmniscientHearing(TestContext c) {
        var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);
        c.getWorld().playSound(null,e.getX()+100,e.getY(),e.getZ(),net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_AMBIENT,net.minecraft.sound.SoundCategory.HOSTILE,1,1);
        c.assertTrue(e.hearing.recent(c.getWorld().getTime()).isEmpty(),"distant sound not recorded");c.complete();
    }
    @GameTest(templateName=FabricGameTest.EMPTY_STRUCTURE,batchId="remoterecall",tickLimit=200)
    public void spokenTeleportPhraseRecallsSameCubeAcrossDimensions(TestContext c) {
        floor(c);var e=echo(c,new BlockPos(1,2,1));var p=player(c,e);index(e,p);e.bag.setStack(0,new ItemStack(Items.DIAMOND,3));var id=e.getUuid();
        var nether=p.getServer().getWorld(net.minecraft.world.World.NETHER);
        nether.getChunk(0,0); // Fixture terrain must finish loading before testing the return recall.
        var moved=(EchoEntity)e.teleportTo(new net.minecraft.world.TeleportTarget(nether,new net.minecraft.util.math.Vec3d(0.5,65,0.5),net.minecraft.util.math.Vec3d.ZERO,0,0,net.minecraft.world.TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
        c.assertTrue(moved!=null && moved.owner()==p,"remote owner remains reachable across dimensions");
        c.waitAndRun(5,()->{
            c.assertTrue(dev.echo.world.EchoArrival.loaded(p)!=null,"transferred cube registered: old="+id+" new="+moved.getUuid()+" removed="+moved.isRemoved()+" directLookup="+nether.getEntity(id));
            EchoCommunication.message(p,"Echo, teleport over to me!");
            dev.echo.world.EchoArrival.tick(p.getServer());
            c.waitAndRun(25,()->{
                var restored=dev.echo.world.EchoArrival.loaded(p);
                c.assertTrue(restored!=null && restored.getWorld()==p.getWorld() && restored.squaredDistanceTo(p)<36,"recall places Echo safely beside owner");
                c.assertEquals(restored.getUuid(),id,"same identity across dimensional recall");c.assertEquals(restored.findItem(Items.DIAMOND).getCount(),3,"inventory preserved");c.complete();
            });
        });
    }
}
