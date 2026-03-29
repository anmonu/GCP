package com.anmonu.gcpobservability.monitoringadmin.service;

import com.anmonu.gcpobservability.monitoringadmin.config.MonitoringProperties;
import com.anmonu.gcpobservability.monitoringadmin.dto.AlertPolicyRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.AlertPolicyResponse;
import com.anmonu.gcpobservability.monitoringadmin.dto.ChannelUpdateRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.CopyAlertRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.EmailChannelRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.NotificationChannelResponse;
import com.anmonu.gcpobservability.monitoringadmin.dto.PubsubChannelRequest;
import com.anmonu.gcpobservability.monitoringadmin.dto.RenameAlertRequest;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.monitoring.v3.AlertPolicyServiceClient;
import com.google.cloud.monitoring.v3.NotificationChannelServiceClient;
import com.google.monitoring.v3.CreateAlertPolicyRequest;
import com.google.monitoring.v3.CreateNotificationChannelRequest;
import com.google.monitoring.v3.ComparisonType;
import com.google.monitoring.v3.ListAlertPoliciesRequest;
import com.google.monitoring.v3.ListNotificationChannelsRequest;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.UpdateAlertPolicyRequest;
import com.google.monitoring.v3.UpdateNotificationChannelRequest;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.monitoring.v3.AlertPolicy;
import com.google.monitoring.v3.AlertPolicy.Condition;
import com.google.monitoring.v3.NotificationChannel;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

@Service
public class MonitoringAdminService {

    private final MonitoringProperties properties;

    public MonitoringAdminService(MonitoringProperties properties) {
        this.properties = properties;
    }

