///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS ch.qos.reload4j:reload4j:1.2.19

//DEPS org.eclipse.jgit:org.eclipse.jgit:7.5.0.202512021534-r

//DEPS org.slf4j:slf4j-api:2.0.7
//DEPS org.slf4j:slf4j-simple:2.0.7

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class gittest {
    private static final Logger log = LoggerFactory.getLogger(gittest.class);

    private static final String[] BRANCHES_TO_CLONE = { "refs/heads/spring-ai", "refs/heads/main" };
    private static final java.io.File REPO_DIR = new java.io.File("./spring-petclinic");

    public static void main(String... args) {
        try {
            Git git;

            if (REPO_DIR.exists()) {
                log.info("Repository already exists, opening...");
                git = Git.open(REPO_DIR);
            } else {
                log.info("Cloning repository with shallow history (depth=1)...");
                git = Git.cloneRepository()
                        .setURI("https://github.com/spring-projects/spring-petclinic.git")
                        .setDirectory(REPO_DIR)
                        .setBranchesToClone(java.util.Arrays.asList(BRANCHES_TO_CLONE))
                        .setBranch("refs/heads/main")
                        .setDepth(1)
                        .call();
                log.info("Repository cloned successfully!");

                // Create local tracking branches for all cloned branches
                for (String branchRef : BRANCHES_TO_CLONE) {
                    String branchName = branchRef.replace("refs/heads/", "");
                    boolean exists = git.branchList().call().stream()
                            .anyMatch(r -> r.getName().equals("refs/heads/" + branchName));
                    if (!exists) {
                        git.branchCreate()
                                .setName(branchName)
                                .setStartPoint("origin/" + branchName)
                                .setUpstreamMode(
                                        org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK)
                                .call();
                        log.info("Created local tracking branch: {}", branchName);
                    }
                }
            }

            // Switch branch if provided as argument: jbang gittest.java <branch>
            if (args.length > 0) {
                checkoutBranch(git, args[0]);
            }

            // List available local branches
            log.info("Available local branches:");
            git.branchList().call()
                    .forEach(r -> log.info("  {}", r.getName().replace("refs/heads/", "")));

            // Print current branch
            String currentBranch = git.getRepository().getBranch();
            log.info("Current branch: {}", currentBranch);

            // From cloned repo, get all yaml files and print their content
            java.nio.file.Files.walk(REPO_DIR.toPath())
                    .filter(p -> p.toString().endsWith(".yaml"))
                    .forEach(p -> {
                        try {
                            log.info("File: {}", p);
                            log.info("Content: {}", new String(java.nio.file.Files.readAllBytes(p)));
                        } catch (java.io.IOException e) {
                            log.error("Error reading file: {}", p, e);
                        }
                    });
        } catch (Exception e) {
            log.error("Error occurred", e);
        }

        pullLatestChanges();
    }

    public static void checkoutBranch(Git git, String branchName) {
        try {
            log.info("Switching to branch: {}", branchName);
            git.checkout().setName(branchName).call();
            log.info("Switched to branch: {}", branchName);
        } catch (Exception e) {
            log.error("Error switching to branch: {}", branchName, e);
        }
    }

    public static void pullLatestChanges() {
        try {
            Git git = Git.open(REPO_DIR);
            for (String branch : BRANCHES_TO_CLONE) {
                String branchName = branch.replace("refs/heads/", "");
                log.info("Pulling latest changes from branch: {}", branchName);
                git.checkout().setName(branchName).call();
                git.pull().call();
                log.info("Pulled latest changes from branch: {}", branchName);
            }
        } catch (Exception e) {
            log.error("Error occurred while pulling latest changes", e);
        }
    }
}
