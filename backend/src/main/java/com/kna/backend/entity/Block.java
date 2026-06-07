package com.kna.backend.entity;

import com.kna.backend.pkg.utils.StringUtil;

import java.util.Date;
import java.util.List;

public class Block {

    private final int index;
    private String hash;
    private final String previousHash;
    private final List<Transaction> transactions;
    private final long timeStamp;
    private int nonce;
    private String tamperMarker = "";

    public Block(int index, List<Transaction> transactions, String previousHash) {
        this.index = index;
        this.transactions = List.copyOf(transactions);
        this.previousHash = previousHash;
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
    }

    public String calculateHash() {
        return StringUtil.applySha256(getCanonicalPayload());
    }

    public String getCanonicalPayload() {
        StringBuilder payload = new StringBuilder();
        payload.append("index=").append(index).append('\n');
        payload.append("previousHash=").append(previousHash).append('\n');
        payload.append("timeStamp=").append(timeStamp).append('\n');
        payload.append("nonce=").append(nonce).append('\n');
        payload.append("tamperMarker=").append(tamperMarker).append('\n');
        payload.append("transactions=").append(transactions.size()).append('\n');
        for (Transaction transaction : transactions) {
            payload.append(transaction.getTransactionId()).append('\n');
        }
        return payload.toString();
    }

    public void mineBlock(int difficulty) {
        if (difficulty < 0) {
            throw new IllegalArgumentException("Difficulty must be greater than or equal to 0");
        }

        String target = "0".repeat(difficulty);
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    public void tamperData(String data) {
        this.tamperMarker = data;
    }

    public int getIndex() {
        return index;
    }

    public String getHash() {
        return hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public int getNonce() {
        return nonce;
    }

    public String getTamperMarker() {
        return tamperMarker;
    }
}
