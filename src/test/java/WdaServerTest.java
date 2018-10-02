import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

import java.io.IOException;

@Slf4j
public class WdaServerTest {

    @Test
    public void testInstallKeys() throws IOException {
        WdaServer wdaServer = new WdaServer("foo", 8100);
        wdaServer.installKeys();
    }

    @Test
    public void testStart() {
        WdaServer wdaServer = new WdaServer("656d6ef9da2ccb39d34b00fe7e0c23fe7ec91278", 8100);
        try {
            wdaServer.start();
        } finally {
            wdaServer.stop();
        }
    }
}
