package com.anmonu.gcpobservability.monitoringadmin.controller;

import com.anmonu.gcpobservability.monitoringadmin.dto.AlertPolicyRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.AlertPolicyResponse;
import com.anmonu.gcpobservability.monitoringadmin.dto.ChannelUpdateRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.EmailChannelRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.NotificationChannelResponse;
import com.anmonu.gcpobservability.monitoringadmin.dto.PubsubChannelRequest;
import com.anmonu.gcpobservability.monitoringadmin.service.MonitoringAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MonitoringAdminController {

    private final MonitoringAdminService service;

    public MonitoringAdminController(MonitoringAdminService service) {
        this.service = service;
    }

    @GetMapping("/alerts")
    public List<AlertPolicyResponse> listAlerts() {
        return service.listAlertPolicies();
    }

    @PostMapping("/alerts")
    public AlertPolicyResponse createAlert(@Valid @RequestBody AlertPolicyRequest request) {
        return service.createAlertPolicy(request);
    }

    @PutMapping("/alerts/{id}")
    public AlertPolicyResponse updateAlert(@PathVariable String id, @Valid @RequestBody AlertPolicyRequest request) {
        return service.updateAlertPolicy(id, request);
    }

    @GetMapping("/channels")
    public List<NotificationChannelResponse> listChannels() {
        return service.listNotificationChannels();
    }

    @PostMapping("/channels/email")
    public NotificationChannelResponse createEmail(@Valid @RequestBody EmailChannelRequest request) {
        return service.createEmailChannel(request);
    }

    @PostMapping("/channels/pubsub")
    public NotificationChannelResponse createPubsub(@Valid @RequestBody PubsubChannelRequest request) {
        return service.createPubsubChannel(request);
    }

    @PutMapping("/channels/{id}")
    public NotificationChannelResponse updateChannel(@PathVariable String id, @RequestBody ChannelUpdateRequest request) {
        return service.updateChannel(id, request);
    }
}
