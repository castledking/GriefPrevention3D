package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The defaults and parent nodes GriefPrevention's plugin.yml gives Bukkit, applied on Fabric wherever
 * no permission provider decides a node.
 *
 * <p>Bukkit resolves a node from, in order: an explicit value on the node, an explicit value on a
 * parent that lists it as a child, and finally the defaults, where a parent granted by default grants
 * its children too. LuckPerms on Fabric only knows the nodes it was given, so the same walk happens
 * here: granting {@code griefprevention.claims: false} still takes {@code /trust} away, and
 * {@code griefprevention.adminclaims} still brings {@code griefprevention.permissiontrust} with it.
 */
final class FabricPermissionDefaults
{
    static final String CLAIMS = "griefprevention.claims";
    static final String ADMIN = "griefprevention.admin.*";
    static final String ADMIN_CLAIMS = "griefprevention.adminclaims";
    static final String TRUST = "griefprevention.trust";
    static final String UNTRUST = "griefprevention.untrust";
    static final String TRUST_LIST = "griefprevention.trustlist";
    static final String ACCESS_TRUST = "griefprevention.accesstrust";
    static final String CONTAINER_TRUST = "griefprevention.containertrust";
    static final String MANAGE_TRUST = "griefprevention.managetrust";
    static final String PERMISSION_TRUST = "griefprevention.permissiontrust";
    static final String CLAIMS_LIST = "griefprevention.claimslist";
    static final String CLAIMS_LIST_OTHER = "griefprevention.claimslistother";
    static final String ABANDON_CLAIM = "griefprevention.abandonclaim";
    static final String ABANDON_TOP_LEVEL_CLAIM = "griefprevention.abandontoplevelclaim";
    static final String ABANDON_ALL_CLAIMS = "griefprevention.abandonallclaims";
    static final String BASIC_CLAIMS = "griefprevention.basicclaims";
    static final String CREATE_CLAIMS = "griefprevention.createclaims";
    static final String CLAIM_PVP = "griefprevention.claimpvp";
    static final String CLAIM_EXPLOSIONS = "griefprevention.claimexplosions";
    static final String WITHER_EXPLOSIONS = "griefprevention.witherexplosions";
    static final String TRANSFER_CLAIM = "griefprevention.transferclaim";
    static final String TRANSFER_CLAIM_FREE = "griefprevention.transferclaim.free";
    static final String TRANSFER_CLAIM_OTHERS = "griefprevention.transferclaim.others";
    static final String ADJUST_CLAIM_BLOCKS = "griefprevention.adjustclaimblocks";
    static final String DELETE_CLAIMS = "griefprevention.deleteclaims";
    static final String DELETE_CLAIMS_IN_WORLD = "griefprevention.deleteclaimsinworld";
    static final String DELETE_ALL_ADMIN_CLAIMS = "griefprevention.deletealladminclaims";
    static final String ADMIN_CLAIMS_LIST = "griefprevention.adminclaimslist";
    static final String CONVERT_CLAIMS = "griefprevention.adminclaims.convert";
    static final String IGNORE_CLAIMS = "griefprevention.ignoreclaims";
    static final String SEE_INACTIVITY = "griefprevention.seeinactivity";
    static final String RELOAD = "griefprevention.reload";

    private static final Map<String, Node> NODES = new HashMap<>();

