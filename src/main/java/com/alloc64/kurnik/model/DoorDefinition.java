package com.alloc64.kurnik.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DoorDefinition {
    private int id;
    private long openTime;
    private long closeTime;
    private int relayId0;
    private int relayId1;
    private String defaultOpenAtTime;
    private String defaultCloseAtTime;
}
