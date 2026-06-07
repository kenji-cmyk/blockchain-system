package com.kna.backend;

import com.kna.backend.entity.Block;
import com.kna.backend.entity.Transaction;
import com.kna.backend.entity.TransactionInput;
import com.kna.backend.entity.TransactionOutput;
import com.kna.backend.entity.Wallet;
import com.kna.backend.pkg.utils.StringUtil;
import com.kna.backend.service.BlockchainService;
import com.kna.backend.service.ChainPersistenceService;
import com.kna.backend.service.OperationalMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsensusPhase13Tests {

    private static final int DIFFICULTY = 0;
    private static final double MINING_REWARD = 10;
    private static final int MAX_TRANSACTIONS_PER_BLOCK = 5;

    @TempDir
    private Path tempDir;

    @Test
    void adjustableConsensusSettingsRejectReorgsAcrossFinalizedBlocks() {
        BlockchainService service = newService();
        service.updateConsensusSettings("longest-chain", 1);
        service.addBlock("local-a");
        service.addBlock("local-b");
        List<Block> localChain = service.getBlocks();

        List<Block> candidate = new ArrayList<>();
        candidate.add(localChain.getFirst());
        Block alternateOne = minedBlock(1, candidate.getLast().getHash(), "peer-a");
        candidate.add(alternateOne);
        Block alternateTwo = minedBlock(2, candidate.getLast().getHash(), "peer-b");
        candidate.add(alternateTwo);
        Block alternateThree = minedBlock(3, candidate.getLast().getHash(), "peer-c");
        candidate.add(alternateThree);

        boolean adopted = service.replaceChainIfStrongerAndValid(candidate);

        assertThat(adopted).isFalse();
        assertThat(service.getBlocks()).extracting(Block::getHash).containsExactlyElementsOf(
                localChain.stream().map(Block::getHash).toList()
        );
        assertThat(service.getConsensusSettings().policy()).isEqualTo("longest-chain");
        assertThat(service.getConsensusSettings().finalityDelayBlocks()).isEqualTo(1);
        assertThat(service.getConsensusBranches())
                .anySatisfy(branch -> {
                    assertThat(branch.status()).isEqualTo("rejected");
                    assertThat(branch.reason()).contains("finalized");
                    assertThat(branch.policy()).isEqualTo("longest-chain");
                    assertThat(branch.commonAncestorIndex()).isEqualTo(0);
                    assertThat(branch.tip().hash()).isEqualTo(alternateThree.getHash());
                });
    }

    @Test
    void remembersForkBranchMetadataWhenValidCandidateIsNotSelected() {
        BlockchainService service = newService();
        service.addBlock("local-a");
        Block genesis = service.getBlocks().getFirst();
        Block fork = minedBlock(1, genesis.getHash(), "fork-miner");

        boolean accepted = service.acceptBroadcastBlock(fork);

        assertThat(accepted).isFalse();
        assertThat(service.getForkBlocks())
                .singleElement()
                .satisfies(reference -> assertThat(reference.hash()).isEqualTo(fork.getHash()));
        assertThat(service.getConsensusBranches())
                .anySatisfy(branch -> {
                    assertThat(branch.status()).isEqualTo("fork");
                    assertThat(branch.reason()).contains("competing fork");
                    assertThat(branch.tip().hash()).isEqualTo(fork.getHash());
                });
    }

    @Test
    void reattachesOrphanBlocksAfterMissingParentArrives() {
        BlockchainService service = newService();
        Block genesis = service.getBlocks().getFirst();
        Block parent = minedBlock(1, genesis.getHash(), "parent-miner");
        Block child = minedBlock(2, parent.getHash(), "child-miner");

        assertThat(service.acceptBroadcastBlock(child)).isFalse();
        assertThat(service.getOrphanBlocks())
                .singleElement()
                .satisfies(reference -> assertThat(reference.hash()).isEqualTo(child.getHash()));

        assertThat(service.acceptBroadcastBlock(parent)).isTrue();

        assertThat(service.getBlocks()).extracting(Block::getHash)
                .containsExactly(genesis.getHash(), parent.getHash(), child.getHash());
        assertThat(service.getOrphanBlocks()).isEmpty();
        assertThat(service.getConsensusBranches())
                .anySatisfy(branch -> {
                    assertThat(branch.status()).isEqualTo("accepted");
                    assertThat(branch.reason()).contains("reattached");
                    assertThat(branch.tip().hash()).isEqualTo(child.getHash());
                });
    }

    @Test
    void deterministicSerializationPayloadsProtectCrossNodeCompatibility() {
        Transaction transaction = new Transaction(
                "sender-key",
                "receiver-key",
                1.23000000,
                0.10000000,
                List.of(new TransactionInput("previous-transaction", 0)),
                List.of(
                        new TransactionOutput("receiver-key", 1.23000000),
                        new TransactionOutput("sender-key", 0.10000000)
                )
        );
        Block block = new Block(1, List.of(transaction), "previous-block-hash");

        assertThat(transaction.getSigningPayload())
                .contains("amount=1.23\n")
                .contains("fee=0.1\n")
                .contains("inputs=1\nprevious-transaction:0\n")
                .contains("outputs=2\nreceiver-key:1.23\nsender-key:0.1\n");
        assertThat(block.getCanonicalPayload())
                .contains("transactions=1\n" + transaction.getTransactionId() + "\n");
        assertThat(block.calculateHash()).isEqualTo(StringUtil.applySha256(block.getCanonicalPayload()));
    }

    private BlockchainService newService() {
        OperationalMetricsService metricsService = new OperationalMetricsService();
        ChainPersistenceService persistenceService = new ChainPersistenceService(
                false,
                "file",
                tempDir.resolve("unused.json").toString(),
                new DefaultListableBeanFactory().getBeanProvider(JdbcTemplate.class)
        );
        return new BlockchainService(
                DIFFICULTY,
                MINING_REWARD,
                MAX_TRANSACTIONS_PER_BLOCK,
                "cumulative-difficulty",
                0,
                persistenceService,
                metricsService
        );
    }

    private Block minedBlock(int index, String previousHash, String receiver) {
        Block block = new Block(index, List.of(Transaction.miningReward(receiver, MINING_REWARD)), previousHash);
        block.mineBlock(DIFFICULTY);
        return block;
    }
}
