package finguard.ingestion;

import finguard.engine.FraudDetectionEngine;
import finguard.model.Transaction;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transaction Ingestion Module — Thread-1 role.
 *
 * Supports:
 *   1. File-based ingestion (CSV)
 *   2. Real-time simulation (random transaction generator)
 */
public class TransactionIngestion {

    private static final Logger LOG = Logger.getLogger(TransactionIngestion.class.getName());

    private final FraudDetectionEngine engine;
    private final AtomicInteger        ingested   = new AtomicInteger(0);
    private final AtomicInteger        parseErrors= new AtomicInteger(0);

    public TransactionIngestion(FraudDetectionEngine engine) {
        this.engine = engine;
    }

    // ── File Ingestion ─────────────────────────────────────────────────────

    /**
     * Read transactions from a CSV file using BufferedReader.
     * Skips header line if it starts with '#' or 'timestamp'.
     */
    public List<Transaction> loadFromFile(String filePath) throws IOException {
        List<Transaction> loaded = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")
                        || line.toLowerCase().startsWith("timestamp")) continue;
                try {
                    Transaction t = Transaction.parse(line);
                    loaded.add(t);
                    ingested.incrementAndGet();
                } catch (IllegalArgumentException ex) {
                    parseErrors.incrementAndGet();
                    LOG.warning("Skipping bad line: " + ex.getMessage());
                }
            }
        }
        LOG.info(String.format("Loaded %d transactions from %s (%d errors)",
                loaded.size(), filePath, parseErrors.get()));
        return loaded;
    }

    /**
     * Load from file and feed directly into the engine (Thread-1 behaviour).
     */
    public void ingestFromFile(String filePath) throws IOException, InterruptedException {
        List<Transaction> txns = loadFromFile(filePath);
        for (Transaction t : txns) {
            engine.submit(t);
        }
    }

    // ── Simulation ─────────────────────────────────────────────────────────

    private static final String[] ACCOUNTS = {
        "ACC101","ACC202","ACC303","ACC404","ACC505",
        "ACC606","ACC707","ACC808","ACC909","ACC010"
    };
    private static final String[] CITIES = {
        "Mumbai","Delhi","Bengaluru","Chennai","Kolkata",
        "Hyderabad","Pune","Ahmedabad","Jaipur","Surat"
    };
    private static final String[] DEVICES = {
        "DEV_A1","DEV_B2","DEV_C3","DEV_D4","DEV_E5",
        "DEV_F6","DEV_G7","DEV_H8","DEV_I9","DEV_J0"
    };

    private final Random rng = new Random(42);

    /**
     * Generate {@code count} simulated transactions starting at {@code baseEpoch}.
     * Occasionally injects suspicious patterns for testing.
     */
    public List<Transaction> simulate(int count, long baseEpoch) {
        List<Transaction> result = new ArrayList<>(count);
        long ts = baseEpoch;

        for (int i = 0; i < count; i++) {
            ts += rng.nextInt(60) + 1;  // 1-60 seconds between transactions

            String acc   = ACCOUNTS[rng.nextInt(ACCOUNTS.length)];
            String city  = CITIES[rng.nextInt(CITIES.length)];
            String dev   = DEVICES[rng.nextInt(DEVICES.length)];
            double amt   = 1_000 + rng.nextDouble() * 99_000;
            Transaction.TransactionType type = rng.nextBoolean()
                    ? Transaction.TransactionType.DEBIT
                    : Transaction.TransactionType.CREDIT;

            // Inject fraud pattern every ~50 transactions
            if (i % 50 == 0) {
                acc  = "ACC_FRAUD";
                amt  = 60_000 + rng.nextDouble() * 90_000;  // always high value
                type = Transaction.TransactionType.DEBIT;
                // rapid city switching
                city = CITIES[i % CITIES.length];
            }

            String txnId = String.format("TXN%06d", i + 1);
            result.add(new Transaction(ts, txnId, acc, type, amt, city, dev));
            ingested.incrementAndGet();
        }
        return result;
    }

    /**
     * Feed simulated transactions into the engine asynchronously (Thread-1).
     */
    public void startSimulation(int count, long baseEpoch) {
        Thread t = new Thread(() -> {
            List<Transaction> txns = simulate(count, baseEpoch);
            for (Transaction tx : txns) {
                try {
                    engine.submit(tx);
                    Thread.sleep(1); // small delay to simulate streaming
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            LOG.info("Simulation complete: " + count + " transactions submitted.");
        }, "FinGuard-Ingestion");
        t.setDaemon(true);
        t.start();
    }

    public int getIngestedCount() { return ingested.get(); }
    public int getParseErrors()   { return parseErrors.get(); }
}
