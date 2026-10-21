package dev.echo.entity;

/** Server-side, model-free behavior. Timers are transient, never a saved gameplay task. */
public final class CompanionIdle {
    public enum Pose { IDLE, SLEEP, WAKE, GREET, CURIOUS, WORK, ALERT }
    public static final int SLEEP_AFTER=600, WAKE_TICKS=20, GREET_TICKS=24;
    private int stillTicks,wakeTicks,greetTicks;
    private boolean sleeping,wasAway;
    public boolean sleeping(){return sleeping;}
    public void interrupt(){
        if(sleeping)wakeTicks=WAKE_TICKS;
        sleeping=false;stillTicks=0;greetTicks=0;
    }
    public void clear(){sleeping=false;stillTicks=0;wakeTicks=0;greetTicks=0;wasAway=false;}
    public Pose tick(boolean ownerMoved,boolean canRest,boolean danger,boolean busy,boolean working,boolean thinking,boolean walking,boolean following,double ownerDistanceSquared){
        if(ownerDistanceSquared>=64)wasAway=true;
        if(ownerMoved || !canRest)interrupt();
        if(danger){wakeTicks=0;return Pose.ALERT;}
        if(busy){wakeTicks=0;return working?Pose.WORK:thinking?Pose.CURIOUS:Pose.IDLE;}
        if(wakeTicks>0){wakeTicks--;return Pose.WAKE;}
        if(sleeping)return Pose.SLEEP;
        if(canRest && !ownerMoved && ++stillTicks>=SLEEP_AFTER){sleeping=true;return Pose.SLEEP;}
        if(wasAway && following && ownerDistanceSquared<=16 && canRest && !ownerMoved && !walking){wasAway=false;greetTicks=GREET_TICKS;}
        if(greetTicks>0){greetTicks--;return Pose.GREET;}
        if(canRest && stillTicks%240>=160 && stillTicks%240<200)return Pose.CURIOUS;
        return Pose.IDLE;
    }
}
