package dev.echo.ai;

import java.util.*;

/** Short-term hearing only: bounded, deduplicated, ages expire; no recorded audio. */
public final class SoundMemory {
    public record Cue(String id,double x,double y,double z,long tick) { }
    private final ArrayDeque<Cue> cues=new ArrayDeque<>();
    private long budgetTick=Long.MIN_VALUE;
    private int accepted;
    public boolean add(Cue cue) {
        expire(cue.tick());
        if(budgetTick!=cue.tick()){budgetTick=cue.tick();accepted=0;}
        if(accepted>=4)return false;
        for(Cue old:cues)if(old.id().equals(cue.id()) && cue.tick()-old.tick()<10 && Math.abs(cue.x()-old.x())<2 && Math.abs(cue.z()-old.z())<2)return false;
        cues.addLast(cue);accepted++;while(cues.size()>16)cues.removeFirst();return true;
    }
    private void expire(long now){while(!cues.isEmpty() && now-cues.peekFirst().tick()>600)cues.removeFirst();}
    public List<Cue> recent(long now){expire(now);return List.copyOf(cues);}
    public String summary(long now){return recent(now).stream().map(c->c.id()+" at "+(int)Math.floor(c.x())+","+(int)Math.floor(c.y())+","+(int)Math.floor(c.z())+" ("+Math.max(0,(now-c.tick())/20)+"s ago)").toList().toString();}
}
