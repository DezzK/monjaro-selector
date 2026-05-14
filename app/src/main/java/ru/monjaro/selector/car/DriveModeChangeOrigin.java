package ru.monjaro.selector.car;

public enum DriveModeChangeOrigin {
    /** Изменение пришло из ECU (физический селектор, голос, руль и т.п.). */
    EXTERNAL,
    /** Эхо нашего собственного setFunctionValue. */
    PROGRAMMATIC
}
