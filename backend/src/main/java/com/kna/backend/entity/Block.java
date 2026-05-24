package com.kna.backend.entity;

import com.kna.backend.pkg.utils.StringUtil;

import java.util.Date;

public class Block {

    private final int index;
    private String hash;
    private final String previousHash;
    private String data;
    private final long timeStamp;
    private int nonce;

    public Block(int index, String data, String previousHash) {
        this.index = index;
        this.data = data;
        this.previousHash = previousHash;
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
    }

    public String calculateHash() {
        return StringUtil.applySha256(
                previousHash
                        + timeStamp
                        + nonce
                        + data
        );
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
        this.data = data;
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

    public String getData() {
        return data;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public int getNonce() {
        return nonce;
    }
}
