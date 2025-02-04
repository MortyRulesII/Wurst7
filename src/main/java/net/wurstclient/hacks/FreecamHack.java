import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.ChunkGenerator;

public final class FreecamHack extends Hack implements UpdateListener, PacketOutputListener, RenderListener {

    private FakePlayerEntity fakePlayer;
    private ZombieEntity zombie;
    private boolean isZombieActive = false;
    private MinecraftClient client = MinecraftClient.getInstance();

    public FreecamHack() {
        super("Freecam");
        setCategory(Category.RENDER);
    }

    @Override
    protected void onEnable() {
        // Register event listeners
        EVENTS.add(UpdateListener.class, this);
        EVENTS.add(PacketOutputListener.class, this);
        EVENTS.add(RenderListener.class, this);

        fakePlayer = new FakePlayerEntity();

        // Spawn zombie named "George" in place of the player
        if (MC.world != null && !isZombieActive) {
            spawnZombie();
        }

        // Reset keybindings and prevent normal player movement
        resetKeyBindings();
    }

    private void spawnZombie() {
        // Create a zombie entity named "George"
        zombie = new ZombieEntity(EntityType.ZOMBIE, MC.world);
        zombie.setCustomName(Text.literal("George").formatted(Formatting.GREEN));
        zombie.setCustomNameVisible(true);

        // Set the zombie's position to the fake player's position
        zombie.setPos(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ());

        // Spawn the zombie in the world
        MC.world.spawnEntity(zombie);
        isZombieActive = true;
    }

    @Override
    protected void onDisable() {
        // Remove event listeners and reset player position
        EVENTS.remove(UpdateListener.class, this);
        EVENTS.remove(PacketOutputListener.class, this);
        EVENTS.remove(RenderListener.class, this);

        if (isZombieActive && zombie != null) {
            MC.world.removeEntity(zombie.getId());
        }

        fakePlayer.resetPlayerPosition();
        fakePlayer.despawn();
        MC.player.setVelocity(Vec3d.ZERO);
        MC.worldRenderer.reload();
    }

    @Override
    public void onUpdate() {
        ClientPlayerEntity player = MC.player;

        // Keep the player in the same position as the fake player
        player.setPosition(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ());
        player.setVelocity(Vec3d.ZERO); // No movement while in Freecam mode

        // Ensure the player can freely look around but remains in the same position
        player.getAbilities().flying = true;

        // Sync fake player position with the zombie to simulate a normal player's location
        fakePlayer.setPosition(player.getX(), player.getY(), player.getZ());
        zombie.setPos(fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ());

        // Force chunk loading around the fake player's position
        forceChunkLoadingAroundFakePlayer();
    }

    // Force chunks to load based on the fake player's position
    private void forceChunkLoadingAroundFakePlayer() {
        int radius = 5; // Adjust the radius as needed to load more chunks around the fake player
        int chunkX = (int) fakePlayer.getX() >> 4;
        int chunkZ = (int) fakePlayer.getZ() >> 4;

        // Load chunks around the fake player's position
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Chunk chunk = client.world.getChunk(chunkX + x, chunkZ + z, ChunkStatus.FULL, true);
                if (chunk != null) {
                    // Trigger the chunk load for rendering and generation around Freecam
                    client.world.getChunkManager().getLightingProvider().updateLighting(chunk);
                }
            }
        }
    }

    @Override
    public void onSentPacket(PacketOutputEvent event) {
        // Cancel movement packets to prevent detection
        if (event.getPacket() instanceof PlayerMoveC2SPacket) {
            event.cancel();
        }
    }

    @Override
    public void onRender(MatrixStack matrixStack, float partialTicks) {
        if (fakePlayer == null) return;

        // Render the zombie (George) instead of the player
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        matrixStack.push();

        RegionPos region = RenderUtils.getCameraRegion();
        RenderUtils.applyRegionalRenderOffset(matrixStack, region);

        // Draw an outline around the zombie (George)
        color.setAsShaderColor(0.5F);

        matrixStack.push();
        matrixStack.translate(zombie.getX() - region.x(), zombie.getY(), zombie.getZ() - region.z());
        matrixStack.scale(zombie.getWidth() + 0.1F, zombie.getHeight() + 0.1F, zombie.getWidth() + 0.1F);
        Box bb = new Box(-0.5, 0, -0.5, 0.5, 1, 0.5);
        RenderUtils.drawOutlinedBox(bb, matrixStack);
        matrixStack.pop();

        Vec3d regionVec = region.toVec3d();
        Vec3d start = RotationUtils.getClientLookVec(partialTicks)
            .add(RenderUtils.getCameraPos()).subtract(regionVec);
        Vec3d end = zombie.getBoundingBox().getCenter().subtract(regionVec);

        Matrix4f matrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = RenderSystem.renderThreadTesselator();
        RenderSystem.setShader(ShaderProgramKeys.POSITION);

        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
        bufferBuilder.vertex(matrix, (float)start.x, (float)start.y, (float)start.z);
        bufferBuilder.vertex(matrix, (float)end.x, (float)end.y, (float)end.z);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());

        matrixStack.pop();

        RenderSystem.setShaderColor(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
    }

    // Ensure that death messages from the zombie (George) are natural
    @Override
    public void onDeath(DeathEvent event) {
        if (event.getEntity() instanceof PlayerEntity) {
            // Change the death message to appear as if killed by "George" (Zombie)
            event.setDeathMessage(Text.literal("Player was slain by Zombie George"));
        }
    }
}
