package dev.echo.ai;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundMemoryTest {
    @Test void repeatedNearbySoundsAreDeduplicated() {
        var memory=new SoundMemory();assertTrue(memory.add(new SoundMemory.Cue("zombie",0,64,0,0)));
        assertFalse(memory.add(new SoundMemory.Cue("zombie",1,64,1,5)));assertEquals(1,memory.recent(5).size());
        assertTrue(memory.add(new SoundMemory.Cue("zombie",0,64,0,20)));
    }
    @Test void historyExpiresAndIsBounded() {
        var memory=new SoundMemory();for(int i=0;i<100;i++)memory.add(new SoundMemory.Cue("sound"+i,0,64,0,i*2));
        assertEquals(16,memory.recent(200).size());assertTrue(memory.recent(1000).isEmpty());
    }
    @Test void sameTickStormIsCapped() {
        var memory=new SoundMemory();for(int i=0;i<1000;i++)memory.add(new SoundMemory.Cue("sound"+i,0,64,0,1));
        assertEquals(4,memory.recent(1).size());assertTrue(memory.summary(21).contains("1s ago"));
    }
}
