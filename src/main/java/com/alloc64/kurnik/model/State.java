package com.alloc64.kurnik.model;

import lombok.Data;

import java.util.List;

@Data
public class State {
    private String message;
    private List<DoorEntry> doors;
}
