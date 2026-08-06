package net.rebornaddon.substitution.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.rebornaddon.RebornAddonMod;
import net.rebornaddon.substitution.SubstitutionEffectMessage;
import net.rebornaddon.village.Village;

import java.util.Random;

@SideOnly(Side.CLIENT)
public final class ClientSubstitutionParticles {
    public static final ClientSubstitutionParticles INSTANCE = new ClientSubstitutionParticles();

    private static final ParticleStyle LEAF = style("leaf_shard", 18, 1.5F, 0.82F, 0.45F, false);
    private static final ParticleStyle SAND = style("sand_wisp", 20, 1.55F, 0.72F, 0.08F, false);
    private static final ParticleStyle LIGHTNING = style("lightning_spark", 8, 1.75F, 0.9F, 0.0F, true);
    private static final ParticleStyle MIST = style("mist_curl", 32, 2.6F, 0.42F, 0.0F, false);
    private static final ParticleStyle RAIN = style("rain_streak", 13, 1.5F, 0.78F, 0.0F, false);
    private static final ParticleStyle STONE = style("stone_chip", 17, 1.35F, 0.9F, 0.9F, false);

    private ClientSubstitutionParticles() {
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        for (ParticleStyle style : new ParticleStyle[]{LEAF, SAND, LIGHTNING, MIST, RAIN, STONE}) {
            event.getMap().registerSprite(style.texture);
        }
    }

    public void spawn(SubstitutionEffectMessage message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.world;
        Village village = Village.byId(message.getVillageId());
        if (world == null || village == null) {
            return;
        }

        ParticleStyle style = styleFor(village);
        int count = countFor(message.getPhase());
        Random random = new Random(Double.doubleToLongBits(message.getX())
                ^ Double.doubleToLongBits(message.getZ()) ^ world.getTotalWorldTime() ^ message.getPhase());

        for (int i = 0; i < count; i++) {
            Particle particle = createParticle(world, style, village, message.getPhase(),
                    message.getX(), message.getY(), message.getZ(), random, i, count);
            minecraft.effectRenderer.addEffect(particle);
        }
    }

    private static Particle createParticle(World world, ParticleStyle style, Village village, int phase,
                                           double x, double y, double z, Random random, int index, int count) {
        double angle = Math.PI * 2.0D * index / Math.max(1, count) + random.nextDouble() * 0.45D;
        double radius = phase == SubstitutionEffectMessage.HIT ? 0.45D : 0.2D;
        double px = x + Math.cos(angle) * radius + spread(random, 0.16D);
        double py = y + spread(random, phase == SubstitutionEffectMessage.HIT ? 0.65D : 0.45D);
        double pz = z + Math.sin(angle) * radius + spread(random, 0.16D);
        double speed = phase == SubstitutionEffectMessage.HIT ? 0.13D : 0.055D;
        double motionX = Math.cos(angle) * speed + spread(random, 0.018D);
        double motionY = 0.035D + random.nextDouble() * speed;
        double motionZ = Math.sin(angle) * speed + spread(random, 0.018D);

        if (village == Village.MIST) {
            radius = phase == SubstitutionEffectMessage.HIT ? 0.75D : 0.45D;
            px = x + Math.cos(angle) * radius;
            py = y + spread(random, 0.5D);
            pz = z + Math.sin(angle) * radius;
            motionX = Math.cos(angle) * 0.012D;
            motionY = 0.006D + random.nextDouble() * 0.01D;
            motionZ = Math.sin(angle) * 0.012D;
        } else if (village == Village.RAIN) {
            px = x + spread(random, phase == SubstitutionEffectMessage.HIT ? 0.85D : 0.55D);
            py = y + 0.35D + random.nextDouble() * 1.25D;
            pz = z + spread(random, phase == SubstitutionEffectMessage.HIT ? 0.85D : 0.55D);
            motionX = spread(random, 0.008D);
            motionY = -0.2D - random.nextDouble() * 0.12D;
            motionZ = spread(random, 0.008D);
        } else if (village == Village.CLOUD) {
            px = x + spread(random, 0.48D);
            py = y + spread(random, 0.8D);
            pz = z + spread(random, 0.48D);
            motionX *= 0.25D;
            motionY *= 0.2D;
            motionZ *= 0.25D;
        } else if (village == Village.SAND) {
            motionY = 0.025D + random.nextDouble() * 0.035D;
        } else if (village == Village.STONE) {
            motionY = 0.08D + random.nextDouble() * 0.13D;
        }

        return new MaterialParticle(world, style, px, py, pz, motionX, motionY, motionZ, random);
    }

