package practice.lld.firstiteration.coffeeold;

final class States {
    private States() {}

    static final CoffeeMachineState IDLE = () -> "IDLE";
    static final CoffeeMachineState PROCESSING_PAYMENT = () -> "PROCESSING_PAYMENT";
    static final CoffeeMachineState DISPENSING = () -> "DISPENSING";
    static final CoffeeMachineState OUT_OF_ORDER = () -> "OUT_OF_ORDER";
}

