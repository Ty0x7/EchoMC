package dev.echo.setup;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.echo.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.*;

/** Dedicated-server setup is operator-only, and also works from its console. */
public final class SetupCommands {
    private static boolean bool(String value) {
        if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException("Use true or false.");
        return Boolean.parseBoolean(value);
    }
    public static EchoConfig changed(EchoConfig current,String option,String value) {
        EchoConfig c=current.copy();
        switch(option){
            case "planner-enabled"->c.aiEnabled=bool(value);
            case "planner-url"->c.plannerUrl=value;
            case "planner-model"->c.plannerModel=value;
            case "planner-key-env"->c.plannerKeyEnv=value;
            case "kev-enabled"->c.kevEnabled=bool(value);
            case "kev-url"->c.kevUrl=value;
            case "kev-model"->c.kevModel=value;
            case "kev-key-env"->c.kevKeyEnv=value;
            case "allow-remote"->c.allowRemoteBackends=bool(value);
            case "auto-arrival"->c.autoSpawnOnJoin=bool(value);
            case "hearing-radius"->c.hearingRadius=Integer.parseInt(value);
            case "timeout-seconds"->c.timeoutSeconds=Integer.parseInt(value);
            default->throw new IllegalArgumentException("Unknown setting. Use command completion to choose one.");
        }
        return c.validate();
    }
    private interface Operation {void run() throws Exception;}
    private static int run(ServerCommandSource source,Operation op) {
        try{op.run();return 1;}catch(IllegalArgumentException ex){source.sendError(Text.literal("Invalid setting. Check value, range, full endpoint and remote HTTPS policy."));}
        catch(Exception ex){source.sendError(Text.literal("Could not read or save Echo configuration. Existing runtime settings remain active."));}return 0;
    }
    private static void reply(ServerCommandSource s,String text){s.sendFeedback(()->Text.literal("[Echo setup] "+text),false);}
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        var root=literal("echo-config").requires(s->s.hasPermissionLevel(2));
        root.executes(c->{reply(c.getSource(),"status | set <option> <value> | reload | test models/planner/kev. Clients: F8 or /echo-setup.");return 1;});
        root.then(literal("status").executes(c->{var cfg=EchoMod.CONFIG;reply(c.getSource(),"Planner: "+cfg.aiEnabled+" ("+cfg.plannerModel+"); Kev: "+cfg.kevEnabled+"; automatic arrival: "+cfg.autoSpawnOnJoin+"; hearing: "+cfg.hearingRadius+" blocks.");reply(c.getSource(),"Planner key environment: "+ConnectionChecks.keyStatus(cfg.plannerKeyEnv)+"; Kev key environment: "+ConnectionChecks.keyStatus(cfg.kevKeyEnv)+". URLs and secrets are not printed.");return 1;}));
        root.then(literal("reload").executes(c->run(c.getSource(),()->{EchoConfig next=EchoConfig.read();EchoMod.CONFIG=next;reply(c.getSource(),"Server configuration reloaded. No restart needed; in-flight results from older settings are discarded.");})));
        root.then(literal("set").then(argument("option",StringArgumentType.word()).suggests((ctx,b)->{
            for(String option:new String[]{"planner-enabled","planner-url","planner-model","planner-key-env","kev-enabled","kev-url","kev-model","kev-key-env","allow-remote","auto-arrival","hearing-radius","timeout-seconds"})if(option.startsWith(b.getRemaining()))b.suggest(option);return b.buildFuture();
        }).then(argument("value",StringArgumentType.greedyString()).executes(c->run(c.getSource(),()->{
            EchoConfig next=changed(EchoMod.CONFIG,StringArgumentType.getString(c,"option"),StringArgumentType.getString(c,"value"));next.save();EchoMod.CONFIG=next;reply(c.getSource(),"Saved and applied. Existing tasks keep their permissions. Old inference results are discarded.");
        })))));
        for(String kind:new String[]{"models","planner","kev"})root.then(literal("test").then(literal(kind).executes(c->{
            var source=c.getSource();var snapshot=EchoMod.CONFIG.copy();reply(source,kind.equals("models")?"Checking model list; no inference.":"Running synthetic "+kind+" inference check; no player/world data or gameplay actions.");
            ConnectionChecks.submit(()->switch(kind){case "models"->ConnectionChecks.models(snapshot);case "planner"->ConnectionChecks.planner(snapshot);default->ConnectionChecks.kev(snapshot);},r->source.getServer().execute(()->reply(source,(r.ok()?"OK: ":"Check needed: ")+r.message())));return 1;
        })));
        d.register(root);
    }
}
