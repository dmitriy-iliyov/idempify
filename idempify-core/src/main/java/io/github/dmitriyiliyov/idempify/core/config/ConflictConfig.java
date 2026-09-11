package io.github.dmitriyiliyov.idempify.core.config;

import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandleStrategy;
import io.github.dmitriyiliyov.idempify.core.conflict.ConflictHandler;

import java.util.Map;
import java.util.Objects;

/**
 * Describes how a conflict - a call arriving while an earlier call for the same idempotency key is still in
 * process - is handled: which strategy applies, and how the handler behind that strategy is tuned.
 * <p>
 * Like the {@link IdempotencyConfig} that owns it, this is a partial description: every getter may return
 * {@code null}, meaning this config does not specify the setting.
 * <p>
 * Besides the builder, the three shorthands {@link #reject()}, {@link #wait(WaitConflictHandlerConfig)} and
 * {@link #custom(ConflictHandler)} cover the way a strategy is normally configured. A handler set on the
 * config outranks the strategy - see {@link Builder#handler(ConflictHandler)}.
 */
public final class ConflictConfig {

    private static final Boolean DEFAULT_ENABLED = Boolean.parseBoolean(IdempifyDefaults.CONFLICT_ENABLED_VALUE);
    public static final ConflictHandleStrategy DEFAULT_CONFLICT_HANDLE_STRATEGY =
            ConflictHandleStrategy.valueOf(IdempifyDefaults.CONFLICT_HANDLE_STRATEGY_VALUE);
    public static final Map<ConflictHandleStrategy, ConflictHandlerConfig> DEFAULT_HANDLER_CONFIGS = Map.of(
            ConflictHandleStrategy.REJECT, ConflictHandlerConfig.NOOP,
            ConflictHandleStrategy.WAIT, WaitConflictHandlerConfig.defaults()
    );

    private final Boolean enabled;
    private final ConflictHandleStrategy strategy;
    private final ConflictHandlerConfig handlerConfig;
    private final ConflictHandler handler;

