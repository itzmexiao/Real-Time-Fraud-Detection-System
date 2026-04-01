package finguard;

import finguard.alert.AlertDispatcher;
import finguard.engine.FraudDetectionEngine;
import finguard.ingestion.TransactionIngestion;
import finguard.model.FraudAlert;
import finguard.util.DatasetGenerator;

import java.io.File;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * FinGuard — Entry Point
 *
 * Usage:
 *   java -cp bin finguard.Main [simulate|file] [count|filepath]
 *
 * Modes:
 *   simulate <count>  — generate and process <count> simulated transactions
 *   file <path>       — load and process transactions from CSV file
 *   (no args)         — simulate 2000 transactions (default)
 */
public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {

        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║   FinGuard – Real-Time Fraud Detection        ║");
        System.out.println("║   IBM Hackathon | Java-based Engine           ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        // ── Setup engine & components ──────────────────────────────────────
        FraudDetectionEngine engine      = new FraudDetectionEngine();
        AlertDispatcher      dispatcher  = new AlertDispatcher("data/fraud_alert.txt");
        TransactionIngestion ingestion   = new TransactionIngestion(engine);

        // Wire alert listener (Thread-3 calls this)
        engine.setAlertListener(dispatcher::dispatch);

        // Start engine threads
        engine.start();

        // ── Choose ingestion mode ──────────────────────────────────────────
        String mode  = args.length > 0 ? args[0].toLowerCase() : "simulate";
        int    count = 2000;
        String path  = "data/transactions.csv";

        if ("file".equals(mode)) {
            path = args.length > 1 ? args[1] : path;
            File f = new File(path);
            if (!f.exists()) {
                System.out.println("File not found. Generating sample dataset at: " + path);
                new File("data").mkdirs();
                DatasetGenerator.generate(path, 2000);
            }
            System.out.println("Loading from file: " + path);
            ingestion.ingestFromFile(path);

        } else {
            // simulate mode
            count = args.length > 1 ? Integer.parseInt(args[1]) : count;
            System.out.println("Starting simulation with " + count + " transactions...");
            ingestion.startSimulation(count, Instant.now().getEpochSecond() - 3600);
        }

        // ── Wait for processing to finish ─────────────────────────────────
        System.out.println("Processing... (press Ctrl+C to stop early)");
        long startMs = System.currentTimeMillis();

        while (engine.getIngestQueueSize() > 0 || engine.getProcessedCount() < count - 5) {
            Thread.sleep(500);
            long elapsed = (System.currentTimeMillis() - startMs) / 1000;
            System.out.printf("\r  Processed: %-5d | Alerts: %-4d | Fraud: %-3d | %ds elapsed",
                    engine.getProcessedCount(), engine.getAlertCount(),
                    engine.getFraudCount(), elapsed);
            if (elapsed > 60) break; // safety timeout
        }
        Thread.sleep(1000); // let dispatcher flush

        // ── Final report ──────────────────────────────────────────────────
        System.out.println("\n\n════════════════ FINAL REPORT ════════════════");
        System.out.printf("  Transactions Processed : %d%n",   engine.getProcessedCount());
        System.out.printf("  Unique Accounts        : %d%n",   engine.getAccountCount());
        System.out.printf("  Total Alerts           : %d%n",   engine.getAlertCount());
        System.out.printf("  Suspicious             : %d%n",   dispatcher.countBySeverity(FraudAlert.Severity.SUSPICIOUS));
        System.out.printf("  High Risk              : %d%n",   dispatcher.countBySeverity(FraudAlert.Severity.HIGH_RISK));
        System.out.printf("  Fraud Alerts           : %d%n",   dispatcher.countBySeverity(FraudAlert.Severity.FRAUD_ALERT));
        System.out.printf("  Fraud log written to   : %s%n",   dispatcher.getAlertFilePath());
        System.out.println("══════════════════════════════════════════════");

        engine.stop();
    }
}
