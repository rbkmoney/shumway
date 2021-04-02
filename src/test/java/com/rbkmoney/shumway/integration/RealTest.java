package com.rbkmoney.shumway.integration;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.rbkmoney.damsel.shumpune.Posting;
import com.rbkmoney.damsel.shumpune.PostingBatch;
import com.rbkmoney.damsel.shumpune.PostingPlan;
import com.rbkmoney.damsel.shumpune.PostingPlanChange;
import com.rbkmoney.shumway.ShumwayApplication;
import com.rbkmoney.shumway.domain.PostingOperation;
import com.rbkmoney.shumway.handler.ShumpuneServiceHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.sql.Types.OTHER;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShumwayApplication.class)
public class RealTest extends DaoTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ShumpuneServiceHandler handler;

    private List<Long> accounts;
    private List<PostingPlanChange> holds;
    private List<PostingPlanChange> commits;
    private List<PostingPlanChange> rollbacks;

    @Before
    public void readOperations() throws IOException {
        List<Map.Entry<PostingOperation, PostingPlanChange>> ops = new ArrayList<>();
        Scanner scanner = new Scanner(new ClassPathResource("data/postings.csv").getFile());
        scanner.nextLine(); // header
        while (scanner.hasNextLine()) {
            Map.Entry<PostingOperation, PostingPlanChange> e = parsePostingPlanInfo(scanner.nextLine());
            ops.add(e);
            System.out.println(e);
        }
        scanner.close();

        ops.stream()
                .flatMap(entry -> entry.getValue().getBatch().getPostings().stream())
                .forEach(this::createAccounts);

        accounts = ops.stream()
                .flatMap(entry -> entry.getValue().getBatch().getPostings().stream())
                .flatMap(posting -> Stream.of(posting.getToId(), posting.getFromId()))
                .collect(Collectors.toList());


        holds = ops.stream()
                .filter(entry -> entry.getKey().equals(PostingOperation.HOLD))
                .map(Map.Entry::getValue)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PostingPlanChange::getId, o -> o.getBatch().getPostings(),
                                (postings, postings2) -> Lists.newArrayList(Iterables.concat(postings, postings2))),
                        stringListMap -> stringListMap.entrySet().stream()
                                .map(o -> new PostingPlanChange(o.getKey(), new PostingBatch(1L, o.getValue())))
                                .collect(Collectors.toList())));


        commits = ops.stream()
                .filter(entry -> entry.getKey().equals(PostingOperation.COMMIT))
                .map(Map.Entry::getValue)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PostingPlanChange::getId, o -> o.getBatch().getPostings(),
                                (postings, postings2) -> Lists.newArrayList(Iterables.concat(postings, postings2))),
                        stringListMap -> stringListMap.entrySet().stream()
                                .map(o -> new PostingPlanChange(o.getKey(), new PostingBatch(1L, o.getValue())))
                                .collect(Collectors.toList())));

        rollbacks = ops.stream()
                .filter(entry -> entry.getKey().equals(PostingOperation.ROLLBACK))
                .map(Map.Entry::getValue)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PostingPlanChange::getId, o -> o.getBatch().getPostings(),
                                (postings, postings2) -> Lists.newArrayList(Iterables.concat(postings, postings2))),
                        stringListMap -> stringListMap.entrySet().stream()
                                .map(o -> new PostingPlanChange(o.getKey(), new PostingBatch(1L, o.getValue())))
                                .collect(Collectors.toList())));


    }

    @Test
    public void test() {
        holds.stream().parallel().forEach(hold -> {
            try {
                handler.hold(hold);
                log.warn("hold successful: {}", hold);
            } catch (TException e) {
                log.error("hold unsuccessful: {}", hold);
            }
        });
        commits.stream().parallel().forEach(commit -> {
            try {
                handler.commitPlan(new PostingPlan(commit.getId(), List.of(commit.getBatch())));
                log.warn("commit successful: {}", commit);
            } catch (TException e) {
                log.error("commit unsuccessful: {}", commit);
            }
        });
        rollbacks.stream().parallel().forEach(rollback -> {
            try {
                handler.rollbackPlan(new PostingPlan(rollback.getId(), List.of(rollback.getBatch())));
                log.warn("rollback successful: {}", rollback);
            } catch (TException e) {
                log.error("rollback unsuccessful: {}", rollback);
            }
        });
        //todo getPlan or getBalanceById после каждой операции?
    }

    private Map.Entry<PostingOperation, PostingPlanChange> parsePostingPlanInfo(String nextLine) {
        String[] strings = nextLine.split(",");
        String id = strings[0];
        String planId = strings[1];
        Long batchId = Long.parseLong(strings[2]);
        Long fromAccountId = Long.parseLong(strings[3]);
        Long toAccountId = Long.parseLong(strings[4]);
        String operation = strings[5];
        Long amount = Long.parseLong(strings[6]);
        String creationTime = strings[7];
        String currSymCode = strings[8];
        String description = "";
        if (strings.length == 10) {
            description = strings[9];
        }

        return Map.entry(
                PostingOperation.valueOf(operation),
                new PostingPlanChange(planId, new PostingBatch(batchId,
                        List.of(new Posting(fromAccountId, toAccountId, amount, currSymCode, description)))));
    }

    private void createAccounts(Posting posting) {
        jdbcTemplate.update("INSERT INTO shm.account(id, curr_sym_code, creation_time, description) " +
                        "VALUES (?,?,?,?) " +
                        "ON CONFLICT ON CONSTRAINT account_pkey DO NOTHING;",
                (ps) -> {
                    ps.setLong(1, posting.getFromId());
                    ps.setString(2, posting.getCurrencySymCode());
                    ps.setObject(3, Instant.now(), OTHER);
                    ps.setString(4, posting.getDescription());
                });
        jdbcTemplate.update("INSERT INTO shm.account(id, curr_sym_code, creation_time, description) " +
                        "VALUES (?,?,?,?) " +
                        "ON CONFLICT ON CONSTRAINT account_pkey DO NOTHING;",
                (ps) -> {
                    ps.setLong(1, posting.getToId());
                    ps.setString(2, posting.getCurrencySymCode());
                    ps.setObject(3, Instant.now(), OTHER);
                    ps.setString(4, posting.getDescription());
                });
    }
}
