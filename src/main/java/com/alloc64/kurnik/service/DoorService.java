package com.alloc64.kurnik.service;

import com.alloc64.kurnik.model.*;
import com.alloc64.kurnik.prefs.JsonSharedPreferences;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DoorService {
    private final OkHttpClient httpClient;
    private final String dingtianRelayHost;
    private final JsonSharedPreferences jsonSharedPreferences;
    private final List<DoorDefinition> doorDefinitions = List.of(
            new DoorDefinition()
                    .setId(0)
                    .setOpenTime(20000L)
                    .setCloseTime(16000L)
                    .setDefaultOpenAtTime("07:30")
                    .setDefaultCloseAtTime("21:00")
                    .setRelayId0(1)
                    .setRelayId1(0),
            new DoorDefinition()
                    .setId(1)
                    .setOpenTime(10000L)
                    .setCloseTime(8000L)
                    .setDefaultOpenAtTime("07:30")
                    .setDefaultCloseAtTime("21:00")
                    .setRelayId0(2)
                    .setRelayId1(3)
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public DoorService(@Value("${kurnik.dingtianrelayhost:http://192.168.1.100}") String dingtianRelayHost,
                       JsonSharedPreferences jsonSharedPreferences) {
        this.dingtianRelayHost = dingtianRelayHost;
        this.jsonSharedPreferences = jsonSharedPreferences;
        this.httpClient = new OkHttpClient.Builder().build();

        this.runScheduledTask();
    }

    private void runScheduledTask() {
        Map<Integer, Long> lastOpenCloseTimestampByDoorId = new LinkedHashMap<>();

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            LocalDateTime nowDateTime = LocalDateTime.now();
            LocalTime now = nowDateTime.toLocalTime();

            for(DoorDefinition doorDefinition : doorDefinitions) {
                try {
                    int doorId = doorDefinition.getId();

                    if(!isDoorOpenCloseEnabled(doorId))
                        continue;

                    Long lastOpenCloseTimestamp = lastOpenCloseTimestampByDoorId.get(doorId);

                    // prevent multiple open/close operations within 2 minutes
                    if(lastOpenCloseTimestamp != null && System.currentTimeMillis() - lastOpenCloseTimestamp < 120 * 1000L) {
                        continue;
                    }

                    LocalTime doorOpenTime = LocalTime.parse(getDoorOpenTime(doorId));
                    LocalTime doorCloseTime = LocalTime.parse(getDoorCloseTime(doorId));
                    boolean isOpenTime = isSameHourAndMinute(now, doorOpenTime);
                    boolean isCloseTime = isSameHourAndMinute(now, doorCloseTime);

                    if (isOpenTime) {
                        log.info("Opening door: " + doorDefinition.getId() + " at scheduled time: " + nowDateTime);
                        openOrCloseDoor(doorDefinition.getId(), true);
                    } else if (isCloseTime) {
                        log.info("Closing door: " + doorDefinition.getId() + " at scheduled time: " + nowDateTime);
                        openOrCloseDoor(doorDefinition.getId(), false);
                    }

                    if(isOpenTime || isCloseTime) {
                        lastOpenCloseTimestampByDoorId.put(doorId, System.currentTimeMillis());
                    }
                }
                catch (Exception e) {
                    log.error("Failed to open door: " + doorDefinition.getId() + " at scheduled time: " + nowDateTime, e);
                }
            }

        }, 0, 1, TimeUnit.SECONDS);
    }

    public State getCurrentState() throws Exception {
        State state = new State();
        try (Response response = httpClient.newCall(
                new Request.Builder()
                        .url(dingtianRelayHost + "/relay_cgi_load.cgi")
                        .build()
        ).execute()) {
            List<String> tokens = tokenizeResponse(response.body().string());
            String relayState = tokens.get(0);
            if(!"0".equals(relayState))
                throw new Exception("Failed to get current relay state, got: " + relayState);

            // this expects dingtian relay with 4 relays

            Long relay0State = Long.parseLong(tokens.get(2));
            Long relay1State = Long.parseLong(tokens.get(3));
            Long relay2State = Long.parseLong(tokens.get(4));
            Long relay3State = Long.parseLong(tokens.get(5));

            state.setDoors(
                    resolveDoorEntries(List.of(relay0State, relay1State, relay2State, relay3State))
            );
        }

        return state;
    }

    private List<DoorEntry> resolveDoorEntries(List<Long> relayStates) {
        List<DoorEntry> doorEntries = new ArrayList<>();

        for(DoorDefinition doorDefinition : doorDefinitions) {
            int doorId = doorDefinition.getId();
            Long relay0State = relayStates.get(doorDefinition.getRelayId0());
            Long relay1State = relayStates.get(doorDefinition.getRelayId1());

            DoorState doorState;
            if((relay0State == 0 && relay1State == 0) || (relay0State == 1 && relay1State == 1)) {
                // doors are stopped, but we must determine if they are open or closed
                doorState = determinePersistedDoorState(doorDefinition.getId());
            } else if(relay0State == 1 && relay1State == 0) {
                doorState = DoorState.OPENING;
            } else if(relay0State == 0 && relay1State == 1) {
                doorState = DoorState.CLOSING;
            } else {
                throw new IllegalStateException("Invalid relay states: " + relay0State + " " + relay1State);
            }

            DoorEntry doorEntry = new DoorEntry();
            doorEntry.setState(doorState);
            doorEntry.setEnabled(isDoorOpenCloseEnabled(doorId));
            doorEntry.setOpenTime(getDoorOpenTime(doorId));
            doorEntry.setCloseTime(getDoorCloseTime(doorId));

            doorEntries.add(doorEntry);
        }

        return doorEntries;
    }

    public void changeState(Integer doorId, String changeToState) throws Exception {
        switch (changeToState) {
            case "open":
                openOrCloseDoor(doorId, true);
                break;
            case "close":
                openOrCloseDoor(doorId, false);
                break;
            case "stop":
                stopDoors(doorId);
                break;
            default:
                throw new IllegalArgumentException("Invalid state: " + changeToState);
        }
    }

    private void stopDoors(Integer doorId) throws Exception {
        DoorDefinition doorDefinition = doorDefinitions.get(doorId);
        int relayId0 = doorDefinition.getRelayId0();
        int relayId1 = doorDefinition.getRelayId1();

        changeRelayState(relayId0, false);
        changeRelayState(relayId1, false);
    }

    private void openOrCloseDoor(Integer doorId, boolean openOrClose) throws Exception {
        DoorDefinition doorDefinition = doorDefinitions.get(doorId);
        int relayId0 = doorDefinition.getRelayId0();
        int relayId1 = doorDefinition.getRelayId1();

        stopDoors(doorId);

        executor.submit(() -> {
            persistDoorState(doorId, openOrClose ? DoorState.OPENING : DoorState.CLOSING);
            try {
                changeRelayState(relayId0, openOrClose);
                changeRelayState(relayId1, !openOrClose);

                Thread.sleep(openOrClose ? doorDefinition.getOpenTime() : doorDefinition.getCloseTime());
            } catch (Exception e) {
                log.error("Failed to open/close (" + openOrClose +  ") door: " + doorId, e);
            }
            finally {
                try {
                    changeRelayState(relayId0, false);
                    changeRelayState(relayId1, false);
                } catch (Exception e) {
                    log.error("Failed to stop door: " + doorId, e);
                }

                persistDoorState(doorId, openOrClose ? DoorState.OPEN : DoorState.CLOSED);
            }
        });
    }

    private DoorState determinePersistedDoorState(Integer doorId) {
        String value = jsonSharedPreferences.getString(
                getDoorKey(doorId), null
        );

        return value == null ? DoorState.CLOSED : DoorState.fromString(value);
    }

    private void persistDoorState(Integer doorId, DoorState doorState) {
        jsonSharedPreferences.putString(
                getDoorKey(doorId), doorState.getState()
        );
    }

    public boolean changeRelayState(Integer relayId, boolean on) throws Exception {
        try (Response response = httpClient.newCall(
                new Request.Builder()
                        .url(dingtianRelayHost + "/relay_cgi.cgi?type=0&relay=" + relayId + "&on=" + (on ? "1" : "0") + "&time=0&pwd=&")
                        .build()
        ).execute()) {
            List<String> tokens = tokenizeResponse(response.body().string());

            String relayState = tokens.get(0);
            if(!"0".equals(relayState))
                throw new Exception("Failed to get updated relay state, got: " + relayState);

            boolean isOn = "1".equals(tokens.get(4));

            return isOn;
        }
    }

    private List<String> tokenizeResponse(String response) {
        if(response == null || response.isBlank())
            throw new IllegalArgumentException("Empty response");

        if(response.startsWith("&"))
            response = response.substring(1);
        return List.of(response.split("&"));
    }

    private String getDoorKey(Integer id) {
        return "door_" + id;
    }

    private String getDoorOpenTimeKey(Integer id) {
        return "door_" + id + "_open_time";
    }

    private String getDoorCloseTimeKey(Integer id) {
        return "door_" + id + "_close_time";
    }

    private String getDoorOpenCloseEnabledKey(Integer id) {
        return "door_" + id + "_open_close_enabled";
    }

    private boolean isDoorOpenCloseEnabled(int doorId) {
        return jsonSharedPreferences.getBoolean(getDoorOpenCloseEnabledKey(doorId), false);
    }

    private String getDoorOpenTime(int doorId) {
        return jsonSharedPreferences.getString(getDoorOpenTimeKey(doorId), doorDefinitions.get(doorId).getDefaultOpenAtTime());
    }

    private String getDoorCloseTime(int doorId) {
        return jsonSharedPreferences.getString(getDoorCloseTimeKey(doorId), doorDefinitions.get(doorId).getDefaultCloseAtTime());
    }

    private static boolean isSameHourAndMinute(LocalTime time1, LocalTime time2) {
        return time1.getHour() == time2.getHour() && time1.getMinute() == time2.getMinute();
    }

    public void changeTimes(Integer doorId, ChangeTimesRequest state) {
        jsonSharedPreferences.putBoolean(
                getDoorOpenCloseEnabledKey(doorId), state.isEnabled()
        );

        jsonSharedPreferences.putString(
                getDoorOpenTimeKey(doorId), state.getOpenTime()
        );

        jsonSharedPreferences.putString(
                getDoorCloseTimeKey(doorId), state.getCloseTime()
        );
    }
}
