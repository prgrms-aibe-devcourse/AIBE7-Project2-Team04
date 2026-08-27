package org.example.project2.domain.region.service;

public interface RegionPinValidator {
    RegionPinValidationResult validate(String regionCode, double longitude, double latitude);
}
