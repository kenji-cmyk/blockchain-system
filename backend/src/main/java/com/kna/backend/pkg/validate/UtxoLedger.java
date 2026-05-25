package com.kna.backend.pkg.validate;

import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.TransactionInput;
import com.kna.backend.entity.TransactionOutput;
import com.kna.backend.entity.UtxoEntry;
import com.kna.backend.entity.UtxoKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UtxoLedger {

    private static final double AMOUNT_TOLERANCE = 0.00000001;

    private UtxoLedger() {
    }

    public static Map<UtxoKey, UtxoEntry> replay(List<Block> blockChain, double miningReward) {
        Map<UtxoKey, UtxoEntry> utxos = new LinkedHashMap<>();
        Set<String> transactionIds = new HashSet<>();
        for (Block block : blockChain) {
            if (!applyBlock(block.getTransactions(), utxos, transactionIds, miningReward)) {
                throw new IllegalArgumentException("Chain contains invalid UTXO transitions");
            }
        }
        return Map.copyOf(utxos);
    }

    public static boolean applyBlock(
            List<Transaction> transactions,
            Map<UtxoKey, UtxoEntry> utxos,
            Set<String> transactionIds,
            double miningReward
    ) {
        double fees = 0;
        Transaction rewardTransaction = null;

        for (Transaction transaction : transactions) {
            if (!transactionIds.add(transaction.getTransactionId())) {
                return false;
            }

            if (transaction.isMiningReward()) {
                if (rewardTransaction != null) {
                    return false;
                }
                rewardTransaction = transaction;
                continue;
            }

            double fee = applySpend(transaction, utxos);
            if (fee < 0 || !amountsEqual(fee, transaction.getFee())) {
                return false;
            }
            fees += fee;
        }

        if (rewardTransaction != null) {
            if (!Double.isNaN(miningReward) && rewardTransaction.getAmount() > miningReward + fees + AMOUNT_TOLERANCE) {
                return false;
            }
            if (!addOutputs(rewardTransaction, utxos)) {
                return false;
            }
        }

        return true;
    }

    public static boolean applyPendingTransaction(Transaction transaction, Map<UtxoKey, UtxoEntry> utxos) {
        double fee = applySpend(transaction, utxos);
        return fee >= 0 && amountsEqual(fee, transaction.getFee());
    }

    public static double balanceOf(Map<UtxoKey, UtxoEntry> utxos, String address) {
        return utxos.values()
                .stream()
                .filter(entry -> address.equals(entry.owner()))
                .mapToDouble(UtxoEntry::amount)
                .sum();
    }

    public static Map<UtxoKey, UtxoEntry> mutableCopy(Map<UtxoKey, UtxoEntry> source) {
        return new LinkedHashMap<>(source);
    }

    public static double outputTotal(List<TransactionOutput> outputs) {
        return outputs.stream().mapToDouble(TransactionOutput::amount).sum();
    }

    private static double applySpend(Transaction transaction, Map<UtxoKey, UtxoEntry> utxos) {
        if (!transaction.isValid() || transaction.getInputs().isEmpty() || !outputsAreValid(transaction.getOutputs())) {
            return -1;
        }

        Set<UtxoKey> seenInputs = new HashSet<>();
        Map<UtxoKey, UtxoEntry> selectedEntries = new HashMap<>();
        for (TransactionInput input : transaction.getInputs()) {
            UtxoKey key = new UtxoKey(input.transactionId(), input.outputIndex());
            if (!seenInputs.add(key)) {
                return -1;
            }
            UtxoEntry entry = utxos.get(key);
            if (entry == null || !transaction.getSender().equals(entry.owner())) {
                return -1;
            }
            selectedEntries.put(key, entry);
        }

        double inputTotal = selectedEntries.values().stream().mapToDouble(UtxoEntry::amount).sum();
        double outputTotal = outputTotal(transaction.getOutputs());
        if (inputTotal + AMOUNT_TOLERANCE < outputTotal) {
            return -1;
        }

        selectedEntries.keySet().forEach(utxos::remove);
        if (!addOutputs(transaction, utxos)) {
            return -1;
        }
        return inputTotal - outputTotal;
    }

    private static boolean addOutputs(Transaction transaction, Map<UtxoKey, UtxoEntry> utxos) {
        List<TransactionOutput> outputs = transaction.getOutputs();
        if (!outputsAreValid(outputs)) {
            return false;
        }
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            UtxoKey key = new UtxoKey(transaction.getTransactionId(), i);
            if (utxos.containsKey(key)) {
                return false;
            }
            utxos.put(key, new UtxoEntry(transaction.getTransactionId(), i, output.receiver(), output.amount()));
        }
        return true;
    }

    private static boolean outputsAreValid(List<TransactionOutput> outputs) {
        return !outputs.isEmpty() && outputs.stream().allMatch(output ->
                output.receiver() != null
                        && !output.receiver().isBlank()
                        && Double.isFinite(output.amount())
                        && output.amount() > 0
        );
    }

    private static boolean amountsEqual(double left, double right) {
        return Math.abs(left - right) < AMOUNT_TOLERANCE;
    }
}