    static
    {
        node(CLAIMS, Default.TRUE);
        node(ADMIN, Default.OP);

        for (String child : Arrays.asList(
                TRUST, UNTRUST, TRUST_LIST, ACCESS_TRUST, CONTAINER_TRUST, MANAGE_TRUST, CLAIMS_LIST,
                ABANDON_CLAIM, ABANDON_TOP_LEVEL_CLAIM, ABANDON_ALL_CLAIMS, BASIC_CLAIMS, CREATE_CLAIMS,
                CLAIM_PVP, CLAIM_EXPLOSIONS, WITHER_EXPLOSIONS, TRANSFER_CLAIM))
        {
            node(child, Default.TRUE, CLAIMS);
        }
        node("griefprevention.visualizenearbyclaims", Default.TRUE, CLAIMS, ADMIN);

        for (String child : Arrays.asList(
                ADMIN_CLAIMS, ADJUST_CLAIM_BLOCKS, DELETE_CLAIMS, SEE_INACTIVITY, RELOAD, CLAIMS_LIST_OTHER,
                IGNORE_CLAIMS, "griefprevention.seeclaimsize", "griefprevention.overrideclaimcountlimit",
                TRANSFER_CLAIM_OTHERS, CONVERT_CLAIMS, DELETE_CLAIMS_IN_WORLD))
        {
            node(child, Default.OP, ADMIN);
        }

        node(PERMISSION_TRUST, Default.FALSE, ADMIN_CLAIMS);
        node(ADMIN_CLAIMS_LIST, Default.OP, ADMIN_CLAIMS);
        node(DELETE_ALL_ADMIN_CLAIMS, Default.OP, ADMIN_CLAIMS);
        node(TRANSFER_CLAIM_FREE, Default.FALSE);
        node("griefprevention.claimpvp.free", Default.FALSE);
    }

    private FabricPermissionDefaults()
    {
    }

    /**
     * @param permission the node to check
     * @param explicit the permission provider's value for a node, or null when it has none
     * @param operator whether the player is a server operator, which Bukkit's {@code op} default reads
     * @return whether the node is granted
     */
    static boolean resolve(
            @NotNull String permission,
            @NotNull Function<String, Boolean> explicit,
            boolean operator)
    {
        Boolean value = explicitValue(permission, explicit, new HashSet<>());
        return value != null ? value : grantedByDefault(permission, operator, new HashSet<>());
    }

    private static @Nullable Boolean explicitValue(
            @NotNull String permission,
            @NotNull Function<String, Boolean> explicit,
            @NotNull Set<String> visited)
    {
        if (!visited.add(permission))
        {
            return null;
        }
        Boolean direct = explicit.apply(permission);
        if (direct != null)
        {
            return direct;
        }
        Node node = NODES.get(permission);
        if (node != null)
        {
            for (String parent : node.parents)
            {
                Boolean inherited = explicitValue(parent, explicit, visited);
                if (inherited != null)
                {
                    return inherited;
                }
            }
        }
        return null;
    }

    private static boolean grantedByDefault(
            @NotNull String permission,
            boolean operator,
            @NotNull Set<String> visited)
    {
        if (!visited.add(permission))
        {
            return false;
        }
        Node node = NODES.get(permission);
        // Bukkit treats a node plugin.yml does not declare as op-only.
        Default value = node == null ? Default.OP : node.value;
        if (value == Default.TRUE || (value == Default.OP && operator))
        {
            return true;
        }
        if (node != null)
        {
            for (String parent : node.parents)
            {
                if (grantedByDefault(parent, operator, visited))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** The nodes declared here, which must match plugin.yml. */
    static @NotNull Set<String> declared()
    {
        return Collections.unmodifiableSet(NODES.keySet());
    }

    /** @return the node's default as plugin.yml writes it: true, op or false */
    static @NotNull String declaredDefault(@NotNull String permission)
    {
        return NODES.get(permission).value.name().toLowerCase(Locale.ROOT);
    }

    /** @return the nodes that list this one as a child */
    static @NotNull List<String> declaredParents(@NotNull String permission)
    {
        return NODES.get(permission).parents;
    }

    private static void node(@NotNull String permission, @NotNull Default value, @NotNull String... parents)
    {
        NODES.put(permission, new Node(value, Collections.unmodifiableList(Arrays.asList(parents))));
    }

    private enum Default
    {
        TRUE,
        OP,
        FALSE
    }

    private static final class Node
    {
        private final @NotNull Default value;
        private final @NotNull List<String> parents;

        private Node(@NotNull Default value, @NotNull List<String> parents)
        {
            this.value = value;
            this.parents = parents;
        }
    }
}
