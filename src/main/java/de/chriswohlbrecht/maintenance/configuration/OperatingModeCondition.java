package de.chriswohlbrecht.maintenance.configuration;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches beans of one operating mode. Binds {@code bm.mode} the same way as {@link OperatingModeProperties}
 * (default {@code portal}), so an unknown value fails the startup here instead of leaving the application
 * without any of the two security configurations.
 */
public abstract class OperatingModeCondition implements Condition {

    private final OperatingMode mode;

    protected OperatingModeCondition(OperatingMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        OperatingMode configured = Binder.get(context.getEnvironment())
                .bind("bm.mode", OperatingMode.class)
                .orElse(OperatingMode.PORTAL);
        return configured == mode;
    }

    public static class Portal extends OperatingModeCondition {
        public Portal() {
            super(OperatingMode.PORTAL);
        }
    }

    public static class Household extends OperatingModeCondition {
        public Household() {
            super(OperatingMode.HOUSEHOLD);
        }
    }
}
