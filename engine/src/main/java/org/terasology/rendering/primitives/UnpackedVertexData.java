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

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-Thread state required to make the chunk tessellator thread-safe and not just reentrant, while sharing
 * and maintaining state.
 */
class UnpackedVertexData {

    public Map<ChunkMesh.RenderType, ChunkVertexElements> elements = new EnumMap<>(ChunkMesh.RenderType.class);

    UnpackedVertexData() {
        for (ChunkMesh.RenderType renderType : ChunkMesh.RenderType.values()) {
            elements.put(renderType, new ChunkVertexElements());
        }
    }

    public void reset() {
        for (ChunkVertexElements vertexElements : elements.values()) {
            vertexElements.reset();
        }
    }

}