    public List<AlertPolicyResponse> listAlertPolicies() {
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create()) {
            ListAlertPoliciesRequest request = ListAlertPoliciesRequest.newBuilder()
                    .setName(ProjectName.of(properties.projectId()).toString())
                    .build();

            return StreamSupport.stream(client.listAlertPolicies(request).iterateAll().spliterator(), false)
                    .map(this::toAlertResponse)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list alert policies", ex);
        }
    }

    public AlertPolicyResponse createAlertPolicy(AlertPolicyRequest request) {
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create()) {
            AlertPolicy created = client.createAlertPolicy(CreateAlertPolicyRequest.newBuilder()
                    .setName(ProjectName.of(properties.projectId()).toString())
                    .setAlertPolicy(toAlertPolicy(request, null))
                    .build());
            return toAlertResponse(created);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create alert policy", ex);
        }
    }

    public AlertPolicyResponse updateAlertPolicy(String id, AlertPolicyRequest request) {
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create()) {
            AlertPolicy updated = client.updateAlertPolicy(UpdateAlertPolicyRequest.newBuilder()
                    .setAlertPolicy(toAlertPolicy(request, normalizeAlertPolicyName(id)))
                    .setUpdateMask(FieldMask.newBuilder().addAllPaths(List.of(
                            "display_name",
                            "documentation",
                            "enabled",
                            "combiner",
                            "conditions",
                            "notification_channels"
                    )).build())
                    .build());
            return toAlertResponse(updated);
        } catch (ApiException ex) {
            throw new IllegalStateException("Unable to update alert policy: " + ex.getStatusCode().getCode(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to update alert policy", ex);
        }
    }

    public AlertPolicyResponse copyAlertPolicy(String id, CopyAlertRequest request) {
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create()) {
            AlertPolicy source = client.getAlertPolicy(normalizeAlertPolicyName(id));

            AlertPolicy.Builder builder = source.toBuilder()
                    .clearName()
                    .setDisplayName(request.displayName());

            if (request.enabled() != null) {
                builder.setEnabled(BoolValue.of(request.enabled()));
            }

            if (request.documentation() != null && !request.documentation().isBlank()) {
                builder.setDocumentation(AlertPolicy.Documentation.newBuilder()
                        .setContent(request.documentation())
                        .setMimeType("text/markdown")
                        .build());
            }

            List<String> channels = mapChannels(source.getNotificationChannelsList(), request.notificationChannelMapping());
            if (request.notificationChannels() != null && !request.notificationChannels().isEmpty()) {
                channels = request.notificationChannels();
            }

            builder.clearNotificationChannels();
            builder.addAllNotificationChannels(channels);

            AlertPolicy created = client.createAlertPolicy(CreateAlertPolicyRequest.newBuilder()
                    .setName(ProjectName.of(properties.projectId()).toString())
                    .setAlertPolicy(builder.build())
                    .build());
            return toAlertResponse(created);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to copy alert policy", ex);
        }
    }

    public AlertPolicyResponse renameAlertPolicy(String id, RenameAlertRequest request) {
        try (AlertPolicyServiceClient client = AlertPolicyServiceClient.create()) {
            AlertPolicy existing = client.getAlertPolicy(normalizeAlertPolicyName(id));
            AlertPolicy updated = client.updateAlertPolicy(UpdateAlertPolicyRequest.newBuilder()
                    .setAlertPolicy(existing.toBuilder().setDisplayName(request.displayName()).build())
                    .setUpdateMask(FieldMask.newBuilder().addPaths("display_name").build())
                    .build());
            return toAlertResponse(updated);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to rename alert policy", ex);
        }
    }

    public List<NotificationChannelResponse> listNotificationChannels() {
        try (NotificationChannelServiceClient client = NotificationChannelServiceClient.create()) {
            ListNotificationChannelsRequest request = ListNotificationChannelsRequest.newBuilder()
                    .setName(ProjectName.of(properties.projectId()).toString())
                    .build();

            return StreamSupport.stream(client.listNotificationChannels(request).iterateAll().spliterator(), false)
                    .map(this::toChannelResponse)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to list notification channels", ex);
        }
    }

    public NotificationChannelResponse createEmailChannel(EmailChannelRequest request) {
        NotificationChannel channel = NotificationChannel.newBuilder()
                .setType("email")
                .setDisplayName(request.displayName())
                .putLabels("email_address", request.emailAddress())
                .setEnabled(BoolValue.of(request.enabled()))
                .build();

        return createNotificationChannel(channel);
    }

    public NotificationChannelResponse createPubsubChannel(PubsubChannelRequest request) {
        NotificationChannel channel = NotificationChannel.newBuilder()
                .setType("pubsub")
                .setDisplayName(request.displayName())
                .putLabels("topic", request.topicName())
                .setEnabled(BoolValue.of(request.enabled()))
                .build();

        return createNotificationChannel(channel);
    }

    public NotificationChannelResponse updateChannel(String id, ChannelUpdateRequest request) {
        NotificationChannel.Builder builder = NotificationChannel.newBuilder()
                .setName(normalizeChannelName(id));

        List<String> paths = new java.util.ArrayList<>();
        if (request.displayName() != null && !request.displayName().isBlank()) {
            builder.setDisplayName(request.displayName());
            paths.add("display_name");
        }
        if (request.type() != null && !request.type().isBlank()) {
            builder.setType(request.type());
            paths.add("type");
        }
        if (request.labels() != null && !request.labels().isEmpty()) {
            builder.putAllLabels(request.labels());
            paths.add("labels");
        }

        if (request.enabled() != null) {
            builder.setEnabled(BoolValue.of(request.enabled()));
            paths.add("enabled");
        }

        if (paths.isEmpty()) {
            throw new IllegalArgumentException("At least one field must be provided for channel update");
        }

        try (NotificationChannelServiceClient client = NotificationChannelServiceClient.create()) {
            NotificationChannel updated = client.updateNotificationChannel(UpdateNotificationChannelRequest.newBuilder()
                    .setUpdateMask(FieldMask.newBuilder().addAllPaths(paths).build())
                    .setNotificationChannel(builder.build())
                    .build());
            return toChannelResponse(updated);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to update notification channel", ex);
        }
    }

    private NotificationChannelResponse createNotificationChannel(NotificationChannel channel) {
        try (NotificationChannelServiceClient client = NotificationChannelServiceClient.create()) {
            NotificationChannel created = client.createNotificationChannel(CreateNotificationChannelRequest.newBuilder()
                    .setName(ProjectName.of(properties.projectId()).toString())
                    .setNotificationChannel(channel)
                    .build());
            return toChannelResponse(created);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create notification channel", ex);
        }
    }

    private List<String> mapChannels(List<String> existingChannels, Map<String, String> channelMapping) {
        if (channelMapping == null || channelMapping.isEmpty()) {
            return existingChannels;
        }
        return existingChannels.stream()
                .map(channel -> channelMapping.getOrDefault(channel, channel))
                .toList();
    }

    private AlertPolicy toAlertPolicy(AlertPolicyRequest request, String existingName) {
        String conditionFilter = (request.filter() == null || request.filter().isBlank())
                ? "metric.type=\"" + request.metricType() + "\""
                : request.filter();

        Duration duration = Duration.newBuilder().setSeconds(Math.max(60, request.durationSeconds())).build();

        AlertPolicy.Condition.MetricThreshold threshold = AlertPolicy.Condition.MetricThreshold.newBuilder()
                .setFilter(conditionFilter)
                .setComparison(ComparisonType.COMPARISON_GT)
                .setThresholdValue(request.thresholdValue())
                .setDuration(duration)
                .build();

        Condition condition = Condition.newBuilder()
                .setDisplayName(request.displayName() + " threshold")
                .setConditionThreshold(threshold)
                .build();

        AlertPolicy.Builder builder = AlertPolicy.newBuilder()
                .setDisplayName(request.displayName())
                .setEnabled(BoolValue.of(request.enabled()))
                .setCombiner(AlertPolicy.ConditionCombinerType.OR)
                .addConditions(condition)
                .addAllNotificationChannels(request.notificationChannels() == null ? List.of() : request.notificationChannels());

        if (request.documentation() != null && !request.documentation().isBlank()) {
            builder.setDocumentation(AlertPolicy.Documentation.newBuilder().setContent(request.documentation()).setMimeType("text/markdown").build());
        }

        if (existingName != null) {
            builder.setName(existingName);
        }

        return builder.build();
    }

    private AlertPolicyResponse toAlertResponse(AlertPolicy policy) {
        return new AlertPolicyResponse(
                policy.getName(),
                policy.getDisplayName(),
                policy.getEnabled().getValue(),
                policy.getCombiner().name(),
                policy.getNotificationChannelsList()
        );
    }

    private NotificationChannelResponse toChannelResponse(NotificationChannel channel) {
        return new NotificationChannelResponse(
                channel.getName(),
                channel.getDisplayName(),
                channel.getType(),
                channel.getEnabled().getValue(),
                channel.getLabelsMap()
        );
    }

    private String normalizeAlertPolicyName(String id) {
        if (id.startsWith("projects/")) {
            return id;
        }
        return "projects/" + properties.projectId() + "/alertPolicies/" + id;
    }

    private String normalizeChannelName(String id) {
        if (id.startsWith("projects/")) {
            return id;
        }
        return "projects/" + properties.projectId() + "/notificationChannels/" + id;
    }
}
