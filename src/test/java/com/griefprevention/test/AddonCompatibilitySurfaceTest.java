package com.griefprevention.test;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationStyle;
import com.griefprevention.visualization.VisualizationStyleRegistry;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonCompatibilitySurfaceTest {

    @Test
    void claimCommandAddonApiRemainsAvailable() {
        assertDoesNotThrow(() -> {
            Class<?> addon = Class.forName("com.griefprevention.api.ClaimCommandAddon");
            Class<?> registry = Class.forName("com.griefprevention.api.ClaimCommandAddonRegistry");
            Class<?> context = Class.forName("com.griefprevention.api.ClaimCommandContext");

            method(addon, "getTabCompletions", CommandSender.class, String.class, String.class, String[].class);
            method(addon, "getSubcommandCompletions", CommandSender.class, String.class);
            method(addon, "handleSubcommand", context);

            method(registry, "register", addon);
            method(registry, "unregister", addon);
            method(registry, "getAdditionalTabCompletions",
                    CommandSender.class, String.class, String.class, String[].class, String.class);
            method(registry, "getAdditionalSubcommandCompletions", CommandSender.class, String.class, String.class);
            method(registry, "handleAddonSubcommand", context);

            context.getConstructor(CommandSender.class, String.class, String.class, String[].class, Claim.class);
            method(context, "getSender");
            method(context, "getRootCommand");
            method(context, "getSubcommand");
            method(context, "getArgs");
            method(context, "getSelectedOrCurrentClaim");
        });
    }

    @Test
    void gpExpansionShapedClaimSurfaceRemainsAvailable() {
        assertDoesNotThrow(() -> {
            method(OrthogonalPolygon.class, "validatePath", List.class);
            method(OrthogonalPolygon.class, "fromRectangle", int.class, int.class, int.class, int.class);
            method(OrthogonalPolygon.class, "fromClosedPath", List.class);
            method(OrthogonalPolygon.class, "corners");
            method(OrthogonalPolygon.class, "closedPath");
            method(OrthogonalPolygon.class, "edges");
            method(OrthogonalPolygon.class, "edgeIndexesContainingInteriorPoint", OrthogonalPoint2i.class);
            method(OrthogonalPolygon.class, "insertNode", int.class, OrthogonalPoint2i.class);
            method(OrthogonalPolygon.class, "expandEdge", int.class, int.class);

            Class<?> skeleton = Class.forName("com.griefprevention.claims.editor.ClaimEditorSkeleton");
            declaredMethod(skeleton, "unionPolygons", OrthogonalPolygon.class, OrthogonalPolygon.class);
            declaredMethod(skeleton, "subtractPolygons", OrthogonalPolygon.class, OrthogonalPolygon.class);
        });
    }

    @Test
    void classicClaimSurfaceUsedByMaintainedAddonsRemainsAvailable() {
        assertDoesNotThrow(() -> {
            field(Claim.class, "ownerID");
            field(Claim.class, "parent");
            field(Claim.class, "children");
            method(Claim.class, "getID");
            method(Claim.class, "getOwnerID");
            method(Claim.class, "setOwnerID", UUID.class);
            method(Claim.class, "getChildren");
            method(Claim.class, "getSubclaims");
            method(Claim.class, "getLesserBoundaryCorner");
            method(Claim.class, "getGreaterBoundaryCorner");
            method(Claim.class, "getArea");
            method(Claim.class, "contains", Location.class, boolean.class, boolean.class);
            method(Claim.class, "isAdminClaim");
            method(Claim.class, "is3D");
            method(Claim.class, "containsY", int.class);
            method(Claim.class, "getMinY");
            method(Claim.class, "getMaxY");
            method(Claim.class, "isShaped");
            method(Claim.class, "getBoundaryPolygon");
            method(Claim.class, "setPermission", String.class, ClaimPermission.class);
            method(Claim.class, "dropPermission", String.class);
        });
    }

    @Test
    void datastoreAndPlayerDataSurfaceUsedByMaintainedAddonsRemainsAvailable() {
        assertDoesNotThrow(() -> {
            method(DataStore.class, "getClaims");
            method(DataStore.class, "getClaim", long.class);
            method(DataStore.class, "getClaimAt", Location.class, boolean.class, Claim.class);
            method(DataStore.class, "getClaimAt", Location.class, boolean.class, boolean.class, Claim.class);
            method(DataStore.class, "getPlayerData", UUID.class);
            method(DataStore.class, "savePlayerData", UUID.class, PlayerData.class);
            method(DataStore.class, "saveClaim", Claim.class);
            method(DataStore.class, "deleteClaim", Claim.class);
            method(DataStore.class, "changeClaimOwner", Claim.class, UUID.class);
            method(DataStore.class, "resizeClaim",
                    Claim.class, int.class, int.class, int.class, int.class, int.class, int.class, Player.class);
            method(DataStore.class, "updateShapedClaim", Player.class, PlayerData.class, Claim.class, OrthogonalPolygon.class);
            method(DataStore.class, "getMessage", Messages.class, String[].class);

            field(PlayerData.class, "lastClaim");
            field(PlayerData.class, "ignoreClaims");
            method(PlayerData.class, "getClaims");
            method(PlayerData.class, "getAccruedClaimBlocks");
            method(PlayerData.class, "setAccruedClaimBlocks", Integer.class);
            method(PlayerData.class, "getBonusClaimBlocks");
            method(PlayerData.class, "setBonusClaimBlocks", Integer.class);
            method(PlayerData.class, "getRemainingClaimBlocks");
        });
    }

    @Test
    void eventsPermissionsAndVisualizationSurfaceRemainAvailable() {
        assertDoesNotThrow(() -> {
            enumConstant(ClaimPermission.class, "Access");
            enumConstant(ClaimPermission.class, "Inventory");
            enumConstant(ClaimPermission.class, "Build");
            enumConstant(ClaimPermission.class, "Manage");
            enumConstant(ClaimPermission.class, "Neighbor");
            enumConstant(ClaimPermission.class, "Edit");

            load("me.ryanhamshire.GriefPrevention.events.ClaimResizeEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimModifiedEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimTransferEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimDeletedEvent");
            load("me.ryanhamshire.GriefPrevention.events.TrustChangedEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimPermissionCheckEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimExpirationEvent");
            load("me.ryanhamshire.GriefPrevention.events.ClaimsInactivityExpireEvent");
            load("me.ryanhamshire.GriefPrevention.events.PreDeleteClaimEvent");
            load("me.ryanhamshire.GriefPrevention.events.StartClaimCreationEvent");
            load("me.ryanhamshire.GriefPrevention.events.StartSubdivideClaimCreationEvent");
            load("me.ryanhamshire.GriefPrevention.events.StartClaimResizeEvent");
            load("me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent");
            load("me.ryanhamshire.GriefPrevention.events.ProtectDeathDropsEvent");
            load("me.ryanhamshire.GriefPrevention.events.PreventPvPEvent");
            load("me.ryanhamshire.GriefPrevention.events.PreventBlockBreakEvent");
            load("com.griefprevention.events.BoundaryVisualizationEvent");

            enumConstant(VisualizationType.class, "CLAIM");
            enumConstant(VisualizationType.class, "ADMIN_CLAIM");
            enumConstant(VisualizationType.class, "SUBDIVISION_3D");
            enumConstant(VisualizationType.class, "ADMIN_CLAIM_3D");
            method(BoundaryVisualization.class, "visualizeClaim", Player.class, Claim.class, VisualizationType.class);
        });
    }

    @Test
    void visualizationStyleApiRemainsAvailable() {
        assertDoesNotThrow(() -> {
            method(VisualizationStyle.class, "getKey");
            method(VisualizationStyle.class, "getBlockRenderer");
            method(VisualizationType.class, "getKey");
            method(VisualizationType.class, "fromKey", String.class);

            method(VisualizationStyleRegistry.class, "register", VisualizationStyle.class);
            method(VisualizationStyleRegistry.class, "unregister", String.class);
            method(VisualizationStyleRegistry.class, "get", String.class);
            method(VisualizationStyleRegistry.class, "getStyles");

            method(GriefPrevention.class, "getVisualizationStyleRegistry");

            Boundary.class.getConstructor(BoundingBox.class, VisualizationStyle.class);
            Boundary.class.getConstructor(BoundingBox.class, VisualizationStyle.class, Claim.class);
            Boundary.class.getConstructor(Claim.class, VisualizationStyle.class);
            method(Boundary.class, "style");
            method(Boundary.class, "type");

            method(BoundaryVisualization.class, "visualizeArea", Player.class, BoundingBox.class, VisualizationStyle.class);
            method(BoundaryVisualization.class, "visualizeArea", Player.class, BoundingBox.class, VisualizationStyle.class, int.class);
            method(BoundaryVisualization.class, "visualizeClaim", Player.class, Claim.class, VisualizationStyle.class);
            method(BoundaryVisualization.class, "visualizeClaim", Player.class, Claim.class, VisualizationStyle.class, Block.class);
        });
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getMethod(name, parameterTypes);
        assertNotNull(method);
        return method;
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        assertNotNull(method);
        return method;
    }

    private static void field(Class<?> type, String name) throws NoSuchFieldException {
        assertNotNull(type.getField(name));
    }

    private static void enumConstant(Class<? extends Enum<?>> type, String name) {
        for (Enum<?> constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return;
            }
        }
        assertTrue(false, type.getName() + " is missing enum constant " + name);
    }

    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }
}
