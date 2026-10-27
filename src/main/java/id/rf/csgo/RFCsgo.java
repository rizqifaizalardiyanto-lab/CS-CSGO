package id.rf.csgo;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class RFCsgo extends JavaPlugin implements Listener, CommandExecutor {
    private enum Team { RED, BLUE }
    private final Map<UUID, Team> teams = new HashMap<>();
    private final Map<String, Location> spawns = new HashMap<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> dead = new HashSet<>();
    private final Map<UUID, String> oldDisplayNames = new HashMap<>();
    private int redScore = 0, blueScore = 0, round = 0;
    private boolean running = false, duel = false, roundEnding = false;
    private BukkitTask nextRoundTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String key : List.of("merah", "biru", "duel")) {
            String path = "spawns." + key;
            if (getConfig().contains(path + ".world")) {
                World w = Bukkit.getWorld(getConfig().getString(path + ".world"));
                if (w != null) spawns.put(key, new Location(w,
                    getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"),
                    getConfig().getDouble(path + ".z"), (float)getConfig().getDouble(path + ".yaw"),
                    (float)getConfig().getDouble(path + ".pitch")));
            }
        }
        for (String cmd : List.of("random","byone","setspawn","startmatch","stopmatch","resetmatch","score"))
            Objects.requireNonNull(getCommand(cmd)).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("RF-CSGO enabled.");
    }

    @Override public void onDisable() {
        if (nextRoundTask != null) nextRoundTask.cancel();
        restoreNames();
    }

    private void msg(CommandSender s, String m) { s.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6RF-CSGO&8] &f" + m)); }
    private void broadcast(String m) { Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&8[&6RF-CSGO&8] &f" + m)); }
    private Location spawn(String key) { return spawns.get(key); }

    private void saveSpawn(String key, Location l) {
        spawns.put(key, l.clone());
        String p = "spawns." + key;
        getConfig().set(p + ".world", l.getWorld().getName());
        getConfig().set(p + ".x", l.getX()); getConfig().set(p + ".y", l.getY());
        getConfig().set(p + ".z", l.getZ()); getConfig().set(p + ".yaw", l.getYaw());
        getConfig().set(p + ".pitch", l.getPitch()); saveConfig();
    }

    private void assignName(Player p, Team t) {
        oldDisplayNames.putIfAbsent(p.getUniqueId(), p.getDisplayName());
        String prefix = t == Team.RED ? ChatColor.RED + "[MERAH] " : ChatColor.BLUE + "[BIRU] ";
        p.setDisplayName(prefix + ChatColor.RESET + p.getName());
        p.setPlayerListName(prefix + ChatColor.RESET + p.getName());
    }

    private void restoreNames() {
        for (UUID id : new HashSet<>(oldDisplayNames.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setDisplayName(oldDisplayNames.get(id));
                p.setPlayerListName(p.getName());
            }
        }
        oldDisplayNames.clear();
    }

    private boolean requireSpawns(CommandSender s, boolean needDuel) {
        if (spawn("merah") == null || spawn("biru") == null || (needDuel && spawn("duel") == null)) {
            msg(s, "&cSpawn belum lengkap. Atur dengan /setspawn merah, /setspawn biru" +
                (needDuel ? " dan /setspawn duel." : "."));
            return false;
        }
        return true;
    }

    private void startTeamMatch(CommandSender sender) {
        if (running) { msg(sender, "&cMatch sedang berlangsung."); return; }
        if (teams.size() < 2) { msg(sender, "&cGunakan /random dahulu dan pastikan minimal 2 pemain online."); return; }
        if (!requireSpawns(sender, false)) return;
        participants.clear(); participants.addAll(teams.keySet());
        duel = false; redScore = 0; blueScore = 0; round = 0; running = true;
        broadcast("&aMatch tim BO3 akan dimulai!");
        beginRound();
    }

    private void beginRound() {
        if (!running) return;
        round++; dead.clear(); roundEnding = false;
        broadcast("&eRONDE " + round + " DIMULAI! &cMERAH " + redScore + " &7- &9" + blueScore + " BIRU");
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            Team t = teams.get(id);
            if (p == null || t == null) continue;
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20.0); p.setFoodLevel(20); p.setFireTicks(0);
            Location loc = spawn(t == Team.RED ? "merah" : "biru");
            if (loc != null) p.teleport(loc);
        }
    }

    private void checkRoundEnd() {
        if (!running || roundEnding) return;
        long redAlive = participants.stream().filter(id -> teams.get(id) == Team.RED && Bukkit.getPlayer(id) != null && !dead.contains(id)).count();
        long blueAlive = participants.stream().filter(id -> teams.get(id) == Team.BLUE && Bukkit.getPlayer(id) != null && !dead.contains(id)).count();
        if (redAlive == 0 || blueAlive == 0) {
            roundEnding = true;
            Team winner = redAlive == 0 && blueAlive == 0 ? null : (redAlive == 0 ? Team.BLUE : Team.RED);
            if (winner == Team.RED) redScore++; else if (winner == Team.BLUE) blueScore++;
            broadcast(winner == null ? "&eRONDE SERI!" :
                (winner == Team.RED ? "&cTIM MERAH WIN!" : "&9TIM BIRU WIN!") +
                " &7| Skor: &c" + redScore + " &7- &9" + blueScore);
            if (redScore >= 2 || blueScore >= 2 || round >= 3) {
                Team matchWinner = redScore == blueScore ? null : (redScore > blueScore ? Team.RED : Team.BLUE);
                Bukkit.getScheduler().runTaskLater(this, () -> finishMatch(matchWinner), 60L);
            } else {
                nextRoundTask = Bukkit.getScheduler().runTaskLater(this, this::beginRound, 100L);
            }
        }
    }

    private void finishMatch(Team winner) {
        if (!running) return;
        broadcast(winner == null ? "&eMATCH SERI!" :
            (winner == Team.RED ? "&cTIM MERAH MENANG MATCH!" : "&9TIM BIRU MENANG MATCH!") +
            " &7Skor akhir: &c" + redScore + " &7- &9" + blueScore);
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.getInventory().clear(); p.getInventory().setArmorContents(null);
                p.getInventory().setItemInOffHand(null);
                p.setFireTicks(0); p.setHealth(20.0); p.setFoodLevel(20);
            }
        }
        clearState();
    }

    private void clearState() {
        running = false; duel = false; roundEnding = false;
        if (nextRoundTask != null) nextRoundTask.cancel();
        participants.clear(); dead.clear(); teams.clear();
        restoreNames();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String c = command.getName().toLowerCase(Locale.ROOT);
        if (c.equals("score")) { msg(sender, "&cMerah " + redScore + " &7- &9Biru " + blueScore + " &8(Ronde " + round + ")"); return true; }
        if (c.equals("setspawn")) {
            if (!(sender instanceof Player p)) { msg(sender, "&cCommand ini harus dijalankan oleh pemain."); return true; }
            if (args.length != 1 || !List.of("merah","biru","duel").contains(args[0].toLowerCase())) { msg(sender, "&e/setspawn <merah|biru|duel>"); return true; }
            String key = args[0].toLowerCase(); saveSpawn(key, p.getLocation()); msg(sender, "&aSpawn " + key + " berhasil disimpan."); return true;
        }
        if (c.equals("random")) {
            if (running) { msg(sender, "&cTidak bisa mengacak tim saat match berlangsung."); return true; }
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(online, new Random());
            teams.clear(); oldDisplayNames.clear();
            for (int i=0;i<online.size();i++) {
                Team t = (i % 2 == 0) ? Team.RED : Team.BLUE;
                teams.put(online.get(i).getUniqueId(), t); assignName(online.get(i), t);
            }
            broadcast("&aTim berhasil diacak! &cMerah: " +
                online.stream().filter(p -> teams.get(p.getUniqueId()) == Team.RED).map(Player::getName).toList() +
                " &9Biru: " + online.stream().filter(p -> teams.get(p.getUniqueId()) == Team.BLUE).map(Player::getName).toList());
            return true;
        }
        if (c.equals("byone")) {
            if (running) { msg(sender, "&cMatch sedang berlangsung."); return true; }
            if (args.length != 2) { msg(sender, "&e/byone <player1> <player2>"); return true; }
            Player a = Bukkit.getPlayerExact(args[0]), b = Bukkit.getPlayerExact(args[1]);
            if (a == null || b == null || a.equals(b)) { msg(sender, "&cPastikan kedua pemain online dan berbeda."); return true; }
            if (!requireSpawns(sender, false)) return true;
            teams.clear(); participants.clear(); dead.clear();
            // Randomly decide which requested player starts on Red or Blue.
            boolean firstPlayerIsRed = new Random().nextBoolean();
            Player redPlayer = firstPlayerIsRed ? a : b;
            Player bluePlayer = firstPlayerIsRed ? b : a;
            teams.put(redPlayer.getUniqueId(), Team.RED);
            teams.put(bluePlayer.getUniqueId(), Team.BLUE);
            assignName(redPlayer, Team.RED); assignName(bluePlayer, Team.BLUE);
            participants.add(a.getUniqueId()); participants.add(b.getUniqueId());
            duel = true; redScore = 0; blueScore = 0; round = 0; running = true;
            broadcast("&eDUEL 1 VS 1: &c" + redPlayer.getName() + " &7(MERAH) &7VS &9" + bluePlayer.getName() + " &7(BIRU) &8(BO3)");
            beginRound(); return true;
        }
        if (c.equals("startmatch")) { startTeamMatch(sender); return true; }
        if (c.equals("stopmatch") || c.equals("resetmatch")) {
            if (!running && teams.isEmpty()) { msg(sender, "&eTidak ada match atau tim aktif."); return true; }
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) { p.getInventory().clear(); p.getInventory().setArmorContents(null); p.getInventory().setItemInOffHand(null); }
            }
            clearState(); redScore = 0; blueScore = 0; round = 0;
            broadcast("&cMatch dihentikan dan data tim di-reset."); return true;
        }
        return false;
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity(); UUID id = p.getUniqueId();
        if (!running || !participants.contains(id)) return;
        e.setKeepInventory(true); e.setKeepLevel(true); e.getDrops().clear(); e.setDroppedExp(0);
        dead.add(id);
        Bukkit.getScheduler().runTask(this, this::checkRoundEnd);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!running || !participants.contains(id)) return;
        Team t = teams.get(id);
        Location loc = t == null ? null : spawn(t == Team.RED ? "merah" : "biru");
        if (loc != null) e.setRespawnLocation(loc);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player p = e.getPlayer();
            if (p.isOnline() && running) { p.setHealth(20.0); p.setFoodLevel(20); p.setFireTicks(0); }
        }, 2L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (running && participants.contains(id)) {
            dead.add(id);
            Bukkit.getScheduler().runTask(this, this::checkRoundEnd);
        }
    }
}
