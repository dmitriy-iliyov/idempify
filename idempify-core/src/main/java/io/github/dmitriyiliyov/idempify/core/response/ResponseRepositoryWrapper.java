package io.github.dmitriyiliyov.idempify.core.response;


/**
 * Hook by which a module puts something of its own in front of the {@link ResponseRepository} a backend
 * built - a cache above all, which is how both of the supplied caches are installed.
 * <p>
 * Wrapping is offered here rather than done by a bean post-processor so that the module owning the store
 * decides what it hands out, and an application registering no wrapper gets back the very object it would
 * have got without the seam.
 */
public interface ResponseRepositoryWrapper {

    /**
     * Returns the repository to be used in place of the given one - normally a decorator delegating to it.
     * <p>
     * The argument is not necessarily the backend's own repository: with several wrappers registered it is
     * whatever the wrappers of higher priority have already produced.
     */
    ResponseRepository wrap(ResponseRepository repository);

    /**
     * Returns how close to the store this wrapper sits - <strong>the higher the priority, the deeper it is
     * nested</strong>, so the highest of them wraps the backend itself and the lowest is what a lookup enters
     * first.
     * <p>
     * The order matters whenever one wrapper's answer is another's subject: a wrapper that counts what the
     * store answers wants a high priority, one that measures what the caller waits for wants a low one. Two
     * wrappers of equal priority are nested in no particular order.
     */
    int getPriority();
}