    private static int countFor(int phase) {
        if (phase == SubstitutionEffectMessage.HIT) {
            return 15;
        }
        if (phase == SubstitutionEffectMessage.ACTIVATION) {
            return 10;
        }
        if (phase == SubstitutionEffectMessage.ARRIVAL) {
            return 6;
        }
        if (phase == SubstitutionEffectMessage.PULSE) {
            return 3;
        }
        return 5;
    }

    private static ParticleStyle styleFor(Village village) {
        if (village == Village.SAND) {
            return SAND;
        }
        if (village == Village.CLOUD) {
            return LIGHTNING;
        }
        if (village == Village.MIST) {
            return MIST;
        }
        if (village == Village.RAIN) {
            return RAIN;
        }
        if (village == Village.STONE) {
            return STONE;
        }
        return LEAF;
    }

    private static ParticleStyle style(String name, int maxAge, float scale, float alpha, float gravity,
                                       boolean fullBright) {
        return new ParticleStyle(new ResourceLocation(RebornAddonMod.MODID, "particle/" + name),
                maxAge, scale, alpha, gravity, fullBright);
    }

    private static double spread(Random random, double amount) {
        return (random.nextDouble() - 0.5D) * amount * 2.0D;
    }

    private static final class MaterialParticle extends Particle {
        private final ParticleStyle style;
        private final float baseScale;

        private MaterialParticle(World world, ParticleStyle style, double x, double y, double z,
                                 double motionX, double motionY, double motionZ, Random random) {
            super(world, x, y, z, motionX, motionY, motionZ);
            this.style = style;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.particleMaxAge = style.maxAge + random.nextInt(Math.max(2, style.maxAge / 3));
            this.particleScale = style.scale * (0.82F + random.nextFloat() * 0.38F);
            this.baseScale = this.particleScale;
            this.particleAlpha = style.alpha;
            this.particleGravity = style.gravity;
            this.particleAngle = style == RAIN ? 0.0F : random.nextFloat() * ((float) Math.PI * 2.0F);
            this.prevParticleAngle = this.particleAngle;
            this.canCollide = false;
            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(style.texture.toString());
            setParticleTexture(sprite);
        }

        @Override
        public void onUpdate() {
            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;
            prevParticleAngle = particleAngle;
            if (particleAge++ >= particleMaxAge) {
                setExpired();
                return;
            }

            float life = particleAge / (float) particleMaxAge;
            motionY -= 0.04D * particleGravity;
            move(motionX, motionY, motionZ);
            motionX *= 0.94D;
            motionY *= style == MIST ? 0.98D : 0.94D;
            motionZ *= 0.94D;
            particleAngle += style == RAIN ? 0.0F : 0.055F;
            particleScale = baseScale * (style == MIST ? 1.0F + life * 0.8F : 1.0F - life * 0.18F);
            particleAlpha = style.alpha * Math.min(1.0F, (1.0F - life) * 2.5F);
        }

        @Override
        public int getBrightnessForRender(float partialTick) {
            return style.fullBright ? 15728880 : super.getBrightnessForRender(partialTick);
        }

        @Override
        public int getFXLayer() {
            return 1;
        }
    }

    private static final class ParticleStyle {
        private final ResourceLocation texture;
        private final int maxAge;
        private final float scale;
        private final float alpha;
        private final float gravity;
        private final boolean fullBright;

        private ParticleStyle(ResourceLocation texture, int maxAge, float scale, float alpha, float gravity,
                              boolean fullBright) {
            this.texture = texture;
            this.maxAge = maxAge;
            this.scale = scale;
            this.alpha = alpha;
            this.gravity = gravity;
            this.fullBright = fullBright;
        }
    }
}
