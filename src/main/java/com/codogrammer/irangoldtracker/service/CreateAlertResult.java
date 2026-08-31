package com.codogrammer.irangoldtracker.service;

import com.codogrammer.irangoldtracker.entity.Alert;

public sealed interface CreateAlertResult {

    record Created(Alert alert) implements CreateAlertResult {
    }

    record LimitReached(int maxAlerts) implements CreateAlertResult {
    }

    record Duplicate(String itemName) implements CreateAlertResult {
    }
}
