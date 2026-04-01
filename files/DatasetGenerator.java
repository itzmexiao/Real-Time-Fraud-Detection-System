package finguard.util;

import finguard.ingestion.TransactionIngestion;
import finguard.model.Transaction;

import java.io.*;
import java.time.Instant;
import java.util.List;

/**
 * Utility to generate a sample transactions.csv dataset.
 * The generated file is compatible with the system's ingestion module.
 *
 * Dataset format:
 *   # timestamp,transactionId,accountId,type,amount,location,deviceId
 *   1708069200,TXN000001,ACC101,DEBIT,25000.00,Mumbai,DEV_A1
 */
public class DatasetGenerator {

    public static void generate(String outputPath, int count) throws IOException {
        long baseEpoch = Instant.parse("2024-02-16T08:00:00Z").getEpochSecond();
        TransactionIngestion gen = new TransactionIngestion(null); // engine not needed
        List<Transaction> txns  = gen.simulate(count, baseEpoch);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
            bw.write("# FinGuard Transaction Dataset — Generated automatically");
            bw.newLine();
            bw.write("# timestamp,transactionId,accountId,type,amount,location,deviceId");
            bw.newLine();

            for (Transaction t : txns) {
                bw.write(String.format("%d,%s,%s,%s,%.2f,%s,%s",
                        t.getTimestamp(), t.getTransactionId(), t.getAccountId(),
                        t.getType(), t.getAmount(), t.getLocation(), t.getDeviceId()));
                bw.newLine();
            }
        }
        System.out.println("Generated " + count + " transactions → " + outputPath);
    }

    public static void main(String[] args) throws IOException {
        String path  = args.length > 0 ? args[0] : "data/transactions.csv";
        int    count = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
        generate(path, count);
    }
}
