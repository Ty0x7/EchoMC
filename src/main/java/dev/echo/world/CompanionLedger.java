package dev.echo.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import java.util.*;

/** Overworld-scoped ownership index prevents duplicate summons when the companion's chunk is unloaded. */
public final class CompanionLedger extends PersistentState {
    public final Map<UUID, UUID> companions = new HashMap<>();
    public final Map<UUID, String> locations = new HashMap<>();
    public final Map<UUID, NbtCompound> carried = new HashMap<>();
    public final Map<UUID, NbtCompound> parked = new HashMap<>();
    public final Map<UUID, NbtCompound> anchors = new HashMap<>();
    private static final Type<CompanionLedger> TYPE = new Type<>(CompanionLedger::new, CompanionLedger::read, null);
    public static CompanionLedger get(MinecraftServer server) { return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "echo_companions"); }
    public static CompanionLedger read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        CompanionLedger state = new CompanionLedger();
        NbtCompound owners = nbt.getCompound("owners");
        NbtCompound cubes = nbt.getCompound("carried");
        for (String key : cubes.getKeys()) try { state.carried.put(UUID.fromString(key), cubes.getCompound(key).copy()); } catch (IllegalArgumentException ignored) { }
        for(String field:List.of("parked","anchors"))for(String key:nbt.getCompound(field).getKeys())try{(field.equals("parked")?state.parked:state.anchors).put(UUID.fromString(key),nbt.getCompound(field).getCompound(key).copy());}catch(IllegalArgumentException ignored){}
        for (String key : owners.getKeys()) {
            try {
                UUID owner = UUID.fromString(key); state.companions.put(owner, owners.getUuid(key));String location=nbt.getCompound("locations").getString(key);state.locations.put(owner,location);
                if(!state.anchors.containsKey(owner)) {
                    var match=java.util.regex.Pattern.compile("^([a-z0-9_.-]+:[a-z0-9_/.-]+) (-?\\d+), (-?\\d+), (-?\\d+)$").matcher(location);
                    if(match.matches()) {NbtCompound anchor=new NbtCompound();anchor.putString("dimension",match.group(1));anchor.putLong("position",new net.minecraft.util.math.BlockPos(Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)),Integer.parseInt(match.group(4))).asLong());state.anchors.put(owner,anchor);}
                }
            }
            catch (IllegalArgumentException ignored) { }
        }
        return state;
    }
    @Override public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtCompound owners = new NbtCompound(), places = new NbtCompound();
        companions.forEach((owner, entity) -> owners.putUuid(owner.toString(), entity));
        locations.forEach((owner, place) -> places.putString(owner.toString(), place));
        NbtCompound cubes = new NbtCompound(); carried.forEach((id, data)->cubes.put(id.toString(),data.copy()));
        for(String field:List.of("parked","anchors")){NbtCompound entries=new NbtCompound();(field.equals("parked")?parked:anchors).forEach((key,value)->entries.put(key.toString(),value.copy()));nbt.put(field,entries);}
        nbt.put("carried",cubes); nbt.put("owners", owners); nbt.put("locations", places); return nbt;
    }
}
