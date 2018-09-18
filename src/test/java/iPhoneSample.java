import io.appium.java_client.ios.IOSDriver;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.AssertJUnit.fail;

@Slf4j
public class iPhoneSample {
    private final int appiumPort = 7500;
    private final AppiumService appiumService = new AppiumService(appiumPort);

    /**
     * This method will provide data to any test method that declares that its Data Provider is named "phones".
     * provides unique port numbers for appium, wda local port, wda proxy port that needs to be unique per phone.
     */
    @DataProvider(name = "phones", parallel = true)
    public Iterator<Object[]> createData() {
        log.info("createData called");
        final AtomicInteger wdaLocalPort = new AtomicInteger(8100);
        final AtomicInteger webkitDebugProxyPort = new AtomicInteger(27335);

        List<Object[]> data = IosHelper.getConnectedDevices().stream()
                .map(d -> new Object[] {d, appiumPort, wdaLocalPort.getAndIncrement(), webkitDebugProxyPort.getAndIncrement()})
                .collect(Collectors.toList());

        return data.iterator();
    }

    @BeforeClass
    public void startAppium() throws Exception {
        log.debug("Starting test appium port {}", appiumPort);
        appiumService.startAppium();
    }

    @AfterClass
    public void stopAppium() {
        appiumService.stopAppium();
    }



    @Test(dataProvider = "phones")
    public void testWebDriver(IosHelper.IosDevice device, Integer appiumPort, Integer wdaLocalPort, Integer webkitDebugProxyPort) throws Exception {
        log.debug("Killing all services related to {}", device.getUuid());
        ProcessResult result = new ProcessExecutor()
                .command("pkill", "-f", device.getUuid())
                .readOutput(true)
                .execute();

        String xcconfig = iPhoneSample.class.getClassLoader().getResource("miw.xcconfig").getFile();
        log.debug("xcconfig: {}", xcconfig);
        try {
            DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setCapability("platformName", "iOS");
            capabilities.setCapability("deviceName", device.getName());
            capabilities.setCapability("platformVersion", "11.2.5");
            capabilities.setCapability("udid", device.getUuid());
            capabilities.setCapability("browserName", "Safari");
            capabilities.setCapability("xcodeOrgId", "TJK2DM8785");
            capabilities.setCapability("xcodeSigningId", "iPhone Developer");
            capabilities.setCapability("automationName", "XCUITest");

            //ios-webkit-debug-proxy http://appium.io/docs/en/writing-running-appium/web/ios-webkit-debug-proxy/
            //NOTE: the proxy requires the "web inspector" to be turned on to allow a connection to be established. Turn it on by going to settings > safari > advanced.
            capabilities.setCapability("startIWDP", true);

            capabilities.setCapability("wdaLocalPort", wdaLocalPort);
            capabilities.setCapability("webkitDebugProxyPort", webkitDebugProxyPort);

            //build a new web driver agent each time
            //capabilities.setCapability("useNewWDA", true);

            //use a prebuilt web driver agent
            //capabilities.setCapability("usePrebuiltWDA", true);

            //show the output of xcode in the appium logs
            capabilities.setCapability("showXcodeLog", true);

            //id of the web driver agent bundle, set in xcode project
            capabilities.setCapability("updatedWDABundleId", "com.miw.WebDriverAgentRunner");

            // This config file manages the code signing for WebDriverAgent
            capabilities.setCapability("xcodeConfigFile", xcconfig);


            log.debug("{} - Going to open driver", device.getUuid());
            WebDriver driver = new IOSDriver<>(new URL("http://127.0.0.1:" + appiumPort + "/wd/hub"), capabilities);

            log.debug("{} - Open google", device.getUuid());
            driver.get("http://www.google.com");
            //Give it a bit of time to get there
            Thread.sleep(1000);
            log.debug("{} - Current URL: {}", device.getUuid(), driver.getCurrentUrl());

            assertThat(driver.getCurrentUrl()).startsWith("https://www.google.com/");
            driver.quit();

        } catch (Exception e) {
            log.error(device.getUuid() + " - Failed: ", e);
            fail(device.getUuid() + " - Failed:");
        }
        log.debug("{} - Finished test", device.getUuid());
    }


}
