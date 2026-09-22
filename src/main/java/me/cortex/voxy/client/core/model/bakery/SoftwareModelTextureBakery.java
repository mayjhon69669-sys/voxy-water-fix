package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.common.util.UnsafeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureImage;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL12.GL_PACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class SoftwareModelTextureBakery {
    // Note: the first bit of metadata is if alpha discard is enabled
    private static final Matrix4f[] VIEWS = new Matrix4f[6];

    private final ReuseVertexConsumer opaqueVC = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC = new ReuseVertexConsumer(1/*has discard*/);
    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer(ModelFactory.MODEL_TEXTURE_SIZE);


    public SoftwareModelTextureBakery() {
    }

    public void setupTexture() {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png"));

        int textureId = texture.getId();

        if (!RenderSystem.isOnRenderThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            RenderSystem.recordRenderCall(() -> {
                try {
                    _doSetupTexture(textureId);
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            future.join();
        } else {
            _doSetupTexture(textureId);
        }
    }

    private void _doSetupTexture(int glId) {
        glBindTexture(GL_TEXTURE_2D, glId);
        int width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);

        int[] pixels = new int[width * height];
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        this.rasterizer.setSamplerTexture(pixels, width, height);
    }

    private void bakeBlockModel(BlockState state, RenderType layer) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;// Dont bake if invisible
        }
        var model = Minecraft.getInstance()
                .getModelManager()
                .getBlockModelShaper()
                .getBlockModel(state);

        for (Direction direction : new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, null }) {
            var quads = model.getQuads(state, direction, new SingleThreadedRandomSource(42L));
            for (var quad : quads) {
                (layer == RenderType.translucent() ? this.translucentVC : this.opaqueVC)
                        .quad(quad, state.is(BlockTags.LEAVES), layer);
            }
        }
    }

    private void bakeFluidState(BlockState state, int face, RenderType layer) {
        BlockAndTintGetter getter = new BlockAndTintGetter() {
            @Override
            public LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }
            @Override
            public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
                //This is such a stupid and bad hack, we can inject tinting state here since this is called
                // before the quad is added
                //TODO: need to make a quad once tinting thing
                translucentVC.setDefaultMeta(translucentVC.getDefaultMeta()|4);//Tinting
                opaqueVC.setDefaultMeta(opaqueVC.getDefaultMeta()|4);//Tinting
                return -1;
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.defaultBlockState();
                }

                //Fixme:
                // This makes it so that the top face of water is always air, if this is commented out
                //  the up block will be a liquid state which makes the sides full
                // if this is uncommented, that issue is fixed but e.g. stacking water layers ontop of eachother
                //  doesnt fill the side of the block

                //if (pos.getY() == 1) {
                //    return Blocks.AIR.getDefaultState();
                //}
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.defaultBlockState().getFluidState();
                }

                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }

            @Override
            public float getShade(Direction direction, boolean bl) {
                return 0;
            }
        
        };
        
        VertexConsumer vc = this.opaqueVC;;

        if (layer == RenderType.translucent()) vc = this.translucentVC;
        if (layer == RenderType.cutout()) {
            this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta()|1);//set discard
        } else {
            this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta()&~1);//remove discard
        }
        Minecraft.getInstance().getBlockRenderer().renderLiquid(BlockPos.ZERO, getter, vc, state, state.getFluidState());
        this.translucentVC.setDefaultMeta(0);//Reset default meta
        this.opaqueVC.setDefaultMeta(0);//Reset default meta
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var fv = Direction.from3DDataValue(face).getNormal();
        int dot = fv.getX() * pos.getX() + fv.getY() * pos.getY() + fv.getZ() * pos.getZ();
        return dot >= 1;
    }

    public void free() {
        this.opaqueVC.free();
        this.translucentVC.free();
    }

    private static final long SINGLE_FACE_OUTPUT_SIZE = (ModelFactory.MODEL_TEXTURE_SIZE
            * ModelFactory.MODEL_TEXTURE_SIZE) * 8;
    // The outputBuffer layout is different from the non software rasterized
    // ModelTextureBakery
    // in this version the values are simply appended
    // (0,0),(1,0),(2,0),(0,1),(1,1),(2,1)

    public int renderToOutput(BlockState state, long outputBuffer) {
        MemoryUtil.memSet(outputBuffer, 0, 16 * 16 * 8 * 6);

        // Only replace leaf models which actually use Minecraft's tint flag.
        // Leaves with colour baked into their texture (cherry and many modded
        // trees) must stay on Voxy's normal model path or they become green.
        if (state.is(BlockTags.LEAVES)) {
            boolean tintedModel = usesTintedLeafModel(state);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            boolean vanillaLeaf = blockId.getNamespace().equals("minecraft");
            boolean cherryLeaf = vanillaLeaf && blockId.getPath().equals("cherry_leaves");
            if (tintedModel && vanillaLeaf && !cherryLeaf) {
                renderLeafCubeFallback(outputBuffer);
                return 1;
            }
            if (renderTextureColouredLeafCubeFallback(state, outputBuffer, tintedModel && !cherryLeaf)) {
                return 1;
            }
        }

        boolean isBlock = true;
        if (state.getBlock() instanceof LiquidBlock) {
            isBlock = false;
        }

        RenderType blockRenderLayer = null;
        if (state.getBlock() instanceof LiquidBlock) {
            blockRenderLayer = ItemBlockRenderTypes.getRenderLayer(state.getFluidState());
        } else {
            if (state.getBlock() instanceof LeavesBlock) {
                blockRenderLayer = RenderType.solid();
            } else {
                blockRenderLayer = ItemBlockRenderTypes.getChunkRenderType(state);
            }
        }

        // TODO: support block model entities
        // BakedBlockEntityModel bbem = null;
        if (state.hasBlockEntity()) {
            // bbem = BakedBlockEntityModel.bake(state);
        }

        boolean isAnyShaded = false;
        boolean isAnyDarkend = false;
        boolean anyTranslucent = false;
        boolean anyDiscard = false;
        if (isBlock) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            this.bakeBlockModel(state, blockRenderLayer);
            isAnyShaded |= this.opaqueVC.anyShaded | this.translucentVC.anyShaded;
            isAnyDarkend |= this.opaqueVC.anyDarkendTex | this.translucentVC.anyDarkendTex;
            anyTranslucent |= !this.translucentVC.isEmpty();
            anyDiscard |= this.opaqueVC.anyDiscard;
            if (!(this.opaqueVC.isEmpty() && this.translucentVC.isEmpty())) {// only render if there... is shit to
                                                                             // render
                for (int i = 0; i < VIEWS.length; i++) {
                    this.rasterizer.setFaceCull(i == 1 || i == 2 || i == 4);
                    this.rasterizer.clear();
                    this.rasterizer.setBlending(false);
                    this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                    this.rasterizer.setBlending(true);
                    this.rasterizer.raster(VIEWS[i], this.translucentVC);
                    UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(),
                            outputBuffer + (SINGLE_FACE_OUTPUT_SIZE * i));
                }
            }
        } else {// Is fluid, slow path :(

            if (!(state.getBlock() instanceof LiquidBlock))
                throw new IllegalStateException();
            for (int i = 0; i < VIEWS.length; i++) {
                this.opaqueVC.reset();
                this.translucentVC.reset();
                this.bakeFluidState(state, i, blockRenderLayer);
                if (this.opaqueVC.isEmpty() && this.translucentVC.isEmpty())
                    continue;
                isAnyShaded |= this.opaqueVC.anyShaded | this.translucentVC.anyShaded;
                isAnyDarkend |= this.opaqueVC.anyDarkendTex | this.translucentVC.anyDarkendTex;
                anyTranslucent |= !this.translucentVC.isEmpty();
                anyDiscard |= this.opaqueVC.anyDiscard;

                this.rasterizer.setFaceCull(i == 1 || i == 2 || i == 4);

                // The projection matrix
                this.rasterizer.clear();
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                this.rasterizer.setBlending(true);
                this.rasterizer.raster(VIEWS[i], this.translucentVC);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer + (SINGLE_FACE_OUTPUT_SIZE * i));
            }
        }

        return (isAnyShaded ? 1 : 0) | (isAnyDarkend ? 2 : 0) | (anyTranslucent ? 4 : 0) | (anyDiscard ? 8 : 0);
    }

    private static boolean usesTintedLeafModel(BlockState state) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return false;
        }

        var model = Minecraft.getInstance()
                .getModelManager()
                .getBlockModelShaper()
                .getBlockModel(state);

        for (Direction direction : new Direction[] { Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST, null }) {
            var quads = model.getQuads(state, direction, new SingleThreadedRandomSource(42L));
            for (var quad : quads) {
                if (quad.isTinted()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void renderLeafCubeFallback(long outputBuffer) {
        final int depthAndTint = 0x81;
        for (int face = 0; face < 6; face++) {
            long facePtr = outputBuffer + (SINGLE_FACE_OUTPUT_SIZE * face);
            for (int y = 0; y < ModelFactory.MODEL_TEXTURE_SIZE; y++) {
                for (int x = 0; x < ModelFactory.MODEL_TEXTURE_SIZE; x++) {
                    int i = x + y * ModelFactory.MODEL_TEXTURE_SIZE;
                    int shade = leafTextureShade(x, y, face);
                    int colour = 0xFF000000 | shade | (shade << 8) | (shade << 16);
                    long pixel = (Integer.toUnsignedLong(depthAndTint) << 32) | Integer.toUnsignedLong(colour);
                    MemoryUtil.memPutLong(facePtr + i * 8L, pixel);
                }
            }
        }
    }

    private boolean renderTextureColouredLeafCubeFallback(BlockState state, long outputBuffer, boolean applyModelTint) {
        this.opaqueVC.reset();
        this.translucentVC.reset();
        this.bakeBlockModel(state, ItemBlockRenderTypes.getChunkRenderType(state));

        long red = 0;
        long green = 0;
        long blue = 0;
        long samples = 0;
        boolean allSamplesTinted = true;

        for (int face = 0; face < VIEWS.length; face++) {
            this.rasterizer.setFaceCull(face == 1 || face == 2 || face == 4);
            this.rasterizer.clear();
            this.rasterizer.setBlending(false);
            this.rasterizer.raster(VIEWS[face], this.opaqueVC);
            this.rasterizer.setBlending(true);
            this.rasterizer.raster(VIEWS[face], this.translucentVC);

            long[] framebuffer = this.rasterizer.getRawFramebuffer();
            int pixelCount = ModelFactory.MODEL_TEXTURE_SIZE * ModelFactory.MODEL_TEXTURE_SIZE;
            for (int i = 0; i < pixelCount; i++) {
                int colour = (int) framebuffer[i];
                if ((colour >>> 24) < 32) {
                    continue;
                }
                red += colour & 0xFF;
                green += (colour >>> 8) & 0xFF;
                blue += (colour >>> 16) & 0xFF;
                samples++;
                allSamplesTinted &= (framebuffer[i] & (0x80L << 32)) != 0;
            }
        }

        if (samples == 0) {
            return false;
        }

        int baseColour = ((int)(blue / samples) << 16)
                | ((int)(green / samples) << 8)
                | (int)(red / samples);

        // Keep fully tinted leaf textures uncoloured here. ModelFactory resolves
        // their colour with a biome-aware getter, including providers that reject
        // a null world (Nature's Spirit mahogany, for example).
        boolean preserveBiomeTint = applyModelTint && allSamplesTinted;
        if (applyModelTint && !preserveBiomeTint) {
            try {
                int tint = Minecraft.getInstance().getBlockColors().getColor(state, null, BlockPos.ZERO, 0);
                if (tint != -1 && tint != 0) {
                    int baseRed = baseColour & 0xFF;
                    int baseGreen = (baseColour >>> 8) & 0xFF;
                    int baseBlue = (baseColour >>> 16) & 0xFF;
                    int tintRed = (tint >>> 16) & 0xFF;
                    int tintGreen = (tint >>> 8) & 0xFF;
                    int tintBlue = tint & 0xFF;
                    baseColour = (baseRed * tintRed / 255)
                            | ((baseGreen * tintGreen / 255) << 8)
                            | ((baseBlue * tintBlue / 255) << 16);
                }
            } catch (Exception ignored) {
                // A few mod colour providers require a live world. Their baked
                // texture colour is still a safer fallback than forcing oak green.
            }
        }
        renderTextureColouredLeafCube(outputBuffer, baseColour, preserveBiomeTint);
        return true;
    }

    private static void renderTextureColouredLeafCube(long outputBuffer, int baseColour, boolean tinted) {
        final int depthWithoutTint = tinted ? 0x81 : 0x01;
        int baseRed = baseColour & 0xFF;
        int baseGreen = (baseColour >>> 8) & 0xFF;
        int baseBlue = (baseColour >>> 16) & 0xFF;

        for (int face = 0; face < 6; face++) {
            long facePtr = outputBuffer + (SINGLE_FACE_OUTPUT_SIZE * face);
            for (int y = 0; y < ModelFactory.MODEL_TEXTURE_SIZE; y++) {
                for (int x = 0; x < ModelFactory.MODEL_TEXTURE_SIZE; x++) {
                    int i = x + y * ModelFactory.MODEL_TEXTURE_SIZE;
                    int shade = leafTextureShade(x, y, face);
                    int red = Math.min(255, baseRed * shade / 112);
                    int green = Math.min(255, baseGreen * shade / 112);
                    int blue = Math.min(255, baseBlue * shade / 112);
                    int colour = 0xFF000000 | red | (green << 8) | (blue << 16);
                    long pixel = (Integer.toUnsignedLong(depthWithoutTint) << 32)
                            | Integer.toUnsignedLong(colour);
                    MemoryUtil.memPutLong(facePtr + i * 8L, pixel);
                }
            }
        }
    }

    private static final int[] FAST_LEAVES_TEXTURE = {
            102,102,69,187,69,153,153,69,118,118,102,153,69,69,187,118,
            153,69,69,69,102,102,118,153,69,153,118,153,69,187,153,187,
            69,69,118,118,69,153,153,118,187,69,153,69,69,187,187,187,
            69,153,118,102,102,69,187,153,187,102,69,69,69,69,187,69,
            153,118,153,118,69,69,153,187,69,69,69,102,153,153,69,69,
            153,153,118,69,69,187,118,102,102,102,69,69,102,118,153,69,
            118,118,69,102,187,153,187,153,69,118,118,69,153,153,118,187,
            118,102,102,69,187,187,187,69,102,102,118,153,69,187,118,153,
            187,153,69,102,102,187,153,153,69,118,153,118,153,69,153,118,
            187,69,69,69,102,102,102,118,153,69,153,153,153,69,187,153,
            69,153,153,69,102,69,153,153,118,187,69,153,69,69,69,187,
            187,118,102,102,69,69,69,187,153,187,69,118,153,69,69,69,
            118,153,153,69,153,153,69,69,187,69,102,102,118,187,69,187,
            153,187,69,187,118,102,102,69,69,102,69,153,187,118,153,187,
            187,69,187,153,187,153,69,69,118,118,69,69,187,153,187,69,
            153,69,187,187,187,69,69,102,102,102,118,69,69,187,69,153
    };

    private static int leafTextureShade(int x, int y, int face) {
        int px = (x + ((face & 1) * 3)) & 15;
        int py = (y + ((face >> 1) * 5)) & 15;
        int shade = FAST_LEAVES_TEXTURE[px + py * 16];
        return 72 + ((shade - 69) / 34) * 28;
    }

    static {
        // the face/direction is the face (e.g. down is the down face)
        addView(0, -90, 0, 0, 0);// Direction.DOWN
        addView(1, 90, 0, 0, 0b100);// Direction.UP

        addView(2, 0, 180, 0, 0b001);// Direction.NORTH
        addView(3, 0, 0, 0, 0);// Direction.SOUTH

        addView(4, 0, 90, 270, 0b100);// Direction.WEST
        addView(5, 0, 270, 270, 0);// Direction.EAST
    }

    private static void addView(int i, float pitch, float yaw, float rotation, int flip) {
        var stack = new PoseStack();
        stack.translate(0.5f, 0.5f, 0.5f);
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0, 0, 1), rotation));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(1, 0, 0), pitch));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0, 1, 0), yaw));
        stack.mulPose(new Matrix4f().scale(1 - 2 * (flip & 1), 1 - (flip & 2), 1 - ((flip >> 1) & 2)));
        stack.translate(-0.5f, -0.5f, -0.5f);
        var mat = new Matrix4f(stack.last().pose());

        mat = new Matrix4f().set(
                2, 0, 0, 0,
                0, 2, 0, 0,
                0, 0, -2, 0,
                -1, -1, 1, 1)
                .mul(mat);
        VIEWS[i] = mat;
    }

    private static Quaternionf makeQuatFromAxisExact(Vector3f vec, float angle) {
        angle = (float) Math.toRadians(angle);
        float hangle = angle / 2.0f;
        float sinAngle = (float) Math.sin(hangle);
        float invVLength = (float) (1 / Math.sqrt(vec.lengthSquared()));
        return new Quaternionf(vec.x * invVLength * sinAngle,
                vec.y * invVLength * sinAngle,
                vec.z * invVLength * sinAngle,
                Math.cos(hangle));
    }
}
