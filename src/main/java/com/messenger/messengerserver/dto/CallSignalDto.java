package com.messenger.messengerserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSignalDto {
    private String type; // "offer", "answer", "ice-candidate", "end"
    private String from;
    private String to;
    private String sdp;
    private String sdpType;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;

    // Конструкторы
    public CallSignalDto() {}

    public CallSignalDto(String type, String from, String to) {
        this.type = type;
        this.from = from;
        this.to = to;
    }

    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSdp() { return sdp; }
    public void setSdp(String sdp) { this.sdp = sdp; }

    public String getSdpType() { return sdpType; }
    public void setSdpType(String sdpType) { this.sdpType = sdpType; }

    public String getCandidate() { return candidate; }
    public void setCandidate(String candidate) { this.candidate = candidate; }

    public String getSdpMid() { return sdpMid; }
    public void setSdpMid(String sdpMid) { this.sdpMid = sdpMid; }

    public Integer getSdpMLineIndex() { return sdpMLineIndex; }
    public void setSdpMLineIndex(Integer sdpMLineIndex) { this.sdpMLineIndex = sdpMLineIndex; }
}