import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final int ONE_SECOND = 1000;
    public static final int ONE_MINUTE = 60 * ONE_SECOND;

    public static final Path KEYCHAIN = Paths.get(System.getProperty("user.home"), "/Library/Keychains/miw-dev.keychain");
    public static final String KEYCHAIN_PASSWORD = "zN38bubQ";
    public static final Path WDA_PROJECT_PATH = Paths.get("/usr/local/lib/node_modules/appium/node_modules/appium-xcuitest-driver/WebDriverAgent");
    public static final String WDA_PROJECT_DIR = "WebDriverAgent.xcodeproj";
    public static final String ORIG_WDA_RUNNER_BUNDLE_ID = "com.facebook.WebDriverAgentRunner";
    public static final String WDA_RUNNER_BUNDLE_ID = "com.miw.WebDriverAgentRunner";
    public static final String PROJECT_FILE = "project.pbxproj";
}
