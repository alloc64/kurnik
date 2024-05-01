package com.alloc64.kurnik.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class State {
    private String message;
    private String serverDateTime = LocalDateTime.now().toString();
    private List<DoorEntry> doors;
}
