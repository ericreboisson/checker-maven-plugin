package com.example.modulechecker.checkers;

import java.util.ArrayList;
import java.util.List;

public class ModuleVerificationResult {

    private final String moduleName;
    private final List<DependencyVersionCheckResult> hardcodedVersions = new ArrayList<>();

    public ModuleVerificationResult(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public List<DependencyVersionCheckResult> getHardcodedVersions() {
        return hardcodedVersions;
    }

    public void addHardcodedVersion(DependencyVersionCheckResult result) {
        hardcodedVersions.add(result);
    }
}