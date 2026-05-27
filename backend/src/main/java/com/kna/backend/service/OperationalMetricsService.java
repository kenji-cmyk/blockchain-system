package com.kna.backend.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class OperationalMetricsService {

    private final AtomicLong validationRuns = new AtomicLong();
    private final AtomicLong minedBlocks = new AtomicLong();
    private final AtomicLong minedTransactions = new AtomicLong();
    private final AtomicLong miningNonceTotal = new AtomicLong();
    private final AtomicLong miningElapsedMsTotal = new AtomicLong();
    private final AtomicLong rejectedTransactions = new AtomicLong();
    private final AtomicLong acceptedBroadcastBlocks = new AtomicLong();
    private final AtomicLong rejectedBroadcastBlocks = new AtomicLong();
    private final AtomicLong peerSyncAttempts = new AtomicLong();
    private final AtomicLong peerSyncSuccesses = new AtomicLong();
    private final AtomicLong peerSyncAdoptions = new AtomicLong();
    private final AtomicLong transactionBroadcastAttempts = new AtomicLong();
    private final AtomicLong transactionBroadcastSuccesses = new AtomicLong();
    private final AtomicLong transactionBroadcastFailures = new AtomicLong();
    private final AtomicLong blockBroadcastAttempts = new AtomicLong();
    private final AtomicLong blockBroadcastSuccesses = new AtomicLong();
    private final AtomicLong blockBroadcastFailures = new AtomicLong();

    public void resetWindow() {
        validationRuns.set(0);
        minedBlocks.set(0);
        minedTransactions.set(0);
        miningNonceTotal.set(0);
        miningElapsedMsTotal.set(0);
        rejectedTransactions.set(0);
        acceptedBroadcastBlocks.set(0);
        rejectedBroadcastBlocks.set(0);
        peerSyncAttempts.set(0);
        peerSyncSuccesses.set(0);
        peerSyncAdoptions.set(0);
        transactionBroadcastAttempts.set(0);
        transactionBroadcastSuccesses.set(0);
        transactionBroadcastFailures.set(0);
        blockBroadcastAttempts.set(0);
        blockBroadcastSuccesses.set(0);
        blockBroadcastFailures.set(0);
    }

    public void recordValidation() {
        validationRuns.incrementAndGet();
    }

    public void recordMinedBlock(int transactionCount, int nonceCount, long elapsedMs) {
        minedBlocks.incrementAndGet();
        minedTransactions.addAndGet(transactionCount);
        miningNonceTotal.addAndGet(nonceCount);
        miningElapsedMsTotal.addAndGet(elapsedMs);
    }

    public void recordRejectedTransaction() {
        rejectedTransactions.incrementAndGet();
    }

    public void recordBroadcastBlockAccepted() {
        acceptedBroadcastBlocks.incrementAndGet();
    }

    public void recordBroadcastBlockRejected() {
        rejectedBroadcastBlocks.incrementAndGet();
    }

    public void recordPeerSync(boolean success, boolean adopted) {
        peerSyncAttempts.incrementAndGet();
        if (success) {
            peerSyncSuccesses.incrementAndGet();
        }
        if (adopted) {
            peerSyncAdoptions.incrementAndGet();
        }
    }

    public void recordBroadcast(String path, int peerCount, int successCount, int failureCount) {
        if (path.contains("/transactions/")) {
            transactionBroadcastAttempts.addAndGet(peerCount);
            transactionBroadcastSuccesses.addAndGet(successCount);
            transactionBroadcastFailures.addAndGet(failureCount);
            return;
        }
        if (path.contains("/blocks/")) {
            blockBroadcastAttempts.addAndGet(peerCount);
            blockBroadcastSuccesses.addAndGet(successCount);
            blockBroadcastFailures.addAndGet(failureCount);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(
                validationRuns.get(),
                minedBlocks.get(),
                minedTransactions.get(),
                miningNonceTotal.get(),
                miningElapsedMsTotal.get(),
                rejectedTransactions.get(),
                acceptedBroadcastBlocks.get(),
                rejectedBroadcastBlocks.get(),
                peerSyncAttempts.get(),
                peerSyncSuccesses.get(),
                peerSyncAdoptions.get(),
                transactionBroadcastAttempts.get(),
                transactionBroadcastSuccesses.get(),
                transactionBroadcastFailures.get(),
                blockBroadcastAttempts.get(),
                blockBroadcastSuccesses.get(),
                blockBroadcastFailures.get()
        );
    }

    public record Snapshot(
            long validationRuns,
            long minedBlocks,
            long minedTransactions,
            long miningNonceTotal,
            long miningElapsedMsTotal,
            long rejectedTransactions,
            long acceptedBroadcastBlocks,
            long rejectedBroadcastBlocks,
            long peerSyncAttempts,
            long peerSyncSuccesses,
            long peerSyncAdoptions,
            long transactionBroadcastAttempts,
            long transactionBroadcastSuccesses,
            long transactionBroadcastFailures,
            long blockBroadcastAttempts,
            long blockBroadcastSuccesses,
            long blockBroadcastFailures
    ) {
    }
}
