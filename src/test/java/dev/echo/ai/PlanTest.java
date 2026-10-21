package dev.echo.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanTest {
    @Test void parsesOrderedMultiStepObjective() {
        Plan p=Plan.parse("{\"reply\":\"I'll gather and craft.\",\"steps\":[{\"action\":\"mine\",\"item\":\"minecraft:oak_log\",\"count\":3},{\"action\":\"craft\",\"item\":\"minecraft:oak_planks\",\"count\":3},{\"action\":\"deliver\"}]}");
        assertEquals(3,p.steps().size()); assertEquals(Plan.Kind.MINE,p.steps().getFirst().kind()); assertEquals(Plan.Kind.DELIVER,p.steps().getLast().kind());
    }
    @Test void cannotPromoteModelTextToServerCommands() {
        assertThrows(RuntimeException.class,()->Plan.parse("{\"steps\":[{\"action\":\"command\",\"item\":\"op @a\"}]}"));
    }
    @Test void rejectsUnboundedAndInvalidPlans() {
        for(String step:new String[]{"{\"action\":\"mine\",\"count\":65}","{\"action\":\"mine\",\"count\":-1}","{\"action\":\"goto\",\"x\":2147483648}","{\"action\":\"goto\",\"x\":1.5}","{\"action\":\"place\",\"item\":\"../../secrets\"}","{\"action\":\"goto\",\"y\":400}"})
            assertThrows(RuntimeException.class,()->Plan.parse("{\"steps\":["+step+"]}"));
        assertThrows(RuntimeException.class,()->Plan.parse("{\"steps\":["+String.join(",",java.util.Collections.nCopies(33,"{\"action\":\"follow\"}"))+"]}"));
        assertThrows(RuntimeException.class,()->Plan.parse("x".repeat(24001)));
    }
    @Test void conversationDoesNotNeedActions() { assertTrue(Plan.parse("{\"reply\":\"Hello!\",\"steps\":[]}").steps().isEmpty()); }
}
