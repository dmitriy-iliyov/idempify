package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Applies the registered {@link ResponseRepositoryWrapper}s to the repository a backend module built.
 */
public final class ResponseRepositoryWrapperUtils {

    private ResponseRepositoryWrapperUtils() { }

    /**
     * Returns the repository wrapped by every given wrapper, nested by {@link ResponseRepositoryWrapper#getPriority()}:
     * the highest priority is applied first and therefore ends up closest to the repository, the lowest is applied
     * last and is what a lookup enters first.
     *
     * @param repository    the repository to wrap; handed back unchanged when there is nothing to wrap it with.
     * @param wrappers the wrappers to apply, in any order.
     */
    public static ResponseRepository wrapWithPriority(ResponseRepository repository, Set<ResponseRepositoryWrapper> wrappers) {
        Objects.requireNonNull(repository, "repository cannot be null");
        Objects.requireNonNull(wrappers, "wrappers cannot be null");
        if (wrappers.isEmpty()) {
            return repository;
        }

        List<ResponseRepositoryWrapper> sortedWrappers = wrappers
                .stream()
                .sorted(Comparator
                        .comparingInt(ResponseRepositoryWrapper::getPriority)
                        .reversed())
                .toList();

        ResponseRepository wrappedResponseRepository = repository;
        for (ResponseRepositoryWrapper wrapper : sortedWrappers) {
            wrappedResponseRepository = wrapper.wrap(wrappedResponseRepository);
        }

        return wrappedResponseRepository;
    }

}
