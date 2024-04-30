package com.alloc64.kurnik.model;

import lombok.Data;

@Data
public class ChangeTimesRequest {
    private boolean enabled;
    private String openTime;
    private String closeTime;
}
