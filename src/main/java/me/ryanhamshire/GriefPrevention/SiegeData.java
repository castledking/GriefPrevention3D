package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.util.TaskHandle;
import org.bukkit.entity.Player;

import java.util.ArrayList;

/** Transient state for one active siege. */
public final class SiegeData {
    public final Player defender;
    public final Player attacker;
    public final ArrayList<Claim> claims = new ArrayList<>();
    public TaskHandle checkupTask;
    boolean ended;

    SiegeData(Player attacker, Player defender, Claim claim) {
        this.defender = defender;
        this.attacker = attacker;
        this.claims.add(claim);
    }
}
