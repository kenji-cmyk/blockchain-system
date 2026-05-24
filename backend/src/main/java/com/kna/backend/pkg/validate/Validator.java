package com.kna.backend.pkg.validate;

import com.kna.backend.entity.Block;

import java.util.ArrayList;

public class Validator {

    public static Boolean isChainValid(ArrayList<Block> blockChain, int difficulty){
        Block currentBlock;
        Block previousBlock;
        String hashTarget = new String(new char[difficulty]).replace('\0', '0');

        for (int i = 1; i < blockChain.size(); ++i){

            currentBlock = blockChain.get(i);

            previousBlock = blockChain.get(i - 1);

            if(!currentBlock.hash.equals(currentBlock.calculateHash())){
                System.out.println("Current Hash not equals");
                return false;
            }

            if(!previousBlock.hash.equals(currentBlock.previousHash) ) {
                System.out.println("Previous Hashes not equal");
                return false;
            }

            if(!currentBlock.hash.substring( 0, difficulty).equals(hashTarget)) {
                System.out.println("This block hasn't been mined");
                return false;
            }

        }

        return true;
    }
}
