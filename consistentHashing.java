import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

class ConsistentHashing {

    private final int numReplicas;
    private final TreeMap<Long, String> ring;
    private final Set<String> servers;

    // Forward index: key -> server
    private final Map<String, String> keyToServer;

    // Reverse index: server -> keys assigned to it
    // This is what makes removeServer O(k) instead of O(n)
    private final Map<String, Set<String>> serverToKeys;

    public ConsistentHashing(List<String> servers, int numReplicas) {
        this.numReplicas = numReplicas;
        this.ring = new TreeMap<>();
        this.servers = new HashSet<>();
        this.keyToServer = new HashMap<>();
        this.serverToKeys = new HashMap<>();

        for (String server : servers) {
            addServer(server);
        }
    }

    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(key.getBytes());
            byte[] digest = md.digest();
            return ((long) (digest[0] & 0xFF) << 24) |
                   ((long) (digest[1] & 0xFF) << 16) |
                   ((long) (digest[2] & 0xFF) << 8) |
                   ((long) (digest[3] & 0xFF));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void addServer(String server) {
        servers.add(server);
        serverToKeys.put(server, new HashSet<>());

        for (int i = 0; i < numReplicas; i++) {
            long h = hash(server + "-" + i);
            ring.put(h, server);
        }

        rebalanceOnAdd(server);
    }

    public void removeServer(String server) {
        if (!servers.contains(server)) return;

        // Step 1: Grab affected keys BEFORE touching the ring
        // O(1) lookup thanks to reverse index — no scan needed
        Set<String> affectedKeys = new HashSet<>(
            serverToKeys.getOrDefault(server, Collections.emptySet())
        );

        // Step 2: Remove virtual nodes from ring
        for (int i = 0; i < numReplicas; i++) {
            ring.remove(hash(server + "-" + i));
        }

        servers.remove(server);
        serverToKeys.remove(server);

        // Step 3: Reassign only the affected keys to their new servers
        for (String key : affectedKeys) {
            String newServer = getServerFromRing(key);
            keyToServer.put(key, newServer);
            serverToKeys.get(newServer).add(key);
        }
    }

    // Only remaps keys that actually move to the new server.
    // Scan is O(n) but updates are O(k) where k = keys migrating.
    // This is unavoidable without a hash-sorted key index.
    private void rebalanceOnAdd(String newServer) {
        List<String> keysToMove = new ArrayList<>();

        for (Map.Entry<String, String> entry : keyToServer.entrySet()) {
            String key = entry.getKey();
            String correctServer = getServerFromRing(key);

            // Only move keys that now belong to the new server
            if (newServer.equals(correctServer)) {
                keysToMove.add(key);
            }
        }

        for (String key : keysToMove) {
            String oldServer = keyToServer.get(key);
            serverToKeys.get(oldServer).remove(key);   // update reverse index
            keyToServer.put(key, newServer);
            serverToKeys.get(newServer).add(key);       // update reverse index
        }
    }

    private String getServerFromRing(String key) {
        if (ring.isEmpty()) return null;
        long h = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(h);
        if (entry == null) entry = ring.firstEntry(); // wrap around
        return entry.getValue();
    }

    public String getServer(String key) {
        if (!keyToServer.containsKey(key)) {
            String server = getServerFromRing(key);
            keyToServer.put(key, server);
            serverToKeys.get(server).add(key);  // keep reverse index in sync
        }
        return keyToServer.get(key);
    }

    public void printMappings() {
        System.out.println("\nKey -> Server:");
        keyToServer.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
    }

    public void printServerLoads() {
        System.out.println("\nServer Loads:");
        serverToKeys.forEach((s, keys) ->
            System.out.println("  " + s + " (" + keys.size() + " keys): " + keys));
    }
}

public class Main {

    public static void main(String[] args) {
        List<String> servers = Arrays.asList("S0", "S1", "S2", "S3", "S4", "S5");
        ConsistentHashing ch = new ConsistentHashing(servers, 3);

        System.out.println("Initial assignments:");
        System.out.println("UserA -> " + ch.getServer("UserA"));
        System.out.println("UserB -> " + ch.getServer("UserB"));
        System.out.println("UserC -> " + ch.getServer("UserC"));
        System.out.println("UserD -> " + ch.getServer("UserD"));

        ch.printMappings();
        ch.printServerLoads();

        System.out.println("\n--- Adding S6 ---");
        ch.addServer("S6");
        ch.printMappings();
        ch.printServerLoads();

        System.out.println("\n--- Removing S2 ---");
        ch.removeServer("S2");
        ch.printMappings();
        ch.printServerLoads();
    }
}