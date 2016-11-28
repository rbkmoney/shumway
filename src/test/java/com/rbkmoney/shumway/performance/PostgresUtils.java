package com.rbkmoney.shumway.performance;

import lombok.Builder;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Builder
public class PostgresUtils {
    public static final String TEMPLATE = "TEMPLATE";
    public static final String DB_HOST = "DB_HOST";
    public static final String DB_PORT = "DB_PORT";
    public static final String DB_SUPERUSER = "DB_SUPERUSER";
    public static final String PGPASSWORD = "PGPASSWORD";
    public static final String DB_NAME = "DB_NAME";
    public static final String DUMP_PATH = "DUMP_PATH";
    public static final String SNAPSHOT_SUFFIX = "SNAPSHOT_SUFFIX";

    private String host;
    private Integer port;
    private String superUser;
    private String password;
    private String database;
    private String bashScriptPath;

    public static void main(String[] args) throws IOException {
        final String bashScriptPath = new ClassPathResource("db/utils.sh").getFile().getAbsolutePath();
        
        final PostgresUtils utils = PostgresUtils.builder()
                .host("localhost")
                .port(5432)
                .superUser("postgres")
                .password("postgres")
                .database("shumway")
                .bashScriptPath(bashScriptPath)
                .build();

        final String dumpPath = "/tmp/shumway.dump";
//        utils.dropDb();
//        utils.createDb();
        t("createDump", () -> utils.createDump(dumpPath));
        t("createSnapshot", () -> utils.createSnapshot());
        utils.dropDb();
        t("restoreDump", () -> utils.restoreDump(dumpPath));
        t("restoreSnapshot", () -> utils.restoreSnapshot());
        utils.dropSnapshot();

        try {
            FileUtils.deleteDirectory(new File(dumpPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDb() {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "create-db");
        runAndOutToStdout(envs);
    }

    public void createSnapshot(String snapshotSuffix) {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "create-snapshot");
        envs.put(SNAPSHOT_SUFFIX, snapshotSuffix);
        runAndOutToStdout(envs);
    }

    public void restoreSnapshot(String snapshotSuffix) {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "restore-snapshot");
        envs.put(SNAPSHOT_SUFFIX, snapshotSuffix);
        runAndOutToStdout(envs);
    }

    public void dropSnapshot(String snapshotSuffix) {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "drop-snapshot");
        envs.put(SNAPSHOT_SUFFIX, snapshotSuffix);
        runAndOutToStdout(envs);
    }

    public void createSnapshot() {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "create-snapshot");
        runAndOutToStdout(envs);
    }

    public void restoreSnapshot() {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "restore-snapshot");
        runAndOutToStdout(envs);
    }

    public void dropSnapshot() {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "drop-snapshot");
        runAndOutToStdout(envs);
    }


    public void dropDb() {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "drop-db");
        runAndOutToStdout(envs);
    }

    public void dropDb(String dbName) {
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "drop-db");
        envs.put(DB_NAME, dbName);
        runAndOutToStdout(envs);
    }

    public void createDump(String dumpPath){
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "create-dump");
        envs.put(DUMP_PATH, dumpPath);
        runAndOutToStdout(envs);
    }

    public void restoreDump(String dumpPath){
        Map<String, String> envs = getDefaultEnvs();
        envs.put(TEMPLATE, "restore-dump");
        envs.put(DUMP_PATH, dumpPath);
        runAndOutToStdout(envs);
    }
    public void runAndOutToStdout(Map<String, String> envs){
        run(bashScriptPath, envs, true);
    }

    public static void run(String bashCmd, Map<String, String> envs, boolean redirectStdouts) {
        try {
            ProcessBuilder pb = new ProcessBuilder(bashCmd).directory(null);
            if(redirectStdouts) {
                pb.inheritIO();
            }
            pb.environment().putAll(envs);
            Process p = pb.start();

            int exitCode = p.waitFor();
            if(exitCode != 0){
                throw new RuntimeException("Fail to execute script. Script exit code: " + exitCode );
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    // ! should return new Map every time
    public Map<String, String> getDefaultEnvs(){
        final Map<String, String> envs = new HashMap<>();
        if(host != null) envs.put(DB_HOST, host);
        if(port != null) envs.put(DB_PORT, "" + port);
        if(superUser != null) envs.put(DB_SUPERUSER, superUser);
        if(password != null) envs.put(PGPASSWORD, password);
        if(database != null) envs.put(DB_NAME, database);

        return envs;
    }

    private static void t(String preffix, Runnable function){
        long startTime = System.currentTimeMillis();
        function.run();
        System.out.println(preffix + ": " + (System.currentTimeMillis() - startTime) + "ms.");
    }
}
