import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class IosHelper {
    private static Pattern deviceParsingPattern = Pattern.compile("((.{0,})\\((.{0,})\\)\\s{0,}\\s\\[(.{0,})])|(((.{0,})\\((.{0,})\\))(\\s-*\\s*(.*)))|((\\w+ \\w+ -)(\\s-*\\s*(.*)))|((\\w*\\s\\-)(.*))");

    @SneakyThrows
    public static List<IosDevice> getConnectedDevices() {
        final List<IosDevice> devices = new LinkedList<>();
        final String command =  "instruments -s device";

        ProcessResult result = new ProcessExecutor()
                .command(command.split("\\s"))
                .readOutput(true)
                .execute();

        if (result.getExitValue() != 0) {
            log.error("Error running {}: exitCode: {} error: {}", command, result.getExitValue(), result.outputUTF8());
        }

        String[] lines = result.outputUTF8().split("\\r?\\n");
        for (String line : lines) {
            Matcher matcher = deviceParsingPattern.matcher(line);
            String UUID;
            if (matcher.find()) {
                UUID = matcher.group(4);

                if (!line.toLowerCase().contains("simulator") && !UUID.contains("-")) {
                    IosDevice device = new IosDevice();
                    device.setName(matcher.group(2));
                    device.setVersion(matcher.group(3));
                    device.setUuid(UUID);
                    devices.add(device);
                }
            }
        }

        return devices;
    }

    @Data
    public static class IosDevice {
        private String uuid;
        private String name;
        private String version;
    }
}