package com.dohis.app.dto;

public class PermissionRequestDto {

    private String fileId;
    private String fileName;
    private String type; // "EDITAR" o "ELIMINAR"
    private String approverId; // OJO: en tu User id es String

    public PermissionRequestDto() {
    }

    public String getFileId() {
        return fileId;
    }
    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() {
        return fileName;
    }
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public String getApproverId() {
        return approverId;
    }
    public void setApproverId(String approverId) {
        this.approverId = approverId;
    }
}
