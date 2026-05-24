package com.kna.backend.entity;

import com.kna.backend.pkg.utils.StringUtil;

import java.util.Date;

public class Block {

    public String hash;
    public String previousHash;
    private final String data;
    private final long timeStamp;
    private int nonce;

    public Block(String data, String previousHash) {
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
        String target = "0".repeat(difficulty);

        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();

            if (nonce % 100000 == 0) {
                System.out.println("Trying nonce: " + nonce + " hash: " + hash);
            }
        }

        System.out.println("Block Mined: " + hash);
    }
}
