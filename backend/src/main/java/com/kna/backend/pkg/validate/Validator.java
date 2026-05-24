package com.kna.backend.pkg.validate;

import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Validator {

    public static boolean isChainValid(List<Block> blockChain, int difficulty) {
        return isChainValid(blockChain, difficulty, Integer.MAX_VALUE);
    }

    public static boolean isChainValid(List<Block> blockChain, int difficulty, int maxTransactionsPerBlock) {
        return isChainValid(blockChain, difficulty, maxTransactionsPerBlock, Double.NaN);
    }

    public static boolean isChainValid(
            List<Block> blockChain,
            int difficulty,
            int maxTransactionsPerBlock,
            double miningReward
    ) {
        String hashTarget = "0".repeat(difficulty);
        Map<String, Double> balances = new HashMap<>();
        Set<String> transactionIds = new HashSet<>();

        for (int i = 0; i < blockChain.size(); ++i) {
            Block currentBlock = blockChain.get(i);
            List<Transaction> transactions = currentBlock.getTransactions();

            if (transactions.isEmpty() || transactions.size() > maxTransactionsPerBlock) {
                return false;
            }

            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                return false;
            }

            if (!currentBlock.getHash().startsWith(hashTarget)) {
                return false;
            }

            String expectedPreviousHash = i == 0 ? "0" : blockChain.get(i - 1).getHash();
            if (!expectedPreviousHash.equals(currentBlock.getPreviousHash())) {
                return false;
            }

            if (transactions.stream().anyMatch(transaction -> !transaction.isValid())) {
                return false;
            }

            if (!validateAndApplyTransactions(transactions, balances, transactionIds, miningReward)) {
                return false;
            }
        }

        return true;
    }

    public static long cumulativeDifficulty(List<Block> blockChain) {
        return blockChain.stream()
                .mapToLong(block -> Math.max(1L, 1L << Math.min(countLeadingZeros(block.getHash()), 30)))
                .sum();
    }

    public static int countLeadingZeros(String hash) {
        int count = 0;
        while (count < hash.length() && hash.charAt(count) == '0') {
            count++;
        }
        return count;
    }

    private static boolean validateAndApplyTransactions(
            List<Transaction> transactions,
            Map<String, Double> balances,
            Set<String> transactionIds,
            double miningReward
    ) {
        double fees = 0;
        int rewardCount = 0;
        double rewardAmount = 0;

        for (Transaction transaction : transactions) {
            if (!transactionIds.add(transaction.getTransactionId())) {
                return false;
            }

            if (transaction.isMiningReward()) {
                rewardCount++;
                rewardAmount += transaction.getAmount();
                continue;
            }

            double totalCost = transaction.getAmount() + transaction.getFee();
            double senderBalance = balances.getOrDefault(transaction.getSender(), 0.0);
            if (senderBalance < totalCost) {
                return false;
            }
            balances.put(transaction.getSender(), senderBalance - totalCost);
            balances.merge(transaction.getReceiver(), transaction.getAmount(), Double::sum);
            fees += transaction.getFee();
        }

        if (rewardCount > 1) {
            return false;
        }
        if (!Double.isNaN(miningReward) && rewardCount == 1 && rewardAmount > miningReward + fees) {
            return false;
        }

        for (Transaction transaction : transactions) {
            if (transaction.isMiningReward()) {
                balances.merge(transaction.getReceiver(), transaction.getAmount(), Double::sum);
            }
        }

        return true;
    }
}
