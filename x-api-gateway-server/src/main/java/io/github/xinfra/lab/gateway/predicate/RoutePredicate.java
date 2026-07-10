package io.github.xinfra.lab.gateway.predicate;

public interface RoutePredicate<T> {

    boolean test(T t);

    default RoutePredicate<T> and(RoutePredicate<T> other) {
        return t -> this.test(t) && other.test(t);
    }

    default RoutePredicate<T> or(RoutePredicate<T> other) {
        return t -> this.test(t) || other.test(t);
    }

    default RoutePredicate<T> negate() {
        return t -> !this.test(t);
    }
}
