package dev.echo.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public final class EchoPackets {
    public record Voice(String text) implements CustomPayload {
        public static final Id<Voice> ID = new Id<>(Identifier.of("echo", "voice"));
        public static final PacketCodec<RegistryByteBuf, Voice> CODEC = new PacketCodec<>() {
            public Voice decode(RegistryByteBuf b) { return new Voice(b.readString(1000)); }
            public void encode(RegistryByteBuf b, Voice p) { b.writeString(p.text, 1000); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record Speech(String text) implements CustomPayload {
        public static final Id<Speech> ID = new Id<>(Identifier.of("echo", "speech"));
        public static final PacketCodec<RegistryByteBuf, Speech> CODEC = new PacketCodec<>() {
            public Speech decode(RegistryByteBuf b) { return new Speech(b.readString(600)); }
            public void encode(RegistryByteBuf b, Speech p) { b.writeString(p.text, 600); }
        };
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
