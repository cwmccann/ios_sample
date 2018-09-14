/**
 * Interface for doing a polling check
 */
@FunctionalInterface
public interface PollingAction {
    boolean poll();
}
