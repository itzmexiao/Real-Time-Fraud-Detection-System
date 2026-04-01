package finguard.engine;

import finguard.model.Transaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
    
/**
 * Maintains a rolling 10-minute time window of transactions for ONE account.
 * All mutations are performed under external synchronization (per account).
 *
 * Complexity:
 *   - addAndEvict : O(k) amortized where k = expired entries (typically 0)
 *   - All counters kept incrementally — no full re-scan needed.
 */
public class AccountWindow {

    private static final long WINDOW_SECONDS      = 600L;   // 10 min
    private static final long CITY_WINDOW_SECONDS =  300L;  // 5 min
    private static final double HIGH_VALUE_THRESHOLD = 50_000.0;
    private static final double TOTAL_SPEND_LIMIT    = 200_000.0;

    private final String accountId;

    // Sliding window queue — transactions sorted by timestamp (ascending)
    private final Deque<Transaction> window = new ArrayDeque<>();

    // Incremental counters (updated on add/evict, never full rescan)
    private int    highValueCount  = 0;
    private double totalSpending   = 0.0;
    private final Set<String> devices   = new HashSet<>();
    private final Set<String> cities    = new HashSet<>();   // full window

    public AccountWindow(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Add a new transaction and evict all entries older than 10 minutes relative to it.
     * Returns the window state after the update (a snapshot for analysis).
     */
    public synchronized void addAndEvict(Transaction t) {
        long cutoff = t.getTimestamp() - WINDOW_SECONDS;

        // Evict expired transactions
        while (!window.isEmpty() && window.peekFirst().getTimestamp() <= cutoff) {
            Transaction expired = window.pollFirst();
            if (expired.getAmount() > HIGH_VALUE_THRESHOLD) highValueCount--;
            totalSpending -= expired.getAmount();
            // Rebuild device/city sets lazily (cheaper than tracking counts)
        }

        // Add new transaction
        window.addLast(t);
        if (t.getAmount() > HIGH_VALUE_THRESHOLD) highValueCount++;
        totalSpending += t.getAmount();

        // Rebuild device & city sets (O(window_size) but window is bounded)
        rebuildSets();
    }

    private void rebuildSets() {
        devices.clear();
        cities.clear();
        for (Transaction tx : window) {
            devices.add(tx.getDeviceId());
            cities.add(tx.getLocation());
        }
    }

    /** Number of distinct cities within the last 5 minutes relative to latest tx */
    public synchronized int getCityChangesIn5Min() {
        if (window.isEmpty()) return 0;
        long latestTs = window.peekLast().getTimestamp();
        long cutoff5  = latestTs - CITY_WINDOW_SECONDS;
        Set<String> recent5Cities = new HashSet<>();
        for (Transaction tx : window) {
            if (tx.getTimestamp() > cutoff5) {
                recent5Cities.add(tx.getLocation());
            }
        }
        return recent5Cities.size();
    }

    public synchronized int    getHighValueCount()  { return highValueCount; }
    public synchronized double getTotalSpending()   { return totalSpending; }
    public synchronized int    getDeviceSwitches()  { return devices.size(); }
    public synchronized int    getWindowSize()      { return window.size(); }
    public synchronized Set<String> getCities()     { return new HashSet<>(cities); }
    public String getAccountId()                    { return accountId; }
}
