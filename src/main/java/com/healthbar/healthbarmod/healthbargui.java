package com.healthbar.healthbarmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = healthbar.MODID, value = Dist.CLIENT)
public class healthbargui {

    private static final Map<Integer, Float> displayedHealth = new HashMap<>();
    private static LivingEntity cachedTarget = null;
    private static long lastSeenTime = 0L;
    private static final long BUFFER_MS = 500;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        LivingEntity target = null;

        if (hit instanceof EntityHitResult ehr &&
                ehr.getEntity() instanceof LivingEntity living &&
                !(living instanceof ArmorStand)) {
            target = living;
            cachedTarget = living;
            lastSeenTime = System.currentTimeMillis();
        } else if (hit instanceof BlockHitResult bhr) {
            // Check if we hit a transparent block we should ignore
            BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            if (isTransparentBlock(state)) {
                // Do entity check through this transparent block
                Vec3 eyePos = mc.player.getEyePosition(1.0F);
                Vec3 lookVec = mc.player.getViewVector(1.0F);
                Vec3 endPos = eyePos.add(lookVec.scale(15.0));

                for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(eyePos, endPos).inflate(0.75))) {
                    if (entity == mc.player || entity instanceof ArmorStand) continue;

                    AABB box = entity.getBoundingBox().inflate(0.3);
                    Optional<Vec3> clip = box.clip(eyePos, endPos);

                    if (clip.isPresent()) {
                        Vec3 aimPoint = clip.get();
                        boolean visible = !isOccluded(mc.level, eyePos, aimPoint, mc.player);

                        if (visible) {
                            target = entity;
                            cachedTarget = entity;
                            lastSeenTime = System.currentTimeMillis();
                            break;
                        }
                    }
                }
            }
        }

        if (target == null) {
            if (cachedTarget == null) return;
            if (System.currentTimeMillis() - lastSeenTime > BUFFER_MS) {
                cachedTarget = null;
                return;
            }
            target = cachedTarget;
        }

        if (!target.isAlive()) return;

        GuiGraphics gui = event.getGuiGraphics();
        float current = target.getHealth();
        float max = target.getMaxHealth();
        if (max <= 0) return;
        float percent = current / max;

        int width = 180;
        int height = 44;
        int x = 8;
        int y = 8;

        int id = target.getId();
        float last = displayedHealth.getOrDefault(id, current);
        float smooth = lerp(last, current, 0.12f);
        if (Math.abs(smooth - current) < 0.01f) smooth = current;
        displayedHealth.put(id, smooth);
        float smoothPercent = smooth / max;

        int barX = x + 10;
        int barY = y + 18;
        int barWidth = width - 20;
        int barHeight = 10;


        gui.fill(barX - 2, barY - 2, barX + barWidth + 2, barY + barHeight + 2, 0xFF000000);
        gui.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF2A2A2A);

        int filled = Math.round(barWidth * smoothPercent);
        for (int i = 0; i < filled; i++) {
            float t = i / (float) Math.max(1, barWidth - 1);
            gui.fill(barX + i, barY, barX + i + 1, barY + barHeight, gradientColor(t));
        }

        gui.fill(barX, barY, barX + filled, barY + 2, 0x55FFFFFF);

        if (percent <= 0.20f) {
            float pulse = (float) ((Math.sin(System.currentTimeMillis() / 150.0) + 1) * 0.5);
            int alpha = (int) (60 + pulse * 80);
            gui.fill(barX, barY, barX + filled, barY + barHeight, (alpha << 24) | 0x00FF0000);
        }

        // === FIXED ENTITY NAME DISPLAY ===
        String entityName;

        // Try multiple methods to get the name
        if (target.hasCustomName()) {
            entityName = target.getCustomName().getString();
        } else {
            entityName = target.getDisplayName().getString();
        }

        if (entityName == null || entityName.isEmpty()) {
            entityName = target.getName().getString();
        }

        String stripped = net.minecraft.ChatFormatting.stripFormatting(entityName);
        if (stripped != null) {
            entityName = stripped;
        }

        int nameW = mc.font.width(entityName);
        int nameX = x + width / 2 - nameW / 2;

        // Draw shadow first (darker, offset)
        gui.drawString(mc.font, entityName, nameX + 1, y + 5, 0xFF000000, false);
        // Draw main text
        gui.drawString(mc.font, entityName, nameX, y + 4, 0xFFFFFFFF, false);

        String hp = String.format("%.0f / %.0f", current, max);
        String pct = String.format("%.0f%%", percent * 100f);

        gui.drawString(mc.font, hp, barX, barY + barHeight + 4, 0xE6FFFFFF, false);
        gui.drawString(mc.font, pct,
                x + width - mc.font.width(pct) - 8,
                barY + barHeight + 4,
                0xFFE0E0E0,
                false);

        if (target.hurtTime > 0) {
            gui.fill(barX, barY, barX + barWidth, barY + barHeight, 0x33FF0000);
        }
    }

    private static boolean isTransparentBlock(BlockState state) {
        // Check for air
        if (state.isAir()) return true;

        // Check for common non-solid blocks
        if (state.is(Blocks.VINE) ||
                state.is(Blocks.TALL_GRASS) ||
                state.is(Blocks.SHORT_GRASS) ||
                state.is(Blocks.FERN) ||
                state.is(Blocks.LARGE_FERN) ||
                state.is(Blocks.WATER) ||
                state.is(Blocks.LILY_PAD)) return true;

        // Check for any type of glass
        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (blockId.contains("glass")) return true;

        // Optionally, check for blocks that do not block motion
        if (!state.blocksMotion()) return true;

        return false;
    }

    private static boolean isOccluded(net.minecraft.world.level.Level level, Vec3 from, Vec3 to, net.minecraft.world.entity.Entity shooter) {
        Vec3 current = from;
        Vec3 direction = to.subtract(from);
        double totalDist = direction.length();
        if (totalDist < 0.01) return false;
        direction = direction.normalize();

        for (int i = 0; i < 32; i++) {
            ClipContext ctx = new ClipContext(current, to,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter);
            BlockHitResult hitResult = level.clip(ctx);

            if (hitResult.getType() != HitResult.Type.BLOCK) return false;

            double distToHit = hitResult.getLocation().distanceTo(from);
            if (distToHit >= totalDist - 0.05) return false;

            BlockState state = level.getBlockState(hitResult.getBlockPos());
            if (!isTransparentBlock(state)) return true;

            current = hitResult.getLocation().add(direction.scale(0.05));
        }
        return true;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int gradientColor(float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r, g;
        if (t <= 0.5f) {
            float v = t / 0.5f;
            r = (int) (255 * v);
            g = 255;
        } else {
            float v = (t - 0.5f) / 0.5f;
            r = 255;
            g = (int) (255 * (1 - v));
        }
        return 0xFF000000 | (r << 16) | (g << 8);
    }
}