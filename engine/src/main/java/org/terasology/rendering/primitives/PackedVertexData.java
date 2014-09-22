/*
 * Copyright 2014 MovingBlocks
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

import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * Represents a tesellated chunk in system memory. The contents of this object are not yet uploaded
 * to the graphics card buffers. It is required since chunk updates are computed on separate threads
 * and then uploaded to the graphics card from the main thread.
 */
public final class PackedVertexData {

    public PackedVertexData() {
        for (ChunkMesh.RenderType type : ChunkMesh.RenderType.values()) {
            vertexElements.put(type, new VertexElements());
        }
    }

    /* MEASUREMENTS */
    private int timeToGenerateBlockVertices;
    private int timeToGenerateOptimizedBuffers;

    /* TEMPORARY DATA */
    private Map<ChunkMesh.RenderType, VertexElements> vertexElements = new EnumMap<>(ChunkMesh.RenderType.class);

    public void setTimeToGenerateBlockVertices(int timeToGenerateBlockVertices) {
        this.timeToGenerateBlockVertices = timeToGenerateBlockVertices;
    }

    public int getTimeToGenerateBlockVertices() {
        return timeToGenerateBlockVertices;
    }

    public void setTimeToGenerateOptimizedBuffers(int timeToGenerateOptimizedBuffers) {
        this.timeToGenerateOptimizedBuffers = timeToGenerateOptimizedBuffers;
    }

    public int getTimeToGenerateOptimizedBuffers() {
        return timeToGenerateOptimizedBuffers;
    }

    public VertexElements getVertexElements(ChunkMesh.RenderType renderType) {
        return vertexElements.get(renderType);
    }

    public void reset() {
        for (VertexElements elements : vertexElements.values()) {
            if (elements.finalIndices != null) {
                elements.finalIndices.clear();
            }
            if (elements.finalVertices != null) {
                elements.finalVertices.clear();
            }
        }
    }

    /**
     * Data structure for storing vertex data. Abused like a "struct" in C/C++. Just sad.
     */
    public static class VertexElements {

        public IntBuffer finalVertices;
        public IntBuffer finalIndices;

    }

}
