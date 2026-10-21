package dev.echo.action;

import dev.echo.ai.Plan;
import dev.echo.entity.EchoEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

public final class ActionController {
    private final EchoEntity echo;
    private Plan.Step step;
    private BlockPos mining;
    private int elapsed, done, miningTicks;
    private BlockPos explore;
    public ActionController(EchoEntity echo) { this.echo = echo; }
    public void reset() { clearCracks(); step = null; mining = null; explore = null; elapsed = done = miningTicks = 0; echo.getNavigation().stop(); }
    public void suspend() { clearCracks(); miningTicks = 0; echo.getNavigation().stop(); }
    private void clearCracks() { if (mining != null && echo.getWorld() instanceof ServerWorld w) w.setBlockBreakingInfo(echo.getId(), mining, -1); }
    public void validate(Plan.Step s, ServerPlayerEntity owner) {
        if(owner.getWorld()!=echo.getWorld())throw new IllegalArgumentException("I'm in another dimension. Tell me 'teleport to me' first");
        switch (s.kind()) {
            case MINE -> { if (!echo.mineAllowed) throw new IllegalArgumentException("mining permission is off"); if (!Registries.BLOCK.containsId(Identifier.of(s.item()))) throw new IllegalArgumentException("unknown block"); }
            case PLACE -> {
                if (!echo.buildAllowed) throw new IllegalArgumentException("building permission is off");
                if (s.count() != 1 || !(Registries.ITEM.get(Identifier.of(s.item())) instanceof BlockItem)) throw new IllegalArgumentException("place needs one block item");
                checkLocation(new BlockPos(s.x(), s.y(), s.z()), owner);
            }
            case GOTO -> checkLocation(new BlockPos(s.x(), s.y(), s.z()), owner);
            case EXPLORE -> { if (!echo.exploreAllowed) throw new IllegalArgumentException("exploration permission is off"); }
            case CRAFT -> { if (!Registries.ITEM.containsId(Identifier.of(s.item()))) throw new IllegalArgumentException("unknown item"); }
            default -> { }
        }
    }
    private void checkLocation(BlockPos p, ServerPlayerEntity owner) {
        if (!p.isWithinDistance(echo.getPos(), 48) || !p.isWithinDistance(owner.getPos(), 48)
            || !echo.getWorld().isChunkLoaded(p) || !echo.getWorld().getWorldBorder().contains(p)
            || p.getY() < echo.getWorld().getBottomY() || p.getY() >= echo.getWorld().getTopY())
            throw new IllegalArgumentException("target must be loaded and within 48 blocks of both of us");
    }
    public void tick(Plan.Step next, ServerPlayerEntity owner) {
        if (step != next) { reset(); step = next; }
        try {
            validate(next, owner);
            if (++elapsed > 2400) throw new IllegalArgumentException("step timed out; path or resources may be blocked");
            switch (next.kind()) {
                case FOLLOW -> { echo.following = true; echo.finishStep("Following you."); }
                case STAY -> { echo.following = false; echo.finishStep("I'll wait here."); }
                case GOTO -> { if (move(new BlockPos(next.x(), next.y(), next.z()), 2)) echo.finishStep("Reached the waypoint."); }
                case EXPLORE -> explore(owner);
                case MINE -> mine(next, owner);
                case CRAFT -> { if (elapsed % 10 == 0) { craft(next); if (++done >= next.count()) echo.finishStep("Crafted " + done + " recipe batches."); } }
                case PLACE -> place(next, owner);
                case DELIVER -> {
                    if (move(owner.getBlockPos(), 2.5)) {
                        for (int i = 0; i < echo.bag.size(); i++) {
                            ItemStack stack = echo.bag.getStack(i); owner.getInventory().insertStack(stack);
                            if (stack.isEmpty()) echo.bag.setStack(i, ItemStack.EMPTY);
                        }
                        echo.finishStep("Returned what fits in your inventory; I kept any overflow.");
                    }
                }
            }
        } catch (IllegalArgumentException ex) { echo.failStep(ex.getMessage()); }
        catch (Exception ex) { dev.echo.EchoMod.LOG.error("Echo action failed: {}", ex.getClass().getSimpleName()); echo.failStep("gameplay action failed safely"); }
    }
    private boolean move(BlockPos p, double reach) {
        if (p.isWithinDistance(echo.getPos(), reach)) { echo.getNavigation().stop(); return true; }
        if (elapsed % 10 == 0) echo.getNavigation().startMovingTo(p.getX()+0.5, p.getY(), p.getZ()+0.5, 1.1);
        return false;
    }
    private void explore(ServerPlayerEntity owner) {
        if (explore == null) {
            ServerWorld w = (ServerWorld)echo.getWorld();
            for (int i = 0; i < 12; i++) {
                BlockPos p = echo.getBlockPos().add(echo.getRandom().nextInt(25)-12, 0, echo.getRandom().nextInt(25)-12);
                if (!w.isChunkLoaded(p)) continue;
                p = w.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
                if (p.isWithinDistance(owner.getPos(), 32) && Math.abs(p.getY()-echo.getY()) < 8
                    && w.getBlockState(p).isAir() && w.getBlockState(p.down()).getFluidState().isEmpty()
                    && echo.getNavigation().findPathTo(p, 1) != null) { explore = p; break; }
            }
            if (explore == null) throw new IllegalArgumentException("no safe nearby exploration path");
        }
        if (move(explore, 2)) { echo.remember("Discovered " + explore.toShortString() + " in " + echo.getWorld().getBiome(explore).getKey().map(k->k.getValue().toString()).orElse("unknown biome")); echo.finishStep("Scouted a nearby waypoint."); }
    }
    private boolean visible(BlockPos p) {
        var hit = echo.getWorld().raycast(new RaycastContext(echo.getEyePos(), Vec3d.ofCenter(p), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, echo));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(p);
    }
    private boolean permitted(BlockPos p, ServerPlayerEntity owner) {
        return owner.getAbilities().allowModifyWorld && echo.getWorld().canPlayerModifyAt(owner, p)
            && echo.getWorld().getWorldBorder().contains(p) && echo.getWorld().isChunkLoaded(p)
            && p.isWithinDistance(owner.getPos(), 48);
    }
    private void mine(Plan.Step s, ServerPlayerEntity owner) {
        ServerWorld w = (ServerWorld)echo.getWorld(); Block requested = Registries.BLOCK.get(Identifier.of(s.item()));
        if (mining == null) {
            if (elapsed % 20 != 0) return;
            double best = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.iterate(echo.getBlockPos().add(-8,-4,-8), echo.getBlockPos().add(8,4,8))) {
                if (!w.isChunkLoaded(p) || !w.getBlockState(p).isOf(requested) || !permitted(p, owner) || w.getBlockEntity(p) != null) continue;
                if (p.equals(echo.getBlockPos().down()) || p.equals(owner.getBlockPos().down()) || !visible(p)) continue;
                double distance = p.getSquaredDistance(echo.getPos());
                if (distance < best) { mining = p.toImmutable(); best = distance; }
            }
            if (mining == null) throw new IllegalArgumentException("no exposed matching blocks nearby");
        }
        if (!w.getBlockState(mining).isOf(requested)) { clearCracks(); mining = null; miningTicks = 0; return; }
        if (!permitted(mining, owner) || w.getBlockEntity(mining) != null) throw new IllegalArgumentException("block is protected");
        if (!move(mining, 3) || !visible(mining)) { miningTicks = 0; clearCracks(); return; }
        BlockState state = w.getBlockState(mining);
        float hardness = state.getHardness(w, mining);
        if (hardness < 0 || !state.getFluidState().isEmpty()) throw new IllegalArgumentException("unbreakable or fluid block");
        ItemStack tool = echo.bestTool(state);
        if (state.isToolRequired() && !tool.isSuitableFor(state)) throw new IllegalArgumentException("I need the correct mining tool; use /echo give");
        int ticks = Math.max(1, (int)Math.ceil(hardness * 30 / Math.max(1, tool.getMiningSpeedMultiplier(state))));
        echo.getLookControl().lookAt(Vec3d.ofCenter(mining));
        if (++miningTicks % 6 == 0) echo.swingHand(Hand.MAIN_HAND);
        w.setBlockBreakingInfo(echo.getId(), mining, Math.min(9, miningTicks * 10 / ticks));
        if (miningTicks < ticks) return;
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(w, owner, mining, state, null)) throw new IllegalArgumentException("a protection mod denied mining");
        // Vanilla loot tables, including tool/enchantment checks. No invented drops.
        var drops = Block.getDroppedStacks(state, w, mining, null, echo, tool);
        if (!w.breakBlock(mining, false, echo)) throw new IllegalArgumentException("block did not break");
        for (ItemStack drop : drops) echo.collectOrDrop(drop);
        state.onStacksDropped(w, mining, tool, true);
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(w, owner, mining, state, null);
        tool.damage(1, echo, net.minecraft.entity.EquipmentSlot.MAINHAND);
        clearCracks(); mining = null; miningTicks = 0;
        if (++done >= s.count()) echo.finishStep("Mined " + done + " blocks.");
    }
    private static final class Context extends ItemUsageContext {
        Context(ServerWorld w, ServerPlayerEntity player, ItemStack stack, BlockHitResult hit) { super(w, player, Hand.MAIN_HAND, stack, hit); }
    }
    private void place(Plan.Step s, ServerPlayerEntity owner) {
        BlockPos target = new BlockPos(s.x(), s.y(), s.z()); ServerWorld w = (ServerWorld)echo.getWorld();
        if (!move(target, 3)) return;
        if (!permitted(target, owner) || !w.getBlockState(target).isAir() || !visible(target)) throw new IllegalArgumentException("placement target is obstructed or protected");
        ItemStack stack = echo.findItem(Registries.ITEM.get(Identifier.of(s.item())));
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem block)) throw new IllegalArgumentException("missing block item");
        Block b = block.getBlock();
        if (b instanceof TntBlock || b instanceof FallingBlock || b instanceof AbstractFireBlock || b instanceof BlockEntityProvider
            || !b.getDefaultState().isFullCube(w, target)) throw new IllegalArgumentException("only ordinary solid construction blocks are supported");
        for (Direction face : Direction.values()) {
            BlockPos support = target.offset(face.getOpposite());
            if (!w.getBlockState(support).isSideSolidFullSquare(w, support, face)) continue;
            if (!permitted(support, owner)) continue;
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(0.5)), face, support, false);
            if (UseBlockCallback.EVENT.invoker().interact(owner, w, Hand.MAIN_HAND, hit) == ActionResult.FAIL) throw new IllegalArgumentException("a protection mod denied placement");
            // A non-player inventory is consumed even when the owner is in creative mode.
            int before = stack.getCount();
            ActionResult result = block.place(new ItemPlacementContext(new Context(w, owner, stack, hit)));
            if (result.isAccepted() && w.getBlockState(target).isOf(b)) {
                stack.setCount(before-1); echo.swingHand(Hand.MAIN_HAND); echo.finishStep("Placed " + s.item() + "."); return;
            }
        }
        throw new IllegalArgumentException("no valid supported placement face");
    }
    private void craft(Plan.Step s) {
        ServerWorld w = (ServerWorld)echo.getWorld(); Item output = Registries.ITEM.get(Identifier.of(s.item()));
        boolean table = false;
        for (BlockPos p : BlockPos.iterate(echo.getBlockPos().add(-3,-2,-3), echo.getBlockPos().add(3,2,3)))
            if (w.isChunkLoaded(p) && w.getBlockState(p).isOf(Blocks.CRAFTING_TABLE) && visible(p)) { table = true; break; }
        for (RecipeEntry<CraftingRecipe> entry : w.getRecipeManager().listAllOfType(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = entry.value();
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) || !recipe.getResult(w.getRegistryManager()).isOf(output)) continue;
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (recipe.getIngredients().size()+2)/3;
            if (!table && !recipe.fits(2,2)) continue;
            var ingredients = recipe.getIngredients();
            List<ItemStack> grid = new ArrayList<>(Collections.nCopies(width*height, ItemStack.EMPTY));
            int[] used = new int[echo.bag.size()]; boolean found = true;
            for (int g = 0; g < ingredients.size(); g++) {
                Ingredient ingredient = ingredients.get(g); if (ingredient.isEmpty()) continue;
                int slot = -1;
                for (int i = 0; i < used.length; i++) if (echo.bag.getStack(i).getCount() > used[i] && ingredient.test(echo.bag.getStack(i))) { slot = i; break; }
                if (slot < 0) { found = false; break; }
                used[slot]++; grid.set(g, echo.bag.getStack(slot).copyWithCount(1));
            }
            if (!found) continue;
            CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
            if (!recipe.matches(input, w)) continue;
            ItemStack result = recipe.craft(input, w.getRegistryManager());
            if (result.isEmpty()) continue;
            var remainders = recipe.getRemainder(input);
            for (int i = 0; i < used.length; i++) if (used[i] > 0) echo.bag.getStack(i).decrement(used[i]);
            echo.collectOrDrop(result);
            for (ItemStack remainder : remainders) if (!remainder.isEmpty()) echo.collectOrDrop(remainder);
            echo.swingHand(Hand.MAIN_HAND); return;
        }
        throw new IllegalArgumentException("no craftable ordinary recipe: missing ingredients or nearby crafting table");
    }
    public String progress() { return step == null ? "idle" : step.kind() + " " + done + "/" + step.count() + " (" + elapsed/20 + "s)"; }
    public Plan.Step remaining(Plan.Step queued) {
        if (queued != step || (queued.kind() != Plan.Kind.MINE && queued.kind() != Plan.Kind.CRAFT)) return queued;
        return new Plan.Step(queued.kind(), queued.item(), Math.max(1,queued.count()-done), queued.x(),queued.y(),queued.z());
    }
}
