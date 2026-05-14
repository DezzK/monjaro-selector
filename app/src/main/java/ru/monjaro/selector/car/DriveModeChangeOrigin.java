package ru.monjaro.selector.car;

public enum DriveModeChangeOrigin {
    /** Change came from ECU (physical selector, voice, steering wheel, etc.). */
    EXTERNAL,
    /** Echo of our own setFunctionValue. */
    PROGRAMMATIC
}
