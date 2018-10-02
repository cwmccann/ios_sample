import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class WdaServer {
    private final String uuid;
    private final int port;

    private Process xcodebuildProcess;
    private Process iproxyProcess;

    public WdaServer(String uuid, int port) {
        this.uuid = uuid;
        this.port = port;

        installKeys();
        updateBundleId();
    }

    /**
     * make sure the keys are installed into the key chain
     */
    void installKeys() {
        try {
            List<String> command = new ArrayList<>();
            File script = new ClassPathResource("scripts/import_keychain.sh").getFile();
            command.add(script.getAbsolutePath());


            //Set the keychain
            final String keychainPath = Constants.KEYCHAIN.toAbsolutePath().toString();
            command.add("-f");
            command.add(keychainPath);


            //Find all keys and certificates and set them for the script to consume
            ClassLoader cl = this.getClass().getClassLoader();
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
            Resource[] resources = resolver.getResources("classpath*:/codesign/*.*");
            for (Resource resource : resources) {
                String key = resource.getFile().getAbsolutePath();
                command.add(key);
            }

            ProcessResult result = new ProcessExecutor()
                    .command(command)
                    .environment("KEYPASS", Constants.KEYCHAIN_PASSWORD)
                    .readOutput(true)
                    .execute();

            if (result.getExitValue() != 0) {
                log.error("Error creating keychain: {}: {}", keychainPath, result.getOutput().getUTF8());
            } else {
                log.info("Keychain created at {}", keychainPath);
            }
        } catch (IOException | InterruptedException | TimeoutException e) {
            log.error("Error while running import keys script", e);
        }
    }

    //Gets the url to connect to
    public String getServerUrl() {
        return String.format("http://127.0.0.1:%d", port);
        //return "http://192.168.1.78:8100";
    }

    /**
     * Updates the bundle id in the wda project file
     */
    void updateBundleId() {
        Path projectFile = Constants.WDA_PROJECT_PATH
                .resolve(Constants.WDA_PROJECT_DIR)
                .resolve(Constants.PROJECT_FILE);

        Path backupProjectFile = Constants.WDA_PROJECT_PATH
                .resolve(Constants.WDA_PROJECT_DIR)
                .resolve(Constants.PROJECT_FILE + ".old");

        try {
            //Backup bundle id
            if (!Files.exists(backupProjectFile)) {
                Files.copy(projectFile, backupProjectFile);
                log.debug("Backed up wda project file");

                //Replace the bundle Id
                Stream<String> lines = Files.lines(projectFile);
                List<String> replaced = lines.map(line -> line.replaceAll(
                        Constants.ORIG_WDA_RUNNER_BUNDLE_ID.replace(".", "\\."),
                        Constants.WDA_RUNNER_BUNDLE_ID.replace(".", "\\.")))
                        .collect(Collectors.toList());
                Files.write(projectFile, replaced);
                lines.close();
                log.info("Replaced the bundle id to be {}" + Constants.WDA_RUNNER_BUNDLE_ID);
            }
        } catch (IOException e) {
            log.error("Error updating bundle id in project file.  WDA server may not work.", e);
        }
    }

    /**
     * starts up the wda server
     */
    public void start() {
        if (xcodebuildProcess != null && xcodebuildProcess.isAlive()) {
            throw new RuntimeException("Server already started");
        }
        if (iproxyProcess != null && iproxyProcess.isAlive()) {
            throw new RuntimeException("Server already started");
        }

        //Unlock the keychain
        //security -v unlock-keychain -p $keychainPassword $keychain
        try {
            final String keychainPath = Constants.KEYCHAIN.toAbsolutePath().toString();
            ProcessResult result = new ProcessExecutor()
                    .command("security", "-v", "unlock-keychain", "-p", Constants.KEYCHAIN_PASSWORD, keychainPath)
                    .readOutput(true)
                    .execute();
            if (result.getExitValue() != 0) {
                log.error("Error unlocking keychain: {}: {}", keychainPath, result.getOutput().getUTF8());
            } else {
                log.info("Keychain unlocked");
            }
        } catch (IOException | InterruptedException | TimeoutException e) {
            log.error("Error Creating keychain: " + e.getMessage());
        }

        //Make sure the directories exist
        try {
            Files.createDirectories(Constants.WDA_PROJECT_PATH.resolve("Resources/WebDriverAgent.bundle"));

            //Run the bootstrap script
            new ProcessExecutor()
                .command(Constants.WDA_PROJECT_PATH.resolve("Scripts/bootstrap.sh").toAbsolutePath().toString(), "-d")
                .directory(Constants.WDA_PROJECT_PATH.toFile())
                .readOutput(true)
                .execute();

            //Start up xcodebuild
            String xcconfig = getClass().getClassLoader().getResource("miw.xcconfig").getFile();
            Slf4jStream xdaStream = Slf4jStream.of(LoggerFactory.getLogger(getClass().getName() + ".xcodebuild"));

            xcodebuildProcess = new ProcessExecutor()
                    .command("xcodebuild", "build-for-testing", "test-without-building",
                            "-project",  Constants.WDA_PROJECT_DIR,
                            "-scheme", "WebDriverAgentRunner",
                            "-destination", "id=" + uuid,
                            "IPHONEOS_DEPLOYMENT_TARGET=11.2",
                            "-allowProvisioningUpdates",
                            "-xcconfig", xcconfig)
                    .directory(Constants.WDA_PROJECT_PATH.toFile())
                    .redirectOutput(xdaStream.asInfo())
                    .redirectError(xdaStream.asError())
                    .start()
                    .getProcess();

            Slf4jStream iproxyStream = Slf4jStream.of(LoggerFactory.getLogger(getClass().getName() + ".iproxy"));
            iproxyProcess = new ProcessExecutor()
                    .command("iproxy", String.valueOf(port), "8100", uuid)
                    .redirectOutput(iproxyStream.asInfo())
                    .redirectError(iproxyStream.asError())
                    .start()
                    .getProcess();

            if (PollingService.poll(this::pollWdaServer, 2 * Constants.ONE_MINUTE, Constants.ONE_SECOND)) {
                log.debug("Wda Server is running and ready to accept requests");
            }

        } catch (IOException | InterruptedException | TimeoutException e) {
            log.error("Error starting WdaServer");
        }
    }

    /**
     * Stops the wda server
     */
    public void stop() {
        if (xcodebuildProcess != null && xcodebuildProcess.isAlive()) {
            xcodebuildProcess.destroy();
        }
        if (iproxyProcess != null && iproxyProcess.isAlive()) {
            iproxyProcess.destroy();
        }
    }

    /**
     * Checks is the wda server is running or not
     *
     * @return result it wda has started
     */
    private boolean pollWdaServer() {
        if (xcodebuildProcess == null) {
            throw new RuntimeException("xcodebuild process is null");
        }
        if (!xcodebuildProcess.isAlive()) {
            throw new RuntimeException("xcodebuild failed with exit code: " + xcodebuildProcess.exitValue());
        }
        if (iproxyProcess == null) {
            throw new RuntimeException("iproxy process is null");
        }
        if (!iproxyProcess.isAlive()) {
            throw new RuntimeException("iproxy failed with exit code: " + iproxyProcess.exitValue());
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(getServerUrl() + "/status", String.class);
            return response
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (RestClientException e) {
            log.debug("WDA server not ready yet, port: {}", port);
            return false;
        }
    }
}
