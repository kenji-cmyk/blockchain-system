package com.kna.backend.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kna.backend.entity.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class ChainPersistenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChainPersistenceService.class);
    private static final Type BLOCK_LIST_TYPE = new TypeToken<List<Block>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final boolean enabled;
    private final String type;
    private final Path filePath;
    private final JdbcTemplate jdbcTemplate;

    public ChainPersistenceService(
            @Value("${blockchain.persistence.enabled:false}") boolean enabled,
            @Value("${blockchain.persistence.type:file}") String type,
            @Value("${blockchain.persistence.file:data/blockchain-chain.json}") String filePath,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider
    ) {
        this.enabled = enabled;
        this.type = type;
        this.filePath = Path.of(filePath);
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public Optional<List<Block>> loadChain() {
        if (!enabled) {
            return Optional.empty();
        }

        if ("database".equalsIgnoreCase(type)) {
            return loadFromDatabase();
        }

        return loadFromFile();
    }

    public void saveChain(List<Block> chain) {
        if (!enabled) {
            return;
        }

        if ("database".equalsIgnoreCase(type)) {
            saveToDatabase(chain);
            return;
        }

        saveToFile(chain);
    }

    private Optional<List<Block>> loadFromFile() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            List<Block> chain = gson.fromJson(reader, BLOCK_LIST_TYPE);
            if (chain == null || chain.isEmpty()) {
                return Optional.empty();
            }
            LOGGER.info("Loaded persisted blockchain from {}", filePath);
            return Optional.of(chain);
        } catch (Exception exception) {
            LOGGER.warn("Could not load persisted blockchain from {}", filePath, exception);
            return Optional.empty();
        }
    }

    private void saveToFile(List<Block> chain) {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(chain, BLOCK_LIST_TYPE, writer);
            }
            LOGGER.info("Saved blockchain to {}", filePath);
        } catch (Exception exception) {
            LOGGER.warn("Could not save blockchain to {}", filePath, exception);
        }
    }

    private Optional<List<Block>> loadFromDatabase() {
        if (jdbcTemplate == null) {
            LOGGER.warn("Database persistence requested, but JdbcTemplate is not available");
            return Optional.empty();
        }

        try {
            ensureSchema();
            List<String> rows = jdbcTemplate.query(
                    "select state_json from blockchain_state where state_key = ?",
                    (resultSet, rowNumber) -> resultSet.getString("state_json"),
                    "chain"
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }

            List<Block> chain = gson.fromJson(rows.getFirst(), BLOCK_LIST_TYPE);
            if (chain == null || chain.isEmpty()) {
                return Optional.empty();
            }
            LOGGER.info("Loaded persisted blockchain from database");
            return Optional.of(chain);
        } catch (Exception exception) {
            LOGGER.warn("Could not load persisted blockchain from database", exception);
            return Optional.empty();
        }
    }

    private void saveToDatabase(List<Block> chain) {
        if (jdbcTemplate == null) {
            LOGGER.warn("Database persistence requested, but JdbcTemplate is not available");
            return;
        }

        try {
            ensureSchema();
            String json = gson.toJson(chain, BLOCK_LIST_TYPE);
            int updated = jdbcTemplate.update(
                    "update blockchain_state set state_json = ? where state_key = ?",
                    json,
                    "chain"
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        "insert into blockchain_state (state_key, state_json) values (?, ?)",
                        "chain",
                        json
                );
            }
            LOGGER.info("Saved blockchain to database");
        } catch (Exception exception) {
            LOGGER.warn("Could not save blockchain to database", exception);
        }
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                create table if not exists blockchain_state (
                    state_key varchar(64) primary key,
                    state_json clob not null
                )
                """);
    }
}
