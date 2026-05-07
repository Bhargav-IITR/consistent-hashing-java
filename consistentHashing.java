import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

class ConsistentHashing {

    private final int numReplicas;
    private final TreeMap<Long, String> ring;
    private final Set<String> servers;

    // Stores which key is mapped to which server
    private final Map<String, String> keyToServer;

    public ConsistentHashing(List<String> servers, int numReplicas) {
        this.numReplicas = numReplicas;
        this.ring = new TreeMap<>();
        this.servers = new HashSet<>();
        this.keyToServer = new HashMap<>();

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

    // Add server + virtual nodes
    public void addServer(String server) {

        servers.add(server);

        for (int i = 0; i < numReplicas; i++) {

            long hash = hash(server + "-" + i);

            ring.put(hash, server);
        }

        // Optional rebalance
        rebalanceKeys();
    }

    // Remove server + reassign affected keys
    public void removeServer(String server) {

        if (!servers.contains(server)) {
            return;
        }

        // Step 1: Remove virtual nodes
        for (int i = 0; i < numReplicas; i++) {

            long hash = hash(server + "-" + i);

            ring.remove(hash);
        }

        servers.remove(server);

        // Step 2: Reassign affected keys
        List<String> affectedKeys = new ArrayList<>();

        for (Map.Entry<String, String> entry : keyToServer.entrySet()) {

            if (entry.getValue().equals(server)) {
                affectedKeys.add(entry.getKey());
            }
        }

        // Re-map only affected keys
        for (String key : affectedKeys) {

            String newServer = getServerFromRing(key);

            keyToServer.put(key, newServer);
        }
    }

    // Internal lookup from ring
    private String getServerFromRing(String key) {

        if (ring.isEmpty()) {
            return null;
        }

        long hash = hash(key);

        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    // Public API
    public String getServer(String key) {

        // Cache assignment
        if (!keyToServer.containsKey(key)) {

            String server = getServerFromRing(key);

            keyToServer.put(key, server);
        }

        return keyToServer.get(key);
    }

    // Rebalance all keys (used when adding server)
    private void rebalanceKeys() {

        for (String key : keyToServer.keySet()) {

            String correctServer = getServerFromRing(key);

            keyToServer.put(key, correctServer);
        }
    }

    // Utility function
    public void printMappings() {

        System.out.println("\nCurrent Key Mappings:");

        for (Map.Entry<String, String> entry : keyToServer.entrySet()) {

            System.out.println(
                entry.getKey() + " -> " + entry.getValue()
            );
        }
    }
}

public class Main {

    public static void main(String[] args) {

        List<String> servers =
            Arrays.asList("S0", "S1", "S2", "S3", "S4", "S5");

        ConsistentHashing ch =
            new ConsistentHashing(servers, 3);

        // Initial assignments
        System.out.println("UserA -> " + ch.getServer("UserA"));
        System.out.println("UserB -> " + ch.getServer("UserB"));
        System.out.println("UserC -> " + ch.getServer("UserC"));
        System.out.println("UserD -> " + ch.getServer("UserD"));

        ch.printMappings();

        // Add server
        System.out.println("\nAdding S6...");
        ch.addServer("S6");

        ch.printMappings();

        // Remove server
        System.out.println("\nRemoving S2...");
        ch.removeServer("S2");

        ch.printMappings();
    }
}