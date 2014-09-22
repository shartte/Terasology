/*
 * Copyright 2013 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.primitives;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import gnu.trove.iterator.TIntIterator;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.lwjgl.BufferUtils;
import org.terasology.math.Direction;
import org.terasology.math.Side;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector3i;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.rendering.RenderMath;
import org.terasology.world.ChunkView;
import org.terasology.world.biomes.Biome;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockAppearance;
import org.terasology.world.block.BlockPart;
import org.terasology.world.chunks.ChunkConstants;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Generates tessellated chunk meshes from chunks.
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 */
public final class ChunkTessellator {

    private static int statVertexArrayUpdateCount;

    private final GenericObjectPool<UnpackedVertexData> unpackedVertexDataPool;

    private final GenericObjectPool<PackedVertexData> tesellatedChunkPool;

    public ChunkTessellator(GenericObjectPool<PackedVertexData> tesellatedChunkPool) {
        this.tesellatedChunkPool = tesellatedChunkPool;

        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxIdle(10);
        unpackedVertexDataPool = new GenericObjectPool<>(new UnpackedVertexDataFactory(), poolConfig);
    }

    public PackedVertexData generateMesh(ChunkView chunkView, int meshHeight, int verticalOffset) {

        PerformanceMonitor.startActivity("GenerateMesh");
        PackedVertexData mesh = null;
        try {
            mesh = tesellatedChunkPool.borrowObject();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        final Stopwatch watch = Stopwatch.createStarted();

        UnpackedVertexData state;
        try {
            state = unpackedVertexDataPool.borrowObject();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        try {
            for (int x = 0; x < ChunkConstants.SIZE_X; x++) {
                for (int z = 0; z < ChunkConstants.SIZE_Z; z++) {
                    for (int y = verticalOffset; y < verticalOffset + meshHeight; y++) {
                        Biome biome = chunkView.getBiome(x, y, z);

                        float biomeTemp = biome.getTemperature();
                        float biomeHumidity = biome.getHumidity();

                        Block block = chunkView.getBlock(x, y, z);
                        if (block != null && !block.isInvisible()) {
                            generateBlockVertices(chunkView, state, x, y, z, biomeTemp, biomeHumidity);
                        }
                    }
                }
            }
            watch.stop();

            mesh.setTimeToGenerateBlockVertices((int) watch.elapsed(TimeUnit.MILLISECONDS));

            watch.reset().start();
            generateOptimizedBuffers(chunkView, mesh, state);
            watch.stop();
            mesh.setTimeToGenerateOptimizedBuffers((int) watch.elapsed(TimeUnit.MILLISECONDS));
            statVertexArrayUpdateCount++;
        } finally {
            unpackedVertexDataPool.returnObject(state);
        }

        PerformanceMonitor.endActivity();
        return mesh;
    }

    private void generateOptimizedBuffers(ChunkView chunkView, PackedVertexData mesh, UnpackedVertexData state) {
        PerformanceMonitor.startActivity("OptimizeBuffers");

        for (ChunkMesh.RenderType type : ChunkMesh.RenderType.values()) {
            ChunkVertexElements inElements = state.elements.get(type);
            PackedVertexData.VertexElements outElements = mesh.getVertexElements(type);

            int desiredVerticesSize = inElements.vertices.size() + /* POSITION */
                inElements.tex.size() + /* TEX0 (UV0 and flags) */
                inElements.tex.size() + /* TEX1 (lighting data) */
                inElements.flags.size() + /* FLAGS */
                inElements.color.size() + /* COLOR */
                inElements.normals.size();  /* NORMALS */

            // Vertices double to account for light info
            if (outElements.finalVertices == null || outElements.finalVertices.capacity() != desiredVerticesSize) {
                outElements.finalVertices = BufferUtils.createIntBuffer(desiredVerticesSize);
            }

            int cTex = 0;
            int cColor = 0;
            int cFlags = 0;
            for (int i = 0; i < inElements.vertices.size(); i += 3, cTex += 2, cColor += 4, cFlags++) {
                Vector3f vertexPos = new Vector3f(
                    inElements.vertices.get(i),
                    inElements.vertices.get(i + 1),
                    inElements.vertices.get(i + 2));

                /* POSITION */
                outElements.finalVertices.put(Float.floatToIntBits(vertexPos.x));
                outElements.finalVertices.put(Float.floatToIntBits(vertexPos.y));
                outElements.finalVertices.put(Float.floatToIntBits(vertexPos.z));

                /* UV0 - TEX DATA 0 */
                outElements.finalVertices.put(Float.floatToIntBits(inElements.tex.get(cTex)));
                outElements.finalVertices.put(Float.floatToIntBits(inElements.tex.get(cTex + 1)));

                /* FLAGS */
                outElements.finalVertices.put(Float.floatToIntBits(inElements.flags.get(cFlags)));

                float[] result = new float[3];
                Vector3f normal = new Vector3f(inElements.normals.get(i), inElements.normals.get(i + 1), inElements.normals.get(i + 2));
                calcLightingValuesForVertexPos(chunkView, vertexPos, result, normal);

                /* LIGHTING DATA / TEX DATA 1 */
                outElements.finalVertices.put(Float.floatToIntBits(result[0]));
                outElements.finalVertices.put(Float.floatToIntBits(result[1]));
                outElements.finalVertices.put(Float.floatToIntBits(result[2]));

                /* PACKED COLOR */
                final int packedColor = RenderMath.packColor(
                    inElements.color.get(cColor),
                    inElements.color.get(cColor + 1),
                    inElements.color.get(cColor + 2),
                    inElements.color.get(cColor + 3));
                outElements.finalVertices.put(packedColor);

                /* NORMALS */
                outElements.finalVertices.put(Float.floatToIntBits(normal.x));
                outElements.finalVertices.put(Float.floatToIntBits(normal.y));
                outElements.finalVertices.put(Float.floatToIntBits(normal.z));
            }

            if (outElements.finalIndices == null || outElements.finalIndices.capacity() != inElements.indices.size()) {
                outElements.finalIndices = BufferUtils.createIntBuffer(inElements.indices.size());
            }
            TIntIterator indexIterator = inElements.indices.iterator();
            while (indexIterator.hasNext()) {
                outElements.finalIndices.put(indexIterator.next());
            }

            outElements.finalVertices.flip();
            outElements.finalIndices.flip();
        }
        PerformanceMonitor.endActivity();
    }

    private void calcLightingValuesForVertexPos(ChunkView chunkView, Vector3f vertexPos, float[] output, Vector3f normal) {
        PerformanceMonitor.startActivity("calcLighting");
        float[] lights = new float[8];
        float[] blockLights = new float[8];
        Block[] blocks = new Block[4];

        PerformanceMonitor.startActivity("gatherLightInfo");
        Direction dir = Direction.inDirection(normal);
        switch (dir) {
            case LEFT:
            case RIGHT:
                blocks[0] = chunkView.getBlock((vertexPos.x + 0.8f * normal.x), (vertexPos.y + 0.1f), (vertexPos.z + 0.1f));
                blocks[1] = chunkView.getBlock((vertexPos.x + 0.8f * normal.x), (vertexPos.y + 0.1f), (vertexPos.z - 0.1f));
                blocks[2] = chunkView.getBlock((vertexPos.x + 0.8f * normal.x), (vertexPos.y - 0.1f), (vertexPos.z - 0.1f));
                blocks[3] = chunkView.getBlock((vertexPos.x + 0.8f * normal.x), (vertexPos.y - 0.1f), (vertexPos.z + 0.1f));
                break;
            case FORWARD:
            case BACKWARD:
                blocks[0] = chunkView.getBlock((vertexPos.x + 0.1f), (vertexPos.y + 0.1f), (vertexPos.z + 0.8f * normal.z));
                blocks[1] = chunkView.getBlock((vertexPos.x + 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.8f * normal.z));
                blocks[2] = chunkView.getBlock((vertexPos.x - 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.8f * normal.z));
                blocks[3] = chunkView.getBlock((vertexPos.x - 0.1f), (vertexPos.y + 0.1f), (vertexPos.z + 0.8f * normal.z));
                break;
            default:
                blocks[0] = chunkView.getBlock((vertexPos.x + 0.1f), (vertexPos.y + 0.8f * normal.y), (vertexPos.z + 0.1f));
                blocks[1] = chunkView.getBlock((vertexPos.x + 0.1f), (vertexPos.y + 0.8f * normal.y), (vertexPos.z - 0.1f));
                blocks[2] = chunkView.getBlock((vertexPos.x - 0.1f), (vertexPos.y + 0.8f * normal.y), (vertexPos.z - 0.1f));
                blocks[3] = chunkView.getBlock((vertexPos.x - 0.1f), (vertexPos.y + 0.8f * normal.y), (vertexPos.z + 0.1f));
        }

        lights[0] = chunkView.getSunlight((vertexPos.x + 0.1f), (vertexPos.y + 0.8f), (vertexPos.z + 0.1f));
        lights[1] = chunkView.getSunlight((vertexPos.x + 0.1f), (vertexPos.y + 0.8f), (vertexPos.z - 0.1f));
        lights[2] = chunkView.getSunlight((vertexPos.x - 0.1f), (vertexPos.y + 0.8f), (vertexPos.z - 0.1f));
        lights[3] = chunkView.getSunlight((vertexPos.x - 0.1f), (vertexPos.y + 0.8f), (vertexPos.z + 0.1f));

        lights[4] = chunkView.getSunlight((vertexPos.x + 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.1f));
        lights[5] = chunkView.getSunlight((vertexPos.x + 0.1f), (vertexPos.y - 0.1f), (vertexPos.z - 0.1f));
        lights[6] = chunkView.getSunlight((vertexPos.x - 0.1f), (vertexPos.y - 0.1f), (vertexPos.z - 0.1f));
        lights[7] = chunkView.getSunlight((vertexPos.x - 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.1f));

        blockLights[0] = chunkView.getLight((vertexPos.x + 0.1f), (vertexPos.y + 0.8f), (vertexPos.z + 0.1f));
        blockLights[1] = chunkView.getLight((vertexPos.x + 0.1f), (vertexPos.y + 0.8f), (vertexPos.z - 0.1f));
        blockLights[2] = chunkView.getLight((vertexPos.x - 0.1f), (vertexPos.y + 0.8f), (vertexPos.z - 0.1f));
        blockLights[3] = chunkView.getLight((vertexPos.x - 0.1f), (vertexPos.y + 0.8f), (vertexPos.z + 0.1f));

        blockLights[4] = chunkView.getLight((vertexPos.x + 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.1f));
        blockLights[5] = chunkView.getLight((vertexPos.x + 0.1f), (vertexPos.y - 0.1f), (vertexPos.z - 0.1f));
        blockLights[6] = chunkView.getLight((vertexPos.x - 0.1f), (vertexPos.y - 0.1f), (vertexPos.z - 0.1f));
        blockLights[7] = chunkView.getLight((vertexPos.x - 0.1f), (vertexPos.y - 0.1f), (vertexPos.z + 0.1f));
        PerformanceMonitor.endActivity();

        float resultLight = 0;
        float resultBlockLight = 0;
        int counterLight = 0;
        int counterBlockLight = 0;

        int occCounter = 0;
        int occCounterBillboard = 0;
        for (int i = 0; i < 8; i++) {
            if (lights[i] > 0) {
                resultLight += lights[i];
                counterLight++;
            }
            if (blockLights[i] > 0) {
                resultBlockLight += blockLights[i];
                counterBlockLight++;
            }

            if (i < 4) {
                Block b = blocks[i];

                if (b.isShadowCasting() && !b.isTranslucent()) {
                    occCounter++;
                } else if (b.isShadowCasting()) {
                    occCounterBillboard++;
                }
            }
        }

        double resultAmbientOcclusion = (TeraMath.pow(0.40, occCounter) + TeraMath.pow(0.80, occCounterBillboard)) / 2.0;

        if (counterLight == 0) {
            output[0] = 0;
        } else {
            output[0] = resultLight / counterLight / 15f;
        }

        if (counterBlockLight == 0) {
            output[1] = 0;
        } else {
            output[1] = resultBlockLight / counterBlockLight / 15f;
        }

        output[2] = (float) resultAmbientOcclusion;
        PerformanceMonitor.endActivity();
    }

    private void generateBlockVertices(ChunkView view, UnpackedVertexData state, int x, int y, int z, float temp, float hum) {
        Block block = view.getBlock(x, y, z);

        // TODO: Needs review - too much hardcoded special cases and corner cases resulting from this.
        ChunkVertexFlag vertexFlag = ChunkVertexFlag.NORMAL;
        if (block.isWater()) {
            vertexFlag = ChunkVertexFlag.WATER;
        } else if (block.isLava()) {
            vertexFlag = ChunkVertexFlag.LAVA;
        } else if (block.isWaving() && block.isDoubleSided()) {
            vertexFlag = ChunkVertexFlag.WAVING;
        } else if (block.isWaving()) {
            vertexFlag = ChunkVertexFlag.WAVING_BLOCK;
        }

        // Gather adjacent blocks
        Map<Side, Block> adjacentBlocks = Maps.newEnumMap(Side.class);
        for (Side side : Side.values()) {
            Vector3i offset = side.getVector3i();
            Block blockToCheck = view.getBlock(x + offset.x, y + offset.y, z + offset.z);
            adjacentBlocks.put(side, blockToCheck);
        }

        BlockAppearance blockAppearance = block.getAppearance(adjacentBlocks);

        /*
         * Determine the render process.
         */
        ChunkMesh.RenderType renderType = ChunkMesh.RenderType.TRANSLUCENT;

        if (!block.isTranslucent()) {
            renderType = ChunkMesh.RenderType.OPAQUE;
        }
        // TODO: Review special case, or alternatively compare uris.
        if (block.isWater() || block.isIce()) {
            renderType = ChunkMesh.RenderType.WATER_AND_ICE;
        }
        if (block.isDoubleSided()) {
            renderType = ChunkMesh.RenderType.BILLBOARD;
        }

        ChunkVertexElements vertexElements = state.elements.get(renderType);

        if (blockAppearance.getPart(BlockPart.CENTER) != null) {
            Vector4f colorOffset = block.calcColorOffsetFor(BlockPart.CENTER, temp, hum);
            blockAppearance.getPart(BlockPart.CENTER).appendTo(vertexElements, x, y, z, colorOffset, vertexFlag);
        }

        boolean[] drawDir = new boolean[6];

        for (Side side : Side.values()) {
            drawDir[side.ordinal()] = blockAppearance.getPart(BlockPart.fromSide(side)) != null && isSideVisibleForBlockTypes(adjacentBlocks.get(side), block, side);
        }

        // If the block is lowered, some more faces may have to be drawn
        if (block.isLiquid()) {
            Block bottomBlock = adjacentBlocks.get(Side.BOTTOM);
            // Draw horizontal sides if visible from below
            for (Side side : Side.horizontalSides()) {
                Vector3i offset = side.getVector3i();
                Block adjacentBelow = view.getBlock(x + offset.x, y - 1, z + offset.z);
                Block adjacent = adjacentBlocks.get(side);

                boolean visible = (blockAppearance.getPart(BlockPart.fromSide(side)) != null
                    && isSideVisibleForBlockTypes(adjacentBelow, block, side) && !isSideVisibleForBlockTypes(bottomBlock, adjacent, side.reverse()));
                drawDir[side.ordinal()] |= visible;
            }

            // Draw the top if below a non-lowered block
            // TODO: Don't need to render the top if each side and the block above each side are either liquid or opaque solids.
            Block blockToCheck = adjacentBlocks.get(Side.TOP);
            drawDir[Side.TOP.ordinal()] |= !blockToCheck.isLiquid();

            if (bottomBlock.isLiquid() || bottomBlock.isInvisible()) {
                for (Side dir : Side.values()) {
                    if (drawDir[dir.ordinal()]) {
                        Vector4f colorOffset = block.calcColorOffsetFor(BlockPart.fromSide(dir), temp, hum);
                        block.getLoweredLiquidMesh(dir).appendTo(vertexElements, x, y, z, colorOffset, vertexFlag);
                    }
                }
                return;
            }
        }

        for (Side dir : Side.values()) {
            if (drawDir[dir.ordinal()]) {
                Vector4f colorOffset = block.calcColorOffsetFor(BlockPart.fromSide(dir), temp, hum);
                // TODO: Needs review since the new per-vertex flags introduce a lot of special scenarios - probably a per-side setting?
                if (block.isGrass() && dir != Side.TOP && dir != Side.BOTTOM) {
                    blockAppearance.getPart(BlockPart.fromSide(dir)).appendTo(vertexElements, x, y, z, colorOffset, ChunkVertexFlag.COLOR_MASK);
                } else {
                    blockAppearance.getPart(BlockPart.fromSide(dir)).appendTo(vertexElements, x, y, z, colorOffset, vertexFlag);
                }
            }
        }
    }

    /**
     * Returns true if the side should be rendered adjacent to the second side provided.
     *
     * @param blockToCheck The block to check
     * @param currentBlock The current block
     * @return True if the side is visible for the given block types
     */
    private boolean isSideVisibleForBlockTypes(Block blockToCheck, Block currentBlock, Side side) {
        // Liquids can be transparent but there should be no visible adjacent faces
        if (currentBlock.isLiquid() && blockToCheck.isLiquid()) {
            return false;
        }

        return currentBlock.isWaving() != blockToCheck.isWaving() || blockToCheck.isInvisible()
            || !blockToCheck.isFullSide(side.reverse()) || (!currentBlock.isTranslucent() && blockToCheck.isTranslucent());

    }

    public static int getVertexArrayUpdateCount() {
        return statVertexArrayUpdateCount;
    }

}
