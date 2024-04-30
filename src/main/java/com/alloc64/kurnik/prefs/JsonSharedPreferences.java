package com.alloc64.kurnik.prefs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class JsonSharedPreferences {
    private final File file;
    private final ObjectMapper objectMapper;
    private Map<String, Object> data;

    public JsonSharedPreferences(@Value("${kurnik.sharedprefsfile:shared-prefs.json}") String filePath) {
        this.file = new File(filePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        loadData();
    }

    private void loadData() {
        if (file.exists()) {
            try {
                data = objectMapper.readValue(file, HashMap.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            data = new HashMap<>();
        }
    }

    public void putBoolean(String key, boolean value) {
        data.put(key, value);
        saveData();
    }

    public void putString(String key, String value) {
        data.put(key, value);
        saveData();
    }

    public String getString(String key, String defaultValue) {
        return data.containsKey(key) ? (String) data.get(key) : defaultValue;
    }

    public void putInt(String key, int value) {
        data.put(key, value);
        saveData();
    }

    public int getInt(String key, int defaultValue) {
        return data.containsKey(key) ? (int) data.get(key) : defaultValue;
    }

    public long getLong(String key, long defaultValue) {
        return data.containsKey(key) ? (long) data.get(key) : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return data.containsKey(key) ? (boolean) data.get(key) : defaultValue;
    }

    private void saveData() {
        try {
            objectMapper.writeValue(file, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}