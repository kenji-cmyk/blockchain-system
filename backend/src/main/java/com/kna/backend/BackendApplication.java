package com.kna.backend;

import com.google.gson.GsonBuilder;
import com.kna.backend.entity.Block;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

import static com.kna.backend.pkg.validate.Validator.isChainValid;

@SpringBootApplication
public class BackendApplication {

    public static ArrayList<Block> blockchain = new ArrayList<>();

    static void main() {

        blockchain.add(new Block("Hi im the first block", "0"));
        System.out.println("Trying to Mine block 1... ");
        int difficulty = 1;
        blockchain.getFirst().mineBlock(difficulty);

        blockchain.add(new Block("Yo im the second block", blockchain.getLast().hash));
        System.out.println("Trying to Mine block 2... ");
        blockchain.get(1).mineBlock(difficulty);

        blockchain.add(new Block("Hey im the third block", blockchain.getLast().hash));
        System.out.println("Trying to Mine block 3... ");
        blockchain.get(2).mineBlock(difficulty);

        System.out.println("\nBlockchain is Valid: " + isChainValid(blockchain, difficulty));

        String blockchainJson = new GsonBuilder().setPrettyPrinting().create().toJson(blockchain);
        System.out.println("\nThe block chain: ");
        System.out.println(blockchainJson);
    }

}