    private ConflictConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.strategy = builder.strategy;
        this.handlerConfig = builder.handlerConfig;
        this.handler = builder.handler;
    }

    public void validate() {
        if (enabled) {
            if (ConflictHandleStrategy.WAIT.equals(strategy)) {
                if (handlerConfig == null) {
                    throw new IllegalStateException(
                            "ConflictHandlerConfig must be specified when ConflictHandleStrategy is WAIT"
                    );
                }

                if (handlerConfig.getClass() != WaitConflictHandlerConfig.class) {
                    throw new IllegalStateException(
                            "ConflictHandlerConfig must be instance of WaitConflictHandlerConfig.class when ConflictHandleStrategy is WAIT"
                    );
                }
            } else if (handlerConfig != null && handlerConfig.getClass() == WaitConflictHandlerConfig.class) {
                throw new IllegalStateException(
                        "ConflictHandleStrategy must be WAIT when ConflictHandlerConfig is instance of WaitConflictHandlerConfig.class"
                );
            }
        }
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public ConflictHandleStrategy getStrategy() {
        return strategy;
    }

    public ConflictHandlerConfig getHandlerConfig() {
        return handlerConfig;
    }

    public ConflictHandler getHandler() {
        return handler;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ConflictConfig that = (ConflictConfig) o;
        return enabled == that.enabled &&
                strategy == that.strategy &&
                Objects.equals(handlerConfig, that.handlerConfig) &&
                Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, strategy, handlerConfig, handler);
    }

    @Override
    public String toString() {
        return "ConflictConfig{" +
                "enabled=" + enabled +
                ", strategy=" + strategy +
                ", handlerConfig=" + handlerConfig +
                ", handler=" + handler +
                '}';
    }

    public static ConflictConfig reject() {
        return builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.REJECT)
                .handlerConfig(ConflictHandlerConfig.NOOP)
                .build();
    }

    public static ConflictConfig wait(WaitConflictHandlerConfig handlerConfig) {
        Objects.requireNonNull(handlerConfig, "handlerConfig cannot be null");
        return builder()
                .enabled(true)
                .strategy(ConflictHandleStrategy.WAIT)
                .handlerConfig(handlerConfig)
                .build();
    }

    public static ConflictConfig custom(ConflictHandler handler) {
        Objects.requireNonNull(handler, "handler cannot be null");
        return builder()
                .enabled(true)
                .handler(handler)
                .build();
    }

    public static ConflictConfig defaults() {
        return builder()
                .enabled(DEFAULT_ENABLED)
                .strategy(DEFAULT_CONFLICT_HANDLE_STRATEGY)
                .handlerConfig(getDefaultHandlerConfig(DEFAULT_CONFLICT_HANDLE_STRATEGY))
                .build();
    }

    public static ConflictConfig disabled() {
        return builder()
                .enabled(false)
                .build();
    }


    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ConflictConfig config) {
        return new Builder(config);
    }

    /**
     * Layers {@code target} over {@code reference}. Either side naming a whole mechanism replaces the other's:
     * a handler of its own discards the reference entirely, and a strategy of its own discards the handler the
     * reference supplied - otherwise the broader layer would keep answering through
     * {@link Builder#handler(ConflictHandler)} after the narrower one had chosen a shipped handler instead.
     */
    public static ConflictConfig merge(ConflictConfig reference, ConflictConfig target) {
        Objects.requireNonNull(reference, "reference cannot be null");
        Objects.requireNonNull(target, "target cannot be null");

        if (!target.isEnabled()) {
            return ConflictConfig.disabled();
        }

        ConflictHandler handler = target.getHandler();
        if (handler != null) {
            return custom(handler);
        }

        ConflictConfig.Builder configBuilder = builder(reference);
        configBuilder.enabled(true);

        ConflictHandleStrategy strategy = target.getStrategy();
        if (strategy != null) {
            configBuilder.handler = null;
            configBuilder.strategy(strategy);
            configBuilder.handlerConfig(getDefaultHandlerConfig(strategy));
        }

        ConflictHandlerConfig handlerConfig = target.getHandlerConfig();
        if (handlerConfig != null) {
            if (handlerConfig.getClass() == WaitConflictHandlerConfig.class
                    && reference.getHandlerConfig() != null
                    && reference.getHandlerConfig().getClass() == WaitConflictHandlerConfig.class) {
                handlerConfig = WaitConflictHandlerConfig.merge(reference.getHandlerConfig(), handlerConfig);
            }
            configBuilder.handlerConfig(handlerConfig);
        }

        ConflictConfig config = configBuilder.build();
        config.validate();
        return config;
    }

    public static ConflictHandlerConfig getDefaultHandlerConfig(ConflictHandleStrategy strategy) {
        ConflictHandlerConfig conflictHandlerConfig = DEFAULT_HANDLER_CONFIGS.get(strategy);
        if (conflictHandlerConfig == null) {
            throw new IllegalStateException("No ConflictHandlerConfig found for %s strategy".formatted(strategy));
        }
        return conflictHandlerConfig;
    }

    public static final class Builder {

        private Boolean enabled = true;
        private ConflictHandleStrategy strategy;
        private ConflictHandlerConfig handlerConfig;
        private ConflictHandler handler;

        private Builder() {}

        private Builder(ConflictConfig config) {
            this.enabled = config.enabled;
            this.strategy = config.strategy;
            this.handlerConfig = config.handlerConfig;
            this.handler = config.handler;
        }

        public Builder enabled(Boolean enabled) {
            this.enabled = Objects.requireNonNull(enabled, "enabled cannot be null");
            return this;
        }

        public Builder strategy(ConflictHandleStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy, "strategy cannot be null");
            return this;
        }

        public Builder handlerConfig(ConflictHandlerConfig handlerConfig) {
            this.handlerConfig = handlerConfig;
            return this;
        }

        public Builder handler(ConflictHandler handler) {
            this.handler = handler;
            return this;
        }

        public ConflictConfig build() {
            Objects.requireNonNull(enabled, "enabled cannot be null");
            if (enabled) {
                if (ConflictHandleStrategy.WAIT.equals(strategy)
                        && handlerConfig != null
                        && !(handlerConfig instanceof WaitConflictHandlerConfig)) {
                    throw new IllegalArgumentException(
                            "handlerConfig must be a WaitConflictHandlerConfig when strategy is WAIT, but was %s".formatted(handlerConfig.getClass().getName())
                    );
                }
            } else {
                strategy = null;
                handlerConfig = null;
                handler = null;
            }
            return new ConflictConfig(this);
        }
    }
}
