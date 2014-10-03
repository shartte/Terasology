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
package org.terasology.rendering.nui.layers.nameplates;

import com.bulletphysics.linearmath.Transform;
import org.lwjgl.opengl.Display;
import org.terasology.asset.Assets;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.common.DisplayNameComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.math.AABB;
import org.terasology.math.Rect2i;
import org.terasology.math.TeraMath;
import org.terasology.math.Vector2i;
import org.terasology.network.ClientComponent;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.logic.MeshComponent;
import org.terasology.rendering.nui.Canvas;
import org.terasology.rendering.nui.CoreScreenLayer;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.UIWidget;
import org.terasology.rendering.nui.widgets.UILabel;
import org.terasology.rendering.world.WorldRenderer;

import javax.vecmath.Point3f;
import javax.vecmath.Vector2f;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a screen layer between the game scene and HUD that displays the nameplates of players or other named
 * entities.
 *
 * @author shartte
 */
public class NameplateScreenLayer extends CoreScreenLayer {

    private NUIManager manager;

    @Override
    public boolean isLowerLayerVisible() {
        return false;
    }

    @Override
    public boolean isReleasingMouse() {
        return false;
    }

    @Override
    public boolean isEscapeToCloseAllowed() {
        return false;
    }

    @Override
    public NUIManager getManager() {
        return manager;
    }

    @Override
    public void setManager(NUIManager manager) {
        this.manager = manager;
    }

    @Override
    public void onDraw(Canvas canvas) {

        WorldRenderer worldRenderer = CoreRegistry.get(WorldRenderer.class);
        if (worldRenderer == null) {
            return;
        }

        Camera activeCamera = worldRenderer.getActiveCamera();

        // Determine the character entity used by ourselves
        LocalPlayer localPlayer = CoreRegistry.get(LocalPlayer.class);
        EntityRef playerRef = null;
        if (localPlayer != null) {
            playerRef = localPlayer.getCharacterEntity();
        }

        for (EntityRef entityRef : getRelevantEntities()) {

            // Do not show nameplates for ourselves
            if (entityRef.equals(playerRef))
                continue;

            CharacterComponent characterComponent = entityRef.getComponent(CharacterComponent.class);
            LocationComponent locationComponent = entityRef.getComponent(LocationComponent.class);

            // Either use a name attached to the entity itself, or the name attached to the player that owns it
            DisplayNameComponent nameComponent = entityRef.getComponent(DisplayNameComponent.class);
            if (nameComponent == null) {
                // Get the name of a networked client
                ClientComponent clientComponent = characterComponent.controller.getComponent(ClientComponent.class);
                if (clientComponent != null) {
                    EntityRef clientInfo = clientComponent.clientInfo;
                    nameComponent = clientInfo.getComponent(DisplayNameComponent.class);
                }
            }

            if (nameComponent == null)
                continue; // can't display a nameplate without a name

            MeshComponent meshComponent = entityRef.getComponent(MeshComponent.class);

            // Skip entities that are outside of visible range (i.e. behind us)
            Transform transform = new Transform(locationComponent.getTransformMatrix());
            AABB aabb = meshComponent.mesh.getAABB().transform(transform);
            if (!worldRenderer.isAABBVisible(aabb))
                continue;

            // Calculate the center point on the top plane of the AABB
            Point3f centerOnTop = new Point3f(
                (aabb.minX() + aabb.maxX()) / 2,
                aabb.maxY(),
                (aabb.minZ() + aabb.maxZ()) / 2
            );

            // Calculate the position of the "top center" in screen space
            Vector2f screenSpace = activeCamera.fromWorldToScreenSpace(centerOnTop);

            float x = screenSpace.x;
            float y = screenSpace.y;

            UILabel label = new UILabel(nameComponent.name);
            label.setSkin(Assets.getSkin("engine:nameplate"));

            Vector2i preferredSize = label.getPreferredContentSize(canvas, new Vector2i(Integer.MAX_VALUE, Integer.MAX_VALUE));

            int minX = TeraMath.floorToInt(x * Display.getWidth() - preferredSize.x / 2.0f);
            int minY = TeraMath.floorToInt(y * Display.getHeight() - preferredSize.y);
            int sizeX = preferredSize.x;
            int sizeY = preferredSize.y;
            Rect2i region = Rect2i.createFromMinAndSize(minX, minY, sizeX, sizeY);

            canvas.drawWidget(label, region);

        }

    }

    /*
        At the moment, only characters that have a mesh and a location can have nameplates.
     */
    @SuppressWarnings("unchecked")
    private Iterable<EntityRef> getRelevantEntities() {
        EntityManager entityManager = CoreRegistry.get(EntityManager.class);
        return entityManager.getEntitiesWith(CharacterComponent.class, MeshComponent.class, LocationComponent.class);
    }

    @Override
    public void onOpened() {
    }

    @Override
    protected void initialise() {
    }

    @Override
    public boolean isModal() {
        return false;
    }

}
