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

/**
 * Data structure for storing vertex data. Abused like a "struct" in C/C++. Just sad.
 */
public class ChunkVertexElements {

    public static final int DEFAULT_CAPACITY = 1000;
    public final TFloatList normals;
    public final TFloatList vertices;
    public final TFloatList tex;
    public final TFloatList color;
    public final TIntList indices;
    public final TIntList flags;
    public int vertexCount;

    public ChunkVertexElements() {
        vertexCount = 0;
        normals = new TFloatArrayList(DEFAULT_CAPACITY);
        vertices = new TFloatArrayList(DEFAULT_CAPACITY);
        tex = new TFloatArrayList(DEFAULT_CAPACITY);
        color = new TFloatArrayList(DEFAULT_CAPACITY);
        indices = new TIntArrayList(DEFAULT_CAPACITY);
        flags = new TIntArrayList(DEFAULT_CAPACITY);
    }

    /**
     * Prepares an object of this class to be reused for another chunk mesh.
     */
    public void reset() {
        normals.clear();
        vertices.clear();
        tex.clear();
        color.clear();
        indices.clear();
        flags.clear();
        vertexCount = 0;
    }

}
