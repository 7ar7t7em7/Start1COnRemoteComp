package aaaStart1COnRemoteComp;

/** Последнее состояние обмена для одной периферийной базы. */
public class ExchangeState {
    private String serverId;
    private String parentName;
    private String serverName;
    private ExchangeStage stage;
    private ExchangeStatus status;
    private String startedAt;
    private String finishedAt;
    private int attempt;
    private String message;
    private String operationId;

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public ExchangeStage getStage() { return stage; }
    public void setStage(ExchangeStage stage) { this.stage = stage; }
    public ExchangeStatus getStatus() { return status; }
    public void setStatus(ExchangeStatus status) { this.status = status; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
}
