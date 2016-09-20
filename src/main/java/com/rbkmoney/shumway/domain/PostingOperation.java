package com.rbkmoney.shumway.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by vpankrashkin on 14.09.16.
 */
public enum PostingOperation {
    HOLD("HOLD"), COMMIT("COMMIT"), ROLLBACK("ROLLBACK");

    private static final Map<String, PostingOperation> keyMap;

    static {
        Map<String, PostingOperation> tmpMap = new HashMap<>();
        for (PostingOperation operation: values()) {
            tmpMap.put(operation.getKey(), operation);
        }
        keyMap = tmpMap;
    }

    private final String key;

    PostingOperation(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static PostingOperation getValueByKey(String key) {
        return keyMap.get(key);
    }

}
