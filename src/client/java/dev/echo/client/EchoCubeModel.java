package dev.echo.client;

import dev.echo.entity.EchoEntity;
import dev.echo.entity.CompanionIdle;
import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Original Minecraft-native 10-pixel white cube. No humanoid body or limbs. */
public final class EchoCubeModel extends SinglePartEntityModel<EchoEntity> {
    public static final EntityModelLayer LAYER=new EntityModelLayer(Identifier.of("echo","cube"),"main");
    private final ModelPart root, cube;
    public EchoCubeModel(ModelPart root) { this.root=root;cube=root.getChild("cube"); }
    public static TexturedModelData data() {
        ModelData model=new ModelData();
        model.getRoot().addChild("cube",ModelPartBuilder.create().uv(0,0).cuboid(-5,-10,-5,10,10,10),ModelTransform.pivot(0,24,0));
        return TexturedModelData.of(model,64,32);
    }
    @Override public ModelPart getPart() { return root; }
    @Override public void setAngles(EchoEntity entity,float limbAngle,float limbDistance,float animationProgress,float headYaw,float headPitch) {
        cube.resetTransform();
        cube.xScale=cube.yScale=cube.zScale=0.5f;
        float time=entity.poseTime(animationProgress-entity.age);
        float gait=Math.min(limbDistance,1);
        cube.pivotY=24-Math.abs(MathHelper.sin(limbAngle*0.8f))*gait*0.6f;
        cube.roll=MathHelper.sin(limbAngle*0.4f)*gait*0.08f;
        cube.yaw=headYaw*MathHelper.RADIANS_PER_DEGREE*0.35f;
        cube.pitch=headPitch*MathHelper.RADIANS_PER_DEGREE*0.2f;
        switch(entity.companionPose()){
            case SLEEP->{
                rest(-0.6f);cube.yaw=0;cube.pitch=0;
                cube.yScale=0.5f*(1+0.025f*MathHelper.sin(time*0.08f));
            }
            case WAKE->{
                float phase=Math.clamp(time/CompanionIdle.WAKE_TICKS,0,1);
                rest(-0.6f*(1-phase));cube.yScale=0.5f*(1+0.12f*MathHelper.sin(phase*MathHelper.PI));
            }
            case GREET->{
                float phase=Math.clamp(time/CompanionIdle.GREET_TICKS,0,1);
                cube.pivotY-=Math.max(0,MathHelper.sin(phase*MathHelper.PI))*1.8f;
                cube.roll+=MathHelper.sin(phase*MathHelper.TAU)*0.15f;
            }
            case CURIOUS->{
                cube.roll+=MathHelper.sin(time*0.1f)*0.15f;
                cube.yaw+=MathHelper.sin(time*0.07f)*0.3f;
                cube.pitch+=MathHelper.sin(time*0.12f)*0.08f;
            }
            case WORK->{
                cube.pitch+=MathHelper.sin(time*0.6f)*0.12f;
                cube.pivotY-=Math.abs(MathHelper.sin(time*0.6f))*0.45f;
            }
            case ALERT->{cube.pitch-=0.08f;cube.yScale=0.53f;}
            default->{if(gait<0.01f)cube.yScale=0.5f*(1+0.012f*MathHelper.sin(animationProgress*0.09f));}
        }
    }
    private void rest(float roll){
        cube.roll=roll;
        // Rotate around the center while keeping the lower corner above the floor.
        cube.pivotX=-2.5f*MathHelper.sin(roll);
        cube.pivotY=24-2.5f*Math.abs(MathHelper.sin(roll));
    }
}
