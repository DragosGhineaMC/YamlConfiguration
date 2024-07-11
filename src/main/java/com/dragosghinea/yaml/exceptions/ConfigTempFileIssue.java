package com.dragosghinea.yaml.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;

@Getter
//@AllArgsConstructor
public class ConfigTempFileIssue extends Exception {

    private File file;

    public ConfigTempFileIssue(File file, String message) {
        super(message);
        this.file = file;
    }

}
