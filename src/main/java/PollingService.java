import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to make polling easier
 */
@Slf4j
public class PollingService {

    /**
     * Default timeout One Minute
     */
    public static long DEFAULT_TIMEOUT = 1000 * 60;

    /**
     * Default max wait time One Minute
     */
    public static long DEFAULT_WAIT_TIME = 100;

    public static boolean poll(PollingAction action) {
        return poll(action, DEFAULT_TIMEOUT, DEFAULT_WAIT_TIME);
    }

    /**
     * using the polling action polls until it returns true or the timeout occurs.
     *
     * @param action   The action that performs the polling operation
     * @param timeout  the max time this operation can occur
     * @param waitTime the amount of time between trying the polling action
     * @return true if the polling completed successfully, false if the action never returned true
     */
    @SneakyThrows(InterruptedException.class)
    public static boolean poll(PollingAction action, long timeout, long waitTime) {
        long t = System.currentTimeMillis();
        boolean success;

        do {
            success = action.poll();

            if (!success) {
                Thread.sleep(waitTime);
            }
        } while (!success && (t > System.currentTimeMillis() - timeout));

        return success;
    }

}
