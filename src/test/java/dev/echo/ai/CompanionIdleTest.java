package dev.echo.ai;

import dev.echo.entity.CompanionIdle;
import dev.echo.entity.CompanionIdle.Pose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionIdleTest {
    private Pose rest(CompanionIdle idle){return idle.tick(false,true,false,false,false,false,false,true,1);}
    private void sleep(CompanionIdle idle){for(int i=0;i<CompanionIdle.SLEEP_AFTER;i++)rest(idle);assertTrue(idle.sleeping());}
    @Test void sleepsOnlyAfterThirtySecondsOfSafeIdle(){
        var idle=new CompanionIdle();for(int i=0;i<CompanionIdle.SLEEP_AFTER-1;i++)assertNotEquals(Pose.SLEEP,rest(idle));
        assertEquals(Pose.SLEEP,rest(idle));assertEquals(Pose.SLEEP,rest(idle));
    }
    @Test void ownerMovementWakesAndStartsANewIdleWindow(){
        var idle=new CompanionIdle();sleep(idle);
        assertEquals(Pose.WAKE,idle.tick(true,true,false,false,false,false,false,true,1));assertFalse(idle.sleeping());
        for(int i=0;i<CompanionIdle.WAKE_TICKS+CompanionIdle.SLEEP_AFTER-2;i++)assertNotEquals(Pose.SLEEP,rest(idle));
        assertEquals(Pose.SLEEP,rest(idle));
    }
    @Test void workingAndPlanningNeverSleep(){
        var idle=new CompanionIdle();for(int i=0;i<1000;i++)assertEquals(Pose.WORK,idle.tick(false,false,false,true,true,false,false,true,1));
        for(int i=0;i<1000;i++)assertEquals(Pose.CURIOUS,idle.tick(false,false,false,true,false,true,false,true,1));
        assertFalse(idle.sleeping());
    }
    @Test void dangerOverridesSleepImmediately(){
        var idle=new CompanionIdle();sleep(idle);assertEquals(Pose.ALERT,idle.tick(false,false,true,false,false,false,false,true,1));assertFalse(idle.sleeping());
    }
    @Test void aNewTaskInterruptsSleepWithoutWaitingForWakeAnimation(){
        var idle=new CompanionIdle();sleep(idle);assertEquals(Pose.WORK,idle.tick(false,false,false,true,true,false,false,true,1));assertFalse(idle.sleeping());
    }
    @Test void greetingsRequireFollowingAndAQuietReunion(){
        var idle=new CompanionIdle();idle.tick(false,false,false,false,false,false,true,true,100);
        assertNotEquals(Pose.GREET,idle.tick(false,true,false,false,false,false,false,false,1));
        assertEquals(Pose.GREET,rest(idle));for(int i=0;i<CompanionIdle.GREET_TICKS-1;i++)assertEquals(Pose.GREET,rest(idle));
        assertNotEquals(Pose.GREET,rest(idle));
    }
    @Test void curiousGlanceDoesNotDelayOrReplaceSleep(){
        var idle=new CompanionIdle();for(int i=0;i<159;i++)rest(idle);assertEquals(Pose.CURIOUS,rest(idle));
        for(int i=160;i<CompanionIdle.SLEEP_AFTER;i++)rest(idle);assertTrue(idle.sleeping());
    }
    @Test void clearingForPauseDisconnectOrStorageLeavesNoSleepTimer(){
        var idle=new CompanionIdle();sleep(idle);idle.clear();assertFalse(idle.sleeping());assertEquals(Pose.IDLE,rest(idle));
    }
}
