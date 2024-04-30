package com.alloc64.kurnik.model;

import lombok.Data;

@Data
public class DoorEntry {
    private DoorState state;
    private boolean isEnabled;
    private String openTime;
    private String closeTime;
}
