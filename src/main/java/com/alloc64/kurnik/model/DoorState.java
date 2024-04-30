package com.alloc64.kurnik.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DoorState {
    OPEN("open"),
    OPENING("opening"),
    CLOSING("closing"),
    CLOSED("closed");

    private final String state;

    public static DoorState fromString(String state) {
        for (DoorState doorState : DoorState.values()) {
            if (doorState.state.equalsIgnoreCase(state)) {
                return doorState;
            }
        }
        throw new IllegalArgumentException("Invalid state: " + state);
    }
}
