import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
public class IosHelperTest {

    @Test
    public void testGetDevices() {
        IosHelper.getConnectedDevices()
                .forEach(d -> log.debug("Found device: {}", d));
    }
}