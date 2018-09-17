import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * @author Aaron Magi
 */
@Data
@Slf4j
public class AppiumService {
    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60 * ONE_SECOND;

    private int pid;
    private Integer port;
    private String logDirectory = "appium";
    private ProcessHandle processHandle;

    public AppiumService(Integer port) {
        this.port = port;
    }

    public Process startAppium() throws Exception {
        Process process;
        try {
            log.info("Device : Starting Appium server with port {}", port);

            String bootstrapPort = String.valueOf(port + 1000); // set the bootstrap port to be in the 46xxx range
            String selendroidPort = String.valueOf(port + 2000); // set the bootstrap port to be in the 47xxx range
            String chromePort = String.valueOf(port + 3000); // set the bootstrap port to be in the 48xxx range

            // create the log dir if needed
            File logsDir = new File(logDirectory);
            log.info("Folder for logs {}", logsDir.getAbsolutePath());
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // run appium directly
            ProcessBuilder processBuilder = new ProcessBuilder();

            // Used to troubleshoot environment issues
            //Map<String, String> env = processBuilder.environment();
            //for (String key : env.keySet())
            //    log.debug(key + ": " + env.get(key));

            Map<String, String> envs = processBuilder.environment();
            log.debug("Path {}",  envs.get("PATH"));
            envs.put("PATH", "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
            log.debug("PATH {}",  envs.get("PATH"));

            processBuilder.command("sh", "-c", "appium --log-timestamp --log-level debug --log " + new File(logsDir, "appium.txt").getAbsolutePath() + " -p " + port
                    + " -bp " + bootstrapPort +
                    " --selendroid-port " + selendroidPort +
                    " --chromedriver-port " + chromePort +
                    " --tmp " + new File(logsDir, "tmp-instruments.txt").getAbsolutePath());

            processBuilder.redirectError(new File(logsDir, "appiumError.txt"));
            processBuilder.redirectOutput(new File(logsDir, "appiumOutput.txt"));
            log.debug("process: " + processBuilder.directory());
            process = processBuilder.start();
            processHandle = process.toHandle();

            log.debug("Appium server started");
            log.debug("Appium server on port {}", port);

            if (PollingService.poll(this::pollAppiumServer, 2 * ONE_MINUTE, ONE_SECOND)) {
                log.debug("Appium Server is running and ready to accept requests");
            }

        } catch (Exception ex) {
            throw new Exception("Failed to start Appium server", ex);
        }
        return process;
    }

    /**
     * Checks is the appium server is running or not
     *
     * @return result it appium has started
     */
    private boolean pollAppiumServer() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(String.format("http://localhost:%s/wd/hub/status", port), String.class);
            return response
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (RestClientException e) {
            log.debug("Appium server not ready yet, port: {}", port);
            return false;
        }
    }

    /**
     * Shutdown the Appium server. This needs to be done before exiting the application so we are not left with node.exe running.
     */
    public void stopAppium() {
        log.info("Stopping Appium server...");
        if (processHandle == null) {
            log.error("Invalid processHandle for appium.  Did appium start?");
            return;
        }
        log.debug("Appium server stopping with PID " + processHandle.pid());

        if (!processHandle.isAlive()) {
            //if it's shutdown that is okay
            return;
        }
        Future<ProcessHandle> onExitFuture = processHandle.onExit();
        //Try to destroy it gracefully
        try {
            processHandle.destroy();
            onExitFuture.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.info("Failed to stop appium gracefully: {}", e.getMessage());
        }

        if (processHandle.isAlive()) {
            //Try to destroy it forcefully
            try {
                processHandle.destroyForcibly();
                onExitFuture.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                log.info("Failed to stop appium forcefully: {}", e.getMessage());
            }
        }

        if (!processHandle.isAlive()) {
            log.info("Appium server stopped");
        } else {
            throw new RuntimeException("Failed to stop Appium server");
        }
    }

}
